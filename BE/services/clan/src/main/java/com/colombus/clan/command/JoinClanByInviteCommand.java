package com.colombus.clan.command;

import java.util.UUID;

public record JoinClanByInviteCommand(
    UUID userId,
    String inviteCode
) {}