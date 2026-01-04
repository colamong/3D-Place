package com.colombus.clan.repository;

import static com.colombus.clan.jooq.tables.ClanInfo.*;
import static com.colombus.clan.jooq.tables.ClanMember.*;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import com.colombus.clan.model.ClanDetail;
import com.colombus.clan.model.ClanSummary;
import com.colombus.clan.model.type.ClanJoinPolicy;
import com.colombus.clan.model.type.ClanMemberStatus;
import com.colombus.common.web.core.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class ClanQueryRepository {

    private final DSLContext dsl;

    public Mono<Boolean> existsAlive(UUID clanId) {
        return Mono.from(
                dsl.selectOne()
                .from(CLAN_INFO)
                .where(
                    CLAN_INFO.ID.eq(clanId)
                        .and(CLAN_INFO.DELETED_AT.isNull())
                )
            )
            .hasElement();
    }

    /**
     * 클랜 상세
     */
    public Mono<ClanDetail> requireClanDetail(UUID clanId) {
        var c = CLAN_INFO;

        return Mono.from(
                dsl.selectFrom(c)
                .where(
                    c.ID.eq(clanId)
                        .and(c.DELETED_AT.isNull())
                )
            )
            .switchIfEmpty(Mono.error(new NotFoundException("Clan not found: " + clanId)))
            .map(r -> {
                ClanJoinPolicy joinPolicy = Objects.requireNonNull(r.getJoinPolicy());

                return new ClanDetail(
                    r.getId(),
                    r.getName(),
                    r.getDescription(),
                    r.getOwnerId(),
                    joinPolicy,
                    Boolean.TRUE.equals(r.getIsPublic()),
                    Objects.requireNonNull(r.getMemberCount()),
                    Objects.requireNonNull(r.getPaintCountTotal()),
                    r.getMetadata(),
                    r.getCreatedAt(),
                    r.getUpdatedAt()
                );
            });
    }
    
    /**
     * 공개 클랜 검색
     */
    public Flux<ClanSummary> findPublicClans(List<ClanJoinPolicy> policies, String keyword, int limit, int offset) {
        var c = CLAN_INFO;

        var cond = c.DELETED_AT.isNull()
                        .and(c.IS_PUBLIC.isTrue())
                        .and(c.JOIN_POLICY.ne(ClanJoinPolicy.INVITE_ONLY));

        if (policies != null && !policies.isEmpty()) {
            List<ClanJoinPolicy> filtered = policies.stream()
                .filter(p -> p != ClanJoinPolicy.INVITE_ONLY)
                .toList();
            cond = cond.and(c.JOIN_POLICY.in(filtered));
        }

        if (keyword != null && !keyword.isBlank()) {
            String k = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            cond = cond.and(
                    DSL.lower(c.NAME).like(k)
                       .or(DSL.lower(c.HANDLE).like(k))
            );
        }

        return Flux.from(
                dsl.select(
                        c.ID,
                        c.NAME,
                        c.DESCRIPTION,
                        c.IS_PUBLIC,
                        c.JOIN_POLICY,
                        c.MEMBER_COUNT,
                        c.PAINT_COUNT_TOTAL
                    )
                    .from(c)
                    .where(cond)
                    .orderBy(c.MEMBER_COUNT.desc(), c.CREATED_AT.desc())
                    .limit(limit)
                    .offset(offset)
            )
            .map(rec -> new ClanSummary(
                    rec.get(c.ID),
                    rec.get(c.NAME),
                    rec.get(c.DESCRIPTION),
                    Boolean.TRUE.equals(rec.get(c.IS_PUBLIC)),
                    rec.get(c.JOIN_POLICY),
                    rec.get(c.MEMBER_COUNT),
                    rec.get(c.PAINT_COUNT_TOTAL)
            ));
    }

    /**
     * 유저가 가입한 클랜 1개 조회 (OWNER 포함).
     * - ACTIVE 멤버십 기준
     * - 없으면 null
     */
    public Mono<ClanSummary> findClanByMember(UUID userId) {
        var c = CLAN_INFO;
        var m = CLAN_MEMBER;

        return Mono.from(
                dsl.select(
                        c.ID,
                        c.NAME,
                        c.DESCRIPTION,
                        c.IS_PUBLIC,
                        c.JOIN_POLICY,
                        c.MEMBER_COUNT,
                        c.PAINT_COUNT_TOTAL
                    )
                    .from(c)
                    .join(m)
                        .on(m.CLAN_ID.eq(c.ID)
                            .and(m.USER_ID.eq(userId))
                            .and(m.STATUS.eq(ClanMemberStatus.ACTIVE))
                            .and(m.DELETED_AT.isNull()))
                    .where(c.DELETED_AT.isNull())
            )
            .map(rec -> new ClanSummary(
                rec.get(c.ID),
                rec.get(c.NAME),
                rec.get(c.DESCRIPTION),
                Boolean.TRUE.equals(rec.get(c.IS_PUBLIC)),
                rec.get(c.JOIN_POLICY),
                rec.get(c.MEMBER_COUNT),
                rec.get(c.PAINT_COUNT_TOTAL)
            ));
    }

    public Mono<Boolean> isOwner(UUID clanId, UUID userId) {
        return Mono.from(
                dsl.selectOne()
                .from(CLAN_INFO)
                .where(
                    CLAN_INFO.ID.eq(clanId)
                        .and(CLAN_INFO.DELETED_AT.isNull())
                        .and(CLAN_INFO.OWNER_ID.eq(userId))
                )
            )
            .hasElement();
    }

    /**
     * 주어진 클랜 ID 목록에 해당하는 요약 정보를 조회한다.
     */
    public Flux<ClanSummary> findClansByIds(List<UUID> clanIds) {
        if (clanIds == null || clanIds.isEmpty()) {
            return Flux.empty();
        }
        var c = CLAN_INFO;
        return Flux.from(
                dsl.select(
                        c.ID,
                        c.NAME,
                        c.DESCRIPTION,
                        c.IS_PUBLIC,
                        c.JOIN_POLICY,
                        c.MEMBER_COUNT,
                        c.PAINT_COUNT_TOTAL
                    )
                    .from(c)
                    .where(
                        c.ID.in(clanIds)
                            .and(c.DELETED_AT.isNull())
                    )
            )
            .map(rec -> new ClanSummary(
                rec.get(c.ID),
                rec.get(c.NAME),
                rec.get(c.DESCRIPTION),
                Boolean.TRUE.equals(rec.get(c.IS_PUBLIC)),
                rec.get(c.JOIN_POLICY),
                rec.get(c.MEMBER_COUNT),
                rec.get(c.PAINT_COUNT_TOTAL)
            ));
    }
}
