package com.colombus.common.web.core.exception;

public interface ErrorCode {
    int getHttpStatus();
    String getCode();
    String getMessage();
}