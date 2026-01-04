package com.colombus.clan.command;

import java.util.UUID;

public record CancelJoinClanCommand(
    UUID clanId,
    UUID actorId
) {}