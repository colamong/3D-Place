package com.colombus.common.web.servlet.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.CommonErrorCode;
import com.colombus.common.web.core.exception.dto.BusinessErrorResponse;
import com.colombus.common.web.core.exception.dto.FieldError;
import com.colombus.common.web.core.exception.dto.ValidationErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
public abstract class BaseExceptionHandler {

  /** 비즈니스 예외 처리 */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<BusinessErrorResponse> handleBusinessException(
      BusinessException ex, HttpServletRequest request) {
    log.warn("Business exception: {} - {}", ex.getCode(), ex.getMessage(), ex);

    BusinessErrorResponse response =
        BusinessErrorResponse.of(ex, request.getRequestURI(), getTraceId(request));

    return ResponseEntity.status(ex.getHttpStatus()).body(response);
  }

  /** Validation 예외 처리 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ValidationErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    log.warn("Validation failed: {} errors", ex.getBindingResult().getErrorCount());

    List<FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    new FieldError(
                        error.getField(),
                        error.getRejectedValue(),
                        error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value"))
            .toList();

    ValidationErrorResponse response =
        ValidationErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            CommonErrorCode.INVALID_INPUT.getCode(),
            "입력값 검증에 실패했습니다",
            request.getRequestURI(),
            fieldErrors,
            getTraceId(request));

    return ResponseEntity.badRequest().body(response);
  }

  /** BindException 처리 (폼 데이터 바인딩 실패) */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ValidationErrorResponse> handleBindException(
      BindException ex, HttpServletRequest request) {
    log.warn("Bind exception: {} errors", ex.getErrorCount());

    List<FieldError> fieldErrors =
        ex.getFieldErrors().stream()
            .map(
                error ->
                    new FieldError(
                        error.getField(),
                        error.getRejectedValue(),
                        error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value"))
            .toList();

    ValidationErrorResponse response =
        ValidationErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            CommonErrorCode.INVALID_INPUT.getCode(),
            "입력값 검증에 실패했습니다",
            request.getRequestURI(),
            fieldErrors,
            getTraceId(request));

    return ResponseEntity.badRequest().body(response);
  }

  /** 처리되지 않은 예외 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<BusinessErrorResponse> handleUnexpectedException(
      Exception ex, HttpServletRequest request) {
    log.error("Unexpected error occurred", ex);

    BusinessErrorResponse response =
        BusinessErrorResponse.of(
            CommonErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI(), getTraceId(request));

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }

  /** Trace ID 추출 */
  protected String getTraceId(HttpServletRequest request) {
    return request.getHeader("X-Trace-Id");
  }
}
