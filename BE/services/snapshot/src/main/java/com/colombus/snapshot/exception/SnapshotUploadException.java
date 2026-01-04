package com.colombus.snapshot.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class SnapshotUploadException extends StorageException {

    public SnapshotUploadException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SnapshotUploadException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public SnapshotUploadException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
