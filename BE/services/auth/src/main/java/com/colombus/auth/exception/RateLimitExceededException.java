package com.colombus.auth.exception;

public class RateLimitExceededException extends AuthException {
    
    public RateLimitExceededException() {
        super(AuthErrorCode.RATE_LIMIT_EXCEEDED);
    }
    
    public RateLimitExceededException(String customMessage) {
        super(AuthErrorCode.RATE_LIMIT_EXCEEDED, customMessage);
    }
}
