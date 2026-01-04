package com.colombus.clan.model;

import java.time.Instant;
import java.util.UUID;

import com.colombus.clan.jooq.tables.records.ClanInviteTicketRecord;

public record ClanInviteTicket(
    UUID id,
    UUID clanId,
    String code,
    UUID createdBy,
    int maxUses,
    int useCount,
    Instant expiresAt,
    boolean revoked,
    Instant createdAt,
    Instant updatedAt
) {
    public static ClanInviteTicket fromRecord(ClanInviteTicketRecord r) {
        return new ClanInviteTicket(
            r.getId(),
            r.getClanId(),
            r.getCode(),
            r.getCreatedBy(),
            r.getMaxUses(),
            r.getUseCount(),
            r.getExpiresAt(),
            Boolean.TRUE.equals(r.getIsRevoked()),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }
}