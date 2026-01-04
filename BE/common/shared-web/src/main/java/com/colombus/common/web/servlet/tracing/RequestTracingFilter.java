package com.colombus.common.web.servlet.tracing;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import com.colombus.common.web.core.tracing.TracingProps;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RequestTracingFilter extends OncePerRequestFilter {

    private final TracingProps props;

    public RequestTracingFilter(TracingProps props) { this.props = props; }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest req,
        @NonNull HttpServletResponse res,
        @NonNull FilterChain chain
    ) throws ServletException, IOException {

        String traceId = null;
        for (String h : props.headerCandidates()) {
            String v = req.getHeader(h);
            if (v != null && !v.isBlank()) { traceId = v; break; }
        }

        if (traceId == null && props.isGenerateIfMissing()) {
            traceId = UUID.randomUUID().toString();
        }

        if (traceId != null) {
            MDC.put("traceId", traceId);
            res.setHeader(props.responseHeader(), traceId);
        }
        
        try { chain.doFilter(req, res); }
        finally { MDC.remove("traceId"); }
    }
}