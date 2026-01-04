package com.colombus.clan.command;

import java.util.UUID;

public record ChangeClanOwnerCommand(
    UUID clanId,
    UUID actorId,
    UUID targetUserId
) {}