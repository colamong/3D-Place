package com.colombus.auth.exception;

import java.util.Map;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class AuthException extends BusinessException {
    
    protected AuthException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    protected AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    protected AuthException(ErrorCode errorCode, String message, Map<String, Object> payload) {
        super(errorCode, message, payload);
    }
    
    protected AuthException(ErrorCode errorCode, String message, Map<String, Object> payload, Throwable cause) {
        super(errorCode, message, payload, cause);
    }
}
