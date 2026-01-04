package com.colombus.common.web.core.exception;

import java.util.Map;

public class ConflictException extends BusinessException {
    
    public ConflictException() {
        super(CommonErrorCode.RESOURCE_CONFLICT);
    }
    
    public ConflictException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public ConflictException(String message) {
        super(CommonErrorCode.RESOURCE_CONFLICT, message);
    }
    
    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public ConflictException(ErrorCode errorCode, String message, Map<String, Object> payload) {
        super(errorCode, message, payload);
    }
}