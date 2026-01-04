package com.colombus.media.messaging.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;

import com.colombus.media.messaging.kafka.props.TopicProperties;

@Configuration
public class KafkaTopicConfiguration {
    
    @Bean
    public NewTopics mediaServiceTopics(TopicProperties p) {

        // subject projection
        NewTopic subjectAssetUpsertV1 = TopicBuilder.name(p.getSubjectAssetUpsertV1())
                .partitions(p.getDefaultPartitions())
                .replicas(p.getDefaultReplicationFactor())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();

        NewTopic subjectAssetUpsertV1Dlt = TopicBuilder.name(p.getSubjectAssetUpsertV1() + ".DLT")
                .partitions(p.getDefaultPartitions())
                .replicas(p.getDefaultReplicationFactor())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();

        return new NewTopics(
                subjectAssetUpsertV1, subjectAssetUpsertV1Dlt
        );
    }
}
