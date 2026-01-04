package com.colombus.media.exception;

public class SubejectNotFoundException extends MediaException {
    
    public SubejectNotFoundException() {
        super(MediaErrorCode.SUBJECT_NOT_ALIVE);
    }

    public SubejectNotFoundException(String customeMessage){
        super(MediaErrorCode.SUBJECT_NOT_ALIVE, customeMessage);
    }

    public SubejectNotFoundException(Throwable cause) {
        super(
            MediaErrorCode.SUBJECT_NOT_ALIVE,
            MediaErrorCode.SUBJECT_NOT_ALIVE.getMessage(),
            null,
            cause
        );
    }
}
