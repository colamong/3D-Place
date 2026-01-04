package com.colombus.common.web.servlet.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.colombus.common.web.core.exception.dto.BusinessErrorResponse;
import com.colombus.common.web.core.exception.dto.FieldError;
import com.colombus.common.web.core.exception.dto.ValidationErrorResponse;

@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnWebApplication(type = Type.SERVLET)
public class GlobalMvcExceptionHandler extends BaseExceptionHandler {

    @Override
    protected String getTraceId(HttpServletRequest request) {
        String id = request.getHeader("X-Trace-Id");
        if (id == null) id = request.getHeader("X-B3-TraceId");
        if (id == null) id = MDC.get("traceId");
        return id;
    }

    /** 본문 파싱 실패(JSON 문법 오류 등) → 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BusinessErrorResponse> handleHttpMessageNotReadable(
        HttpMessageNotReadableException ex, HttpServletRequest req
    ) {
        log.warn(
            "Message not readable: {}",
            ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage());

        var body =
            new BusinessErrorResponse(
                java.time.Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_INPUT",
                "요청 본문을 해석할 수 없습니다",
                req.getRequestURI(),
                null,
                getTraceId(req));
        return ResponseEntity.badRequest().body(body);
    }

    /** 필수 파라미터 누락 → 400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ValidationErrorResponse> handleMissingServletRequestParameter(
        MissingServletRequestParameterException ex, HttpServletRequest req) {
        log.warn("Missing request parameter: {} ({})", ex.getParameterName(), ex.getParameterType());

        var fields = List.of(new FieldError(ex.getParameterName(), null, "필수 파라미터가 누락되었습니다"));

        var body =
            ValidationErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_INPUT",
                "입력값 검증에 실패했습니다",
                req.getRequestURI(),
                fields,
                getTraceId(req));
        return ResponseEntity.badRequest().body(body);
    }

    /** 파라미터 타입 불일치 (?page=abc 등) → 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ValidationErrorResponse> handleMethodArgumentTypeMismatch(
        MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        log.warn(
            "Type mismatch: {} -> expected {}",
            ex.getName(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        var fields = List.of(new FieldError(ex.getName(), ex.getValue(), "값의 타입이 올바르지 않습니다"));

        var body =
            ValidationErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_INPUT",
                "입력값 검증에 실패했습니다",
                req.getRequestURI(),
                fields,
                getTraceId(req));
        return ResponseEntity.badRequest().body(body);
    }

    /** 멀티파트 파트 누락(파일 업로드 등) → 400 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ValidationErrorResponse> handleMissingServletRequestPart(
        MissingServletRequestPartException ex, HttpServletRequest req) {
        log.warn("Missing multipart part: {}", ex.getRequestPartName());

        var fields = List.of(new FieldError(ex.getRequestPartName(), null, "필수 파일/파트가 누락되었습니다"));

        var body =
            ValidationErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_INPUT",
                "입력값 검증에 실패했습니다",
                req.getRequestURI(),
                fields,
                getTraceId(req));
        return ResponseEntity.badRequest().body(body);
    }

    /** 자카르타 Bean Validation (@Validated on @RequestParam 등) → 400 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponse> handleConstraintViolation(
        ConstraintViolationException ex, HttpServletRequest req) {
        log.warn("Constraint violations: {}", ex.getConstraintViolations().size());

        var fields =
            ex.getConstraintViolations().stream()
                .map(
                    v ->
                        new FieldError(
                            v.getPropertyPath() != null ? v.getPropertyPath().toString() : null,
                            v.getInvalidValue(),
                            v.getMessage() != null ? v.getMessage() : "Invalid value"))
                .toList();

        var body =
            ValidationErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_INPUT",
                "입력값 검증에 실패했습니다",
                req.getRequestURI(),
                fields,
                getTraceId(req));
        return ResponseEntity.badRequest().body(body);
    }

    /** 405 Method Not Allowed */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<BusinessErrorResponse> handleMethodNotSupported(
        HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        log.warn("Method not allowed: {} (supported: {})", ex.getMethod(), ex.getSupportedHttpMethods());

        var body =
            new BusinessErrorResponse(
                java.time.Instant.now(),
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "METHOD_NOT_ALLOWED",
                "허용되지 않은 HTTP 메서드입니다",
                req.getRequestURI(),
                null,
                getTraceId(req));

        // Allow 헤더 설정 (가능하면)
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
          builder.headers(h -> h.setAllow(supported));
        }
        return builder.body(body);
    }

    /** 415 Unsupported Media Type */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<BusinessErrorResponse> handleUnsupportedMedia(
        HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        log.warn("Unsupported media type: {}", ex.getContentType());

        var body =
            new BusinessErrorResponse(
                java.time.Instant.now(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "UNSUPPORTED_MEDIA_TYPE",
                "지원하지 않는 콘텐츠 타입입니다",
                req.getRequestURI(),
                null,
                getTraceId(req));

        // Accept 헤더 힌트(선택)
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        List<MediaType> supported = ex.getSupportedMediaTypes();
        if (supported != null && !supported.isEmpty()) {
          String accept = supported.stream().map(MediaType::toString).collect(Collectors.joining(", "));
          builder.header(HttpHeaders.ACCEPT, accept);
        }
        return builder.body(body);
    }

    /** 406 Not Acceptable */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<BusinessErrorResponse> handleNotAcceptable(
        HttpMediaTypeNotAcceptableException ex, HttpServletRequest req) {
        log.warn("Not acceptable: {}", ex.getMessage());

        var body =
            new BusinessErrorResponse(
                java.time.Instant.now(),
                HttpStatus.NOT_ACCEPTABLE.value(),
                "NOT_ACCEPTABLE",
                "수용 가능한 응답 형태가 아닙니다",
                req.getRequestURI(),
                null,
                getTraceId(req));
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(body);
    }

    /** 404 No handler (필요: spring.mvc.throw-exception-if-no-handler-found=true) */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<BusinessErrorResponse> handleNoHandlerFound(
        NoHandlerFoundException ex, HttpServletRequest req) {
        log.warn("No handler found: {} {}", ex.getHttpMethod(), ex.getRequestURL());

        var body =
            new BusinessErrorResponse(
                java.time.Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                "요청하신 리소스를 찾을 수 없습니다",
                req.getRequestURI(),
                null,
                getTraceId(req));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}