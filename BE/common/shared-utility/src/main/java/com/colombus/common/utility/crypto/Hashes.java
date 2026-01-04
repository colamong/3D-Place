package com.colombus.common.utility.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

public final class Hashes {

    private static final HexFormat HEX_LOWER = HexFormat.of();

    private Hashes() {}

    /* ---------- SHA-256 ---------- */

    /** String → SHA-256(url-safe Base64, no padding) */
    public static String sha256Base64Url(String s) {
        if (s == null) return null;
        return base64Url(sha256Bytes(s.getBytes(StandardCharsets.UTF_8)));
    }

    /** String → SHA-256(hex) */
    public static String sha256Hex(String s) {
        if (s == null) return null;
        return HEX_LOWER.formatHex(sha256Bytes(s.getBytes(StandardCharsets.UTF_8)));
    }

    /** bytes → SHA-256(bytes) */
    public static byte[] sha256Bytes(byte[] input) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            return d.digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /* ---------- SHA-512 (옵션) ---------- */

    public static String sha512Base64Url(String s) {
        if (s == null) return null;
        return base64Url(sha512Bytes(s.getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] sha512Bytes(byte[] input) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-512");
            return d.digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /* ---------- HMAC-SHA256 ---------- */

    public static String hmacSha256Base64Url(String data, byte[] secret) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
            return base64Url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /* ---------- helpers ---------- */

    public static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null) return null;
        return HEX_LOWER.formatHex(bytes);
    }

    public static byte[] fromHex(String hex) {
        if (hex == null) return null;
        return HEX_LOWER.parseHex(hex);
    }
}