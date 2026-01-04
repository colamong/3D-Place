package com.colombus.clan.command;

import java.util.UUID;

public record DeleteClanCommand(
    UUID clanId,
    UUID actorId
) {}