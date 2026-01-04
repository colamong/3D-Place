package com.colombus.auth.security.support;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.server.ServerWebExchange;

import com.colombus.auth.exception.AuthErrorCode;

public class SecurityErrors {

    private SecurityErrors() {}

    /**
     * 401을 발생시킬 때 미리 AuthErrorCode를 Exchange attribute에 심어 두면
     * reactive entry point가 JSON 응답을 정확히 내려줄 수 있다.
     */
    public static void unauth(ServerWebExchange exchange, AuthErrorCode code) {
        exchange.getAttributes().put(AuthErrorAttributes.ERROR_CODE_ATTR, code);
        throw new InsufficientAuthenticationException(code.getMessage());
    }

    /** 동일하게 403 상황에서도 attribute를 심어 두면 AccessDeniedHandler에서 참조 가능하다. */
    public static void forbid(ServerWebExchange exchange, AuthErrorCode code) {
        exchange.getAttributes().put(AuthErrorAttributes.ERROR_CODE_ATTR, code);
        throw new AccessDeniedException(code.getMessage());
    }   
}
