package com.colombus.common.web.core.exception;

import java.util.Map;

public class TooManyRequestsException extends BusinessException {
    
    public TooManyRequestsException() {
        super(CommonErrorCode.TOO_MANY_REQUESTS);
    }
    
    public TooManyRequestsException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public TooManyRequestsException(String message) {
        super(CommonErrorCode.TOO_MANY_REQUESTS, message);
    }
    
    public TooManyRequestsException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public TooManyRequestsException(ErrorCode errorCode, String message, Map<String, Object> payload) {
        super(errorCode, message, payload);
    }
}