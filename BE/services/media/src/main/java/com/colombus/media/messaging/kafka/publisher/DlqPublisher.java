package com.colombus.media.messaging.kafka.publisher;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.colombus.media.messaging.kafka.props.TopicProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DlqPublisher {

    @Qualifier("jsonKafkaTemplate")
    private final KafkaTemplate<String, Object> kafka;
    private final TopicProperties topics;

    private void send(String topic, String key, Object event) {
        kafka.send(topic, key, event);
    }

    private static String keyOrUnknown(String key) {
        return (key == null || key.isBlank()) ? "unknown" : key;
    }
}