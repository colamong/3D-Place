package com.colombus.auth.exception;

public class InsufficientScopeException extends AuthException {
    
    public InsufficientScopeException (){
        super(AuthErrorCode.INSUFFICIENT_SCOPE);
    }

    public InsufficientScopeException(String customMessage){
        super(AuthErrorCode.INSUFFICIENT_SCOPE, customMessage);
    }
}
