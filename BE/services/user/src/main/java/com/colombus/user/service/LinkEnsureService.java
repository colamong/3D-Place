package com.colombus.user.service;

import com.colombus.user.command.LinkIdentityCommand;
import com.colombus.user.messaging.kafka.RetryClassifier;
import com.colombus.user.messaging.kafka.dto.UserLinkFailed;
import com.colombus.user.messaging.kafka.dto.UserLinkPayload;
import com.colombus.user.messaging.kafka.publisher.DlqPublisher;
import com.colombus.user.service.result.LinkIdentityResult;
import com.colombus.user.web.internal.mapper.UserTypeMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkEnsureService {

    private final UserAuthService userAuthService;
    private final DlqPublisher dlq;

    @Value("${ingest.retry.max-attempts:5}")
    int maxAttempts;

    @Value("${ingest.retry.delay-ms:200}")
    long baseDelayMs;

    @Value("${ingest.retry.multiplier:2.0}")
    double multiplier;

    @Value("${ingest.retry.max-delay-ms:5000}")
    long maxDelayMs;

    @Async("dlqExecutor")
    public void handleLink(UserLinkPayload p, String ip, String ua, String bearer) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {

                var provider = UserTypeMapper.parseProvider(p.provider());

                var cmd = LinkIdentityCommand.of(p.userId(), provider, p.providerTenant(), p.providerSub());

                LinkIdentityResult r = userAuthService.linkIdentity(cmd, ip, ua);

                if (r.finalResult()) {
                    log.info(
                        "link final: userId={}, provider={}, sub={}",
                        p.userId(),
                        p.provider(),
                        p.providerSub());
                    return;
                }

                if (r.retry()) {
                    long d = nextDelay(attempt);
                    log.warn(
                        "unlink retrying... userId={}, provider={}, sub={}, attempt={}/{}, next={}ms",
                        p.userId(),
                        p.provider(),
                        p.providerSub(),
                        attempt,
                        maxAttempts,
                        d);
                    sleep(d);
                    continue;
                }

                sendDlq(p, "unknown_result", attempt, null);
                return;
            } catch (Exception e) {
                if (RetryClassifier.isPermanent(e) || attempt >= maxAttempts) {
                    sendDlq(p, "exception:" + e.getClass().getSimpleName(), attempt, e.getMessage());
                    return;
                }
                long d = nextDelay(attempt);
                log.warn(
                    "link retry on exception... userId={}, provider={}, sub={}, attempt={}/{}, next={}ms, err={}",
                    p.userId(),
                    p.provider(),
                    p.providerSub(),
                    attempt,
                    maxAttempts,
                    d,
                    shortErr(e));
                sleep(d);
            }
        }
    }

    private void sendDlq(UserLinkPayload p, String reason, int attempt, String msg) {
        dlq.publishLink(
            new UserLinkFailed(
                p.userId(),
                p.provider(),
                p.providerTenant(),
                p.providerSub(),
                reason,
                Optional.ofNullable(msg).orElse(""),
                attempt,
                Instant.now()));
        log.error(
            "link DLQ sent: userId={}, provider={}, sub={}, reason={}",
            p.userId(),
            p.provider(),
            p.providerSub(),
            reason);
    }

    private long nextDelay(int attempt) {
        long delay = (long) (baseDelayMs * Math.pow(multiplier, attempt - 1));
        if (delay > maxDelayMs) delay = maxDelayMs;
        long jitter = ThreadLocalRandom.current().nextLong((long) (delay * 0.2));
        return delay - (delay / 10) + jitter;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String shortErr(Throwable t) {
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }
}