package com.colombus.paint.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ErrorCode;

public class PaintNotFoundException extends BusinessException {
	public PaintNotFoundException(ErrorCode errorCode) {
		super(errorCode);
	}
}
