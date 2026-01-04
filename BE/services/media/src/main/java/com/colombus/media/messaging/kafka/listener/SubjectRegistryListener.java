package com.colombus.media.messaging.kafka.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.colombus.common.kafka.subject.event.SubjectRegistEvent;
import com.colombus.media.subject.service.SubjectRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SubjectRegistryListener {
    
    private final ObjectMapper om;
    private final SubjectRegistryService service;

    @KafkaListener(
        topics = "${kafka.topics.subject-registry-v1}",
        containerFactory = "bytesKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, byte[]> rec, Acknowledgment ack) throws Exception {
        var evt = om.readValue(rec.value(), SubjectRegistEvent.class);

        service.apply(evt);
        ack.acknowledge();
    }
}
