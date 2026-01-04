package com.colombus.paint.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

// 429
public class PaintRateLimitException extends BusinessException {
	public PaintRateLimitException(ErrorCode errorCode) {
		super(errorCode);
	}
}
