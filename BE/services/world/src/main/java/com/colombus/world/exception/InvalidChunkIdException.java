package com.colombus.world.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class InvalidChunkIdException extends BusinessException {

    public InvalidChunkIdException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InvalidChunkIdException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public InvalidChunkIdException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, null, cause);
    }
}
