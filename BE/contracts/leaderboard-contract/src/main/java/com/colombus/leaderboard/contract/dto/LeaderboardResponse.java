package com.colombus.leaderboard.contract.dto;

import com.colombus.leaderboard.contract.enums.LeaderboardPeriodKey;
import com.colombus.leaderboard.contract.enums.LeaderboardSubjectType;
import java.util.List;

public record LeaderboardResponse(
    LeaderboardSubjectType subject,
    LeaderboardPeriodKey key,
    List<LeaderboardEntryResponse> entries
) {}
