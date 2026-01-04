package com.colombus.clan.command;

import java.util.UUID;

public record IssueClanInviteCommand(
    UUID clanId,
    UUID actorId,
    int maxUses   // 0 = 무제한
) {}