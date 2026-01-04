package com.colombus.bff.util;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.annotation.Nullable;

public final class ActorIdResolver {

    private ActorIdResolver() {}

    /**
     * JWT에서 uid 클레임을 읽어 actor/user UUID로 파싱.
     * 존재하지 않거나 잘못된 형식이면 null 반환.
     */
    @Nullable
    public static UUID resolveUserId(@Nullable Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        String raw = jwt.getClaimAsString("uid");
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            // log.warn("Invalid uid in JWT: {}", raw, e);
            return null;
        }
    }

    /**
     * uid가 반드시 있어야 하는 경우에 사용하는 헬퍼.
     * 없거나 잘못된 경우 예외 발생.
     */
    public static UUID requireUserId(Jwt jwt) {
        UUID id = resolveUserId(jwt);
        if (id == null) {
            throw new IllegalStateException("JWT does not contain a valid uid claim");
        }
        return id;
    }
}
