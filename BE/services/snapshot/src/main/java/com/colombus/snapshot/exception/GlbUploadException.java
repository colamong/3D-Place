package com.colombus.snapshot.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class GlbUploadException extends StorageException {

    public GlbUploadException(ErrorCode errorCode) {
        super(errorCode);
    }

    public GlbUploadException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public GlbUploadException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
