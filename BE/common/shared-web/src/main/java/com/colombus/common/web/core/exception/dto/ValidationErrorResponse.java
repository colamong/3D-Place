package com.colombus.common.web.core.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    List<FieldError> errors,
    String traceId
) {
    // 유효성 검사 오류로부터 ValidationErrorResponse를 생성합니다.
    public static ValidationErrorResponse of(
        int status,
        String code,
        String message,
        String path,
        List<FieldError> errors,
        String traceId
    ) {
        return new ValidationErrorResponse(
            Instant.now(),
            status,
            code,
            message,
            path,
            errors,
            traceId
        );
    }
}
