package com.colombus.common.web.core.exception;

import java.util.Map;

public abstract class BusinessException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final Map<String, Object> payload;
    
    protected BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null, null);
    }
    
    protected BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }
    
    protected BusinessException(ErrorCode errorCode, String message, Map<String, Object> payload) {
        this(errorCode, message, payload, null);
    }
    
    protected BusinessException(ErrorCode errorCode, String message, Map<String, Object> payload, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.payload = payload;
    }
    
    public int getHttpStatus() {
        return errorCode.getHttpStatus();
    }
    
    public String getCode() {
        return errorCode.getCode();
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    public Map<String, Object> getPayload() {
        return payload;
    }
}