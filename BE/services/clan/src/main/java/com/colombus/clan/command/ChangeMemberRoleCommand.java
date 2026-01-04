package com.colombus.clan.command;

import java.util.UUID;
import com.colombus.clan.model.type.ClanMemberRole;

public record ChangeMemberRoleCommand(
    UUID clanId,
    UUID actorId,
    UUID targetUserId,
    ClanMemberRole newRole
) {}