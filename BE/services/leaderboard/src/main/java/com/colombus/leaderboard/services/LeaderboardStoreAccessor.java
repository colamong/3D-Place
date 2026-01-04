package com.colombus.leaderboard.services;

import com.colombus.leaderboard.contract.dto.LeaderboardEntryResponse;
import com.colombus.leaderboard.contract.enums.LeaderboardPeriodKey;
import com.colombus.leaderboard.contract.enums.LeaderboardSubjectType;
import com.colombus.leaderboard.kafka.config.LeaderboardTopologyConfig;
import com.colombus.leaderboard.kafka.config.LeaderboardTopologyConfig.ClanPeriodKey;
import com.colombus.leaderboard.kafka.config.LeaderboardTopologyConfig.UserPeriodKey;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

@Component
public class LeaderboardStoreAccessor {

    private static final int MAX_LIMIT = 100;

    private final StreamsBuilderFactoryBean kafkaStreamsFactory;

    public LeaderboardStoreAccessor(StreamsBuilderFactoryBean kafkaStreamsFactory) {
        this.kafkaStreamsFactory = kafkaStreamsFactory;
    }

    public List<LeaderboardEntryResponse> getLeaderboard(
        LeaderboardSubjectType subject,
        LeaderboardPeriodKey periodKey,
        int limit
    ) {
        int sanitizedLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return switch (subject) {
            case USER -> fetchUserLeaderboard(periodKey, sanitizedLimit);
            case CLAN -> fetchClanLeaderboard(periodKey, sanitizedLimit);
        };
    }

    private List<LeaderboardEntryResponse> fetchUserLeaderboard(LeaderboardPeriodKey key, int limit) {
        return switch (key) {
            case DAY -> fetchUserPeriodStore(
                LeaderboardTopologyConfig.USER_DAILY_PAINTS_STORE,
                currentDay(),
                limit
            );
            case WEEK -> fetchUserPeriodStore(
                LeaderboardTopologyConfig.USER_WEEKLY_PAINTS_STORE,
                currentWeekStart(),
                limit
            );
            case MONTH -> fetchUserPeriodStore(
                LeaderboardTopologyConfig.USER_MONTHLY_PAINTS_STORE,
                currentMonthStart(),
                limit
            );
        };
    }

    private List<LeaderboardEntryResponse> fetchClanLeaderboard(LeaderboardPeriodKey key, int limit) {
        return switch (key) {
            case DAY -> fetchClanPeriodStore(
                LeaderboardTopologyConfig.CLAN_DAILY_PAINTS_STORE,
                currentDay(),
                limit
            );
            case WEEK -> fetchClanPeriodStore(
                LeaderboardTopologyConfig.CLAN_WEEKLY_PAINTS_STORE,
                currentWeekStart(),
                limit
            );
            case MONTH -> fetchClanPeriodStore(
                LeaderboardTopologyConfig.CLAN_MONTHLY_PAINTS_STORE,
                currentMonthStart(),
                limit
            );
        };
    }

    private List<LeaderboardEntryResponse> fetchUserPeriodStore(
        String storeName,
        LocalDate periodStart,
        int limit
    ) {
        ReadOnlyKeyValueStore<UserPeriodKey, Long> store = readStore(storeName);
        if (store == null) {
            return List.of();
        }
        return collectEntries(
            store,
            key -> periodStart.equals(key.periodStart()),
            UserPeriodKey::userId,
            limit
        );
    }

    private List<LeaderboardEntryResponse> fetchClanPeriodStore(
        String storeName,
        LocalDate periodStart,
        int limit
    ) {
        ReadOnlyKeyValueStore<ClanPeriodKey, Long> store = readStore(storeName);
        if (store == null) {
            return List.of();
        }
        return collectEntries(
            store,
            key -> periodStart.equals(key.periodStart()),
            ClanPeriodKey::clanId,
            limit
        );
    }

    private <K> List<LeaderboardEntryResponse> collectEntries(
        ReadOnlyKeyValueStore<K, Long> store,
        Predicate<K> keyPredicate,
        Function<K, UUID> idExtractor,
        int limit
    ) {
        List<LeaderboardEntryResponse> rows = new ArrayList<>();
        try (KeyValueIterator<K, Long> iterator = store.all()) {
            while (iterator.hasNext()) {
                var next = iterator.next();
                if (!keyPredicate.test(next.key)) {
                    continue;
                }
                long paints = next.value != null ? next.value : 0L;
                rows.add(new LeaderboardEntryResponse(idExtractor.apply(next.key), paints));
            }
        }
        rows.sort(Comparator.comparingLong(LeaderboardEntryResponse::paints).reversed());
        return rows.stream()
            .limit(limit)
            .toList();
    }

    private <K> ReadOnlyKeyValueStore<K, Long> readStore(String storeName) {
        KafkaStreams streams = kafkaStreamsFactory.getKafkaStreams();
        if (streams == null) {
            return null;
        }
        return streams.store(
            StoreQueryParameters.fromNameAndType(
                storeName,
                QueryableStoreTypes.<K, Long>keyValueStore()
            )
        );
    }

    private LocalDate currentDay() {
        return LocalDate.now(LeaderboardTopologyConfig.AGGREGATION_ZONE);
    }

    private LocalDate currentWeekStart() {
        return currentDay()
            .with(TemporalAdjusters.previousOrSame(LeaderboardTopologyConfig.WEEK_START));
    }

    private LocalDate currentMonthStart() {
        return currentDay().withDayOfMonth(1);
    }
}
