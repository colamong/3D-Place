package com.colombus.clan.messaging.outbox.worker;

import java.util.List;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.colombus.common.utility.error.Exceptions;
import com.colombus.common.utility.json.Jsons;
import com.colombus.common.jooq.JooqJsons;
import com.colombus.clan.jooq.tables.records.OutboxEventRecord;
import com.colombus.clan.messaging.kafka.publisher.DomainEventPublisher;
import com.colombus.clan.messaging.outbox.props.OutboxWorkerProperties;
import com.colombus.clan.messaging.outbox.repository.OutboxConsumeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWorker {

    private final OutboxConsumeRepository repo;
    private final OutboxWorkerProperties props;
    private final DomainEventPublisher publisher;
    private final ObjectMapper om;

    @Scheduled(
        initialDelayString = "${outbox.worker.initial-delay-ms:2000}",
        fixedDelayString   = "${outbox.worker.poll-interval-ms:500}"
    )
    public void poll() {
        List<OutboxEventRecord> batch = repo.claim(props.getConsumerId(), props.getBatch(), props.getLockSec())
            .collectList()
            .blockOptional()
            .orElseGet(List::of);
        if (batch.isEmpty()) return;
        for (OutboxEventRecord rec : batch) {
            processAsync(rec);
        }
    }

    @Async("outboxExecutor")
    void processAsync(OutboxEventRecord rec) {
        var id = rec.getId();
        try {
            routeAndPublish(rec); // 카프카 전송
            boolean ok = repo.succeed(id, props.getConsumerId())
                .blockOptional()
                .orElse(false);
            if (!ok) log.warn("outbox succeed lost-lock id={}", id);
        } catch (Exception e) {
            log.warn("outbox publish failed id={}, err={}", id, Exceptions.shortErr(e));
            boolean ok = repo.fail(
                    id, Exceptions.shortErr(e), props.getConsumerId(),
                    props.getRetrySec(), props.getMaxAttempts(), props.getBackoffMul()
                )
                .blockOptional()
                .orElse(false);
            if (!ok) log.warn("outbox fail lost-lock id={}", id);
        }
    }

    private void routeAndPublish(OutboxEventRecord rec) throws Exception {
        JsonNode headers = JooqJsons.toJsonNode(om, rec.getHeaders());
        JsonNode payload = JooqJsons.toJsonNode(om, rec.getPayload());
        String schema = Jsons.firstNonBlankText(headers, "schema");
        String type   = rec.getType();

        // 라우팅 규칙
        if ("subject.registry.v1".equals(schema) || "SubjectUpsert".equals(type)) {
            String key = Jsons.firstNonBlankText(payload, "subjectId");
            publisher.publishSubjectRegistry(payload, key, props.getSendTimeout().toMillis());
            return;
        }

        if ("clan.membership.current.v1".equals(schema) || "ClanMembershipUpsert".equals(type)) {
            String key = Jsons.firstNonBlankText(payload, "userId");
            publisher.publishClanMembership(payload, key, props.getSendTimeout().toMillis());
            return;
        }

        throw new IllegalStateException("No route for event: type=" + type + ", schema=" + schema);
    }

    // 타임아웃 재큐 주기 태스크
    @Scheduled(fixedDelayString = "${outbox.worker.requeue-interval-ms:60000}")
    public void requeueTimeouts() {
        long n = repo.requeueTimeouts(0)
            .blockOptional()
            .orElse(0L);
        if (n > 0) log.info("outbox requeued timeouts={}", n);
    }
}
