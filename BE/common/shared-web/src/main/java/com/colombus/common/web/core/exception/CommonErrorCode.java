package com.colombus.common.web.core.exception;

public enum CommonErrorCode implements ErrorCode {
    // 4xx Client Errors
    BAD_REQUEST(400, "COMMON-400-001", "잘못된 요청입니다"),
    INVALID_INPUT(400, "COMMON-400-002", "입력값이 유효하지 않습니다"),
    UNAUTHORIZED(401, "COMMON-401-001", "인증이 필요합니다"),
    ACCESS_DENIED(403, "COMMON-403-001", "접근 권한이 없습니다"),
    RESOURCE_NOT_FOUND(404, "COMMON-404-001", "리소스를 찾을 수 없습니다"),
    RESOURCE_CONFLICT(409, "COMMON-409-001", "리소스가 이미 존재합니다"),
    TOO_MANY_REQUESTS(429, "COMMON-429-001", "요청이 너무 많습니다"),
    
    // 5xx Server Errors
    INTERNAL_SERVER_ERROR(500, "COMMON-500-001", "서버 내부 오류가 발생했습니다"),
    SERVICE_UNAVAILABLE(503, "COMMON-503-001", "서비스를 일시적으로 사용할 수 없습니다"),
    EXTERNAL_API_ERROR(503, "COMMON-503-002", "외부 API 호출에 실패했습니다");
    
    private final int httpStatus;
    private final String code;
    private final String message;
    
    CommonErrorCode(int httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
    
    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
    
    @Override
    public String getCode() {
        return code;
    }
    
    @Override
    public String getMessage() {
        return message;
    }
}
