package com.colombus.media.exception;

public class InternalErrorException extends MediaException {
    
    public InternalErrorException() {
        super(MediaErrorCode.MEDIA_INTERNAL_ERROR);
    }

    public InternalErrorException(String customeMessage){
        super(MediaErrorCode.MEDIA_INTERNAL_ERROR, customeMessage);
    }

    public InternalErrorException(Throwable cause) {
        super(
            MediaErrorCode.MEDIA_INTERNAL_ERROR,
            MediaErrorCode.MEDIA_INTERNAL_ERROR.getMessage(),
            null,
            cause
        );
    }
}
