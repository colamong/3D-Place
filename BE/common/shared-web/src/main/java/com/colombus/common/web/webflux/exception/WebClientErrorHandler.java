package com.colombus.common.web.webflux.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.common.web.core.exception.ConflictException;
import com.colombus.common.web.core.exception.ErrorCode;
import com.colombus.common.web.core.exception.ForbiddenException;
import com.colombus.common.web.core.exception.NotFoundException;
import com.colombus.common.web.core.exception.ServiceUnavailableException;
import com.colombus.common.web.core.exception.TooManyRequestsException;
import com.colombus.common.web.core.exception.UnauthorizedException;
import com.colombus.common.web.core.exception.ValidationException;
import com.colombus.common.web.core.exception.dto.RemoteErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class WebClientErrorHandler {
    
    private final ObjectMapper objectMapper;
    
    public WebClientErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * WebClient Builder에 적용할 상태 핸들러 생성
     */
    public WebClient.Builder configure(WebClient.Builder builder) {
        return builder.defaultStatusHandler(HttpStatusCode::isError, this::handleError);
    }
    
    /**
     * 에러 응답 처리
     */
    private Mono<? extends Throwable> handleError(ClientResponse response) {
        final int status = response.statusCode().value();

        return response.bodyToMono(byte[].class)
            .defaultIfEmpty(new byte[0])
            .flatMap(body -> {

                if (body.length == 0) {
                    return Mono.error(createDefaultException(status));
                }

                try {
                    RemoteErrorResponse er = objectMapper.readValue(body, RemoteErrorResponse.class);
                    return Mono.error(mapToBusinessException(status, er));
                } catch (Exception e) {
                    log.warn("Failed to parse error body (status={}): {}", status, e.getMessage());
                    return Mono.error(createDefaultException(status));
                }
            });
    }
    
    /**
     * ErrorResponse를 BusinessException으로 변환
     */
    private BusinessException mapToBusinessException(int statusCode, RemoteErrorResponse errorResponse) {

        String code = (errorResponse.code() == null || errorResponse.code().isBlank())
            ? "REMOTE_" + statusCode
            : errorResponse.code();

        String message = (errorResponse.message() == null || errorResponse.message().isBlank())
            ? defaultMessageForStatus(statusCode)
            : errorResponse.message();
            
        DynamicErrorCode errorCode = new DynamicErrorCode(statusCode, code, message);
        
        return switch (statusCode) {
            case 400 -> new ValidationException(errorCode, message, errorResponse.payload());
            case 401 -> new UnauthorizedException(errorCode, message);
            case 403 -> new ForbiddenException(errorCode, message);
            case 404 -> new NotFoundException(errorCode, message, errorResponse.payload());
            case 409 -> new ConflictException(errorCode, message, errorResponse.payload());
            case 429 -> new TooManyRequestsException(errorCode, message, errorResponse.payload());
            case 502, 503, 504 -> new ServiceUnavailableException(errorCode, message);
            default -> new BusinessException(errorCode, message, errorResponse.payload()) {};
        };
    }
    
    /**
     * 파싱 실패 시 기본 예외 생성
     */
    private BusinessException createDefaultException(int statusCode) {
        String code    = "REMOTE_" + statusCode;
        String message = defaultMessageForStatus(statusCode);
        DynamicErrorCode errorCode = new DynamicErrorCode(statusCode, code, message);

        // RemoteErrorResponse 파싱 실패 or 바디가 없는 경우의 fallback 매핑
        return switch (statusCode) {
            case 400 -> new ValidationException(errorCode, message, null);
            case 401 -> new UnauthorizedException(errorCode, message);
            case 403 -> new ForbiddenException(errorCode, message);
            case 404 -> new NotFoundException(errorCode, message, null);
            case 409 -> new ConflictException(errorCode, message, null);
            case 429 -> new TooManyRequestsException(errorCode, message, null);
            case 502, 503, 504 -> new ServiceUnavailableException(errorCode, message);
            default -> switch (statusCode / 100) {
                case 4 -> new ValidationException(errorCode, message, null);
                case 5 -> new ServiceUnavailableException(errorCode, message);
                default -> new ServiceUnavailableException(errorCode, message);
            };
        };
    }

    private String defaultMessageForStatus(int statusCode) {
        var hs = HttpStatus.resolve(statusCode);
        if (hs != null) return hs.getReasonPhrase();
        int cat = statusCode / 100;
        return switch (cat) {
            case 4 -> "클라이언트 요청 오류가 발생했습니다";
            case 5 -> "서비스 응답 오류가 발생했습니다";
            default -> "알 수 없는 오류가 발생했습니다";
        };
    }
    
    /**
     * 동적 에러 코드 (다른 서비스의 에러 코드를 래핑)
     */
    private record DynamicErrorCode(
        int httpStatus,
        String code,
        String message
    ) implements ErrorCode {
        
        @Override
        public int getHttpStatus() {
            return httpStatus;
        }
        
        @Override
        public String getCode() {
            return code;
        }
        
        @Override
        public String getMessage() {
            return message;
        }
    }
}