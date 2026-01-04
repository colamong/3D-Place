package com.colombus.snapshot.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class DataCorruptionException extends BusinessException {

    public DataCorruptionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DataCorruptionException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DataCorruptionException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, null, cause);
    }
}