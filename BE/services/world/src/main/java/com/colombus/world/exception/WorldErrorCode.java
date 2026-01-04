package com.colombus.world.exception;

import com.colombus.common.web.core.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorldErrorCode implements ErrorCode {
    INVALID_CHUNK_ID(400, "WORLD-400-001", "청크 ID가 유효하지 않습니다."),
    CHUNK_COORDINATES_OUT_OF_RANGE(400, "WORLD-400-002", "청크 좌표가 유효한 범위를 벗어났습니다."),
    CHUNK_NOT_FOUND(404, "WORLD-404-001", "청크를 찾을 수 없습니다."),
    S3_URL_GENERATION_FAILED(503, "WORLD-503-001", "S3 URL 생성에 실패했습니다.");

    private final int httpStatus;
    private final String code;
    private final String message;
}
