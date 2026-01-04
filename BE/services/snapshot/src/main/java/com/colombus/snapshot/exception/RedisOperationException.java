package com.colombus.snapshot.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class RedisOperationException extends BusinessException {

    public RedisOperationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RedisOperationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public RedisOperationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, null, cause);
    }
}
