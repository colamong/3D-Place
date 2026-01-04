package com.colombus.auth.exception;

public class EmailVerificationRequiredException extends AuthException {
    
    public EmailVerificationRequiredException() {
        super(AuthErrorCode.EMAIL_VERIFICATION_REQUIRED);
    }
    
    public EmailVerificationRequiredException(String customMessage) {
        super(AuthErrorCode.EMAIL_VERIFICATION_REQUIRED, customMessage);
    }
}
