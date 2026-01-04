package com.colombus.user.messaging.outbox.repository;

import java.time.Instant;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static com.colombus.user.jooq.tables.OutboxEvent.OUTBOX_EVENT;

import com.colombus.common.kafka.subject.event.SubjectRegistEvent;
import com.colombus.common.kafka.subject.model.type.SubjectKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OutboxPublishRepository {

    private final DSLContext dsl;
    private final ObjectMapper om;

    public UUID append(
        String aggregate,
        UUID aggregateId,
        String type,
        long version,
        JsonNode payload,
        JsonNode headers
    ) {
        return dsl.insertInto(OUTBOX_EVENT)
            .set(OUTBOX_EVENT.AGGREGATE, aggregate)
            .set(OUTBOX_EVENT.AGGREGATE_ID, aggregateId)
            .set(OUTBOX_EVENT.TYPE, type)
            .set(OUTBOX_EVENT.VERSION, version)
            .set(OUTBOX_EVENT.PAYLOAD, payload)
            .set(OUTBOX_EVENT.HEADERS, headers)
            .onConflict(OUTBOX_EVENT.AGGREGATE, OUTBOX_EVENT.AGGREGATE_ID, OUTBOX_EVENT.TYPE, OUTBOX_EVENT.VERSION)
            .where(OUTBOX_EVENT.DELETED_AT.isNull())
            .doNothing()
            .returning(OUTBOX_EVENT.ID)
            .fetchOptional(OUTBOX_EVENT.ID)
            .orElse(null);
    }

    public UUID appendSubjectUpsert(UUID userId, boolean alive) {
        final String aggregate = "SUBJECT";
        final String type = "SubjectUpsert";
        final long version = nextVersion(aggregate, userId, type);

        SubjectRegistEvent evt = new SubjectRegistEvent(
            SubjectKind.USER, userId, alive, version, Instant.now()
        );

        ObjectNode payload = om.valueToTree(evt);

        ObjectNode headers = JsonNodeFactory.instance.objectNode()
            .put("schema", "subject.registry.v1")
            .put("source", "usersvc")
            .put("aggregate", aggregate)
            .put("type", type);

        return append(aggregate, userId, type, version, payload, headers);
    }

    private long nextVersion(String aggregate, UUID aggregateId, String type) {
        Long v = dsl.select(DSL.coalesce(DSL.max(OUTBOX_EVENT.VERSION), 0L).plus(1))
            .from(OUTBOX_EVENT)
            .where(OUTBOX_EVENT.AGGREGATE.eq(aggregate))
            .and(OUTBOX_EVENT.AGGREGATE_ID.eq(aggregateId))
            .and(OUTBOX_EVENT.TYPE.eq(type))
            .fetchOne(0, Long.class);
        return (v == null ? 1L : v);
    }
}