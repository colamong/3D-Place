package com.colombus.auth.exception;

public class SessionExpiredException extends AuthException {

    public SessionExpiredException() {
        super(AuthErrorCode.SESSION_EXPIRED);
    }
    
    public SessionExpiredException(String customMessage) {
        super(AuthErrorCode.SESSION_EXPIRED, customMessage);
    }
}
