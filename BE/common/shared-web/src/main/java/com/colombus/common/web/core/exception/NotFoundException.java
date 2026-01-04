package com.colombus.common.web.core.exception;

import java.util.Map;

public class NotFoundException extends BusinessException {
    
    public NotFoundException() {
        super(CommonErrorCode.RESOURCE_NOT_FOUND);
    }
    
    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public NotFoundException(String message) {
        super(CommonErrorCode.RESOURCE_NOT_FOUND, message);
    }
    
    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public NotFoundException(ErrorCode errorCode, String message, Map<String, Object> payload) {
        super(errorCode, message, payload);
    }
}