package com.colombus.common.security.assertion;

public class ClientAssertionProperties {
    
    /** iss / sub 로 들어갈 값 (보통 client_id 또는 서비스 ID) */
    private String issuer;

    /** aud 로 들어갈 값 */
    private String audience;

    /** JWK kid */
    private String kid;

    /** JWS alg (기본 RS256) */
    private String alg = "RS256";

    /** PKCS8 PRIVATE KEY PEM 문자열 (옵션) */
    private String privateKeyPem;

    /** PKCS8 PRIVATE KEY 파일 경로 (옵션) */
    private String privateKeyPath;

    /** 기본 TTL (초). sign() 호출 시 사용. 0 이하이면 60초로 대체 */
    private long ttlSeconds = 60;

    // ===== getters / setters =====

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getKid() {
        return kid;
    }

    public void setKid(String kid) {
        this.kid = kid;
    }

    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
