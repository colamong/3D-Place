package com.colombus.auth.web.internal;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;

import com.colombus.auth.security.jwt.InternalTokenCacheService;
import com.colombus.auth.security.jwt.InternalTokenIssuer;
import com.colombus.common.utility.text.Texts;
import reactor.core.publisher.Mono;

import lombok.extern.slf4j.Slf4j;

@Slf4j
// 내부 서비스 토큰/세션 토큰을 교환하는 컨트롤러: gateway 헤더, CSRF, 세션 절대시간 등 세밀히 검증한다.
@RestController
@RequestMapping("/internal/token")
class InternalTokenController {

    private static final String HDR_INTERNAL       = "X-Internal-Exchange";
    private static final String HDR_SERVICE        = "X-Service-Name";
    private static final String HDR_TOUCH          = "X-Session-Touch";
    private static final String HDR_ORIG_METHOD    = "X-Original-Method";
    private static final String HDR_XSRF           = "X-CSRF-TOKEN";
    private static final String COOKIE_XSRF        = "XSRF-TOKEN";

    private static final Set<String> ALLOWED_SOURCES = Set.of(
        "gateway-service",
        "bff-service",
        "internal-proxy"
    );

    private static final String ATTR_ABS_DEADLINE  = "ABS_DEADLINE";
    private static final String ATTR_TOUCH_AT      = "S_TOUCH_AT";
    private static final Duration MIN_TOUCH_INTERVAL = Duration.ofSeconds(60);
    private static final long   MAX_TTL_SEC_GENERIC = 3600L;
    private static final long   MAX_TTL_SEC_SESSION = 300L;

    private final InternalTokenIssuer issuer;
    private final InternalTokenCacheService cache;

    InternalTokenController(InternalTokenIssuer issuer, InternalTokenCacheService cache) {
        this.issuer = issuer;
        this.cache = cache;
    }

    @PostMapping({"", "/"})
    public Mono<Map<String, String>> mint(
            @RequestParam(name = "aud", required = false) String aud,
            @RequestParam(name = "need", required = false) String need,
            @RequestParam(name = "ttl", required = false) Long ttlSec,
            Authentication auth,
            ServerWebExchange exchange
    ) {
        return issue(aud, need, ttlSec, auth, exchange, false);
    }

    @PostMapping("/session")
    public Mono<Map<String, String>> mintForSession(
            @RequestParam(name = "aud", required = false) String aud,
            @RequestParam(name = "need", required = false) String need,
            @RequestParam(name = "ttl", required = false) Long ttlSec,
            Authentication auth,
            ServerWebExchange exchange
    ) {
        return issue(aud, need, ttlSec, auth, exchange, true);
    }

    private Mono<Map<String, String>> issue(
            String aud, String need, Long ttlSec,
            Authentication auth, ServerWebExchange exchange,
            boolean sessionEndpoint
    ) {
        log.info("[InternalTokenController] issue sessionEndpoint={}", sessionEndpoint);
        return requireGatewayHeader(exchange.getRequest())
            .then(sessionEndpoint ? requireAuthenticated(auth) : Mono.empty())
            // .then(requireValidNeedAndAud(aud, need))
            // .then(requireHasNeededScope(auth, need, sessionEndpoint))
            .then(Mono.defer(() -> sessionEndpoint
                ? exchange.getSession().flatMap(session -> handleSessionToken(aud, need, ttlSec, auth, exchange, session))
                : issueToken(aud, need, ttlSec, auth, exchange, false, null)));
    }

    private Mono<Map<String, String>> handleSessionToken(
        String aud,
        String need,
        Long ttlSec,
        Authentication auth,
        ServerWebExchange exchange,
        WebSession session
    ) {
        if (auth instanceof AbstractAuthenticationToken a) {
            Object suid = session.getAttribute("S_UID");
            if (suid instanceof String s && !s.isBlank()) {
                Map<String, Object> map = new HashMap<>();
                Object cur = a.getDetails();
                if (cur instanceof Map<?, ?> m) m.forEach((k,v) -> map.put(String.valueOf(k), v));
                map.put("uid", s);
                a.setDetails(map);
            }
        }
        
        Instant abs = session.<Instant>getAttribute(ATTR_ABS_DEADLINE);
        
        if (abs != null && Instant.now().isAfter(abs)) {
            return session.invalidate()
                .then(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session absolute deadline reached")));
        }

        return maybeTouchSession(exchange, session)
            .then(issueToken(aud, need, ttlSec, auth, exchange, true, session));
    }

    private Mono<Map<String, String>> issueToken(
        String aud,
        String need,
        Long ttlSec,
        Authentication auth,
        ServerWebExchange exchange,
        boolean sessionEndpoint,
        WebSession session
    ) {
        long ttl = resolveTtl(ttlSec, sessionEndpoint);
        Mono<String> tokenMono = sessionEndpoint
            ? cache.issueOrReuseUser(auth, aud, need, ttl)
            : cache.issueOrReuseService(auth,
                defaultServiceName(exchange.getRequest().getHeaders().getFirst(HDR_SERVICE)), aud, need, ttl);

        return tokenMono
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("[InternalTokenController] tokenMono was EMPTY - auth={}, sessionId={}",
                    auth, session != null ? session.getId() : "NO_SESSION");
                return Mono.empty();
            }))
            .doOnNext(token -> {
                // String preview = token.length() > 15
                //     ? token.substring(0, 15) + "..."
                //     : token;
                log.info("[InternalTokenController] issued internal token (len={}): {}",
                    token.length(), token);
            })
            .map(token -> Map.of("token", token));
    }

    private static String defaultServiceName(String svc) {
        return Texts.hasText(svc) ? svc : "unknown";
    }

    private Mono<Void> maybeTouchSession(ServerWebExchange exchange, WebSession session) {
        boolean interactive = isTruthy(exchange.getRequest().getHeaders().getFirst(HDR_TOUCH));
        if (!interactive) {
            String orig = exchange.getRequest().getHeaders().getFirst(HDR_ORIG_METHOD);
            if (isWriteMethod(orig) && csrfOk(exchange)) {
                interactive = true;
            }
        }
        if (!interactive) {
            return Mono.empty();
        }

        Instant now = Instant.now();
        Instant last = session.<Instant>getAttribute(ATTR_TOUCH_AT);
        if (last == null || now.isAfter(last.plus(MIN_TOUCH_INTERVAL))) {
            session.getAttributes().put(ATTR_TOUCH_AT, now);
        }
        return Mono.empty();
    }

    private static boolean isWriteMethod(String m) {
        if (m == null) return false;
        return switch (m.toUpperCase()) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    private boolean csrfOk(ServerWebExchange exchange) {
        String header = nullToEmpty(exchange.getRequest().getHeaders().getFirst(HDR_XSRF)).trim();
        String cookie = nullToEmpty(getCookieValue(exchange, COOKIE_XSRF)).trim();
        if (header.isEmpty() || cookie.isEmpty()) return false;
        return MessageDigest.isEqual(header.getBytes(), cookie.getBytes());
    }

    private static String getCookieValue(ServerWebExchange exchange, String name) {
        var cookie = exchange.getRequest().getCookies().getFirst(name);
        return cookie != null ? cookie.getValue() : null;
    }

    private Mono<Void> requireGatewayHeader(ServerHttpRequest req) {
        String internalHeader = req.getHeaders().getFirst(HDR_INTERNAL);
        String sourceService = req.getHeaders().getFirst(HDR_SERVICE);

        log.info("[requireGatewayHeader] internalHeader={}, sourceService={}",
            internalHeader, sourceService
        );

        if (!"1".equals(internalHeader) || !ALLOWED_SOURCES.contains(sourceService)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN));
        }
        return Mono.empty();
    }

    private Mono<Void> requireAuthenticated(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        }
        return Mono.empty();
    }

    private Mono<Void> requireValidNeedAndAud(String aud, String need) {
        if (need == null || !need.matches("^[a-z][a-z0-9-]*:(read|write)$")) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request parameters"));
        }
        String domainFromAud  = aud.endsWith("-svc") ? aud.substring(0, aud.length() - 4) : aud;
        String domainFromNeed = need.substring(0, need.indexOf(':'));
        if (!domainFromAud.equals(domainFromNeed)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "domain mismatch between aud and need"));
        }
        return Mono.empty();
    }

    // private Mono<Void> requireHasNeededScope(Authentication auth, String need, boolean sessionEndpoint) {
    //     Set<String> scopes = auth.getAuthorities().stream()
    //             .map(GrantedAuthority::getAuthority)
    //             .filter(a -> a != null && a.startsWith("SCOPE_"))
    //             .map(a -> a.substring("SCOPE_".length()))
    //             .collect(Collectors.toUnmodifiableSet());
        
    //     int idx = need.indexOf(':');
    //     String domain = need.substring(0, idx);
    //     String action = need.substring(idx + 1);

    //     if(sessionEndpoint && "read".equals(action)) {
    //         return Mono.empty();
    //     }
    //     if (scopes.contains(need)) return Mono.empty();
    //     if ("read".equals(action) && scopes.contains(domain + ":write")) return Mono.empty();

    //     return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient scope for need=" + need));
    // }

    private long resolveTtl(Long ttlSec, boolean sessionEndpoint) {
        long base = (ttlSec == null || ttlSec <= 0) ? issuer.defaultTtlSec() : ttlSec;
        long cap  = sessionEndpoint ? MAX_TTL_SEC_SESSION : MAX_TTL_SEC_GENERIC;
        return Math.min(base, cap);
    }

    private static boolean isTruthy(String v) {
        if (v == null) return false;
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
