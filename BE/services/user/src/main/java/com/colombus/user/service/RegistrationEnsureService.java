package com.colombus.user.service;

import com.colombus.user.messaging.kafka.RetryClassifier;
import com.colombus.user.messaging.kafka.dto.Auth0RegistrationPayload;
import com.colombus.user.messaging.kafka.dto.RegistrationIngestFailed;
import com.colombus.user.messaging.kafka.publisher.DlqPublisher;
import com.colombus.user.command.EnsureUserCommand;
import com.colombus.user.web.internal.mapper.UserTypeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationEnsureService  {

    private final UserAuthService userAuthService;
    // private final RegistrationDlqPublisher dlqPublisher;
    private final DlqPublisher dlqPublisher;

    @Value("${ingest.retry.max-attempts:5}")
    private int maxAttempts;

    @Value("${ingest.retry.delay-ms:200}")
    private long baseDelayMs;

    @Value("${ingest.retry.multiplier:2.0}")
    private double multiplier;

    @Value("${ingest.retry.max-delay-ms:5000}")
    private long maxDelayMs;

    @Async("dlqExecutor")
    public void handleRegistration(Auth0RegistrationPayload p, String ip, String ua) {
        // provider/tenant/sub 보정
        final String sub = p.user_id();
        final String providerStr = p.provider() != null && !p.provider().isBlank()
                ? p.provider()
                : (sub != null && sub.contains("|") ? sub.substring(0, sub.indexOf('|')) : "auth0");
        final String tenant = p.tenant();
        final boolean verified = Boolean.TRUE.equals(p.email_verified());
        final Instant createdAt = parseInstantSafe(p.created_at());

        var provider = UserTypeMapper.parseProvider(providerStr);

        final EnsureUserCommand cmd = EnsureUserCommand.of(provider, tenant, sub, p.email(), verified, p.locale(), p.avatarUrl(), createdAt);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var r = userAuthService.ensureUserAndIdentity(cmd, ip, ua);
                log.info("ingest/registration ensured userId={}, signup={}, link={}, sync={}, login={}",
                        r.userId(), r.signup(), r.link(), r.sync(), r.login());

                return;
            } catch (Exception e) {

                if (RetryClassifier.isPermanent(e)) {
                    // 퍼머넌트: 즉시 DLQ
                    sendDlq(p, cmd, e, attempt, ip, ua);
                    return;
                }

                if (attempt >= maxAttempts) {
                    // 한계 도달: DLQ
                    sendDlq(p, cmd, e, attempt, ip, ua);
                    return;
                }

                long delay = nextDelayMs(attempt);
                log.warn("ingest/registration retrying... sub={}, attempt={}/{}, nextDelay={}ms, err={}",
                        sub, attempt, maxAttempts, delay, shortErr(e));

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    sendDlq(p, cmd, e, attempt, ip, ua);
                    return;
                }
            }
        }
    }

    private long nextDelayMs(int attempt) {
        long delay = (long) (baseDelayMs * Math.pow(multiplier, attempt - 1));
        if (delay > maxDelayMs) {
            delay = maxDelayMs;
        }
        long jitter = ThreadLocalRandom.current().nextLong((long) (delay * 0.2)); // ±10% 수준
        return delay - (delay / 10) + jitter;
    }

    private void sendDlq(Auth0RegistrationPayload raw, EnsureUserCommand cmd, Exception e, int attempts, String ip, String ua) {
        var ev = new RegistrationIngestFailed(
            cmd.provider(), cmd.providerTenant(), cmd.providerSub(), cmd.email(), cmd.emailVerified(),
            cmd.createdAt(),
            ip, ua,
            e.getClass().getSimpleName(),
            Optional.ofNullable(e.getMessage()).orElse(""),
            attempts,
            Instant.now()
        );
        dlqPublisher.publishRegistration(ev);
        log.error("ingest/registration DLQ sent: sub={}, attempts={}, error={}", cmd.providerSub(), attempts, shortErr(e));
    }

    private static String shortErr(Throwable t) {
        var msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg == null ? "" : (": " + msg));
    }

    private static Instant parseInstantSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception ignore) {
            return null;
        }
    }
}
