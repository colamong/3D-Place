package com.colombus.snapshot.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class LockAcquisitionException extends BusinessException {

    public LockAcquisitionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public LockAcquisitionException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public LockAcquisitionException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, null, cause);
    }
}
