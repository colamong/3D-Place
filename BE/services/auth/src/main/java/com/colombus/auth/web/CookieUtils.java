package com.colombus.auth.web;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseCookie.ResponseCookieBuilder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import com.colombus.common.utility.text.Texts;

public class CookieUtils {
    private CookieUtils() {}

    /** 기본 SameSite 정책 (환경에 맞게 변경 가능) */
    public static final String DEFAULT_SAMESITE = "Lax";

    /** RFC 토큰 패턴(쿠키 이름용) 대략적 허용: !#$%&'*+-.^_`|~ 0-9 a-z A-Z */
    private static final Pattern COOKIE_NAME_TOKEN = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    
    // WebFlux 환경에서는 ServerHttpResponse.addCookie로 직접 Set-Cookie 헤더를 조작한다.
    public static void expireSessionCookies(ServerHttpRequest req, ServerHttpResponse resp, String... names) {
        boolean secure = isSecureRequest(req);
        String sameSite = DEFAULT_SAMESITE;
        String ctxPath = normalizeContextPath(req.getPath().contextPath().value());
        expire(resp, secure, sameSite, null, pathCandidates(ctxPath), names);
    }

    public static void expireSessionCookiesEx(
            ServerHttpRequest req, ServerHttpResponse resp,
            String cookieDomain, String sameSite, String... names
    ) {
        boolean secure = isSecureRequest(req);
        String normalizedSameSite = normalizeSameSite(sameSite);
        if ("None".equalsIgnoreCase(sameSite)) secure = true;

        String ctxPath = normalizeContextPath(req.getPath().contextPath().value());
        Set<String> paths = pathCandidates(ctxPath);

        expire(resp, secure, normalizedSameSite, null, paths, names);
        if (Texts.hasText(cookieDomain)) {
            expire(resp, secure, normalizedSameSite, cookieDomain, paths, names);
        }
    }

    private static void expire(
            ServerHttpResponse resp, boolean secure, String sameSite, String domain,
            Set<String> paths, String... names
    ) {
        List<String> cleanedNames = cleanCookieNames(names);
        if (cleanedNames.isEmpty()) return;
        for (String name : cleanedNames) {
            for (String path : paths) {
                ResponseCookieBuilder b = ResponseCookie.from(name, "")
                        .path(path)
                        .httpOnly(true)
                        .secure(secure)
                        .maxAge(0);
                if (Texts.hasText(sameSite)) b.sameSite(sameSite);
                if (Texts.hasText(domain))   b.domain(domain);
                resp.addCookie(b.build());
            }
        }
    }

    /** 컨텍스트 경로 및 상위 경로 후보를 모두 반환: "/", "/app", "/app/sub" ... */
    private static Set<String> pathCandidates(String ctxPath) {
        // 중복 방지 + 순서 유지
        Set<String> set = new LinkedHashSet<>();
        set.add("/"); // 항상 시도

        if (!Texts.hasText(ctxPath) || "/".equals(ctxPath)) return set;

        String p = ctxPath;
        String[] segments = p.substring(1).split("/");
        StringBuilder acc = new StringBuilder();
        for (String seg : segments) {
            if (!Texts.hasText(seg)) continue;
            acc.append('/').append(seg);
            set.add(acc.toString());
        }

        return set;
    }

    /** 컨텍스트 경로 정규화: null/빈 → "/", 끝 슬래시 제거(루트 제외) */
    private static String normalizeContextPath(String cp) {
        if (!Texts.hasText(cp)) return "/";
        cp = cp.trim();
        if (cp.length() > 1 && cp.endsWith("/")) cp = cp.substring(0, cp.length() - 1);
        return cp.isEmpty() ? "/" : cp;
    }

    /** SameSite 정규화: Strict/Lax/None 외는 null 처리(헤더 미설정) */
    private static String normalizeSameSite(String s) {
        if (!Texts.hasText(s)) return DEFAULT_SAMESITE;
        String lc = s.trim().toLowerCase(Locale.ROOT);
        return switch (lc) {
            case "strict" -> "Strict";
            case "lax"    -> "Lax";
            case "none"   -> "None";
            default       -> null;
        };
    }

    /** 요청이 HTTPS로 처리되는지 추정 (프록시 헤더 포함) */
    private static boolean isSecureRequest(ServerHttpRequest req) {
        String xfp = req.getHeaders().getFirst("X-Forwarded-Proto");
        if (Texts.hasText(xfp) && "https".equalsIgnoreCase(xfp.trim())) return true;

        String xfs = req.getHeaders().getFirst("X-Forwarded-Ssl");
        if (Texts.hasText(xfs) && ("on".equalsIgnoreCase(xfs.trim()) || "1".equals(xfs.trim()))) return true;

        String fwd = req.getHeaders().getFirst("Forwarded");
        if (Texts.hasText(fwd) && fwd.toLowerCase(Locale.ROOT).contains("proto=https")) return true;

        if (req.getSslInfo() != null) return true;
        String scheme = req.getURI().getScheme();
        return scheme != null && scheme.equalsIgnoreCase("https");
    }

    /** 쿠키 이름 정리/검증/중복 제거 */
    private static List<String> cleanCookieNames(String... names) {
        List<String> out = new ArrayList<>();
        if (names == null || names.length == 0) return out;

        Set<String> uniq = new LinkedHashSet<>();
        for (String raw : names) {
            if (!Texts.hasText(raw)) continue;
            String n = raw.trim();
            if (!COOKIE_NAME_TOKEN.matcher(n).matches()) continue;
            if (n.length() > 128) continue;
            uniq.add(n);
        }
        out.addAll(uniq);
        return out;
    }
}
