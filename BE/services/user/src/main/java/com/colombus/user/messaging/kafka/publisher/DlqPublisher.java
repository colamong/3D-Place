package com.colombus.user.messaging.kafka.publisher;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.colombus.user.messaging.kafka.dto.RegistrationIngestFailed;
import com.colombus.user.messaging.kafka.dto.UserLinkFailed;
import com.colombus.user.messaging.kafka.dto.UserUnlinkFailed;
import com.colombus.user.messaging.kafka.props.TopicProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DlqPublisher {

    @Qualifier("jsonKafkaTemplate")
    private final KafkaTemplate<String, Object> kafka;
    private final TopicProperties topics;

    public void publishRegistration(RegistrationIngestFailed event) {
        send(topics.getRegistrationDlq(), keyOrUnknown(event.providerSub()), event);
    }

    public void publishLink(UserLinkFailed event) {
        send(topics.getLinkDlq(), keyOrUnknown(event.providerSub()), event);
    }

    public void publishUnlink(UserUnlinkFailed event) {
        send(topics.getUnlinkDlq(), keyOrUnknown(event.providerSub()), event);
    }

    private void send(String topic, String key, Object event) {
        kafka.send(topic, key, event);
    }

    private static String keyOrUnknown(String key) {
        return (key == null || key.isBlank()) ? "unknown" : key;
    }
}