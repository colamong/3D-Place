package com.colombus.user.exception;

import com.colombus.common.web.core.exception.ErrorCode;

public enum UserErrorCode implements ErrorCode {
    // ===== 4xx: Client Errors =====
    // Account / 상태
    USER_NOT_FOUND(404, "USER-404-001", "사용자를 찾을 수 없습니다"),
    USER_ALREADY_EXISTS(409, "USER-409-001", "사용자가 이미 존재합니다"),
    USER_DEACTIVATED(403, "USER-403-001", "비활성화된 계정입니다"),
    USER_SUSPENDED(423, "USER-423-001", "정지된 계정입니다"),
    USER_ALREADY_DELETED(410, "USER-410-001", "이미 삭제된 사용자입니다"),

    // 프로필/계정 속성 충돌
    USERNAME_ALREADY_IN_USE(409, "USER-409-002", "이미 사용 중인 사용자명입니다"),
    EMAIL_ALREADY_IN_USE(409, "USER-409-003", "이미 사용 중인 이메일입니다"),

    // 검증/정책
    EMAIL_NOT_VERIFIED(403, "USER-403-002", "이메일 인증이 필요합니다"),
    USER_ALREADY_VERIFIED(409, "USER-409-004", "이미 인증이 완료된 사용자입니다"),
    USERNAME_POLICY_VIOLATION(400, "USER-400-004", "허용되지 않는 사용자명 형식입니다"),
    PASSWORD_WEAK(400, "USER-400-005", "비밀번호 복잡도 정책을 만족하지 않습니다"),
    TOO_MANY_USERNAME_CHANGES(429, "USER-429-001", "사용자명 변경 요청이 너무 빈번합니다"),

    // 아이덴티티(SSO/OIDC) 연동
    IDENTITY_NOT_FOUND(404, "USER-404-002", "연동된 외부 계정을 찾을 수 없습니다"),
    IDENTITY_ALREADY_LINKED(409, "USER-409-005", "이미 연동된 외부 계정입니다"),
    IDENTITY_OWNERSHIP_MISMATCH(403, "USER-403-003", "다른 사용자에 속한 외부 계정입니다"),
    PRIMARY_IDENTITY_CANNOT_BE_UNLINKED(409, "USER-409-006", "기본 인증 수단은 해제할 수 없습니다"),
    LAST_VERIFIED_IDENTITY_CANNOT_BE_UNLINKED(409, "USER-409-007", "마지막 검증된 인증 수단은 해제할 수 없습니다"),
    INVALID_IDENTITY_PROVIDER(400, "USER-400-001", "지원하지 않는 인증 제공자입니다"),
    INVALID_IDENTITY_STATE(409, "USER-409-008", "유효하지 않은 연동 상태입니다"),

    // 토큰/검증 플로우
    VERIFICATION_TOKEN_INVALID(400, "USER-400-002", "유효하지 않은 인증 토큰입니다"),
    VERIFICATION_TOKEN_EXPIRED(410, "USER-410-002", "만료된 인증 토큰입니다"),
    EMAIL_CHANGE_TOKEN_INVALID(400, "USER-400-003", "유효하지 않은 이메일 변경 토큰입니다"),
    EMAIL_CHANGE_TOKEN_EXPIRED(410, "USER-410-003", "만료된 이메일 변경 토큰입니다"),
    TFA_REQUIRED(401, "USER-401-002", "2단계 인증이 필요합니다"),

    // 업로드/미디어
    PROFILE_IMAGE_TOO_LARGE(413, "USER-413-001", "프로필 이미지가 허용 용량을 초과했습니다"),
    PROFILE_IMAGE_UNSUPPORTED_TYPE(415, "USER-415-001", "지원하지 않는 이미지 형식입니다"),

    // 동시성/일관성
    VERSION_CONFLICT(409, "USER-409-009", "동시 수정 충돌이 발생했습니다"),
    DUPLICATE_REQUEST(409, "USER-409-010", "중복 요청입니다"),

    // ===== 5xx: Server/Integration Errors =====
    USER_PERSISTENCE_ERROR(500, "USER-500-001", "사용자 저장소 처리 중 오류가 발생했습니다"),
    AVATAR_STORAGE_ERROR(500, "USER-500-002", "프로필 이미지 저장 중 오류가 발생했습니다"),
    EVENT_PUBLISH_FAILED(503, "USER-503-001", "이벤트 발행에 실패했습니다"),
    AUTH_PROVIDER_ERROR(502, "USER-502-001", "외부 인증 제공자 통신 실패"),
    EMAIL_SEND_FAILED(503, "USER-503-002", "인증/알림 이메일 전송에 실패했습니다"),
    CACHE_SYNC_ERROR(500, "USER-500-003", "캐시 동기화 중 오류가 발생했습니다"),
    TRANSACTION_TIMEOUT(504, "USER-504-001", "처리 시간이 초과되었습니다"),
    
    ;

    private final int httpStatus;
    private final String code;
    private final String message;

    UserErrorCode(int httpStatus, String code, String message) {
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