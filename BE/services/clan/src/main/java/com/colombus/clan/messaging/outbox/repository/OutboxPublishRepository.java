package com.colombus.clan.messaging.outbox.repository;

import java.time.Instant;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static com.colombus.clan.jooq.tables.OutboxEvent.OUTBOX_EVENT;
import com.colombus.common.kafka.subject.event.SubjectRegistEvent;
import com.colombus.common.kafka.subject.model.type.SubjectKind;
import com.colombus.common.utility.time.TimeConv;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OutboxPublishRepository {

    private final DSLContext dsl;
    private final ObjectMapper om;

    public Mono<UUID> append(
        String aggregate,
        UUID aggregateId,
        String type,
        long version,
        JsonNode payload,
        JsonNode headers
    ) {
        return Mono.from(
                dsl.insertInto(OUTBOX_EVENT)
                .set(OUTBOX_EVENT.AGGREGATE,    aggregate)
                .set(OUTBOX_EVENT.AGGREGATE_ID, aggregateId)
                .set(OUTBOX_EVENT.TYPE,         type)
                .set(OUTBOX_EVENT.VERSION,      version)
                .set(OUTBOX_EVENT.PAYLOAD,      payload)
                .set(OUTBOX_EVENT.HEADERS,      headers)
                .onConflict(
                    OUTBOX_EVENT.AGGREGATE,
                    OUTBOX_EVENT.AGGREGATE_ID,
                    OUTBOX_EVENT.TYPE,
                    OUTBOX_EVENT.VERSION
                )
                .where(OUTBOX_EVENT.DELETED_AT.isNull())
                .doNothing()
                .returning(OUTBOX_EVENT.ID)
            )
            .map(r -> r.get(OUTBOX_EVENT.ID));
    }


    private ObjectNode defaultHeaders(String schema, String source, String aggregate, String type) {
        return JsonNodeFactory.instance.objectNode()
            .put("schema", schema)
            .put("source", source)
            .put("aggregate", aggregate)
            .put("type", type);
    }

    public Mono<UUID> appendSubjectUpsert(UUID clanId, boolean alive) {
        final String aggregate = "SUBJECT";
        final String type      = "SubjectUpsert";

        return nextVersion(aggregate, clanId, type)
            .flatMap(version -> {
                SubjectRegistEvent evt = new SubjectRegistEvent(
                    SubjectKind.CLAN,
                    clanId,
                    alive,
                    version,
                    Instant.now()
                );

                ObjectNode payload = om.valueToTree(evt);
                ObjectNode headers = defaultHeaders(
                    "subject.registry.v1",
                    "clansvc",
                    aggregate,
                    type
                );

                return append(aggregate, clanId, type, version, payload, headers);
            });
    }

    /**
     * 유저-클랜 멤버십 스냅샷 (userId 기준)
     * key = userId, schema = clan.membership.current.v1
     */
    public Mono<UUID> appendClanMembershipUpsert(UUID userId, @Nullable UUID clanId, boolean active) {
        final String aggregate = "CLAN_MEMBERSHIP";
        final String type      = "ClanMembershipUpsert";

        final Instant now = TimeConv.nowUtc();

        ObjectNode payload = JsonNodeFactory.instance.objectNode()
            .put("userId", userId.toString())
            .put("status", active ? "ACTIVE" : "LEFT")
            .put("updatedAt", now.toString());

        if (clanId != null) {
            payload.put("clanId", clanId.toString());
        } else {
            payload.putNull("clanId");
        }

        ObjectNode headers = defaultHeaders("clan.membership.current.v1", "clansvc", aggregate, type);

        // nextVersion: Mono<Long>, append: Mono<UUID> 라고 가정
        return nextVersion(aggregate, userId, type)
            .flatMap(version ->
                append(aggregate, userId, type, version, payload, headers)
            );
    }

    public Mono<Long> nextVersion(String aggregate, UUID aggregateId, String type) {
        return Mono.from(
                dsl.select(
                        DSL.coalesce(DSL.max(OUTBOX_EVENT.VERSION), 0L).plus(1L)
                )
                .from(OUTBOX_EVENT)
                .where(OUTBOX_EVENT.AGGREGATE.eq(aggregate))
                .and(OUTBOX_EVENT.AGGREGATE_ID.eq(aggregateId))
                .and(OUTBOX_EVENT.TYPE.eq(type))
            )
            .map(r -> r.value1())
            .defaultIfEmpty(1L);
    }
}