package com.colombus.leaderboard.web.internal;

import com.colombus.leaderboard.contract.dto.LeaderboardResponse;
import com.colombus.leaderboard.contract.enums.LeaderboardPeriodKey;
import com.colombus.leaderboard.contract.enums.LeaderboardSubjectType;
import com.colombus.leaderboard.services.LeaderboardStoreAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/leaderboard")
public class LeaderboardQueryController {

    private final LeaderboardStoreAccessor accessor;

    public LeaderboardQueryController(LeaderboardStoreAccessor accessor) {
        this.accessor = accessor;
    }

    @GetMapping
    public LeaderboardResponse getLeaderboard(
        @RequestParam LeaderboardSubjectType subject,
        @RequestParam LeaderboardPeriodKey key,
        @RequestParam(defaultValue = "50") int size
    ) {
        return new LeaderboardResponse(
            subject,
            key,
            accessor.getLeaderboard(subject, key, size)
        );
    }
}
