package com.colombus.paint.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

// 400
public class PaintInputException extends BusinessException {
	public PaintInputException(ErrorCode errorCode) {
		super(errorCode);
	}
}
