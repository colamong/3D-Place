package com.colombus.common.web.core.exception;

public class ForbiddenException extends BusinessException {
    
    public ForbiddenException() {
        super(CommonErrorCode.ACCESS_DENIED);
    }
    
    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public ForbiddenException(String message) {
        super(CommonErrorCode.ACCESS_DENIED, message);
    }
    
    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}