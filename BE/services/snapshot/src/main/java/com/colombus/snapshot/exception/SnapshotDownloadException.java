package com.colombus.snapshot.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class SnapshotDownloadException extends StorageException {

    public SnapshotDownloadException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SnapshotDownloadException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public SnapshotDownloadException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
