package com.colombus.user.exception;

import java.util.Map;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class UserException extends BusinessException {
    
    protected UserException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    protected UserException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    protected UserException(ErrorCode errorCode, String message, Map<String, Object> payload) {
        super(errorCode, message, payload);
    }
    
    protected UserException(ErrorCode errorCode, String message, Map<String, Object> payload, Throwable cause) {
        super(errorCode, message, payload, cause);
    }
}
