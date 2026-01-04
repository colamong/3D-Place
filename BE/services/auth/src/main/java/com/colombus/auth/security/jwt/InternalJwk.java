package com.colombus.auth.security.jwt;

import com.colombus.common.utility.text.Texts;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;


@Configuration
public class InternalJwk {

    private final RSAKey privateRsaJwk;
    private final JWKSet publicJwkSet;

    public InternalJwk(
        // 공통
        @Value("${internal-jwt.mode:generate}") String mode,
        @Value("${internal-jwt.kid:}") String kidOpt,

        // JWK 모드
        @Value("${internal-jwt.jwk-json:}") String jwkJson,

        // PEM 모드
        @Value("${internal-jwt.pem.private-path:}") String pemPrivatePath,
        @Value("${internal-jwt.pem.public-path:}") String pemPublicPath, // 선택(없으면 private에서 도출 시도)

        // Keystore 모드
        @Value("${internal-jwt.keystore.path:}")  String ksPath,
        @Value("${internal-jwt.keystore.type:PKCS12}") String ksType,
        @Value("${internal-jwt.keystore.password:}") String ksPassword,
        @Value("${internal-jwt.keystore.alias:}") String ksAlias,
        @Value("${internal-jwt.keystore.key-password:}") String keyPassword // 선택
    ) {
        try {
            RSAKey rsa;

            // JWK JSON 모드
            if ("jwk".equalsIgnoreCase(mode) && Texts.isBlank(jwkJson)) {
                rsa = RSAKey.parse(jwkJson);
                if (rsa.getKeyID() == null && Texts.isBlank(kidOpt)) {
                    rsa = new RSAKey.Builder(rsa).keyID(kidOpt).build();
                }

            // PEM 모드
            } else if ("pem".equalsIgnoreCase(mode) && Texts.hasText(pemPrivatePath)) {
                RSAPrivateKey priv = loadRsaPrivateKeyPkcs8(Path.of(pemPrivatePath));
                RSAPublicKey pub = Texts.hasText(pemPublicPath)
                        ? loadRsaPublicKeyX509(Path.of(pemPublicPath))
                        : derivePublicIfPossible(priv);
                if (pub == null) {
                    throw new IllegalStateException("PEM public-path 미지정 && private로 공개키 도출 불가");
                }
                rsa = buildRsaJwk(pub, priv, kidOpt);
                
            // Keystore 모드
            } else if ("keystore".equalsIgnoreCase(mode) && Texts.hasText(ksPath) && Texts.hasText(ksAlias)) {
                var pair = loadFromKeyStore(ksPath, ksType, ksPassword, ksAlias,
                        Texts.hasText(keyPassword) ? keyPassword : ksPassword);
                rsa = buildRsaJwk(pair.pub(), pair.priv(), kidOpt);

            // dev용 generate
            } else {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                KeyPair kp = gen.generateKeyPair();
                rsa = buildRsaJwk((RSAPublicKey) kp.getPublic(), (RSAPrivateKey) kp.getPrivate(), kidOpt);
            }

            this.privateRsaJwk = rsa;
            this.publicJwkSet = new JWKSet(this.privateRsaJwk.toPublicJWK());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to init InternalJwk", e);
        }
    }

    // ========== public API ==========

    @Bean
    public JWKSet internalPublicJwkSet() {
        return this.publicJwkSet;
    }

    public JWKSource<SecurityContext> signingJwkSource() {
        JWKSet privateSet = new JWKSet(this.privateRsaJwk);
        return (selector, ctx) -> selector.select(privateSet);
    }

    public String keyId() {
        return this.privateRsaJwk.getKeyID();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(signingJwkSource());
    }

    // ========== helpers ==========

    private static RSAKey buildRsaJwk(RSAPublicKey pub, RSAPrivateKey priv, String kidOpt) {
        String kid = Texts.hasText(kidOpt) ? kidOpt : UUID.randomUUID().toString();
        return new RSAKey.Builder(pub)
                .privateKey(priv)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(kid)
                .build();
    }

    private static RSAPrivateKey loadRsaPrivateKeyPkcs8(Path path) throws Exception {
        byte[] der = readPem(path, "PRIVATE KEY");
        var keySpec = new PKCS8EncodedKeySpec(der);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private static RSAPublicKey loadRsaPublicKeyX509(Path path) throws Exception {
        byte[] der = readPem(path, "PUBLIC KEY");
        var keySpec = new X509EncodedKeySpec(der);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    /** RSAPrivateCrtKey면 public 파라미터로부터 공개키 복원 */
    private static RSAPublicKey derivePublicIfPossible(RSAPrivateKey priv) throws Exception {
        if (priv instanceof java.security.interfaces.RSAPrivateCrtKey crt) {
            var spec = new java.security.spec.RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent());
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        }
        return null;
    }

    private static byte[] readPem(Path path, String type) throws Exception {
        String pem = Files.readString(path);
        String begin = "-----BEGIN " + type + "-----";
        String end   = "-----END " + type + "-----";
        int s = pem.indexOf(begin);
        int e = pem.indexOf(end);
        if (s < 0 || e < 0) throw new IllegalArgumentException("PEM boundary not found: " + type);
        String b64 = pem.substring(s + begin.length(), e).replaceAll("\\s", "");
        return Base64.getDecoder().decode(b64);
    }

    private record RsaPair(RSAPublicKey pub, RSAPrivateKey priv) {}

    private static RsaPair loadFromKeyStore(
            String path, String type, String storePass,
            String alias, String keyPass
    ) throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        try (var in = new FileInputStream(path)) {
            ks.load(in, Texts.nullIfBlank(storePass));
        }
        var key = (PrivateKey) ks.getKey(alias, Texts.nullIfBlank(keyPass));
        if (key == null) throw new IllegalStateException("Key not found: " + alias);
        Certificate cert = ks.getCertificate(alias);
        if (cert == null) throw new IllegalStateException("Cert not found for alias: " + alias);
        RSAPublicKey pub = (RSAPublicKey) cert.getPublicKey();
        return new RsaPair(pub, (RSAPrivateKey) key);
    }
}