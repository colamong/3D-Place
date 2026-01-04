package com.colombus.user.messaging.kafka.config;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;

import com.colombus.user.messaging.kafka.props.TopicProperties;

@Configuration
public class KafkaTopicConfiguration {
    
    @Bean
    public NewTopics userServiceTopics(TopicProperties p) {

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

        // DLQ들 — 운영에선 보통 소스 토픽과 같은 파티션 수 권장
        String retentionMs = String.valueOf(Duration.ofDays(14).toMillis());
        NewTopic registrationDlq = TopicBuilder.name(p.getRegistrationDlq())
                .partitions(p.getDefaultPartitions())
                .replicas(p.getDefaultReplicationFactor())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, retentionMs)
                .build();

        NewTopic linkDlq = TopicBuilder.name(p.getLinkDlq())
                .partitions(p.getDefaultPartitions())
                .replicas(p.getDefaultReplicationFactor())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, retentionMs)
                .build();

        NewTopic unlinkDlq = TopicBuilder.name(p.getUnlinkDlq())
                .partitions(p.getDefaultPartitions())
                .replicas(p.getDefaultReplicationFactor())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, retentionMs)
                .build();

        return new NewTopics(
                subjectAssetUpsertV1, subjectAssetUpsertV1Dlt, registrationDlq, linkDlq, unlinkDlq
        );
    }
}
