package com.colombus.auth.exception;

public class EntryPointMismatchException extends AuthException {
    
    public EntryPointMismatchException(){
        super(AuthErrorCode.ENTRYPOINT_MISMATCH);
    }

    public EntryPointMismatchException(String customMessage){
        super(AuthErrorCode.ENTRYPOINT_MISMATCH, customMessage);
    }
}
