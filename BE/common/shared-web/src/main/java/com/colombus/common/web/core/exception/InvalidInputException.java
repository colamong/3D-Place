package com.colombus.common.web.core.exception;

public class InvalidInputException extends BusinessException {
    
    public InvalidInputException() {
        super(CommonErrorCode.INVALID_INPUT);
    }
    
    public InvalidInputException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public InvalidInputException(String message) {
        super(CommonErrorCode.INVALID_INPUT, message);
    }

    public InvalidInputException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public InvalidInputException(String message, Throwable cause) {
        super(CommonErrorCode.INVALID_INPUT, message, null, cause);
    }
    
    public InvalidInputException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, null, cause);
    }
}