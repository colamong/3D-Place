package com.colombus.auth.infra.auth0;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.colombus.auth.infra.auth0.exception.Auth0BadRequestException;
import com.colombus.auth.infra.auth0.exception.Auth0ClientException;
import com.colombus.auth.infra.auth0.exception.Auth0ConflictException;
import com.colombus.auth.infra.auth0.exception.Auth0NotFoundException;
import com.colombus.auth.infra.auth0.exception.Auth0ServerException;
import com.colombus.auth.infra.auth0.exception.Auth0UnauthorizedException;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class Auth0MgmtClient {

    private final Auth0Properties props;
    private final WebClient auth0Client;

    private Mono<String> getMgmtToken() {
        Map<String, String> body = Map.of(
            "grant_type", "client_credentials",
            "client_id", props.mgmt().clientId(),
            "client_secret", props.mgmt().clientSecret(),
            "audience", props.mgmt().audience()
        );

        return auth0Client.post()
            .uri("/oauth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(TokenRes.class)
            .map(res -> {
                if (res == null || res.access_token == null) {
                    throw new IllegalStateException("Mgmt token failed");
                }
                return res.access_token;
            });
    }

    public Mono<User> findDbUserByEmail(String email) {
        return getMgmtToken()
            .flatMap(token -> auth0Client.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v2/users-by-email")
                    .queryParam("email", email)
                    .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(User[].class))
            .flatMap(users -> Mono.justOrEmpty(filterDbUser(users)));
    }

    public Mono<Void> sendVerificationEmail(String userId) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("client_id", props.appClientId());

        return getMgmtToken()
            .flatMap(token -> auth0Client.post()
                .uri("/api/v2/jobs/verification-email")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class));
    }

    public Mono<Void> linkWithIdToken(String primaryAuth0UserId, String secondaryIdToken) {
        return getMgmtToken().flatMap(token -> auth0Client.post()
            .uri(uri -> uri.path("/api/v2/users/{id}/identities").build(primaryAuth0UserId))
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .bodyValue(Map.of("link_with", secondaryIdToken))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, res -> mapClientError(res, true))
            .onStatus(HttpStatusCode::is5xxServerError, res -> mapServerError(res))
            .bodyToMono(Void.class));
    }

    public Mono<Void> unlinkIdentity(String primaryAuth0UserId, String provider, String providerUserId) {
        return getMgmtToken().flatMap(token -> auth0Client.delete()
            .uri("/api/v2/users/{id}/identities/{provider}/{uid}", primaryAuth0UserId, provider, providerUserId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, res -> mapClientError(res, false))
            .onStatus(HttpStatusCode::is5xxServerError, res -> mapServerError(res))
            .bodyToMono(Void.class));
    }

    public Mono<Void> updateUserAvatar(String auth0UserId, String avatarUrl) {
        if (auth0UserId == null || auth0UserId.isBlank()) {
            return Mono.error(new IllegalArgumentException("auth0UserId is required"));
        }
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return Mono.error(new IllegalArgumentException("avatarUrl is required"));
        }

        Map<String, Object> userMetadata = Map.of("avatarUrl", avatarUrl);

        Map<String, Object> body = new HashMap<>();
        body.put("picture", avatarUrl);
        body.put("user_metadata", userMetadata);

        return getMgmtToken()
            .flatMap(token -> auth0Client.patch()
                .uri("/api/v2/users/{id}", auth0UserId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, res -> mapClientError(res, false))
                .onStatus(HttpStatusCode::is5xxServerError, this::mapServerError)
                .bodyToMono(Void.class));
    }

    public Mono<Void> blockUser(String auth0UserId) {
        return getMgmtToken()
            .flatMap(token -> auth0Client.patch()
                .uri("/api/v2/users/{id}", auth0UserId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("blocked", true))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, res -> mapClientError(res, false))
                .onStatus(HttpStatusCode::is5xxServerError, this::mapServerError)
                .bodyToMono(Void.class));
    }

    public Mono<Void> unblockUser(String auth0UserId) {
        return getMgmtToken()
            .flatMap(token -> auth0Client.patch()
                .uri("/api/v2/users/{id}", auth0UserId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("blocked", false))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, res -> mapClientError(res, false))
                .onStatus(HttpStatusCode::is5xxServerError, this::mapServerError)
                .bodyToMono(Void.class));
    }

    public Mono<Void> deleteUser(String auth0UserId) {
        return getMgmtToken()
            .flatMap(token -> auth0Client.delete()
                .uri("/api/v2/users/{id}", auth0UserId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, res -> mapClientError(res, false))
                .onStatus(HttpStatusCode::is5xxServerError, this::mapServerError)
                .bodyToMono(Void.class));
    }

    private Optional<User> filterDbUser(User[] users) {
        if (users == null) return Optional.empty();
        return Arrays.stream(users)
            .filter(u -> u.user_id != null && u.email != null)
            .filter(u -> u.identities != null && u.identities.stream().anyMatch(id ->
                "auth0".equalsIgnoreCase(id.provider)
                    && (props.dbConnection() == null || props.dbConnection().equals(id.connection))
            ))
            .findFirst();
    }

    private Mono<RuntimeException> mapClientError(ClientResponse res, boolean allowConflict) {
        return res.bodyToMono(byte[].class)
            .defaultIfEmpty(new byte[0])
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
            .map(body -> {
                int status = res.statusCode().value();
                return switch (status) {
                    case 400 -> new Auth0BadRequestException(body);
                    case 401, 403 -> new Auth0UnauthorizedException(body);
                    case 404 -> new Auth0NotFoundException(body);
                    case 409 -> allowConflict
                        ? new Auth0ConflictException(body)
                        : new Auth0ClientException("409: " + body);
                    default -> new Auth0ClientException("4xx " + status + ": " + body);
                };
            });
    }

    private Mono<RuntimeException> mapServerError(ClientResponse res) {
        return res.bodyToMono(byte[].class)
            .defaultIfEmpty(new byte[0])
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
            .map(Auth0ServerException::new);
    }

    static class TokenRes { public String access_token; public String token_type; }

    public static class User {
        public String user_id;
        public String email;
        public Boolean email_verified;
        public String created_at;
        public List<Identity> identities;
    }
    public static class Identity {
        public String provider;
        public String connection;
        public String user_id;
    }
}
