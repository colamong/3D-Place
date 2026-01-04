package com.colombus.common.web.core.exception.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 다른 내부 서비스로부터의 에러 응답을 역직렬화하기 위한 DTO.
 * BusinessErrorResponse와 ValidationErrorResponse의 모든 가능한 필드를 포함합니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    Map<String, Object> payload,
    List<FieldError> errors,
    String traceId
) {}
