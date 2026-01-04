package com.colombus.snapshot.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public abstract class StorageException extends BusinessException {

    public StorageException(ErrorCode errorCode) {
        super(errorCode);
    }

    public StorageException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public StorageException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, null, cause);
    }
}
