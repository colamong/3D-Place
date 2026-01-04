package com.colombus.auth.security.jwt;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.exchange")
public class TrustedIssuersProperties {
    private List<Issuer> issuers = new ArrayList<>();

    public List<Issuer> getIssuers() { return issuers; }
    public void setIssuers(List<Issuer> issuers) { this.issuers = issuers; }

    public static class Issuer {
        /** 외부 발급자(iss) */
        private String issuer;
        /** (선택) JWK URI 직접 지정. 없으면 issuer로 자동 유추 */
        private String jwkSetUri;
        /** (선택) inline JWK JSON (내부 서비스 client_assertion용) */
        private String jwkJson;
        /** (선택) 이 발급자 토큰이 가져야 하는 aud 목록(하나라도 일치하면 OK) */
        private List<String> audiences = new ArrayList<>();

        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }

        public String getJwkSetUri() { return jwkSetUri; }
        public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }

        public String getJwkJson() { return jwkJson; }
        public void setJwkJson(String jwkJson) { this.jwkJson = jwkJson; }

        public List<String> getAudiences() { return audiences; }
        public void setAudiences(List<String> audiences) { this.audiences = audiences; }
    }
}
