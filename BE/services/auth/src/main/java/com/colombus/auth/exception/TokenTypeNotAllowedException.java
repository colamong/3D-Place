package com.colombus.auth.exception;

public class TokenTypeNotAllowedException extends AuthException {
    
    public TokenTypeNotAllowedException() {
        super(AuthErrorCode.TOKEN_TYPE_NOT_ALLOWED);
    }
    
    public TokenTypeNotAllowedException(String customMessage) {
        super(AuthErrorCode.TOKEN_TYPE_NOT_ALLOWED, customMessage);
    }
}
