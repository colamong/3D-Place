package com.colombus.clan.messaging.outbox.props;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox.worker")
public class OutboxWorkerProperties {
    private String consumerId = defaultConsumerId();
    private int batch = 100;
    private int lockSec = 60;
    private long pollIntervalMs = 500;
    private int retrySec = 30;
    private int maxAttempts = 20;
    private double backoffMul = 1.5;
    private Duration sendTimeout = Duration.ofSeconds(10);

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }
    public int getBatch() { return batch; }
    public void setBatch(int batch) { this.batch = batch; }
    public int getLockSec() { return lockSec; }
    public void setLockSec(int lockSec) { this.lockSec = lockSec; }
    public long getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    public int getRetrySec() { return retrySec; }
    public void setRetrySec(int retrySec) { this.retrySec = retrySec; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public double getBackoffMul() { return backoffMul; }
    public void setBackoffMul(double backoffMul) { this.backoffMul = backoffMul; }
    public Duration getSendTimeout() { return sendTimeout; }
    public void setSendTimeout(Duration sendTimeout) { this.sendTimeout = sendTimeout; }

    private static String defaultConsumerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "#" + UUID.randomUUID();
        } catch (Exception e) {
            return "clansvc#" + UUID.randomUUID();
        }
    }
}