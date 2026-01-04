package com.colombus.clan.messaging.kafka.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.kafka.consumer")
public class KafkaConsumerProps {

    private String bootstrapServers = "localhost:9092";
    private String groupId = "clansvc";
    private String autoOffsetReset = "earliest";
    private int maxPollRecords = 200;
    private long initialBackoffMs = 500L;
    private double backoffMultiplier = 2.0;
    private long maxBackoffMs = 30_000L;
    
    // === getters ===
    public String getBootstrapServers() { return bootstrapServers; }
    public String getGroupId() { return groupId; }
    public String getAutoOffsetReset() { return autoOffsetReset; }
    public int getMaxPollRecords() { return maxPollRecords; }
    public long getInitialBackoffMs() { return initialBackoffMs; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public long getMaxBackoffMs() { return maxBackoffMs; }

    // === setters ===
    public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public void setAutoOffsetReset(String autoOffsetReset) { this.autoOffsetReset = autoOffsetReset; }
    public void setMaxPollRecords(int maxPollRecords) { this.maxPollRecords = maxPollRecords; }
    public void setInitialBackoffMs(long initialBackoffMs) { this.initialBackoffMs = initialBackoffMs; }
    public void setBackoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }
    public void setMaxBackoffMs(long maxBackoffMs) { this.maxBackoffMs = maxBackoffMs; }
}