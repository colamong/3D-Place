package com.colombus.snapshot.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class DatabaseOperationException extends BusinessException {

    public DatabaseOperationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DatabaseOperationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DatabaseOperationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, null, cause);
    }
}