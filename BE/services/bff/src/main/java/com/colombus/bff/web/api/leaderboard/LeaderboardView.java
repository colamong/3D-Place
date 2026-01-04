package com.colombus.bff.web.api.leaderboard;

import com.colombus.leaderboard.contract.enums.LeaderboardPeriodKey;
import com.colombus.leaderboard.contract.enums.LeaderboardSubjectType;
import java.util.List;
import java.util.UUID;

public record LeaderboardView(
    LeaderboardSubjectType subject,
    LeaderboardPeriodKey key,
    List<LeaderboardEntryView> entries
) {

    public record LeaderboardEntryView(
        UUID id,
        String name,
        long paints,
        int rank
    ) {}
}
