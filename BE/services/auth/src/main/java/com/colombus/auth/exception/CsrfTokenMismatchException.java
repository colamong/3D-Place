package com.colombus.auth.exception;

public class CsrfTokenMismatchException extends AuthException {

    public CsrfTokenMismatchException() {
        super(AuthErrorCode.CSRF_TOKEN_MISMATCH);
    }
    
    public CsrfTokenMismatchException(String customMessage) {
        super(AuthErrorCode.CSRF_TOKEN_MISMATCH, customMessage);
    }
}
