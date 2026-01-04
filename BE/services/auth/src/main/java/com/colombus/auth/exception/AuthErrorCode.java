package com.colombus.auth.exception;

import com.colombus.common.web.core.exception.ErrorCode;

public enum AuthErrorCode implements ErrorCode {
    // ===== 4xx: Client Errors =====
    // 세션/쿠키
    SESSION_EXPIRED(401, "AUTH-401-001", "세션이 만료되었습니다. 다시 로그인해주세요"),
    MAX_SESSIONS_EXCEEDED(401, "AUTH-401-002", "최대 허용 세션 개수를 초과했습니다. 다른 세션을 종료합니다"),
    INVALID_SESSION(401, "AUTH-401-004", "유효하지 않은 세션입니다. 다시 로그인해주세요"),
    SESSION_TAMPERED(401, "AUTH-401-014", "세션 무결성 검증에 실패했습니다"),

    // 로그인/검증(로컬/IdP 공통)
    INVALID_CREDENTIALS(401, "AUTH-401-005", "이메일 또는 비밀번호가 올바르지 않습니다"),
    EMAIL_VERIFICATION_REQUIRED(401, "AUTH-401-011", "로그인 실패: 이메일 인증이 필요합니다"),
    MFA_REQUIRED(401, "AUTH-401-012", "추가 인증(MFA)이 필요합니다"),

    // CSRF
    CSRF_TOKEN_REQUIRED(403, "AUTH-403-004", "CSRF 토큰이 필요합니다"),
    CSRF_TOKEN_MISMATCH(403, "AUTH-403-005", "CSRF 토큰이 일치하지 않습니다"),

    // OAuth/OIDC 콜백 파라미터
    INVALID_REQUEST(400, "AUTH-400-001", "잘못된 인증 요청입니다"),
    INVALID_REDIRECT_URI(400, "AUTH-400-002", "등록되지 않은 리다이렉트 URI입니다"),
    OAUTH_STATE_MISMATCH(400, "AUTH-400-003", "요청 상태(state)가 일치하지 않습니다"),
    NONCE_MISMATCH(400, "AUTH-400-004", "요청 nonce가 일치하지 않습니다"),
    PKCE_CODE_VERIFIER_INVALID(400, "AUTH-400-005", "PKCE code_verifier가 유효하지 않습니다"),

    // 토큰 검증/수명
    ID_TOKEN_INVALID(401, "AUTH-401-006", "ID 토큰이 유효하지 않습니다"),
    ID_TOKEN_EXPIRED(401, "AUTH-401-007", "ID 토큰이 만료되었습니다"),
    ACCESS_TOKEN_INVALID(401, "AUTH-401-008", "액세스 토큰이 유효하지 않습니다"),
    REFRESH_TOKEN_EXPIRED(401, "AUTH-401-009", "리프레시 토큰이 만료되었습니다"),
    REFRESH_TOKEN_REVOKED(401, "AUTH-401-010", "리프레시 토큰이 폐기되었습니다"),

    // 내부 토큰 교환/권한
    TOKEN_EXCHANGE_REQUIRED(401, "AUTH-401-013", "내부 토큰 교환이 필요합니다"),
    TOKEN_EXCHANGE_FORBIDDEN(403, "AUTH-403-006", "토큰 교환이 허용되지 않습니다"),
    INSUFFICIENT_SCOPE(403, "AUTH-403-001", "요청에 필요한 권한 범위가 없습니다"),
    AUDIENCE_MISMATCH(403, "AUTH-403-002", "요청 대상(aud)이 허용되지 않습니다"),
    TOKEN_TYPE_NOT_ALLOWED(403, "AUTH-403-003", "허용되지 않는 토큰 유형입니다"),
    ENTRYPOINT_MISMATCH(403, "AUTH-403-007", "요청 엔트리포인트가 정책과 일치하지 않습니다"),

    // 레이트 리밋/브루트포스
    TOO_MANY_LOGIN_ATTEMPTS(429, "AUTH-429-001", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도하세요"),
    RATE_LIMIT_EXCEEDED(429, "AUTH-429-002", "요청 한도를 초과했습니다"),

    // ===== 5xx: Server/Integration Errors =====
    SESSION_STORE_ERROR(500, "AUTH-500-001", "세션 저장소 처리 중 오류가 발생했습니다"),
    TOKEN_SERVICE_ERROR(500, "AUTH-500-002", "토큰 처리 중 오류가 발생했습니다"),
    IDP_RESPONSE_ERROR(502, "AUTH-502-001", "인증 제공자(IdP) 응답 오류"),
    JWKS_FETCH_FAILED(502, "AUTH-502-002", "토큰 검증 키(JWKS) 조회에 실패했습니다"),
    LOGOUT_PROPAGATION_FAILED(502, "AUTH-502-003", "로그아웃 전파가 일부 실패했습니다"),
    IDP_UNAVAILABLE(503, "AUTH-503-001", "인증 제공자 서비스가 일시적으로 사용 불가합니다"),
    TOKEN_EXCHANGE_UNAVAILABLE(503, "AUTH-503-002", "내부 토큰 교환 서비스를 사용할 수 없습니다"),
    
    ;

    private final int httpStatus;
    private final String code;
    private final String message;

    AuthErrorCode(int httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

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