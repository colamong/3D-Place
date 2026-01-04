package com.colombus.clan.command;

import java.util.UUID;

public record JoinClanCommand(
    UUID clanId,
    UUID userId
) {}