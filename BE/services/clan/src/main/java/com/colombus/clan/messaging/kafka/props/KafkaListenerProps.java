package com.colombus.clan.messaging.kafka.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.listener")
public class KafkaListenerProps {
    
    private int concurrency = 3;
    private long pollTimeoutMs = 3000L;
    
    public int concurrency() { return concurrency; }
    public long pollTimeoutMs() { return pollTimeoutMs; }
}
