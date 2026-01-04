package com.colombus.user.messaging.outbox.repository;

import static com.colombus.user.jooq.tables.OutboxEvent.OUTBOX_EVENT;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.colombus.common.domain.outbox.model.type.OutboxStatus;
import com.colombus.user.jooq.tables.records.OutboxEventRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class OutboxConsumeRepository {

    private final DSLContext dsl;

    public List<OutboxEventRecord> claim(String consumer, int batch, int lockSec) {
        return dsl.resultQuery("select * from outbox_pull(?, ?, ?)", consumer, batch, lockSec)
                  .fetchInto(OutboxEventRecord.class);
    }

    /** 성공 처리: outbox_succeed(id, consumer) → boolean */
    public boolean succeed(UUID id, String consumer) {
        Boolean ok = dsl.fetchValue(
            DSL.field("outbox_succeed({0}, {1})", Boolean.class, id, consumer)
        );
        return Boolean.TRUE.equals(ok);
    }

    /** 실패 처리: outbox_fail(id, error, consumer, retrySec, maxAttempts, backoffMul) → boolean */
    public boolean fail(
        UUID id,
        String error,
        String consumer,
        int retrySec,
        int maxAttempts,
        double backoffMul
    ) {
        Boolean ok = dsl.fetchValue(
            DSL.field("outbox_fail({0}, {1}, {2}, {3}, {4}, {5})", Boolean.class,
            id, error, consumer, retrySec, maxAttempts, BigDecimal.valueOf(backoffMul))
        );
        return Boolean.TRUE.equals(ok);
    }

    /** TIMEOUT 재큐: outbox_requeue_timeouts(graceSec) → updated count */
    public long requeueTimeouts(int graceSec) {
        Long cnt = dsl.fetchValue(
            DSL.field("outbox_requeue_timeouts({0})", Long.class, graceSec)
        );
        return cnt == null ? 0L : cnt;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeOld() {
        int days = 30;
        long deleted = purge(days);
        if (deleted > 0) {
            log.info("outbox purged {} rows older than {} days", deleted, days);
        }
    }

    /** 보존기간 지난 레코드 정리: purge_outbox(days) → deleted count */
    public long purge(int days) {
        Long cnt = dsl.fetchValue(
            DSL.field("purge_outbox({0})", Long.class, days)
        );
        return cnt == null ? 0L : cnt;
    }

    @Transactional(readOnly = true)
    public int countPending() {
        return dsl.fetchCount(OUTBOX_EVENT, OUTBOX_EVENT.STATUS.eq(OutboxStatus.PENDING));
    }
}