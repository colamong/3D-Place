package com.colombus.user.config;

import org.jooq.DSLContext;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DatabaseScheduler {

    private final DSLContext dsl;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        dsl.execute("""
        SELECT ensure_auth_event_partitions(
            (date_trunc('month', now()) - INTERVAL '1 month')::date,
            (date_trunc('month', now()) + INTERVAL '1 month')::date
        )
        """);
    }

    @Scheduled(cron = "0 10 0 1 * *", zone = "Asia/Seoul")
    public void dropOld() {
        dsl.execute("SELECT drop_old_auth_event_partitions(?)", 12);
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void ensureCurrentAndNextMonth() {
        dsl.execute("""
            SELECT ensure_auth_event_partitions(
                date_trunc('month', (now() AT TIME ZONE 'Asia/Seoul'))::date,
                date_trunc('month', (now() AT TIME ZONE 'Asia/Seoul') + INTERVAL '2 month')::date
            )
        """);
    }
}