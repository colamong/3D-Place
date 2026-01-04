package com.colombus.clan.messaging.kafka.config;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;
import com.colombus.clan.messaging.kafka.props.TopicProperties;

@Configuration
public class KafkaTopicConfiguration {
    
    @Bean
    public NewTopics userServiceTopics(TopicProperties p) {

        // subject projection
        NewTopic subjectRegistryV1 = TopicBuilder.name(p.getSubjectRegistryV1())
                .partitions(p.getDefaultPartitions())
                .replicas(p.getDefaultReplicationFactor())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();

        // clan membership projection
        NewTopic clanMembershipCurrentV1 = TopicBuilder.name(p.getClanMembershipCurrentV1())
                .partitions(p.getDefaultPartitions())
                .replicas(p.getDefaultReplicationFactor())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();

        // DLQ들 — 운영에선 보통 소스 토픽과 같은 파티션 수 권장
        String retentionMs = String.valueOf(Duration.ofDays(14).toMillis());

        return new NewTopics(
                subjectRegistryV1,
                clanMembershipCurrentV1
        );
    }
}
