package com.colombus.clan.command;

import java.util.UUID;

public record IssueInviteLinkCommand(
    UUID clanId,
    UUID actorId,
    int maxUses
) {}