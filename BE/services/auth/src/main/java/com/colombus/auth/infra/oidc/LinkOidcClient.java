package com.colombus.auth.infra.oidc;

import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class LinkOidcClient {

    private final ReactiveClientRegistrationRepository registrations;
    private final WebClient oidcClient;

    public Mono<String> buildAuthorizeUrl(
        String registrationId,
        String state,
        String codeChallenge,
        String redirectUri,
        String connection
    ) {
        return require(registrationId)
            .map(cr -> {
                UriComponentsBuilder b = UriComponentsBuilder
                    .fromUriString(cr.getProviderDetails().getAuthorizationUri())
                    .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, "code")
                    .queryParam(OAuth2ParameterNames.CLIENT_ID, cr.getClientId())
                    .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
                    .queryParam(OAuth2ParameterNames.SCOPE, String.join(" ", cr.getScopes()))
                    .queryParam(OAuth2ParameterNames.STATE, state)
                    .queryParam(PkceParameterNames.CODE_CHALLENGE, codeChallenge)
                    .queryParam(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256")
                    .queryParam("prompt", "login")
                    .queryParam("max_age", "0");

                if (connection != null && !connection.isBlank()) {
                    b.queryParam("connection", connection);
                }
                return b.build().toUriString();
            });
    }

    public Mono<OidcTokens> exchangeCode(
        String registrationId,
        String code,
        String codeVerifier,
        String redirectUri
    ) {
        return require(registrationId)
            .flatMap(cr -> {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
                form.add(OAuth2ParameterNames.CODE, code);
                form.add(OAuth2ParameterNames.REDIRECT_URI, redirectUri);
                form.add(OAuth2ParameterNames.CLIENT_ID, cr.getClientId());
                if (cr.getClientSecret() != null && !cr.getClientSecret().isBlank()) {
                    form.add(OAuth2ParameterNames.CLIENT_SECRET, cr.getClientSecret());
                }
                form.add(PkceParameterNames.CODE_VERIFIER, codeVerifier);

                return oidcClient.post()
                    .uri(cr.getProviderDetails().getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .map(this::mapTokens);
            });
    }

    public Mono<Jwt> decodeIdToken(String registrationId, String idToken) {
        return require(registrationId)
            .map(cr -> {
                String jwkSetUri = cr.getProviderDetails().getJwkSetUri();
                JwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
                return decoder.decode(idToken);
            });
    }

    private OidcTokens mapTokens(Map<String, Object> token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("Token endpoint returned empty body.");
        }

        String error = asString(token, "error");
        if (error != null) {
            String desc = asString(token, "error_description");
            throw new IllegalStateException("Token endpoint error: " + error + (desc != null ? " - " + desc : ""));
        }

        String access = asString(token, "access_token");
        if (access == null || access.isBlank()) {
            throw new IllegalStateException("Token endpoint did not return access_token.");
        }

        String idToken = asString(token, "id_token");
        String refresh = asString(token, "refresh_token");

        return new OidcTokens(access, idToken, refresh);
    }

    private Mono<ClientRegistration> require(String id) {
        return registrations.findByRegistrationId(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Unknown registrationId: " + id)));
    }

    private static String asString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return (v instanceof String s) ? s : null;
    }

    public record OidcTokens(String accessToken, String idToken, String refreshToken) {}
}
