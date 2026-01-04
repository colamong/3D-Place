package com.colombus.clan.messaging.kafka.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.colombus.common.kafka.subject.event.SubjectAssetUpsertEvent;
import com.colombus.common.kafka.subject.model.type.SubjectKind;
import com.colombus.clan.service.ClanModifyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SubjectAssetUpsertListener {
    
    private final ObjectMapper om;
    private final ClanModifyService service;

    @KafkaListener(
        topics = "${kafka.topics.subject-asset-upsert-v1}",
        containerFactory = "bytesKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, byte[]> rec, Acknowledgment ack) throws Exception {
        var evt = om.readValue(rec.value(), SubjectAssetUpsertEvent.class);
        
        if (evt.subjectKind() != SubjectKind.CLAN) {
            ack.acknowledge();
            return;
        }
    
        service.applyAssetUpsert(evt);
        ack.acknowledge();
    }
}
