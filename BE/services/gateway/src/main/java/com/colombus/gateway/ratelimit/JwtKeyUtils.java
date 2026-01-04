package com.colombus.gateway.ratelimit;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Base64.Decoder;

import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JwtKeyUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Decoder B64U = Base64.getUrlDecoder();

    private JwtKeyUtils() {}

    static Optional<Map<String, Object>> decodeJwtPayload(String bearerValue) {
        // bearerValue: "Bearer xxx.yyy.zzz"
        if (bearerValue == null) return Optional.empty();
        String[] parts = bearerValue.split(" ");
        if (parts.length != 2 || !"Bearer".equalsIgnoreCase(parts[0])) return Optional.empty();
        String token = parts[1];
        String[] seg = token.split("\\.");
        if (seg.length < 2) return Optional.empty();
        try {
            byte[] json = B64U.decode(seg[1]);
            Map<String, Object> claims = MAPPER.readValue(json, new TypeReference<>() {});
            return Optional.ofNullable(claims);
        } catch (Exception ignore) {
            return Optional.empty();
        }
    }

    static String firstForwardedIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) return first;
        }
        var ra = exchange.getRequest().getRemoteAddress();
        if (ra != null && ra.getAddress() != null) {
            return ra.getAddress().getHostAddress();
        }
        return "unknown";
    }

    static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    static String hostFromIss(String iss) {
        if (iss == null) return null;
        try {
            var u = new java.net.URI(iss);
            return u.getHost();
        } catch (Exception e) {
            return iss;
        }
    }
}
