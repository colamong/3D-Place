package com.colombus.world.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class S3ObjectNotFoundException extends BusinessException {

    public S3ObjectNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public S3ObjectNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public S3ObjectNotFoundException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, null, cause);
    }
}