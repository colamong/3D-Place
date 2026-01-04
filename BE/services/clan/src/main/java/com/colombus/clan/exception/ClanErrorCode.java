package com.colombus.clan.exception;

import com.colombus.common.web.core.exception.ErrorCode;

public enum ClanErrorCode implements ErrorCode {
    
    // 4xx Client Errors
    CLAN_NOT_FOUND(404, "CLAN-404-001", "클랜을 찾을 수 없습니다"),
    CLAN_MEMBER_NOT_FOUND(404, "CLAN-404-002", "클랜 멤버를 찾을 수 없습니다"),
    CLAN_JOIN_REQUEST_NOT_FOUND(404, "CLAN-404-003", "클랜 가입 요청을 찾을 수 없습니다"),
    CLAN_INVITE_NOT_FOUND(404, "CLAN-404-004", "클랜 초대 정보를 찾을 수 없습니다"),

    CLAN_ACCESS_DENIED(403, "CLAN-403-001", "해당 클랜에 대한 권한이 없습니다"),
    CLAN_OWNER_CANNOT_LEAVE(403, "CLAN-403-002", "클랜 마스터는 클랜을 탈퇴할 수 없습니다"),
    CLAN_JOIN_NOT_ALLOWED(403, "CLAN-403-003", "현재 정책상 클랜에 가입할 수 없습니다"),
    CLAN_MEMBER_BANNED(403, "CLAN-403-004", "해당 사용자는 클랜에서 차단되었습니다"),

    CLAN_INVALID_STATE(400, "CLAN-400-001", "클랜 상태가 유효하지 않습니다"),
    CLAN_INVALID_MEMBER_STATE(400, "CLAN-400-002", "클랜 멤버 상태가 유효하지 않습니다"),
    CLAN_INVALID_JOIN_REQUEST(400, "CLAN-400-003", "잘못된 클랜 가입 요청입니다"),

    CLAN_NAME_ALREADY_EXISTS(409, "CLAN-409-001", "이미 사용 중인 클랜 이름입니다"),
    CLAN_TAG_ALREADY_EXISTS(409, "CLAN-409-002", "이미 사용 중인 클랜 태그입니다"),
    CLAN_ALREADY_MEMBER(409, "CLAN-409-003", "이미 클랜에 가입된 사용자입니다"),
    CLAN_NOT_MEMBER(409, "CLAN-409-004", "클랜 멤버가 아닌 사용자입니다"),
    CLAN_MEMBER_LIMIT_EXCEEDED(409, "CLAN-409-005", "클랜 최대 인원 수를 초과했습니다"),
    CLAN_JOIN_REQUEST_ALREADY_PENDING(409, "CLAN-409-006", "이미 이 클랜에 가입 대기 중입니다."),
    CLAN_JOIN_REQUEST_CONFLICT(409, "CLAN-409-007", "가입 요청 상태가 이미 변경되었습니다."),

    // 5xx Server Errors
    CLAN_INTERNAL_ERROR(500, "CLAN-500-001", "클랜 처리 중 서버 오류가 발생했습니다"),
    CLAN_SERVICE_UNAVAILABLE(503, "CLAN-503-001", "클랜 서비스를 일시적으로 사용할 수 없습니다"),
    
    ;

    private final int httpStatus;
    private final String code;
    private final String message;

    ClanErrorCode(int httpStatus, String code, String message) {
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
