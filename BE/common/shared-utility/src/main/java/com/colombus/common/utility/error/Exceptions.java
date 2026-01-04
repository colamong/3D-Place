package com.colombus.common.utility.error;

import java.util.IdentityHashMap;
import java.util.Map;

public class Exceptions {
    
    private Exceptions() {}

    /** "SimpleClass: message" 형태로 축약 (기본 300자, 공백 정리 + 민감정보 레드액트) */
    public static String shortErr(Throwable t) {
        return shortErr(t, 300);
    }

    public static String shortErr(Throwable t, int maxLen) {
        if (t == null) return "null";
        String msg = t.getMessage();
        String base = t.getClass().getSimpleName() + (msg == null || msg.isBlank() ? "" : (": " + sanitize(msg)));
        base = base.replaceAll("\\s+", " ").trim();
        if (maxLen > 0 && base.length() > maxLen) {
            return base.substring(0, Math.max(1, maxLen - 1)) + "…";
        }
        return base;
    }

    /** 최하위 cause 반환(순환 방지) */
    public static Throwable rootCause(Throwable t) {
        if (t == null) return null;
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        Throwable cur = t, next = t.getCause();
        while (next != null && !seen.containsKey(next)) {
            seen.put(next, Boolean.TRUE);
            cur = next;
            next = next.getCause();
        }
        return cur;
    }

    /** cause 체인을 "Simple: msg -> Simple: msg"로 최대 depth만큼 요약 */
    public static String causeChain(Throwable t, int maxDepth, int eachMaxLen) {
        if (t == null) return "null";
        StringBuilder sb = new StringBuilder();
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        int depth = 0;
        Throwable cur = t;
        while (cur != null && depth < Math.max(1, maxDepth) && !seen.containsKey(cur)) {
            if (depth > 0) sb.append(" -> ");
            sb.append(shortErr(cur, eachMaxLen));
            seen.put(cur, Boolean.TRUE);
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }

    /** 로그에 섞일 수 있는 민감 정보 간단 레드액트 */
    private static String sanitize(String s) {
        if (s == null) return null;
        String x = s;
        // Bearer 토큰
        x = x.replaceAll("(?i)bearer\\s+[A-Za-z0-9._-]+", "Bearer [REDACTED]");
        // AWS Access Key (형태만 간단 감지)
        x = x.replaceAll("AKIA[0-9A-Z]{16}", "AKIA[REDACTED]");
        // secret/password 키-값
        x = x.replaceAll("(?i)(secret(?:access)?key|password)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]");
        return x;
    }
}
