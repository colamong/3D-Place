package com.colombus.media.exception;

import com.colombus.common.web.core.exception.ErrorCode;

public enum MediaErrorCode implements ErrorCode {
    
    // =========================
    // 4xx Client Errors
    // =========================

    MEDIA_NOT_FOUND(404, "MEDIA-404-001", "미디어를 찾을 수 없습니다"),
    STAGING_TICKET_NOT_FOUND(404, "MEDIA-404-002", "업로드 티켓을 찾을 수 없습니다"),
    SUBJECT_NOT_ALIVE(404, "MEDIA-404-003", "연결 대상(Subject)이 존재하지 않거나 비활성 상태입니다"),

    MEDIA_ACCESS_DENIED(403, "MEDIA-403-001", "해당 미디어에 대한 권한이 없습니다"),
    MEDIA_OWNER_MISMATCH(403, "MEDIA-403-002", "미디어 소유자가 아닌 사용자입니다"),

    INVALID_MEDIA_REQUEST(400, "MEDIA-400-001", "잘못된 미디어 요청입니다"),
    INVALID_MEDIA_PURPOSE(400, "MEDIA-400-002", "지원하지 않는 미디어 용도입니다"),
    INVALID_MEDIA_TYPE(400, "MEDIA-400-003", "지원하지 않는 미디어 형식입니다"),
    INVALID_UPLOAD_STATE(400, "MEDIA-400-004", "업로드 상태가 유효하지 않습니다"),
    INVALID_S3_KEY(400, "MEDIA-400-005", "유효하지 않은 S3 키입니다"),

    MEDIA_UPLOAD_CONFLICT(409, "MEDIA-409-001", "이미 업로드가 진행 중인 자원입니다"),
    MEDIA_STATUS_CONFLICT(409, "MEDIA-409-002", "현재 상태에서는 요청을 처리할 수 없습니다"),
    S3_OBJECT_ALREADY_EXISTS(409, "MEDIA-409-003", "이미 존재하는 S3 객체입니다"),

    // =========================
    // 5xx Server Errors
    // =========================

    MEDIA_INTERNAL_ERROR(500, "MEDIA-500-001", "미디어 처리 중 서버 오류가 발생했습니다"),
    MEDIA_STORAGE_ERROR(500, "MEDIA-500-002", "스토리지 처리 중 오류가 발생했습니다"),
    MEDIA_SERVICE_UNAVAILABLE(503, "MEDIA-503-001", "미디어 서비스를 일시적으로 사용할 수 없습니다"),
    ;

    private final int httpStatus;
    private final String code;
    private final String message;

    MediaErrorCode(int httpStatus, String code, String message) {
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
