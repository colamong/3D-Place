package com.colombus.auth.web.support;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;

import com.colombus.common.web.core.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonErrorWriter {

    private JsonErrorWriter() {}

    // WebFlux는 ServletResponse가 없으므로 bufferFactory로 직접 JSON 바이트를 써야 한다.
    public static Mono<Void> write(ServerHttpResponse resp, ErrorCode ec, ObjectMapper om) {
        resp.setStatusCode(HttpStatusCode.valueOf(ec.getHttpStatus()));
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = om.writeValueAsBytes(Map.of(
                "httpStatus", ec.getHttpStatus(),
                "code", ec.getCode(),
                "message", ec.getMessage()
            ));
            return resp.writeWith(Mono.just(resp.bufferFactory().wrap(body)));
        } catch (Exception e) {
            byte[] fallback = ("{\"code\":\"" + ec.getCode() + "\"}")
                .getBytes(StandardCharsets.UTF_8);
            return resp.writeWith(Mono.just(resp.bufferFactory().wrap(fallback)));
        }
    }
}
