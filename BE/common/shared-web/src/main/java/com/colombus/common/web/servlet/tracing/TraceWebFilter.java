package com.colombus.common.web.servlet.tracing;

import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.colombus.common.web.core.tracing.TracingProps;

import reactor.core.publisher.Mono;

import java.util.UUID;

public class TraceWebFilter implements WebFilter {

    private final TracingProps props;

    public TraceWebFilter(TracingProps props) { this.props = props; }

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String traceId = null;
        for (String h : props.headerCandidates()) {
            String v = exchange.getRequest().getHeaders().getFirst(h);
            if (v != null && !v.isBlank()) { traceId = v; break; }
        }
        if (traceId == null && props.isGenerateIfMissing()) {
            traceId = UUID.randomUUID().toString();
        }

        if (traceId == null) return chain.filter(exchange); // 그냥 통과

        String finalTraceId = traceId;
        exchange.getResponse().getHeaders().set(props.responseHeader(), finalTraceId);

        // Reactor 컨텍스트 + MDC 브리지
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put("traceId", finalTraceId))
                .doFirst(() -> MDC.put("traceId", finalTraceId))
                .doFinally(sig -> MDC.remove("traceId"));
    }
}
