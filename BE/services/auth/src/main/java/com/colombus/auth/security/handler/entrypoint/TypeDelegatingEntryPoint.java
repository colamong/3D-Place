package com.colombus.auth.security.handler.entrypoint;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import com.colombus.auth.exception.AuthErrorCode;
import com.colombus.auth.security.support.AuthErrorAttributes;
import com.colombus.auth.web.support.JsonErrorWriter;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TypeDelegatingEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper om;
    private final Map<Class<? extends AuthenticationException>, AuthErrorCode> map = new LinkedHashMap<>();
    private final AuthErrorCode defaultCode;

    public TypeDelegatingEntryPoint(ObjectMapper om, AuthErrorCode defaultCode) {
        this.om = om;
        this.defaultCode = defaultCode;
    }

    public TypeDelegatingEntryPoint register(Class<? extends AuthenticationException> type, AuthErrorCode code) {
        map.put(type, code);
        return this;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {

        // SecurityErrors가 심어둔 에러 코드가 있으면 우선적으로 JSON 응답을 작성한다.
        Object attr = exchange.getAttribute(AuthErrorAttributes.ERROR_CODE_ATTR);
        if (attr instanceof AuthErrorCode code) {
            return JsonErrorWriter.write(exchange.getResponse(), code, om);
        }
        
        for (var e : map.entrySet()) {
            if (e.getKey().isInstance(ex)) {
                return JsonErrorWriter.write(exchange.getResponse(), e.getValue(), om);
            }
        }
        return JsonErrorWriter.write(exchange.getResponse(), defaultCode, om);
    }
    
}
