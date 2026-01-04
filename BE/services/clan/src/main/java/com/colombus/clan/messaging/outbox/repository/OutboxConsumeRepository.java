package com.colombus.clan.messaging.outbox.repository;

import static com.colombus.clan.jooq.tables.OutboxEvent.OUTBOX_EVENT;
import java.math.BigDecimal;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.colombus.common.domain.outbox.model.type.OutboxStatus;
import com.colombus.clan.jooq.tables.records.OutboxEventRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class OutboxConsumeRepository {

    private final DSLContext dsl;

    public Flux<OutboxEventRecord> claim(String consumer, int batch, int lockSec) {
        return Flux.from(
                dsl.resultQuery("select * from outbox_pull(?, ?, ?)", consumer, batch, lockSec)
            )
            .map(rec -> rec.into(OutboxEventRecord.class));
    }

    /** 성공 처리: outbox_succeed(id, consumer) → boolean */
    public Mono<Boolean> succeed(UUID id, String consumer) {
        return fetchBoolean("select outbox_succeed(?, ?) as ok", id, consumer);
    }

    /** 실패 처리: outbox_fail(id, error, consumer, retrySec, maxAttempts, backoffMul) → boolean */
    public Mono<Boolean> fail(
        UUID id,
        String error,
        String consumer,
        int retrySec,
        int maxAttempts,
        double backoffMul
    ) {
        return fetchBoolean(
            "select outbox_fail(?, ?, ?, ?, ?, ?) as ok",
            id, error, consumer, retrySec, maxAttempts, BigDecimal.valueOf(backoffMul)
        );
    }

    /** TIMEOUT 재큐: outbox_requeue_timeouts(graceSec) → updated count */
    public Mono<Long> requeueTimeouts(int graceSec) {
        return fetchLong("select outbox_requeue_timeouts(?) as cnt", graceSec);
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeOld() {
        int days = 30;
        long deleted = purge(days).blockOptional().orElse(0L);
        if (deleted > 0) {
            log.info("outbox purged {} rows older than {} days", deleted, days);
        }
    }

    /** 보존기간 지난 레코드 정리: purge_outbox(days) → deleted count */
    public Mono<Long> purge(int days) {
        return fetchLong("select purge_outbox(?) as cnt", days);
    }

    public Mono<Integer> countPending() {
        return Mono.from(
                dsl.selectCount()
                   .from(OUTBOX_EVENT)
                   .where(OUTBOX_EVENT.STATUS.eq(OutboxStatus.PENDING))
            )
            .map(r -> r.value1());
    }

    private Mono<Boolean> fetchBoolean(String sql, Object... args) {
        return Mono.from(dsl.resultQuery(sql, args))
            .map(rec -> rec.get(0, Boolean.class))
            .map(Boolean.TRUE::equals)
            .defaultIfEmpty(Boolean.FALSE);
    }

    private Mono<Long> fetchLong(String sql, Object... args) {
        return Mono.from(dsl.resultQuery(sql, args))
            .map(rec -> rec.get(0, Long.class))
            .map(val -> val == null ? 0L : val)
            .defaultIfEmpty(0L);
    }
}
