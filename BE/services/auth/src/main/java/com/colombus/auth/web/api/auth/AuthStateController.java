package com.colombus.auth.web.api.auth;

import com.colombus.auth.application.AuthService;
import com.colombus.auth.exception.MaxSessionExceededException;
import com.colombus.auth.exception.SessionExpiredException;
import com.colombus.auth.security.SecurityConfig;
import com.colombus.auth.web.support.CurrentUserResolver;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AuthStateController {

    private final AuthService authService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/auth/state")
    // WebFlux 컨트롤러에서도 WebSession을 직접 다루므로 Mono<Map> 형태로 응답한다.
    public Mono<Map<String, Object>> state(
        @AuthenticationPrincipal OidcUser user,
        Authentication auth,
        WebSession session
    ) {
        var out = new HashMap<String, Object>();
        boolean ok = auth != null && auth.isAuthenticated() && user != null;

        out.put("authenticated", ok);
        if (ok) {
            out.put("sub", user.getSubject());
            out.put("email", user.getEmail());
            out.put("email_verified", user.getEmailVerified());
            out.put("next_action", user.getEmailVerified() ? "NONE" : "VERIFY_EMAIL");
        } else {
            Object status = session != null ? session.getAttribute(SecurityConfig.S_LOGIN_STATUS) : null;
            Object reason = session != null ? session.getAttribute(SecurityConfig.S_LOGIN_REASON) : null;
            out.put("next_action", status != null ? status : "LOGIN");
            if (reason != null) out.put("reason", reason);
            if (session != null) {
                session.getAttributes().remove(SecurityConfig.S_LOGIN_STATUS);
                session.getAttributes().remove(SecurityConfig.S_LOGIN_REASON);
            }
        }

        return Mono.just(out);
    }

    @PostMapping("/auth/logout-all")
    public Mono<ResponseEntity<Void>> logoutAll(
        Authentication auth,
        WebSession session
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return Mono.just(ResponseEntity.status(401).build());
        }

        final UUID userId;
        try {
            userId = currentUserResolver.platformUserId(auth);
        } catch (IllegalStateException e) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        
        return authService.logoutAll(userId)
            .then(session.invalidate())
            .then(Mono.just(ResponseEntity.ok().build()));
    }

    @GetMapping("/session-expired")
    public void sessionExpired() {
        throw new SessionExpiredException();
    }

    @GetMapping("/session-invalid")
    public void maxSessionExceeded() {
        throw new MaxSessionExceededException();
    }
    
}
