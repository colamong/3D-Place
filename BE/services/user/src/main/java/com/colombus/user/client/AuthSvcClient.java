package com.colombus.user.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.colombus.common.security.assertion.ClientAssertionSigner;
import com.colombus.common.web.core.tracing.TraceIdProvider;
import com.colombus.user.contract.dto.DeleteIdentitiesRequest;
import com.colombus.user.contract.dto.ExternalIdentity;
import com.colombus.user.contract.dto.ExternalIdentityPayload;
import com.colombus.user.model.type.AuthProvider;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AuthSvcClient {

    private static final long ASSERTION_TTL_SECONDS = 180L;

    private final RestClient rest;
    private final ClientAssertionSigner assertionSigner;
    private final TraceIdProvider trace;

    public AuthSvcClient(
        RestClient.Builder restBuilder,
        @Value("${service.auth.base-url}") String authSvcBaseUrl,
        ClientAssertionSigner assertionSigner,
        TraceIdProvider trace
    ) {
        this.rest = restBuilder.baseUrl(authSvcBaseUrl).build();
        this.assertionSigner = assertionSigner;
        this.trace = trace;
    }

    /** 내부 unlink: auth-svc가 Auth0 unlink를 수행하고 {unlinked:true|false} 반환 */
    public boolean unlinkAtAuth0(UUID userId, AuthProvider provider, String tenant, String providerSub) {
        String assertion = assertionSigner.signAssertion(ASSERTION_TTL_SECONDS);
        log.info("[AuthSvcClient] Using client assertion for unlinkAtAuth0 (len={})", assertion.length());

        try {
            var resp = rest.post()
                .uri("/internal/auth0/unlink")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + assertion)
                .header("X-Internal-Exchange", "1")
                .header("X-Service-Name", "user-service")
                .header("X-Trace-Id", trace.currentTraceId())
                .header("X-CSRF-REQUIRED", "0")
                .body(Map.of(
                    "userId", userId.toString(),
                    "provider", provider,
                    "providerTenant", tenant,
                    "providerSub", providerSub
                ))
                .retrieve()
                .body(Map.class);

            return Boolean.TRUE.equals(resp.get("unlinked"));
        } catch (RestClientResponseException e) {
            // if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
            //     String retryBearer = authSvcToken(true);
            //     var resp = rest.post()
            //         .uri("/internal/auth0/unlink")
            //         .header(HttpHeaders.AUTHORIZATION, "Bearer " + retryBearer)
            //         .contentType(MediaType.APPLICATION_JSON)
            //         .body(Map.of(
            //             "userId", userId.toString(),
            //             "provider", provider,
            //             "providerTenant", tenant,
            //             "providerSub", providerSub
            //         ))
            //         .retrieve()
            //         .body(Map.class);

            //     return Boolean.TRUE.equals(resp.get("unlinked"));
            // }
            // throw e;
        }
        return true;
    }

    /** 내부 link: auth-svc가 Auth0 link를 수행하고 {linked:true|false} 반환 */
    public boolean linkAtAuth0(UUID userId, AuthProvider provider, String tenant, String providerSub) {
        String assertion = assertionSigner.signAssertion(ASSERTION_TTL_SECONDS);
        log.info("[AuthSvcClient] Using client assertion for linkAtAuth0 (len={})", assertion.length());

        try {
            var resp = rest.post()
                .uri("/internal/auth0/link")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + assertion)
                .header("X-Internal-Exchange", "1")
                .header("X-Service-Name", "user-service")
                .header("X-Trace-Id", trace.currentTraceId())
                .header("X-CSRF-REQUIRED", "0")
                .body(Map.of(
                    "userId", userId.toString(),
                    "provider", provider,
                    "providerTenant", tenant,
                    "providerSub", providerSub
                ))
                .retrieve()
                .body(Map.class);
            
            return Boolean.TRUE.equals(resp.get("linked"));
        } catch (RestClientResponseException e) {
            // if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
            //     String retryBearer = authSvcToken(true);
            //     var resp = rest.post()
            //         .uri("/internal/auth0/link")
            //         .contentType(MediaType.APPLICATION_JSON)
            //         .header(HttpHeaders.AUTHORIZATION, "Bearer " + retryBearer)
            //         .body(Map.of(
            //             "userId", userId.toString(),
            //             "provider", provider,
            //             "providerTenant", tenant,
            //             "providerSub", providerSub
            //         ))
            //         .retrieve()
            //         .body(Map.class);
                
            //     return Boolean.TRUE.equals(resp.get("linked"));
            // }
            // throw e;
        }
        return true;
    }

    public boolean updateAvatar(UUID userId, String avatarUrl) {
        String assertion = assertionSigner.signAssertion(ASSERTION_TTL_SECONDS);
        log.info("[AuthSvcClient] Using client assertion for updateAvatar (len={})", assertion.length());
        
        try {
            rest.put()
                .uri("/internal/auth0/avatar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION,"Bearer " + assertion)
                .header("X-Internal-Exchange", "1")
                .header("X-Service-Name", "user-service")
                .header("X-Trace-Id", trace.currentTraceId())
                .header("X-CSRF-REQUIRED", "0")
                .body(Map.of(
                    "userId", userId.toString(),
                    "avatarUrl", avatarUrl
                ))
                .retrieve()
                .body(Map.class);
        } catch (RestClientResponseException e) {
            // ignored - user flow should proceed even if avatar sync fails
        }
        return true;
    }

    public void deactivateIdentities(
        UUID userId,
        List<ExternalIdentity> identities,
        String reason
    ) {
        String assertion = assertionSigner.signAssertion(ASSERTION_TTL_SECONDS);
        log.info("[AuthSvcClient] Using client assertion for deleteUser (len={})", assertion.length());

        List<ExternalIdentityPayload> payloadIdentities = identities.stream()
            .map(i -> new ExternalIdentityPayload(
                i.provider(),
                i.tenant(),
                i.sub()
            ))
            .toList();

        DeleteIdentitiesRequest body = new DeleteIdentitiesRequest(payloadIdentities, reason);
        log.info("[AuthSvcClient] playloadIdentities={}", payloadIdentities.toArray());
        log.info("[AuthSvcClient] token={}", assertion);
        try {
            rest.post()
                .uri("/internal/auth0/{userId}/delete-identities", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + assertion)
                .header("X-Internal-Exchange", "1")
                .header("X-Service-Name", "user-service")
                .header("X-Trace-Id", trace.currentTraceId())
                .header("X-CSRF-REQUIRED", "0")
                .body(body)
                .retrieve()
                .toBodilessEntity();
            
            log.info("[AuthSvcClient] deleteUser({}) request sent successfully", userId);
        } catch (RestClientResponseException e) {
            log.warn(
                "[AuthSvcClient] Failed to delete Auth0 user={} - status={}, body={}",
                userId, e.getStatusCode(), e.getResponseBodyAsString()
            );
        }
    }
}
