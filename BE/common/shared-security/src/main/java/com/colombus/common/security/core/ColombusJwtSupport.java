package com.colombus.common.security.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;

import reactor.core.publisher.Mono;

public final class ColombusJwtSupport {

    private ColombusJwtSupport() {}

    // ===== MVC (servlet) =====
    public static JwtDecoder jwtDecoder(JwtSecurityProperties props) {
        NimbusJwtDecoder dec = NimbusJwtDecoder.withJwkSetUri(props.jwkSetUri()).build();

        OAuth2TokenValidator<Jwt> withIssuer =
            JwtValidators.createDefaultWithIssuer(props.issuer());

        OAuth2TokenValidator<Jwt> audienceIfPresent = jwt -> {
            var aud = jwt.getAudience();
            String requiredAud = props.requiredAudience();
            if (aud == null || aud.isEmpty() || requiredAud == null || requiredAud.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }
            return aud.contains(requiredAud)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "audience mismatch", null));
        };

        dec.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audienceIfPresent));
        return dec;
    }

    public static JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthorityPrefix("SCOPE_");

        Converter<Jwt, Collection<GrantedAuthority>> merge = jwt -> {
            List<GrantedAuthority> auths = new ArrayList<>(scopes.convert(jwt));

            List<String> perms = jwt.getClaimAsStringList("permissions");
            if (perms != null) {
                perms.forEach(p -> auths.add(new SimpleGrantedAuthority("PERM_" + p)));
            }

            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(r -> auths.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }

            return auths;
        };

        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(merge);
        return conv;
    }

    // ===== WebFlux (reactive) =====
    public static ReactiveJwtDecoder reactiveJwtDecoder(JwtSecurityProperties props) {
        NimbusReactiveJwtDecoder dec =
            NimbusReactiveJwtDecoder.withJwkSetUri(props.jwkSetUri()).build();

        OAuth2TokenValidator<Jwt> withIssuer =
            JwtValidators.createDefaultWithIssuer(props.issuer());

        OAuth2TokenValidator<Jwt> audienceIfPresent = jwt -> {
            var aud = jwt.getAudience();
            String requiredAud = props.requiredAudience();
            if (aud == null || aud.isEmpty() || requiredAud == null || requiredAud.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }
            return aud.contains(requiredAud)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "audience mismatch", null));
        };

        dec.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audienceIfPresent));
        return dec;
    }

    public static Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveJwtAuthConverter() {
        JwtAuthenticationConverter base = jwtAuthConverter();
        return new ReactiveJwtAuthenticationConverterAdapter(base);
    }
}