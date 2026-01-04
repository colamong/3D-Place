package com.colombus.clan.command;

import java.util.UUID;

public record ClanJoinRequestCommand(
    UUID clanId,
    UUID userId,
    String message
) {}