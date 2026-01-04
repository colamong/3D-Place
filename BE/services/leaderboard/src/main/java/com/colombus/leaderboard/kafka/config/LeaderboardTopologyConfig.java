package com.colombus.leaderboard.kafka.config;

import com.colombus.common.kafka.paint.event.PaintEvent;
import com.colombus.common.kafka.paint.model.PaintOperationType;
import com.colombus.common.kafka.subject.model.ClanMemberKey;
import com.colombus.common.kafka.subject.model.UserClanState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

@Configuration
public class LeaderboardTopologyConfig {

    public static final String PAINT_EVENTS_TOPIC              = "colombus-paint-events";
    public static final String CLAN_MEMBERSHIP_TOPIC           = "clan.membership.current.v1";
    public static final String USER_PAINT_COUNTS_TOPIC         = "leaderboard.user-paints.v1";
    public static final String CLAN_PAINT_COUNTS_TOPIC         = "leaderboard.clan-paints.v1";
    public static final String CLAN_MEMBER_PAINT_COUNTS_TOPIC  = "leaderboard.clan-member-paints.v1";

    public static final String USER_DAILY_PAINT_COUNTS_TOPIC = "leaderboard.user-daily-paints.v1";
    public static final String USER_WEEKLY_PAINT_COUNTS_TOPIC = "leaderboard.user-weekly-paints.v1";
    public static final String USER_MONTHLY_PAINT_COUNTS_TOPIC = "leaderboard.user-monthly-paints.v1";

    public static final String CLAN_DAILY_PAINT_COUNTS_TOPIC = "leaderboard.clan-daily-paints.v1";
    public static final String CLAN_WEEKLY_PAINT_COUNTS_TOPIC = "leaderboard.clan-weekly-paints.v1";
    public static final String CLAN_MONTHLY_PAINT_COUNTS_TOPIC = "leaderboard.clan-monthly-paints.v1";

    public static final String CLAN_MEMBER_DAILY_PAINT_COUNTS_TOPIC = "leaderboard.clan-member-daily-paints.v1";
    public static final String CLAN_MEMBER_WEEKLY_PAINT_COUNTS_TOPIC = "leaderboard.clan-member-weekly-paints.v1";
    public static final String CLAN_MEMBER_MONTHLY_PAINT_COUNTS_TOPIC = "leaderboard.clan-member-monthly-paints.v1";

    public static final String USER_DAILY_PAINTS_STORE = "user_daily_paints_store";
    public static final String USER_WEEKLY_PAINTS_STORE = "user_weekly_paints_store";
    public static final String USER_MONTHLY_PAINTS_STORE = "user_monthly_paints_store";

    public static final String CLAN_DAILY_PAINTS_STORE = "clan_daily_paints_store";
    public static final String CLAN_WEEKLY_PAINTS_STORE = "clan_weekly_paints_store";
    public static final String CLAN_MONTHLY_PAINTS_STORE = "clan_monthly_paints_store";
    public static final String CLAN_MEMBER_DAILY_PAINTS_STORE = "clan_member_daily_paints_store";
    public static final String CLAN_MEMBER_WEEKLY_PAINTS_STORE = "clan_member_weekly_paints_store";
    public static final String CLAN_MEMBER_MONTHLY_PAINTS_STORE = "clan_member_monthly_paints_store";

    public static final ZoneId AGGREGATION_ZONE = ZoneId.of("Asia/Seoul");
    public static final DayOfWeek WEEK_START = DayOfWeek.SUNDAY;

    // paint + clan join 결과 모델
    private record PaintWithClan(UUID userId, UUID clanId, long amount, Instant occurredAt) {}

    public static record UserPeriodKey(UUID userId, LocalDate periodStart) {}
    public static record ClanPeriodKey(UUID clanId, LocalDate periodStart) {}
    public static record ClanMemberPeriodKey(UUID clanId, UUID userId, LocalDate periodStart) {}

    @Bean
    public KStream<UUID, PaintEvent> leaderboardTopology(
        StreamsBuilder builder,
        Serde<UUID> uuidSerde,
        Serde<PaintEvent> paintEventSerde,
        Serde<UserClanState> userClanStateSerde,
        Serde<ClanMemberKey> clanMemberKeySerde,
        ObjectMapper objectMapper
    ) {
        // 소스 스트림 (Key: opId, Value: PaintEvent)
        KStream<UUID, PaintEvent> paintsStream = builder.stream(
            PAINT_EVENTS_TOPIC,
            Consumed.with(uuidSerde, paintEventSerde)
        );

        // Rekeying 및 필터링
        // Key를 opId에서 actor(userId)로 변경하고, 점수 집계 대상(UPSERT)만 필터링
        KStream<UUID, PaintEvent> paintsByActor = paintsStream
            .filter((key, event) ->
                event.actor() != null && event.operationType() == PaintOperationType.UPSERT
            )
            .selectKey((key, event) -> event.actor());

        // userId -> 현재 클랜 상태 KTable
        KTable<UUID, UserClanState> userClanTable = builder.table(
            CLAN_MEMBERSHIP_TOPIC,
            Consumed.with(uuidSerde, userClanStateSerde),
            Materialized.<UUID, UserClanState, KeyValueStore<Bytes, byte[]>>as("user_clan_store")
                .withKeySerde(uuidSerde)
                .withValueSerde(userClanStateSerde)
        );

        JsonSerde<PaintWithClan> paintWithClanSerde = jsonSerde(PaintWithClan.class, objectMapper);
        JsonSerde<UserPeriodKey> userPeriodKeySerde = jsonSerde(UserPeriodKey.class, objectMapper);
        JsonSerde<ClanPeriodKey> clanPeriodKeySerde = jsonSerde(ClanPeriodKey.class, objectMapper);
        JsonSerde<ClanMemberPeriodKey> clanMemberPeriodKeySerde = jsonSerde(ClanMemberPeriodKey.class, objectMapper);

        // userId(actor) 기준으로 Join
        KStream<UUID, PaintWithClan> paintWithClan = paintsByActor.leftJoin(
            userClanTable,
            (event, clanState) -> {
                UUID clanId = null;
                if (clanState != null && clanState.active() && clanState.clanId() != null) {
                    clanId = clanState.clanId();
                }
                return new PaintWithClan(event.actor(), clanId, 1L, event.timestamp());
            },
            Joined.with(uuidSerde, paintEventSerde, userClanStateSerde)
        );

        // ============================
        // 유저별 누적 카운트
        // ============================
        KTable<UUID, Long> userCounts = paintWithClan
            .groupByKey(Grouped.with(uuidSerde, paintWithClanSerde))
            .aggregate(
                () -> 0L,
                (userId, pwc, agg) -> agg + pwc.amount(),
                Materialized.<UUID, Long, KeyValueStore<Bytes, byte[]>>as("user_paints_store")
                    .withKeySerde(uuidSerde)
                    .withValueSerde(Serdes.Long())
            );

        userCounts
            .toStream()
            .to(USER_PAINT_COUNTS_TOPIC, Produced.with(uuidSerde, Serdes.Long()));

        // ============================
        // 클랜별 누적 카운트
        // ============================
        KTable<UUID, Long> clanCounts = paintWithClan
            .filter((userId, pwc) -> pwc.clanId() != null)
            .selectKey((userId, pwc) -> pwc.clanId())
            .groupByKey(Grouped.with(uuidSerde, paintWithClanSerde))
            .aggregate(
                () -> 0L,
                (clanId, pwc, agg) -> agg + pwc.amount(),
                Materialized.<UUID, Long, KeyValueStore<Bytes, byte[]>>as("clan_paints_store")
                    .withKeySerde(uuidSerde)
                    .withValueSerde(Serdes.Long())
            );
        
        clanCounts
            .toStream()
            .to(CLAN_PAINT_COUNTS_TOPIC, Produced.with(uuidSerde, Serdes.Long()));

        // ============================
        // 클랜-멤버별 누적 카운트 (clanId + userId)
        // ============================
        KTable<ClanMemberKey, Long> clanMemberCounts = paintWithClan
            .filter((userId, pwc) -> pwc.clanId() != null)
            .selectKey((userId, pwc) -> new ClanMemberKey(pwc.clanId(), pwc.userId()))
            .groupByKey(Grouped.with(clanMemberKeySerde, paintWithClanSerde))
            .aggregate(
                () -> 0L,
                (key, pwc, agg) -> agg + pwc.amount(),
                Materialized.<ClanMemberKey, Long, KeyValueStore<Bytes, byte[]>>as("clan_member_paints_store")
                    .withKeySerde(clanMemberKeySerde)
                    .withValueSerde(Serdes.Long())
            );

        clanMemberCounts
            .toStream()
            .to(CLAN_MEMBER_PAINT_COUNTS_TOPIC, Produced.with(clanMemberKeySerde, Serdes.Long()));

        // ============================
        // 기간별 집계
        // ============================
        aggregateCalendarBuckets(
            paintWithClan,
            paintWithClanSerde,
            userPeriodKeySerde,
            clanPeriodKeySerde,
            clanMemberPeriodKeySerde
        );


        return paintsStream; // 원본 스트림 반환
    }

    private void aggregateCalendarBuckets(
        KStream<UUID, PaintWithClan> paintWithClan,
        JsonSerde<PaintWithClan> paintWithClanSerde,
        JsonSerde<UserPeriodKey> userPeriodKeySerde,
        JsonSerde<ClanPeriodKey> clanPeriodKeySerde,
        JsonSerde<ClanMemberPeriodKey> clanMemberPeriodKeySerde
    ) {
        KStream<UUID, PaintWithClan> paintsByClan = paintWithClan
            .filter((userId, pwc) -> pwc.clanId() != null)
            .selectKey((userId, pwc) -> pwc.clanId());
        KStream<ClanMemberKey, PaintWithClan> paintsByClanMember = paintWithClan
            .filter((userId, pwc) -> pwc.clanId() != null)
            .selectKey((userId, pwc) -> new ClanMemberKey(pwc.clanId(), pwc.userId()));

        aggregatePeriod(
            paintWithClan,
            (userId, value) -> new UserPeriodKey(userId, toDailyBucket(value.occurredAt())),
            userPeriodKeySerde,
            paintWithClanSerde,
            USER_DAILY_PAINTS_STORE,
            USER_DAILY_PAINT_COUNTS_TOPIC
        );
        aggregatePeriod(
            paintWithClan,
            (userId, value) -> new UserPeriodKey(userId, toWeeklyBucket(value.occurredAt())),
            userPeriodKeySerde,
            paintWithClanSerde,
            USER_WEEKLY_PAINTS_STORE,
            USER_WEEKLY_PAINT_COUNTS_TOPIC
        );
        aggregatePeriod(
            paintWithClan,
            (userId, value) -> new UserPeriodKey(userId, toMonthlyBucket(value.occurredAt())),
            userPeriodKeySerde,
            paintWithClanSerde,
            USER_MONTHLY_PAINTS_STORE,
            USER_MONTHLY_PAINT_COUNTS_TOPIC
        );

        aggregatePeriod(
            paintsByClan,
            (clanId, value) -> new ClanPeriodKey(clanId, toDailyBucket(value.occurredAt())),
            clanPeriodKeySerde,
            paintWithClanSerde,
            CLAN_DAILY_PAINTS_STORE,
            CLAN_DAILY_PAINT_COUNTS_TOPIC
        );
        aggregatePeriod(
            paintsByClan,
            (clanId, value) -> new ClanPeriodKey(clanId, toWeeklyBucket(value.occurredAt())),
            clanPeriodKeySerde,
            paintWithClanSerde,
            CLAN_WEEKLY_PAINTS_STORE,
            CLAN_WEEKLY_PAINT_COUNTS_TOPIC
        );
        aggregatePeriod(
            paintsByClan,
            (clanId, value) -> new ClanPeriodKey(clanId, toMonthlyBucket(value.occurredAt())),
            clanPeriodKeySerde,
            paintWithClanSerde,
            CLAN_MONTHLY_PAINTS_STORE,
            CLAN_MONTHLY_PAINT_COUNTS_TOPIC
        );

        aggregatePeriod(
            paintsByClanMember,
            (key, value) -> new ClanMemberPeriodKey(key.clanId(), key.userId(), toDailyBucket(value.occurredAt())),
            clanMemberPeriodKeySerde,
            paintWithClanSerde,
            CLAN_MEMBER_DAILY_PAINTS_STORE,
            CLAN_MEMBER_DAILY_PAINT_COUNTS_TOPIC
        );
        aggregatePeriod(
            paintsByClanMember,
            (key, value) -> new ClanMemberPeriodKey(key.clanId(), key.userId(), toWeeklyBucket(value.occurredAt())),
            clanMemberPeriodKeySerde,
            paintWithClanSerde,
            CLAN_MEMBER_WEEKLY_PAINTS_STORE,
            CLAN_MEMBER_WEEKLY_PAINT_COUNTS_TOPIC
        );
        aggregatePeriod(
            paintsByClanMember,
            (key, value) -> new ClanMemberPeriodKey(key.clanId(), key.userId(), toMonthlyBucket(value.occurredAt())),
            clanMemberPeriodKeySerde,
            paintWithClanSerde,
            CLAN_MEMBER_MONTHLY_PAINTS_STORE,
            CLAN_MEMBER_MONTHLY_PAINT_COUNTS_TOPIC
        );
    }

    private <K, KR> void aggregatePeriod(
        KStream<K, PaintWithClan> stream,
        KeyValueMapper<K, PaintWithClan, KR> keySelector,
        Serde<KR> periodKeySerde,
        JsonSerde<PaintWithClan> paintWithClanSerde,
        String storeName,
        String topicName
    ) {
        stream
            .groupBy(keySelector, Grouped.with(periodKeySerde, paintWithClanSerde))
            .aggregate(
                () -> 0L,
                (key, value, aggregate) -> aggregate + value.amount(),
                Materialized.<KR, Long, KeyValueStore<Bytes, byte[]>>as(storeName)
                    .withKeySerde(periodKeySerde)
                    .withValueSerde(Serdes.Long())
            )
            .toStream()
            .to(topicName, Produced.with(periodKeySerde, Serdes.Long()));
    }

    private static LocalDate toDailyBucket(Instant timestamp) {
        return toZonedDate(timestamp).toLocalDate();
    }

    private static LocalDate toWeeklyBucket(Instant timestamp) {
        LocalDate date = toDailyBucket(timestamp);
        return date.with(TemporalAdjusters.previousOrSame(WEEK_START));
    }

    private static LocalDate toMonthlyBucket(Instant timestamp) {
        return toDailyBucket(timestamp).withDayOfMonth(1);
    }

    private static ZonedDateTime toZonedDate(Instant timestamp) {
        Instant ts = timestamp != null ? timestamp : Instant.EPOCH;
        return ZonedDateTime.ofInstant(ts, AGGREGATION_ZONE);
    }

    private static <T> JsonSerde<T> jsonSerde(Class<T> type, ObjectMapper objectMapper) {
        JsonSerde<T> serde = new JsonSerde<>(type, objectMapper);
        serde.deserializer().ignoreTypeHeaders();
        return serde;
    }
}
