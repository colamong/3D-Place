package com.colombus.paint.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class RedisOperationException extends BusinessException {
	public RedisOperationException(ErrorCode errorCode) {
		super(errorCode);
	}
}
