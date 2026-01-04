package com.colombus.auth.exception;

public class MaxSessionExceededException extends AuthException {
    
    public MaxSessionExceededException() {
        super(AuthErrorCode.MAX_SESSIONS_EXCEEDED);
    }
    
    public MaxSessionExceededException(String customMessage) {
        super(AuthErrorCode.MAX_SESSIONS_EXCEEDED, customMessage);
    }
}
