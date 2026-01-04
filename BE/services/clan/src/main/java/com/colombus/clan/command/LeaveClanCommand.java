package com.colombus.clan.command;

import java.util.UUID;

public record LeaveClanCommand(
    UUID clanId,
    UUID userId
) {}