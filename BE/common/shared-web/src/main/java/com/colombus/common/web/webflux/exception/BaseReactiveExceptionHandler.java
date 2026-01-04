package com.colombus.common.web.webflux.exception;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.CommonErrorCode;
import com.colombus.common.web.core.exception.dto.BusinessErrorResponse;
import com.colombus.common.web.core.exception.dto.FieldError;
import com.colombus.common.web.core.exception.dto.ValidationErrorResponse;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public abstract class BaseReactiveExceptionHandler {

  /** 비즈니스 예외 처리 */
  @ExceptionHandler(BusinessException.class)
  public Mono<ResponseEntity<BusinessErrorResponse>> handleBusinessException(
      BusinessException ex, ServerWebExchange exchange) {
    log.warn("Business exception: {} - {}", ex.getCode(), ex.getMessage(), ex);

    BusinessErrorResponse response =
        BusinessErrorResponse.of(
            ex, exchange.getRequest().getURI().getPath(), getTraceId(exchange));

    return Mono.just(ResponseEntity.status(ex.getHttpStatus()).body(response));
  }

  /** Validation 예외 처리 (WebFlux 바인딩 예외) */
  @ExceptionHandler(WebExchangeBindException.class)
  public Mono<ResponseEntity<ValidationErrorResponse>> handleWebExchangeBindException(
      WebExchangeBindException ex, ServerWebExchange exchange) {
    log.warn("WebFlux bind/validation failed: {} errors", ex.getErrorCount());

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
            exchange.getRequest().getURI().getPath(),
            fieldErrors,
            getTraceId(exchange));

    return Mono.just(ResponseEntity.badRequest().body(response));
  }

  /**
   * Validation 예외 처리 (메서드 인자 @Valid 실패) - 일부 경우(WebFlux에서도) MethodArgumentNotValidException이 발생할 수 있어 함께
   * 처리
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public Mono<ResponseEntity<ValidationErrorResponse>> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException ex, ServerWebExchange exchange) {
    log.warn("Method argument validation failed: {} errors", ex.getBindingResult().getErrorCount());

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
            exchange.getRequest().getURI().getPath(),
            fieldErrors,
            getTraceId(exchange));

    return Mono.just(ResponseEntity.badRequest().body(response));
  }

  /** 처리되지 않은 예외 */
  @ExceptionHandler(Exception.class)
  public Mono<ResponseEntity<BusinessErrorResponse>> handleUnexpectedException(
      Exception ex, ServerWebExchange exchange) {
    log.error("Unexpected error occurred", ex);

    BusinessErrorResponse response =
        BusinessErrorResponse.of(
            CommonErrorCode.INTERNAL_SERVER_ERROR,
            exchange.getRequest().getURI().getPath(),
            getTraceId(exchange));

    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response));
  }

  /** Trace ID 추출 */
  protected String getTraceId(ServerWebExchange exchange) {
    var headers = exchange.getRequest().getHeaders();
    return headers.getFirst("X-Trace-Id");
  }
}