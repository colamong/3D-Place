package com.colombus.leaderboard.contract.dto;

import java.util.UUID;

public record LeaderboardEntryResponse(
    UUID id,
    long paints
) {}
