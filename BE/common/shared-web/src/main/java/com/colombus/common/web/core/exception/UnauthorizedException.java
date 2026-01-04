package com.colombus.common.web.core.exception;

public class UnauthorizedException extends BusinessException {
    
    public UnauthorizedException() {
        super(CommonErrorCode.UNAUTHORIZED);
    }
    
    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public UnauthorizedException(String message) {
        super(CommonErrorCode.UNAUTHORIZED, message);
    }
    
    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}