package com.colombus.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@EnableAsync
@Configuration
public class AsyncConfig {
    
    @Bean(name = "dlqExecutor", destroyMethod = "close")
    public ExecutorService dlqExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(name = "outboxExecutor", destroyMethod = "close")
    public ExecutorService outboxExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
