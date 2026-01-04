package com.colombus.paint.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

// 403
public class PaintForbiddenException extends BusinessException {
	public PaintForbiddenException(ErrorCode errorCode) {
		super(errorCode);
	}
}
