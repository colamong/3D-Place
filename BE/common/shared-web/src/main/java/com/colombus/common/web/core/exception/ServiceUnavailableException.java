package com.colombus.common.web.core.exception;

public class ServiceUnavailableException extends BusinessException {
    
    public ServiceUnavailableException() {
        super(CommonErrorCode.SERVICE_UNAVAILABLE);
    }
    
    public ServiceUnavailableException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public ServiceUnavailableException(String message) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, message);
    }

    public ServiceUnavailableException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public ServiceUnavailableException(String message, Throwable cause) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, message, null, cause);
    }
    
    public ServiceUnavailableException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, null, cause);
    }
}