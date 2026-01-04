package com.colombus.auth.web.support;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {
    public static final String UID_CLAIM = "uid";
    public static final String UID_ATTR  = "S_UID";

    /** 우리 플랫폼의 사용자 UUID */
    public UUID platformUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Unauthenticated");
        }
        Object p = auth.getPrincipal();

        //  OIDC 세션 로그인
        if (p instanceof OidcUser u) {
            // 커스텀 클레임 uid 시도
            String uid = u.getClaimAsString(UID_CLAIM);
            if (uid != null && !uid.isBlank()) return UUID.fromString(uid);

            // 없으면 세션 attributes에서 시도
            Map<String, Object> attrs = u.getAttributes();
            Object sessUid = attrs.get(UID_ATTR);
            if (sessUid instanceof String s && !s.isBlank()) return UUID.fromString(s);
        }

        // 리소스 서버(JWT)라면 JWT 클레임에서 uid 사용
        if (p instanceof Jwt jwt) {
            String uid = jwt.getClaimAsString(UID_CLAIM);
            if (uid != null && !uid.isBlank()) return UUID.fromString(uid);
        }

        Object d = auth.getDetails();
        if (d instanceof Map<?,?> m) {
            Object v = m.get("uid");
            if (v instanceof String s && !s.isBlank()) {
                return UUID.fromString(s);
            }
        }

        throw new IllegalStateException("Current platform userId not found (uid claim/attr missing)");
    }

    /** Auth0의 기본 계정 user_id, 없으면 sub 사용 */
    public String primaryAuth0UserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Unauthenticated");
        }
        Object p = auth.getPrincipal();

        if (p instanceof OidcUser u) {
            String sub = u.getSubject();
            if (sub != null && !sub.isBlank()) return sub;
            return u.getClaimAsString("user_id");
        }

        if (p instanceof Jwt jwt) {
            String sub = jwt.getClaimAsString("sub");
            if (sub != null && !sub.isBlank()) return sub;
            return jwt.getClaimAsString("user_id");
        }

        throw new IllegalStateException("Auth0 primary user_id not found");
    }
}
