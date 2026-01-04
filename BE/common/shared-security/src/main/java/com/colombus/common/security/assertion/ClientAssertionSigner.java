package com.colombus.common.security.assertion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * 공통 client_assertion JWT 서명기.
 *
 * - 헤더:
 *   alg = props.alg
 *   kid = props.kid
 *   typ = "JWT" (필요하면 "at+jwt"로 변경 가능)
 *
 * - 클레임:
 *   iss = props.issuer
 *   sub = props.issuer
 *   aud = props.audience
 *   iat / exp / jti 자동 설정
 */
public final class ClientAssertionSigner {
    private static final long MIN_TTL_SECONDS = 60L;

    private final JWSSigner signer;
    private final ClientAssertionProperties props;
    private final Clock clock;

    public ClientAssertionSigner(
        PrivateKey privateKey,
        ClientAssertionProperties props,
        Clock clock
    ) {
        this.signer = new RSASSASigner(privateKey);
        this.props = props;
        this.clock = clock;
    }

    /**
     * 기본 TTL(props.ttlSeconds)을 사용해서 assertion 생성.
     */
    public String sign() {
        return signAssertion(props.getTtlSeconds());
    }

    /**
     * TTL override 로 assertion 생성.
     * ttlSecondsOverride <= 0 이면 props.ttlSeconds 사용.
     * 최종 TTL은 최소 60초 보장.
     */
    public String signAssertion(long ttlSecondsOverride) {
        try {
            long baseTtl = props.getTtlSeconds() > 0 ? props.getTtlSeconds() : MIN_TTL_SECONDS;
            long effectiveTtl = ttlSecondsOverride > 0 ? ttlSecondsOverride : baseTtl;
            if (effectiveTtl < MIN_TTL_SECONDS) {
                effectiveTtl = MIN_TTL_SECONDS;
            }

            Instant now = clock.instant();
            Date iat = Date.from(now);
            Date exp = Date.from(now.plusSeconds(effectiveTtl));

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(props.getIssuer())
                    .subject(props.getIssuer())
                    .audience(props.getAudience())
                    .issueTime(iat)
                    .expirationTime(exp)
                    .jwtID(UUID.randomUUID().toString())
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.parse(props.getAlg()))
                    .keyID(props.getKid())
                    .type(JOSEObjectType.JWT) // 필요하면 new JOSEObjectType("at+jwt") 로 변경
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign client assertion", e);
        }
    }

    /**
     * PEM 문자열 또는 파일 경로에서 PKCS8 PrivateKey 로딩.
     * - privateKeyPem 이 우선
     * - 없으면 privateKeyPath 사용
     */
    public static PrivateKey loadPrivateKey(ClientAssertionProperties p) {
        try {
            byte[] pkcs8;
            if (p.getPrivateKeyPem() != null && !p.getPrivateKeyPem().isBlank()) {
                String pem = p.getPrivateKeyPem()
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
                pkcs8 = Base64.getDecoder().decode(pem);
            } else if (p.getPrivateKeyPath() != null && !p.getPrivateKeyPath().isBlank()) {
                String path = p.getPrivateKeyPath().replace("file:", "");
                pkcs8 = Files.readAllBytes(Path.of(path));
            } else {
                throw new IllegalStateException("No private key configured for client assertion");
            }

            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load private key for client assertion", e);
        }
    }
}
