package com.colombus.common.web.core.exception.dto;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BusinessErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    Map<String, Object> payload,
    String traceId
) {
    // BusinessException으로부터 BusinessErrorResponse를 생성합니다.
    public static BusinessErrorResponse of(
        BusinessException exception,
        String path,
        String traceId
    ) {
        return new BusinessErrorResponse(
            Instant.now(),
            exception.getHttpStatus(),
            exception.getCode(),
            exception.getMessage(),
            path,
            exception.getPayload(),
            traceId
        );
    }

    // ErrorCode로부터 BusinessErrorResponse를 생성합니다.
    public static BusinessErrorResponse of(
        ErrorCode errorCode,
        String path,
        String traceId
    ) {
        return new BusinessErrorResponse(
            Instant.now(),
            errorCode.getHttpStatus(),
            errorCode.getCode(),
            errorCode.getMessage(),
            path,
            null,
            traceId
        );
    }
}
