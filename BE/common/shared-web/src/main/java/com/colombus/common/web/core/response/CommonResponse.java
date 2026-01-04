package com.colombus.common.web.core.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommonResponse<T>(
    LocalDateTime timestamp,
    int status,
    String code,
    String message,
    String path,
    String traceId,
    T data
) {
    
    /**
     * 기본 성공 (200 OK) 응답을 생성하는 팩토리 메서드
     */
    public static <T> CommonResponse<T> success(String path, String traceId, T data) {
        return new CommonResponse<>(
            LocalDateTime.now(),
            200,
            "SUCCESS",
            "요청이 성공적으로 처리되었습니다",
            path,
            traceId,
            data
        );
    }
    
    /**
     * 리소스 생성 (201 Created) 응답을 생성하는 팩토리 메서드
     */
    public static <T> CommonResponse<T> created(String path, String traceId, T data) {
        return new CommonResponse<>(
            LocalDateTime.now(),
            201,
            "CREATED",
            "리소스를 성공적으로 생성했습니다",
            path,
            traceId,
            data
        );
    }
}