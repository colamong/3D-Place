package com.colombus.common.web.core.tracing;

import org.slf4j.MDC;

import io.micrometer.tracing.Tracer;

public class TraceIdProvider {
    
    private final Tracer tracer;

    public TraceIdProvider(Tracer tracer) {
        this.tracer = tracer;
    }

    public String currentTraceId() {
        if (tracer != null && tracer.currentSpan() != null) {
        return tracer.currentSpan().context().traceId();
        }
        return MDC.get("traceId");
    }
}