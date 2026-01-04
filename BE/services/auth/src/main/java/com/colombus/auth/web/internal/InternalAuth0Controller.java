package com.colombus.auth.web.internal;

import com.colombus.auth.services.IdentityDeactivationService;
import com.colombus.auth.infra.auth0.Auth0MgmtClient;
import com.colombus.auth.web.internal.dto.LinkIdentityRequest;
import com.colombus.auth.web.internal.dto.UnlinkIdentityRequest;
import com.colombus.auth.web.internal.dto.UpdateAuth0AvatarRequest;
import com.colombus.user.contract.dto.DeleteIdentitiesRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/auth0")
public class InternalAuth0Controller {

    private final Auth0MgmtClient client;
    private final IdentityDeactivationService identityDeactivationService;

    @PostMapping("/link")
    public Mono<Map<String, Object>> auth0LinkProvider(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody LinkIdentityRequest body
    ) {
        String callerUid = jwt.getClaimAsString("uid");
        log.info("Linking Auth0 identity: callerUid={}, primaryAuth0UserId={}",
            callerUid, body.primaryAuth0UserId());

        return client.linkWithIdToken(body.primaryAuth0UserId(), body.secondaryIdToken())
                    .thenReturn(Map.of(
                        "status", "linked",
                        "primaryAuth0UserId", body.primaryAuth0UserId()
                    ));
    }

    @PostMapping("/unlink")
    public Mono<Map<String, Object>> auth0UnlinkProvider(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody UnlinkIdentityRequest body
    ) {
        String callerUid = jwt.getClaimAsString("uid");
        log.info("Unlinking Auth0 identity: callerUid={}, primaryAuth0UserId={}, provider={}, providerUserId={}",
            callerUid, body.primaryAuth0UserId(), body.provider(), body.providerUserId());

        return client.unlinkIdentity(body.primaryAuth0UserId(), body.provider(), body.providerUserId())
                    .thenReturn(Map.of(
                        "status", "unlinked",
                        "primaryAuth0UserId", body.primaryAuth0UserId(),
                        "provider", body.provider(),
                        "providerUserId", body.providerUserId()
                    ));
    }

    @PutMapping("/avatar")
    public Mono<Void> postMethodName(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody UpdateAuth0AvatarRequest body
    ) {
        return client.updateUserAvatar(body.auth0UserId(), body.avatarUrl());
    }

    @PostMapping("/{userId}/delete-identities")
    public Mono<ResponseEntity<Void>> deleteIdentities(
        @PathVariable UUID userId,
        @RequestBody DeleteIdentitiesRequest request
    ) {
        return identityDeactivationService
            .deactivateIdentities(userId, request.identities(), request.reason())
            .thenReturn(ResponseEntity.noContent().build());
    }
}
