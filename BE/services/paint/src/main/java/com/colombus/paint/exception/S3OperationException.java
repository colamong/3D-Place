package com.colombus.paint.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class S3OperationException extends BusinessException {
	public S3OperationException(ErrorCode errorCode) {
		super(errorCode);
	}
}
