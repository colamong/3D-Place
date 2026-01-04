package com.colombus.auth.exception;

public class CsrfTokenRequiredException extends AuthException {

    public CsrfTokenRequiredException() {
        super(AuthErrorCode.CSRF_TOKEN_REQUIRED);
    }
    
    public CsrfTokenRequiredException(String customMessage) {
        super(AuthErrorCode.CSRF_TOKEN_REQUIRED, customMessage);
    }
}
