package com.colombus.auth.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.colombus.auth.infra.auth0.Auth0MgmtClient;
import com.colombus.user.contract.dto.ExternalIdentityPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityDeactivationService {

    private final Auth0MgmtClient auth0MgmtClient;
    private final SessionTerminationService sessionTerminationService;

    public Mono<Void> deactivateIdentities(
        UUID userId,
        List<ExternalIdentityPayload> identities,
        String reason
    ) {
        if (identities == null || identities.isEmpty()) {
            log.info("No external identities to deactivate for userId={}, reason={}", userId, reason);
            // identity 없으면 세션도 없다고 보고 그냥 종료
            return Mono.empty();
        }

        // 외부 provider 계정 삭제
        Mono<Void> external = Flux.fromIterable(identities)
            .flatMap(identity -> {
                log.info("Deleting Auth0 user for userId={}, sub={}", userId, identity.sub());
                return auth0MgmtClient.deleteUser(identity.sub());
            })
            .then();

        // 세션 정리
        Mono<Void> sessions = sessionTerminationService.terminateSessionsByIdentities(identities);

        return Mono.when(external, sessions)
            .doOnSuccess(v ->
                log.info("Deactivated external identities & terminated sessions for userId={}, reason={}",
                    userId, reason)
            );
    }
}
