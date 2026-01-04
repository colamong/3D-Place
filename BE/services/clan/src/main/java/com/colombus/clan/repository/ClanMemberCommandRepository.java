package com.colombus.clan.repository;

import static com.colombus.clan.jooq.tables.ClanMember.*;
import static com.colombus.clan.jooq.tables.ClanJoinRequest.*;
import static com.colombus.clan.jooq.tables.ClanInviteTicket.*;
import java.time.Instant;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import com.colombus.clan.model.ClanInviteTicket;
import com.colombus.clan.model.type.ClanJoinRequestStatus;
import com.colombus.clan.model.type.ClanMemberRole;
import com.colombus.clan.model.type.ClanMemberStatus;
import com.colombus.common.utility.time.TimeConv;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class ClanMemberCommandRepository {
    
    private final DSLContext dsl;

    /**
     * 새 멤버 생성 (owner 포함).
     * - joined_at, paint_count_total 등은 DB default 사용
     */
    public Mono<UUID> insertMember(
        UUID clanId,
        UUID userId,
        ClanMemberRole role,
        ClanMemberStatus status
    ) {
        return Mono.from(
                dsl.insertInto(CLAN_MEMBER)
                .set(CLAN_MEMBER.CLAN_ID, clanId)
                .set(CLAN_MEMBER.USER_ID, userId)
                .set(CLAN_MEMBER.ROLE,   role)
                .set(CLAN_MEMBER.STATUS, status)
                .returning(CLAN_MEMBER.ID)
            )
            .map(r -> r.get(CLAN_MEMBER.ID));
    }

    
    /**
     * 새 가입 요청 생성.
     * - 상태는 DB default(PENDING)로 설정됨
     */
    public Mono<UUID> insertJoinRequest(
        UUID clanId,
        UUID userId
    ) {
        return Mono.from(
                dsl.insertInto(CLAN_JOIN_REQUEST)
                .set(CLAN_JOIN_REQUEST.CLAN_ID, clanId)
                .set(CLAN_JOIN_REQUEST.USER_ID, userId)
                .returning(CLAN_JOIN_REQUEST.ID)
        )
        .map(r -> r.get(CLAN_JOIN_REQUEST.ID));
    }

    /**
     * 가입 요청 상태 변경 (PENDING → APPROVED/REJECTED/CANCELLED).
     * - 승인/거절 시에만 reviewer_id와 decided_at을 세팅
     * - 낙관적 락: updated_at 기반
     */
    public Mono<Boolean> updateJoinRequestStatus(
        UUID joinRequestId,
        ClanJoinRequestStatus newStatus,
        @Nullable UUID reviewerId
    ) {
        var m = CLAN_JOIN_REQUEST;

        return Mono.from(
                dsl.selectFrom(m)
                .where(
                    m.ID.eq(joinRequestId)
                        .and(m.STATUS.eq(ClanJoinRequestStatus.PENDING))
                        .and(m.DELETED_AT.isNull())
                )
            )
            .flatMap(r -> {
                if (r == null) {
                    return Mono.just(false);
                }

                // 동일 상태면 굳이 업데이트 안 함
                if (newStatus == r.getStatus()) {
                    return Mono.just(true);
                }

                Instant oldUpdatedAt = r.getUpdatedAt();
                Instant now          = TimeConv.nowUtc();

                var updateQuery = dsl.update(m)
                    .set(m.STATUS, newStatus)
                    .set(m.UPDATED_AT, now);

                // 승인 / 거절일 때만 reviewer + decided_at 세팅
                if (newStatus == ClanJoinRequestStatus.APPROVED
                    || newStatus == ClanJoinRequestStatus.REJECTED) {

                    updateQuery.set(m.REVIEWER_ID, reviewerId);
                    updateQuery.set(m.DECIDED_AT,   now);
                }

                return Mono.from(
                        updateQuery
                            .where(
                                m.ID.eq(joinRequestId)
                                    .and(m.STATUS.eq(ClanJoinRequestStatus.PENDING))
                                    .and(m.DECIDED_AT.isNull())
                                    .and(m.DELETED_AT.isNull())
                                    .and(m.UPDATED_AT.eq(oldUpdatedAt))
                            )
                    )
                    .map(rows -> rows == 1);
            })
            .defaultIfEmpty(false);
    }

    /**
     * 본인의 가입 요청 취소 (PENDING → CANCELLED).
     * - 낙관적 락: updated_at 기반
     */
    public Mono<Boolean> cancelOwnJoinRequest(UUID clanId, UUID userId) {
        var m = CLAN_JOIN_REQUEST;

        return Mono.from(
                dsl.selectFrom(m)
                    .where(
                        m.CLAN_ID.eq(clanId)
                            .and(m.USER_ID.eq(userId))
                            .and(m.STATUS.eq(ClanJoinRequestStatus.PENDING))
                            .and(m.DELETED_AT.isNull())
                    )
                )
                .flatMap(r -> {
                    Instant oldUpdatedAt = r.getUpdatedAt();
                    Instant now          = TimeConv.nowUtc();

                    return Mono.from(
                            dsl.update(m)
                                .set(m.STATUS, ClanJoinRequestStatus.CANCELLED)
                                .set(m.UPDATED_AT, now)
                                .where(
                                    m.CLAN_ID.eq(clanId)
                                        .and(m.USER_ID.eq(userId))
                                        .and(m.STATUS.eq(ClanJoinRequestStatus.PENDING))
                                        .and(m.DECIDED_AT.isNull())
                                        .and(m.DELETED_AT.isNull())
                                        .and(m.UPDATED_AT.eq(oldUpdatedAt))
                                )
                        )
                        .map(rows -> rows == 1);
                })
                .defaultIfEmpty(false);
    }

    /**
     * 초대 티켓 발급 (무제한 사용).
     * - maxUses = 0 (무제한)
     */
    public Mono<ClanInviteTicket> issueInviteTicket(
        UUID clanId,
        UUID createdBy
    ) {
        // 기본값: maxUses = 0 (무제한)
        return issueInviteTicket(clanId, createdBy, 0);
    }

    /**
     * 초대 티켓 발급 (사용 횟수 제한 옵션).
     * - maxUses = 0이면 무제한, > 0이면 제한된 횟수만 사용 가능
     * - DB의 create_clan_invite_ticket 스토어드 프로시저 호출
     */
    public Mono<ClanInviteTicket> issueInviteTicket(
        UUID clanId,
        UUID createdBy,
        int maxUses
    ) {
        // maxUses = 0 이면 무제한
        return Mono.from(
                dsl.resultQuery(
                    "select * from create_clan_invite_ticket(?, ?, ?)",
                    clanId, createdBy, maxUses
                )
            )
            .map(r -> ClanInviteTicket.fromRecord(r.into(CLAN_INVITE_TICKET)));
    }

    /**
     * 유효한 초대 티켓 소비 (사용 횟수 증가 및 검증).
     * - DB의 consume_clan_invite_ticket 스토어드 프로시저 호출
     * - 유효성 검사 및 사용 횟수 차감은 DB에서 처리
     */
    public Mono<ClanInviteTicket> consumeValidTicket(String code) {
        return Mono.from(
                dsl.resultQuery(
                    "select * from consume_clan_invite_ticket(?)",
                    code
                )
            )
            .map(r -> ClanInviteTicket.fromRecord(r.into(CLAN_INVITE_TICKET)));
    }

    /**
     * 역할 변경 (MASTER/OFFICER/MEMBER).
     * - 낙관적 락: updated_at 기반
     *
     * @return true  - 정상 변경
     *         false - 미존재 / 삭제됨 / 락 충돌
     */
    public Mono<Boolean> updateMemberRole(
        UUID clanId,
        UUID userId,
        ClanMemberRole newRole
    ) {

        var m = CLAN_MEMBER;

        return Mono.from(
                dsl.selectFrom(m)
                .where(
                    m.CLAN_ID.eq(clanId)
                        .and(m.USER_ID.eq(userId))
                        .and(m.DELETED_AT.isNull())
                )
            )
            .flatMap(r -> {
                if (r == null) {
                    return Mono.just(false);
                }

                // 동일 role이면 굳이 업데이트 안 함
                if (newRole == r.getRole()) {
                    return Mono.just(true);
                }

                Instant oldUpdatedAt = r.getUpdatedAt();
                Instant now          = TimeConv.nowUtc();

                return Mono.from(
                        dsl.update(m)
                        .set(m.ROLE,       newRole)
                        .set(m.UPDATED_AT, now)
                        .where(
                            m.CLAN_ID.eq(clanId)
                                .and(m.USER_ID.eq(userId))
                                .and(m.DELETED_AT.isNull())
                                .and(m.UPDATED_AT.eq(oldUpdatedAt))
                        )
                    )
                    .map(rows -> rows == 1);
            })
            .defaultIfEmpty(false);
    }

    /**
     * 상태 변경 (ACTIVE/INVITED/PENDING/LEFT/KICKED/BANNED).
     * - LEFT/KICKED/BANNED 로 바뀔 때 left_at 자동 세팅
     */
    public Mono<Boolean> updateMemberStatus(
        UUID clanId,
        UUID userId,
        ClanMemberStatus newStatus
    ) {
        var m = CLAN_MEMBER;

        return Mono.from(
                dsl.selectFrom(m)
                .where(
                    m.CLAN_ID.eq(clanId)
                        .and(m.USER_ID.eq(userId))
                        .and(m.DELETED_AT.isNull())
                )
            )
            .flatMap(r -> {
                if (r == null) {
                    return Mono.just(false);
                }

                ClanMemberStatus oldStatus = r.getStatus();
                if (newStatus == oldStatus) {
                    return Mono.just(true);
                }

                Instant oldUpdatedAt = r.getUpdatedAt();
                Instant now          = TimeConv.nowUtc();

                Instant leftAt = r.getLeftAt();
                if (isTerminalStatus(newStatus) && leftAt == null) {
                    leftAt = TimeConv.nowUtc();
                }

                var newStatusField = DSL.val(newStatus, m.STATUS.getDataType());
                var deletedAtExpr =
                    DSL.when(
                        newStatusField.in(
                            ClanMemberStatus.LEFT,
                            ClanMemberStatus.KICKED
                        ),
                        DSL.coalesce(m.DELETED_AT, DSL.val(now))
                    )
                    .otherwise(m.DELETED_AT);

                return Mono.from(
                        dsl.update(m)
                        .set(m.STATUS,     newStatus)
                        .set(m.LEFT_AT,    leftAt)
                        .set(m.UPDATED_AT, now)
                        .set(m.DELETED_AT, deletedAtExpr)
                        .where(
                            m.CLAN_ID.eq(clanId)
                                .and(m.USER_ID.eq(userId))
                                .and(m.DELETED_AT.isNull())
                                .and(m.UPDATED_AT.eq(oldUpdatedAt))
                        )
                    )
                    .map(rows -> rows == 1);
            })
            .defaultIfEmpty(false);
    }

    public Mono<Boolean> softDeleteAllByClanId(UUID clanId) {
        var now = TimeConv.nowUtc();
        return Mono.from(
                dsl.update(CLAN_MEMBER)
                .set(
                    CLAN_MEMBER.LEFT_AT,
                    DSL.when(
                        CLAN_MEMBER.STATUS.eq(ClanMemberStatus.ACTIVE)
                            .and(CLAN_MEMBER.LEFT_AT.isNull()),
                        now
                    ).otherwise(CLAN_MEMBER.LEFT_AT)
                )
                .set(CLAN_MEMBER.UPDATED_AT, now)
                .set(CLAN_MEMBER.DELETED_AT, now)
                .where(
                    CLAN_MEMBER.CLAN_ID.eq(clanId)
                        .and(CLAN_MEMBER.DELETED_AT.isNull())
                )
            )
            .map(updated -> updated > 0);
    }

    /**
     * 멤버십 soft delete.
     * - 일반적으로 ACTIVE → LEFT 로 바꾸고, left_at / deleted_at 세팅
     * - 완전 제거가 아니라 "이 멤버십은 끝났다" 정도의 의미
     */
    public Mono<Boolean> softDeleteMember(UUID clanId, UUID userId) {
        var m = CLAN_MEMBER;

        return Mono.from(
                dsl.selectFrom(m)
                .where(
                    m.CLAN_ID.eq(clanId)
                        .and(m.USER_ID.eq(userId))
                        .and(m.DELETED_AT.isNull())
                )
            )
            .flatMap(r -> {
                if (r == null) {
                    return Mono.just(false);
                }

                ClanMemberStatus status = r.getStatus();
                Instant now      = TimeConv.nowUtc();
                Instant leftAt   = r.getLeftAt();
                Instant oldUpdatedAt = r.getUpdatedAt();

                if (status == ClanMemberStatus.ACTIVE) {
                    status = ClanMemberStatus.LEFT;
                }
                if (leftAt == null) {
                    leftAt = now;
                }

                return Mono.from(
                        dsl.update(m)
                        .set(m.STATUS,     status)
                        .set(m.LEFT_AT,    leftAt)
                        .set(m.DELETED_AT, now)
                        .set(m.UPDATED_AT, now)
                        .where(
                            m.CLAN_ID.eq(clanId)
                                .and(m.USER_ID.eq(userId))
                                .and(m.DELETED_AT.isNull())
                                .and(m.UPDATED_AT.eq(oldUpdatedAt))
                        )
                    )
                    .map(rows -> rows == 1);
            })
            .defaultIfEmpty(false);
    }

    /**
     * 멤버별 paint 누적 카운터 증가.
     */
    public Mono<Void> incrementMemberPaintCount(UUID clanId, UUID userId, long delta) {
        if (delta == 0L) return Mono.empty();

        return Mono.from(
                dsl.update(CLAN_MEMBER)
                .set(CLAN_MEMBER.PAINT_COUNT_TOTAL, CLAN_MEMBER.PAINT_COUNT_TOTAL.plus(delta))
                .where(
                    CLAN_MEMBER.CLAN_ID.eq(clanId)
                        .and(CLAN_MEMBER.USER_ID.eq(userId))
                        .and(CLAN_MEMBER.DELETED_AT.isNull())))
                .flatMap(updated -> {
                    if (updated == 0) {
                        return Mono.error(new IllegalStateException("Active clan member not found: clan=" + clanId + ", user=" + userId));
                    }
                    return Mono.empty();
                });
    }

    public Mono<Long> expireExpiredInvites(int graceSec) {
        // expire_clan_invite_tickets(p_grace_sec) 호출
        Field<Long> f = DSL.field(
            "expire_clan_invite_tickets({0})",
            SQLDataType.BIGINT,
            DSL.val(graceSec)
        );

        return Mono.from(dsl.select(f))
                   .map(r -> r.value1());  // 만료 처리된 행 수
    }

    // ================== 내부 헬퍼 ==================

    private boolean isTerminalStatus(ClanMemberStatus s) {
        return s == ClanMemberStatus.LEFT
                || s == ClanMemberStatus.KICKED
                || s == ClanMemberStatus.BANNED;
    }
}
