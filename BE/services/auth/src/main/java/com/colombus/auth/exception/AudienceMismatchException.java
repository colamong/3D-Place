package com.colombus.auth.exception;

public class AudienceMismatchException extends AuthException {
    
    public AudienceMismatchException() {
        super(AuthErrorCode.AUDIENCE_MISMATCH);
    }
    
    public AudienceMismatchException(String customMessage) {
        super(AuthErrorCode.AUDIENCE_MISMATCH, customMessage);
    }
}
