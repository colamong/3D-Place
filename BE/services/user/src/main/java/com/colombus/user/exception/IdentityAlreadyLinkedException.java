package com.colombus.user.exception;

public class IdentityAlreadyLinkedException extends UserException {
    
    public IdentityAlreadyLinkedException() {
        super(UserErrorCode.IDENTITY_ALREADY_LINKED);
    }

    public IdentityAlreadyLinkedException(String customeMessage){
        super(UserErrorCode.IDENTITY_ALREADY_LINKED, customeMessage);
    }

    public IdentityAlreadyLinkedException(Throwable cause) {
        super(
            UserErrorCode.IDENTITY_ALREADY_LINKED,
            UserErrorCode.IDENTITY_ALREADY_LINKED.getMessage(),
            null,
            cause
        );
    }
}
