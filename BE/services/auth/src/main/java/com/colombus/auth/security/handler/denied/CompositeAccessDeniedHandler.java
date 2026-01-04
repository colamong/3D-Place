package com.colombus.auth.security.handler.denied;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.csrf.CsrfException;
import org.springframework.web.server.ServerWebExchange;

import com.colombus.auth.exception.AuthErrorCode;
import com.colombus.auth.security.support.AuthErrorAttributes;
import com.colombus.auth.web.support.JsonErrorWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

public class CompositeAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ObjectMapper om;

    public CompositeAccessDeniedHandler(ObjectMapper om) {
        this.om = om;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {

        Throwable t = ex;
        // CSRF 예외는 AuthErrorCode로 변환해 프론트가 세션 재구성 루틴을 돌릴 수 있게 한다.
        while (t != null) {
            if (t instanceof MissingCsrfTokenException) {
                return JsonErrorWriter.write(exchange.getResponse(), AuthErrorCode.CSRF_TOKEN_REQUIRED, om);
            }
            if (t instanceof InvalidCsrfTokenException || t instanceof CsrfException) {
                return JsonErrorWriter.write(exchange.getResponse(), AuthErrorCode.CSRF_TOKEN_MISMATCH, om);
            }
            t = t.getCause();
        }

        Object attr = exchange.getAttribute(AuthErrorAttributes.ERROR_CODE_ATTR);
        if (attr instanceof AuthErrorCode code) {
            return JsonErrorWriter.write(exchange.getResponse(), code, om);
        }

        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }
}
