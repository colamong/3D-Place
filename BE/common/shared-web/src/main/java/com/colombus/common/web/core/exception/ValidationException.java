package com.colombus.common.web.core.exception;

import java.util.Map;

public class ValidationException extends BusinessException {
    
    public ValidationException() {
        super(CommonErrorCode.INVALID_INPUT);
    }
    
    public ValidationException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public ValidationException(String message) {
        super(CommonErrorCode.INVALID_INPUT, message);
    }
    
    public ValidationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public ValidationException(ErrorCode errorCode, String message, Map<String, Object> payload) {
        super(errorCode, message, payload);
    }
}