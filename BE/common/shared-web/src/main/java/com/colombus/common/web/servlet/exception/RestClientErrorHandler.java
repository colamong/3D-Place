package com.colombus.common.web.servlet.exception;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

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

@Slf4j
public class RestClientErrorHandler {
    
    private final ObjectMapper objectMapper;
    
    public RestClientErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * RestClient Builder에 적용할 상태 핸들러 생성
     */
    public RestClient.Builder configure(RestClient.Builder builder) {
        return builder.defaultStatusHandler(HttpStatusCode::isError, this::handleError);
    }
    
    /**
     * 에러 응답 처리
     */
    private void handleError(HttpRequest request, ClientHttpResponse response) throws IOException {
        int statusCode = response.getStatusCode().value();
        byte[] body = response.getBody().readAllBytes();
        
        try {
            RemoteErrorResponse errorResponse = objectMapper.readValue(body, RemoteErrorResponse.class);
            throw mapToBusinessException(statusCode, errorResponse);
        } catch (Exception e) {
            // JSON 파싱 실패 시 기본 예외 발생
            log.warn("Failed to parse error response from {}: {}", request.getURI(), e.getMessage());
            throw createDefaultException(statusCode);
        }
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
        return switch (statusCode / 100) {
            case 4 -> new ValidationException("클라이언트 요청 오류가 발생했습니다");
            case 5 -> new ServiceUnavailableException("서비스 응답 오류가 발생했습니다");
            default -> new ServiceUnavailableException("알 수 없는 오류가 발생했습니다");
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