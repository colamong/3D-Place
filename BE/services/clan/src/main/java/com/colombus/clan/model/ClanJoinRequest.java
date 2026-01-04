package com.colombus.clan.model;

import java.time.Instant;
import java.util.UUID;

import com.colombus.clan.jooq.tables.records.ClanJoinRequestRecord;
import com.colombus.clan.model.type.ClanJoinRequestStatus;

public record ClanJoinRequest(
    UUID id,
    UUID clanId,
    UUID userId,
    ClanJoinRequestStatus status,
    Instant createdAt,
    Instant updatedAt,
    Instant decidedAt
) {
    public static ClanJoinRequest fromRecord(ClanJoinRequestRecord r) {
        return new ClanJoinRequest(
            r.getId(),
            r.getClanId(),
            r.getUserId(),
            r.getStatus(),
            r.getCreatedAt(),
            r.getUpdatedAt(),
            r.getDecidedAt()
        );
    }
}