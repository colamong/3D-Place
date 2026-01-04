package com.colombus.common.web.core.clientip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ForwardedHeaders {
    private ForwardedHeaders() {}

    /** X-Forwarded-For 파싱 (좌→우 순서 유지) */
    public static List<String> parseXForwardedFor(String headerVal) {
        if (headerVal == null || headerVal.isBlank()) return Collections.emptyList();
        String[] parts = headerVal.split(",");
        List<String> out = new ArrayList<>(parts.length);
        for (String raw : parts) {
            String token = raw.trim();
            if (token.isEmpty()) continue;
            out.add(stripPortAndBrackets(unquote(token)));
        }
        return out;
    }

    /** RFC 7239 Forwarded 헤더에서 for= 값들만 추출 */
    public static List<String> parseForwardedFor(String headerVal) {
        if (headerVal == null || headerVal.isBlank()) return Collections.emptyList();
        List<String> out = new ArrayList<>();

        // Forwarded는 콤마(여러 엔트리)와 세미콜론(파라미터)로 섞여 있음.
        // 단순/견고하게: 세미콜론 기준으로 쪼개고 for= 키만 추출.
        String[] parts = headerVal.split(";");
        for (String p : parts) {
            String s = p.trim();
            int i = s.toLowerCase().indexOf("for=");
            if (i >= 0) {
                String v = s.substring(i + 4).trim();
                int end = v.indexOf(',');
                if (end >= 0) v = v.substring(0, end);
                v = v.replaceAll("\\s+", "");
                v = unquote(v);
                v = stripPortAndBrackets(v);
                if (!v.isEmpty() && !"unknown".equalsIgnoreCase(v)) out.add(v);
            }
        }
        return out;
    }

    /** "[::1]:8080" → "::1", "1.2.3.4:80" → "1.2.3.4" */
    public static String stripPortAndBrackets(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.isEmpty()) return s;

        // IPv6 [addr]:port
        if (s.charAt(0) == '[') {
            int end = s.indexOf(']');
            if (end > 1) return s.substring(1, end);
        }

        // IPv4:port (콜론이 한 개만 있을 때)
        int lastColon = s.lastIndexOf(':');
        if (lastColon > -1 && s.indexOf(':') == lastColon) {
            return s.substring(0, lastColon);
        }
        return s;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}