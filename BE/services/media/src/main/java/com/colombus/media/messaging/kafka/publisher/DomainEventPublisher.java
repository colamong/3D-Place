package com.colombus.media.messaging.kafka.publisher;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.colombus.media.messaging.kafka.props.TopicProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    @Qualifier("bytesKafkaTemplate")
    private final KafkaTemplate<String, byte[]> kafka;
    private final TopicProperties topics;
    private final ObjectMapper om;

    public void publishSubjectAssetUpsert(Object payload, String key, long timeoutMs) throws Exception {
        send(topics.getSubjectAssetUpsertV1(), key, payload, timeoutMs);
    }

    public void send(String topic, String key, Object payload, long timeoutMs) throws Exception {
        byte[] value = om.writeValueAsBytes(payload);
        kafka.send(topic, (key == null || key.isBlank()) ? "unknown" : key, value)
             .get(timeoutMs, TimeUnit.MILLISECONDS);
    }
}