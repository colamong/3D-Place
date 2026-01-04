package com.colombus.auth.application;

import com.colombus.auth.infra.usersvc.UserSvcClient;
import com.colombus.auth.services.SessionTerminationService;
import com.colombus.user.contract.enums.AuthProviderCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserSvcClient userSvcClient;
    private final SessionTerminationService sessionTerminationService;

    public Mono<Void> logoutAll(UUID userId) {
        log.info("Logout-all requested for platform userId={}", userId);

        return userSvcClient
            .getIdentities(userId)
            .flatMap(identities -> {
                if (identities == null || identities.isEmpty()) {
                    log.info("No identities found for userId={}, nothing to logout", userId);
                    return Mono.empty();
                }

                log.info("Found {} identities for userId={} (logout-all)", identities.size(), userId);
                return sessionTerminationService.terminateSessionsByIdentities(identities);
            });
    }

    private String extractProvider(String sub) {
        int idx = sub.indexOf('|');
        if (idx <= 0) {
            return sub;
        }
        return sub.substring(0, idx);
    }

    private AuthProviderCode normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }

        return switch (provider.trim().toLowerCase()) {
            case "auth0", "auth0|" -> AuthProviderCode.AUTH0;
            case "google", "google-oauth2", "google_oauth2", "google-oidc" -> AuthProviderCode.GOOGLE;
            case "kakao" -> AuthProviderCode.KAKAO;
            case "guest" -> AuthProviderCode.GUEST;
            default -> null;
        };
    }
}
