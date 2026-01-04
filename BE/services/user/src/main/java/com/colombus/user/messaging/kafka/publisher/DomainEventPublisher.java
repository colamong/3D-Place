package com.colombus.user.messaging.kafka.publisher;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.colombus.user.messaging.kafka.props.TopicProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    @Qualifier("bytesKafkaTemplate")
    private final KafkaTemplate<String, byte[]> kafka;
    private final TopicProperties topics;
    private final ObjectMapper om;

    public void publishSubjectRegistry(Object payload, String key, long timeoutMs) throws Exception {
        send(topics.getSubjectRegistryV1(), key, payload, timeoutMs);
    }

    public void send(String topic, String key, Object payload, long timeoutMs) throws Exception {
        byte[] value = om.writeValueAsBytes(payload);
        kafka.send(topic, (key == null || key.isBlank()) ? "unknown" : key, value)
             .get(timeoutMs, TimeUnit.MILLISECONDS);
    }
}