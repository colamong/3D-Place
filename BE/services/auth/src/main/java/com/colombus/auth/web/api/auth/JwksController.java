package com.colombus.auth.web.api.auth;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JWKSet internalPublicJwkSet;

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> jwks() {
        return Mono.fromSupplier(internalPublicJwkSet::toJSONObject);
    }
}
