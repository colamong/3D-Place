package com.colombus.clan.repository;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.colombus.clan.jooq.tables.ClanInfo.CLAN_INFO;
import com.colombus.clan.exception.ClanErrorCode;
import com.colombus.clan.model.type.ClanJoinPolicy;
import com.colombus.common.utility.json.Jsons;
import com.colombus.common.utility.time.TimeConv;
import com.colombus.common.web.core.exception.ConflictException;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class ClanCommandRepository {

    private final DSLContext dsl;

    public Mono<UUID> insertClan(String name, UUID ownerId, @Nullable String description, @Nullable ClanJoinPolicy policy) {
        String handle = normalizeHandle(name);
        ClanJoinPolicy effectivePolicy = (policy != null ? policy : ClanJoinPolicy.OPEN);
        boolean isPublic = resolveIsPublic(effectivePolicy);

        var insert = dsl.insertInto(CLAN_INFO)
                        .set(CLAN_INFO.OWNER_ID, ownerId)
                        .set(CLAN_INFO.HANDLE, handle)
                        .set(CLAN_INFO.NAME, name)
                        .set(CLAN_INFO.JOIN_POLICY, effectivePolicy)
                        .set(CLAN_INFO.IS_PUBLIC, isPublic);
        
        if (description != null) {
            insert = insert.set(CLAN_INFO.DESCRIPTION, description);
        }

        return Mono.from(insert.returning(CLAN_INFO.ID))
                     .map(r -> r.get(CLAN_INFO.ID));
    }

    public Mono<Void> incrementPaintCount(UUID clanId, long delta) {
        if (delta == 0L) return Mono.empty();

        return Mono.from(dsl.update(CLAN_INFO)
                .set(CLAN_INFO.PAINT_COUNT_TOTAL, CLAN_INFO.PAINT_COUNT_TOTAL.plus(delta))
                .set(CLAN_INFO.UPDATED_AT, TimeConv.nowUtc())
                .where(CLAN_INFO.ID.eq(clanId)
                        .and(CLAN_INFO.DELETED_AT.isNull())))
                .flatMap(updated -> {
                    if (updated == 0) {
                        return Mono.error(new IllegalStateException("Clan not found or already deleted: " + clanId));
                    }
                    return Mono.empty();
                });
    }

    public Mono<Void> incrementMemberCount(UUID clanId, int delta) {
        if (delta == 0) return Mono.empty();

        return Mono.from(dsl.update(CLAN_INFO)
                .set(CLAN_INFO.MEMBER_COUNT, CLAN_INFO.MEMBER_COUNT.plus(delta))
                .set(CLAN_INFO.UPDATED_AT, TimeConv.nowUtc())
                .where(CLAN_INFO.ID.eq(clanId)
                        .and(CLAN_INFO.DELETED_AT.isNull())))
                .flatMap(updated -> {
                    if (updated == 0) {
                        return Mono.error(new IllegalStateException("Clan not found or already deleted: " + clanId));
                    }
                    return Mono.empty();
                });
    }

    public Mono<Boolean> updateClan(
        UUID clanId,
        @Nullable UUID newOwnerId,
        @Nullable String newClanName,
        @Nullable String newDescription,
        @Nullable JsonNode metadataPatch,
        @Nullable ClanJoinPolicy newPolicy
    ) {
        return Mono.from(
                    dsl.selectFrom(CLAN_INFO)
                    .where(CLAN_INFO.ID.eq(clanId))
                    .and(CLAN_INFO.DELETED_AT.isNull())
            )
            .flatMap(r -> {
                boolean changed = false;

                UUID ownerId         = r.getOwnerId();
                String name          = r.getName();
                String handle        = r.getHandle();
                String description   = r.getDescription();
                ClanJoinPolicy policy = r.getJoinPolicy();
                Boolean isPublic     = r.getIsPublic();
                JsonNode metadata    = r.getMetadata();
                Instant oldUpdatedAt = r.getUpdatedAt();

                // owner
                if (newOwnerId != null && !newOwnerId.equals(ownerId)) {
                    ownerId = newOwnerId;
                    changed = true;
                }

                // name + handle
                boolean handleChanged = false;
                String  newHandle     = handle;

                if (newClanName != null && !newClanName.equals(name)) {
                    name = newClanName;

                    String normalized = normalizeHandle(newClanName);
                    if (!normalized.equals(handle)) {
                        newHandle = normalized;
                        handleChanged = true;
                    }

                    changed = true;
                }

                // description
                if (newDescription != null && !newDescription.equals(description)) {
                    description = newDescription;
                    changed = true;
                }

                // join policy + is_public
                if (newPolicy != null && newPolicy != policy) {
                    policy   = newPolicy;
                    isPublic = resolveIsPublic(newPolicy);
                    changed  = true;
                }

                // metadata patch
                if (metadataPatch != null) {
                    JsonNode merged = Jsons.mergePatchObject(metadata, metadataPatch);
                    if (!merged.equals(metadata)) {
                        metadata = merged;
                        changed  = true;
                    }
                }

                if (!changed) {
                    return Mono.just(true);
                }

                // ----- handle 유니크 체크 -----
                Mono<Void> handleCheckMono;
                if (handleChanged) {
                    String handleToCheck = newHandle;

                    handleCheckMono =
                        Mono.from(
                                dsl.selectCount()
                                    .from(CLAN_INFO)
                                    .where(
                                        CLAN_INFO.HANDLE.eq(handleToCheck)
                                            .and(CLAN_INFO.ID.ne(clanId))
                                            .and(CLAN_INFO.DELETED_AT.isNull())
                                    )
                            )
                            .flatMap(countRec -> {
                                if (countRec.value1() > 0) {
                                    return Mono.error(new ConflictException(ClanErrorCode.CLAN_NAME_ALREADY_EXISTS));
                                }
                                return Mono.<Void>empty();
                            });
                } else {
                    handleCheckMono = Mono.empty();
                }

                // ----- 낙관락 포함 UPDATE -----
                Instant now = TimeConv.nowUtc();

                Mono<Boolean> doUpdate =
                    Mono.from(
                            dsl.update(CLAN_INFO)
                                .set(CLAN_INFO.OWNER_ID,    ownerId)
                                .set(CLAN_INFO.NAME,        name)
                                .set(CLAN_INFO.HANDLE,      newHandle)
                                .set(CLAN_INFO.DESCRIPTION, description)
                                .set(CLAN_INFO.JOIN_POLICY, policy)
                                .set(CLAN_INFO.IS_PUBLIC,   isPublic)
                                .set(CLAN_INFO.METADATA,    metadata)
                                .set(CLAN_INFO.UPDATED_AT,  now)
                                .where(CLAN_INFO.ID.eq(clanId))
                                .and(CLAN_INFO.UPDATED_AT.eq(oldUpdatedAt))
                                .and(CLAN_INFO.DELETED_AT.isNull())
                        )
                        .map(rows -> rows == 1);

                return handleCheckMono.then(doUpdate);
            })
            .switchIfEmpty(Mono.just(false));
    }
    
    public Mono<Boolean> softDeleteClan(UUID clanId) {
        return Mono.from(
                    dsl.selectFrom(CLAN_INFO)
                    .where(CLAN_INFO.ID.eq(clanId))
                    .and(CLAN_INFO.DELETED_AT.isNull())
            )
            .flatMap(r -> {
                Instant oldUpdatedAt = r.getUpdatedAt();
                Instant now          = TimeConv.nowUtc();

                return Mono.from(
                        dsl.update(CLAN_INFO)
                            .set(CLAN_INFO.DELETED_AT, now)
                            .set(CLAN_INFO.UPDATED_AT, now)
                            .where(CLAN_INFO.ID.eq(clanId))
                            .and(CLAN_INFO.UPDATED_AT.eq(oldUpdatedAt))
                            .and(CLAN_INFO.DELETED_AT.isNull())
                    )
                    .map(rows -> rows == 1);
            })
            .switchIfEmpty(Mono.just(false));
    }

        private String normalizeHandle(String name) {
        String h = name.trim().toLowerCase(Locale.ROOT);
        if (h.isEmpty()) {
            throw new IllegalArgumentException("Clan name cannot be empty");
        }
        return h;
    }

    private boolean resolveIsPublic(ClanJoinPolicy policy) {
        return policy != ClanJoinPolicy.INVITE_ONLY;
    }
}
