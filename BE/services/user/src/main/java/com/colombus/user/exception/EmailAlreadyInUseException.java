package com.colombus.user.exception;

public class EmailAlreadyInUseException extends UserException {
    
    public EmailAlreadyInUseException() {
        super(UserErrorCode.EMAIL_ALREADY_IN_USE);
    }

    public EmailAlreadyInUseException(String customeMessage){
        super(UserErrorCode.EMAIL_ALREADY_IN_USE, customeMessage);
    }

    public EmailAlreadyInUseException(Throwable cause) {
        super(
            UserErrorCode.EMAIL_ALREADY_IN_USE,
            UserErrorCode.EMAIL_ALREADY_IN_USE.getMessage(),
            null,
            cause
        );
    }
}
