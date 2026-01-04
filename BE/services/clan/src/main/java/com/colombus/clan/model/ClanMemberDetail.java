package com.colombus.clan.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.colombus.clan.jooq.tables.records.ClanMemberRecord;
import com.colombus.clan.model.type.ClanMemberRole;
import com.colombus.clan.model.type.ClanMemberStatus;

import jakarta.annotation.Nullable;

public record ClanMemberDetail(
    UUID id,
    UUID clanId,
    UUID userId,
    ClanMemberRole role,
    ClanMemberStatus status,
    long paintCountTotal,
    Instant joinedAt,
    @Nullable Instant leftAt
) {
    public static ClanMemberDetail fromRecord(ClanMemberRecord r) {
        return new ClanMemberDetail(
            r.getId(),
            r.getClanId(),
            r.getUserId(),
            Objects.requireNonNull(r.getRole()),
            Objects.requireNonNull(r.getStatus()),
            Objects.requireNonNull(r.getPaintCountTotal()),
            r.getJoinedAt(),
            r.getLeftAt()
        );
    }
}