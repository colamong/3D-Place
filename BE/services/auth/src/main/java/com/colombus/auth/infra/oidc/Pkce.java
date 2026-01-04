package com.colombus.auth.infra.oidc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class Pkce {
    private static final SecureRandom RNG = new SecureRandom();
    private Pkce() {}

    // 32바이트 ≈ 43자 base64url
    public static String randomState() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        return b64url(b);
    }

    // RFC 7636: 43~128자
    public static String newVerifier() {
        byte[] b = new byte[64];
        RNG.nextBytes(b);
        return b64url(b);
    }

    public static String challengeS256(String verifier) {
        try {
            var d = MessageDigest.getInstance("SHA-256");
            return b64url(d.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String b64url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    // 일정 시간 비교(타이밍 공격 완화)
    public static boolean constantTimeEq(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        int diff = x.length ^ y.length;
        for (int i = 0; i < Math.min(x.length, y.length); i++) diff |= (x[i] ^ y[i]);
        return diff == 0;
    }
}