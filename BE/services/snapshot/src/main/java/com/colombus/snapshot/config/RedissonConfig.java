package com.colombus.snapshot.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:#{null}}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int redisDatabase;

@Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 클러스터 모드 + TLS
        String address = String.format("rediss://%s:%d", redisHost, redisPort);
        ClusterServersConfig clusterConfig = config.useClusterServers()
                .addNodeAddress(address)
                .setPassword(redisPassword)
                .setScanInterval(2000) 
                .setConnectTimeout(10000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setMasterConnectionMinimumIdleSize(10)
                .setMasterConnectionPoolSize(64)
                .setSlaveConnectionMinimumIdleSize(10)
                .setSlaveConnectionPoolSize(64);

        config.setLockWatchdogTimeout(30000L);

        return Redisson.create(config);
    } 

}