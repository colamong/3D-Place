package com.colombus.common.web.webflux.exception;

import com.colombus.common.web.core.exception.CommonErrorCode;
import com.colombus.common.web.core.exception.dto.BusinessErrorResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnWebApplication(type = Type.REACTIVE)
public class GlobalReactiveExceptionHandler extends BaseReactiveExceptionHandler {

  /** trace id 폴백 확장 (B3 등) */
  @Override
  protected String getTraceId(ServerWebExchange exg) {
    var h = exg.getRequest().getHeaders();
    String id = h.getFirst("X-Trace-Id");
    if (id == null) id = h.getFirst("X-B3-TraceId");
    return id;
  }

  /** 파라미터/바디 변환 오류(쿼리 파싱, 타입 변환 등) → 400 */
  @ExceptionHandler(ServerWebInputException.class)
  public Mono<ResponseEntity<BusinessErrorResponse>> handleServerWebInput(
      ServerWebInputException ex, ServerWebExchange exg) {
    log.warn("ServerWebInputException: {}", ex.getReason());
    var body =
        new BusinessErrorResponse(
            java.time.Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            CommonErrorCode.INVALID_INPUT.getCode(),
            "입력값 검증에 실패했습니다",
            exg.getRequest().getURI().getPath(),
            null,
            getTraceId(exg));
    return Mono.just(ResponseEntity.badRequest().body(body));
  }

  /** 406 Not Acceptable (컨텐츠 네고 실패) */
  @ExceptionHandler(NotAcceptableStatusException.class)
  public Mono<ResponseEntity<BusinessErrorResponse>> handleNotAcceptable(
      NotAcceptableStatusException ex, ServerWebExchange exg) {
    log.warn("NotAcceptable: {}", ex.getMessage());
    var body =
        new BusinessErrorResponse(
            java.time.Instant.now(),
            HttpStatus.NOT_ACCEPTABLE.value(),
            "NOT_ACCEPTABLE",
            "수용 가능한 응답 형태가 아닙니다",
            exg.getRequest().getURI().getPath(),
            null,
            getTraceId(exg));
    return Mono.just(ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(body));
  }

  /** 415 Unsupported Media Type */
  @ExceptionHandler(UnsupportedMediaTypeStatusException.class)
  public Mono<ResponseEntity<BusinessErrorResponse>> handleUnsupportedMedia(
      UnsupportedMediaTypeStatusException ex, ServerWebExchange exg) {
    log.warn("UnsupportedMediaType: {}", ex.getContentType());
    var body =
        new BusinessErrorResponse(
            java.time.Instant.now(),
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
            "UNSUPPORTED_MEDIA_TYPE",
            "지원하지 않는 콘텐츠 타입입니다",
            exg.getRequest().getURI().getPath(),
            null,
            getTraceId(exg));
    return Mono.just(ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body));
  }

  /** @ResponseStatus 기반 예외 매핑(프레임워크 내부/사용자 정의) */
  @ExceptionHandler(ResponseStatusException.class)
  public Mono<ResponseEntity<BusinessErrorResponse>> handleResponseStatus(
      ResponseStatusException ex, ServerWebExchange exg) {
    HttpStatusCode sc = ex.getStatusCode();
    String reason = ex.getReason() != null ? ex.getReason() : sc.toString();
    log.warn("ResponseStatusException: {} {}", sc.value(), reason);

    var body =
        new BusinessErrorResponse(
            java.time.Instant.now(),
            sc.value(),
            "RESPONSE_STATUS_ERROR", // 필요시 세분화 Enum 확장
            reason,
            exg.getRequest().getURI().getPath(),
            null,
            getTraceId(exg));
    return Mono.just(ResponseEntity.status(sc).body(body));
  }

  // /**
  //  * 필요 시: WebExchangeBindException을 더 리치하게 가공하고 싶다면 오버라이드 가능 (BaseReactiveExceptionHandler에 이미 기본 처리 있음)
  //  */
  // @ExceptionHandler(WebExchangeBindException.class)
  // public Mono<ResponseEntity<ValidationErrorResponse>> handleBind(
  //     WebExchangeBindException ex, ServerWebExchange exg) {
  //   log.warn("WebFlux bind/validation failed: {} errors", ex.getErrorCount());
  //   List<FieldError> fields =
  //       ex.getFieldErrors().stream()
  //           .map(
  //               err ->
  //                   new FieldError(
  //                       err.getField(),
  //                       err.getRejectedValue(),
  //                       err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid value"))
  //           .toList();

  //   var body =
  //       ValidationErrorResponse.of(
  //           HttpStatus.BAD_REQUEST.value(),
  //           CommonErrorCode.INVALID_INPUT.getCode(),
  //           "입력값 검증에 실패했습니다",
  //           exg.getRequest().getURI().getPath(),
  //           fields,
  //           getTraceId(exg));
  //   return Mono.just(ResponseEntity.badRequest().body(body));
  // }
}