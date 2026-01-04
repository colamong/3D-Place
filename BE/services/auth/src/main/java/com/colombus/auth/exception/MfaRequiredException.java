package com.colombus.auth.exception;

public class MfaRequiredException extends AuthException {
    
    public MfaRequiredException() {
        super(AuthErrorCode.MFA_REQUIRED);
    }
    
    public MfaRequiredException(String customMessage) {
        super(AuthErrorCode.MFA_REQUIRED, customMessage);
    }
}
