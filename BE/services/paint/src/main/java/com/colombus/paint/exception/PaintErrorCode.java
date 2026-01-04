package com.colombus.paint.exception;

import com.colombus.common.web.core.exception.ErrorCode;

public enum PaintErrorCode implements ErrorCode {

	// 400 Bad Request
	INVALID_VSEQ(400, "PAINT-400-001", "유효하지 않은 vSeq 요청입니다."),
	INVALID_OPID_FORMAT(400, "PAINT-400-002", "유효하지 않은 opId 형식입니다."),
	MISSING_EXISTING_OP_ID(400, "PAINT-400-003", "vSeq가 2 이상일 때, existingOpId는 필수입니다."),
	INVALID_CHUNKID_FORMAT(400, "PAINT-400-004", "유효하지 않은 chunkId 형식입니다."),

	// 403 Forbidden
	VOXEL_PERMISSION_DENIED(403, "PAINT-403-001", "자신이 칠한 픽셀만 삭제할 수 있습니다."),

	// 404 Not Found
	VOXEL_NOT_FOUND(404, "PAINT-404-001", "삭제할 데이터(Voxel)가 존재하지 않습니다."),

	// 429 Too Many Request
	RATE_LIMIT_EXCEEDED(429, "PAINT-429-001", "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."),

	// 500 Internal Server Error (Redis/S3 Data parsing error)
	DELTA_PARSE_FAILED(500, "SYS-500-001", "데이터 파싱에 실패했습니다."),

	// 503 Service Unavailable
	EXTERNAL_SERVICE_UNAVAILABLE(503, "SYS-503-001", "외부 서비스(S3/Redis) 연결에 실패했습니다.");

	private final int httpStatus;
	private final String code;
	private final String message;

	PaintErrorCode(int httpStatus, String code, String message) {
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
