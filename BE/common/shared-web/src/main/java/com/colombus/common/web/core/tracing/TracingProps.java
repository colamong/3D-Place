package com.colombus.common.web.core.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "colombus.trace")
public record TracingProps(
    String responseHeader,
    String[] headerCandidates,
    Boolean generateIfMissing
) {
    public TracingProps {
        if (responseHeader == null || responseHeader.isBlank()) {
            responseHeader = "X-Trace-Id";
        }

        if (headerCandidates == null || headerCandidates.length == 0) {
            headerCandidates = new String[]{
                "traceparent",
                "x-b3-traceid",
                "x-trace-id",
                "x-request-id",
                "x-correlation-id"
            };
        }

        // generateIfMissing 기본값: null이면 true
        if (generateIfMissing == null) {
            generateIfMissing = Boolean.TRUE;
        }
    }

    /**
     * 외부에서 쓸 때는 boolean으로 보고 싶으면 이 메서드 쓰면 됨.
     * (필요 없으면 기존 generateIfMissing() 그대로 써도 됨)
     */
    public boolean isGenerateIfMissing() {
        return Boolean.TRUE.equals(generateIfMissing);
    }
}