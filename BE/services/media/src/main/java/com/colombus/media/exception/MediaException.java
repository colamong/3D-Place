package com.colombus.media.exception;

import java.util.Map;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class MediaException extends BusinessException {
    
    protected MediaException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    protected MediaException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    protected MediaException(ErrorCode errorCode, String message, Map<String, Object> payload) {
        super(errorCode, message, payload);
    }
    
    protected MediaException(ErrorCode errorCode, String message, Map<String, Object> payload, Throwable cause) {
        super(errorCode, message, payload, cause);
    }
}