package com.colombus.clan.command;

import java.util.UUID;
import com.colombus.clan.model.type.ClanMemberStatus;

public record ChangeMemberStatusCommand(
    UUID clanId,
    UUID actorId,
    UUID targetUserId,
    ClanMemberStatus newStatus
) {}