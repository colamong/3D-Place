package com.colombus.clan.repository;

import static com.colombus.clan.jooq.tables.ClanJoinRequest.*;
import static com.colombus.clan.jooq.tables.ClanMember.*;

import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import com.colombus.clan.exception.ClanErrorCode;
import com.colombus.clan.model.ClanJoinRequest;
import com.colombus.clan.model.ClanMemberDetail;
import com.colombus.clan.model.type.ClanJoinRequestStatus;
import com.colombus.clan.model.type.ClanMemberStatus;
import com.colombus.common.web.core.exception.InvalidInputException;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class ClanMemberQueryRepository {
    
    private final DSLContext dsl;

    /**
     * 해당 클랜의 ACTIVE 멤버 단건 조회
     */
    public Mono<ClanMemberDetail> requireActiveMember(UUID clanId, UUID userId) {
        return findActiveByClanAndUser(clanId, userId)
            .switchIfEmpty(Mono.error(new InvalidInputException(
                ClanErrorCode.CLAN_MEMBER_NOT_FOUND,
                "해당 클랜의 활성 멤버가 아닙니다."
            )));
    }
    
    public Mono<ClanMemberDetail> findActiveByClanAndUser(UUID clanId, UUID userId) {
        var m = CLAN_MEMBER;
        return Mono.from(
            dsl.selectFrom(m)
            .where(
                m.CLAN_ID.eq(clanId)
                    .and(m.USER_ID.eq(userId))
                    .and(m.STATUS.eq(ClanMemberStatus.ACTIVE))
                    .and(m.DELETED_AT.isNull())
            )
            .limit(1)
        ).map(ClanMemberDetail::fromRecord);
    }

    /**
     * 유저가 현재 가입한 클랜 멤버십 1개 조회 (ACTIVE 기준, 없으면 null).
     * 유저당 1클랜 제약(unique index) 전제.
     */
    public Mono<ClanMemberDetail> findActiveMemberByUser(UUID userId) {
        var m = CLAN_MEMBER;

        return Mono.from(
                dsl.selectFrom(m)
                .where(
                    m.USER_ID.eq(userId)
                        .and(m.STATUS.eq(ClanMemberStatus.ACTIVE))
                        .and(m.DELETED_AT.isNull())
                )
                .limit(1)
            )
            .map(ClanMemberDetail::fromRecord);
    }

    /**
     * 특정 클랜의 멤버 목록(상태 필터 optional).
     */
    public Flux<ClanMemberDetail> findMembersInClan(UUID clanId, @Nullable ClanMemberStatus status) {
        var m = CLAN_MEMBER;
        Condition condition = m.CLAN_ID.eq(clanId)
            .and(m.DELETED_AT.isNull());
        if (status != null) {
            condition = condition.and(m.STATUS.eq(status));
        }
        return Flux.from(
                dsl.selectFrom(m)
                    .where(condition)
                    .orderBy(m.JOINED_AT.asc())
            )
            .map(ClanMemberDetail::fromRecord);
    }

    public Flux<ClanMemberDetail> findActiveMembersInClan(UUID clanId) {
        return findMembersInClan(clanId, ClanMemberStatus.ACTIVE);
    }

    /**
     * 특정 클랜의 ACTIVE 유저 목록
     */
    public Flux<UUID> findActiveUsersInClan(UUID clanId) {
        var m = CLAN_MEMBER;

        return Flux.from(
                dsl.selectFrom(m)
                .where(
                    m.CLAN_ID.eq(clanId)
                        .and(m.STATUS.eq(ClanMemberStatus.ACTIVE))
                        .and(m.DELETED_AT.isNull())
                )
                .orderBy(m.JOINED_AT.asc())
            )
            .map(r -> r.getUserId());
    }

    public Flux<UUID> findPendingJoinRequestsByClan(
        UUID clanId,
        int limit,
        int offset
    ) {
        var m = CLAN_JOIN_REQUEST;

        return Flux.from(
            dsl.selectFrom(m)
            .where(m.CLAN_ID.eq(clanId)
                .and(m.STATUS.eq(ClanJoinRequestStatus.PENDING))
                .and(m.DELETED_AT.isNull())
            )
            .orderBy(m.CREATED_AT.asc())
            .limit(limit)
            .offset(offset)
        )
        .map(r -> r.getUserId());
    }

    public Mono<ClanJoinRequest> findPendingByClanAndUser(
        UUID clanId,
        UUID userId
    ) {
        var m = CLAN_JOIN_REQUEST;

        return Mono.from(
            dsl.selectFrom(m)
            .where(
                m.CLAN_ID.eq(clanId)
                    .and(m.USER_ID.eq(userId))
                    .and(m.STATUS.eq(ClanJoinRequestStatus.PENDING))
                    .and(m.DELETED_AT.isNull())
            )
            .orderBy(m.CREATED_AT.desc())
            .limit(1)
        )
        .map(ClanJoinRequest::fromRecord);
    }

    public Flux<UUID> findMyPendingJoinRequests(
        UUID actorId,
        List<UUID> clanIds
    ) {
        var m = CLAN_MEMBER;

        var cond = m.USER_ID.eq(actorId)
            .and(m.DELETED_AT.isNull())
            .and(m.STATUS.eq(ClanMemberStatus.PENDING));

        // 전달받은 클랜 ID들만 필터링
        cond = cond.and(m.CLAN_ID.in(clanIds));

        return Flux.from(
                dsl.selectDistinct(m.CLAN_ID)
                   .from(m)
                   .where(cond)
            )
            .map(r -> r.get(m.CLAN_ID));
    }

    /**
     * ACTIVE 멤버 여부만 필요할 때.
     */
    public boolean isActiveMember(UUID clanId, UUID userId) {
        var m = CLAN_MEMBER;

        return dsl.fetchExists(
            m,
            m.CLAN_ID.eq(clanId)
                .and(m.USER_ID.eq(userId))
                .and(m.STATUS.eq(ClanMemberStatus.ACTIVE))
                .and(m.DELETED_AT.isNull())
        );
    }
}
