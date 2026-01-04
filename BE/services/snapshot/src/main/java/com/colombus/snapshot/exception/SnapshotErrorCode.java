package com.colombus.snapshot.exception;

import com.colombus.common.web.core.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SnapshotErrorCode implements ErrorCode {

    // ===== Lock 관련 =====
    FAILED_TO_ACQUIRE_LOCK(423, "LOCK-423-001", "락을 획득하지 못했습니다."),

    // ===== S3 Storage 관련 =====
    S3_SNAPSHOT_DOWNLOAD_FAILED(500, "S3-500-001", "S3에서 스냅샷 다운로드 실패"),
    S3_SNAPSHOT_UPLOAD_FAILED(500, "S3-500-002", "S3에 스냅샷 업로드 실패"),
    S3_GLB_UPLOAD_FAILED(500, "S3-500-003", "S3에 GLB 업로드 실패"),
    S3_NETWORK_ERROR(503, "S3-503-001", "S3 네트워크 오류 (일시적)"),
    S3_PERMISSION_DENIED(403, "S3-403-001", "S3 접근 권한 없음"),

    // ===== Redis 관련 =====
    REDIS_CONNECTION_FAILED(503, "REDIS-503-001", "Redis 연결 실패"),
    REDIS_OPERATION_FAILED(500, "REDIS-500-001", "Redis 작업 실패"),

    // ===== 데이터 손상 관련 =====
    DELTA_PARSE_FAILED(500, "DATA-500-001", "Delta JSON 파싱 실패"),
    SNAPSHOT_PARSE_FAILED(500, "DATA-500-002", "Snapshot JSON 파싱 실패"),
    DATA_CORRUPTION_DETECTED(500, "DATA-500-003", "데이터 손상 감지"),

    // ===== Database 관련 =====
    DB_CONNECTION_FAILED(503, "DB-503-001", "데이터베이스 연결 실패"),
    DB_VERSION_CONFLICT(409, "DB-409-001", "버전 충돌 (다른 프로세스가 이미 처리함)"),
    DB_CONSTRAINT_VIOLATION(400, "DB-400-001", "데이터 제약 조건 위반"),
    DB_TRANSACTION_TIMEOUT(408, "DB-408-001", "트랜잭션 타임아웃"),

    // ===== GLB 생성 관련 =====
    GLB_GENERATION_FAILED(500, "GLB-500-001", "GLB 파일 생성 실패"),
    GLB_MEMORY_EXHAUSTED(500, "GLB-500-002", "GLB 생성 중 메모리 부족"),

    // ===== Chunk 처리 관련 =====
    CHUNK_PROCESSING_FAILED(500, "CHUNK-500-001", "청크 처리 실패"),
    CHUNK_KEY_INVALID(400, "CHUNK-400-001", "유효하지 않은 청크 키");

    private final int httpStatus;
    private final String code;
    private final String message;
}
