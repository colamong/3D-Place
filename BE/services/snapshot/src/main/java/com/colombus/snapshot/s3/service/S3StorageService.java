package com.colombus.snapshot.s3.service;

import com.colombus.common.domain.dto.ChunkInfo;
import com.colombus.snapshot.exception.GlbUploadException;
import com.colombus.snapshot.exception.SnapshotDownloadException;
import com.colombus.snapshot.exception.SnapshotUploadException;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

import static com.colombus.snapshot.exception.SnapshotErrorCode.*;

@Service
@RequiredArgsConstructor
public class S3StorageService {
    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);
    private static final String SNAPSHOT_PREFIX = "snapshot";
    private static final String SNAPSHOT_EXTENSION = "json";
    private static final String GLB_AFFIX = "glb";

    private final S3Service s3Service;

    public Optional<String> getLatestSnapshot(ChunkInfo chunkInfo, int version) {
        String key = buildLatestS3Key(SNAPSHOT_PREFIX, chunkInfo, SNAPSHOT_EXTENSION);
        String chunkKey = ChunkInfo.toChunkKey(chunkInfo);

        try {
            return s3Service.getChunkFile(key)
                    .filter(content -> !content.isBlank())
                    .filter(content -> !content.equals("[]"))
                    .or(() -> {
                        log.info("유효한 스냅샷 없음. 청크: {}, 버전: {}", chunkInfo, version);
                        return Optional.empty();
                    });
        } catch (NoSuchKeyException e) {
            // 정상 케이스: 파일이 없음 (신규 청크)
            log.debug("스냅샷 파일 없음: {}", key);
            return Optional.empty();

        } catch (S3Exception e) {
            if (e.statusCode() == 403 || e.statusCode() == 401) {
                // 권한 오류
                log.error("S3 권한 없음: {}", key, e);
                throw new SnapshotDownloadException(
                        S3_PERMISSION_DENIED,
                        "S3 권한 없음: " + chunkKey
                );
            }
            // 기타 S3 오류 -> 재시도
            log.error("S3 오류: {}", key, e);
            throw new SnapshotDownloadException(
                    S3_NETWORK_ERROR,
                    "S3 네트워크 오류: " + chunkKey,
                    e
            );

        } catch (SdkException e) {
            // SDK 네트워크 오류 -> 재시도
            log.error("S3 SDK 오류: {}", key, e);
            throw new SnapshotDownloadException(
                    S3_NETWORK_ERROR,
                    "S3 네트워크 오류: " + chunkKey,
                    e
            );
        } 
    }

    public String uploadSnapshot(ChunkInfo chunkInfo, int version, String finalSnapshot) {
        String key = buildS3Key(SNAPSHOT_PREFIX, chunkInfo, version, SNAPSHOT_EXTENSION);
        String latestKey = buildLatestS3Key(SNAPSHOT_PREFIX, chunkInfo, SNAPSHOT_EXTENSION);
        String chunkKey = ChunkInfo.toChunkKey(chunkInfo);
        try {
            return s3Service.uploadFile(key, latestKey, finalSnapshot);
        } catch (S3Exception e) {
            if (e.statusCode() == 403 || e.statusCode() == 401) {
                // 권한 오류
                log.error("S3 업로드 권한 없음: {}", key, e);
                throw new SnapshotUploadException(
                        S3_PERMISSION_DENIED,
                        "S3 업로드 권한 없음: " + chunkKey
                );
            }
            // 기타 S3 오류 -> 재시도
            log.error("S3 업로드 오류: {}", key, e);
            throw new SnapshotUploadException(
                    S3_NETWORK_ERROR,
                    "S3 업로드 네트워크 오류: " + chunkKey,
                    e
            );
        } catch (SdkException e) {
            // SDK 네트워크 오류 -> 재시도
            log.error("S3 업로드 SDK 오류: {}", key, e);
            throw new SnapshotUploadException(
                    S3_NETWORK_ERROR,
                    "S3 업로드 네트워크 오류: " + chunkKey,
                    e
            );
        } catch (Exception e) {
            log.error("스냅샷 업로드 실패: {}", key, e);
            throw new SnapshotUploadException(S3_SNAPSHOT_UPLOAD_FAILED, "스냅샷 업로드 실패: " + chunkKey, e);
        }
    }

    public String uploadGLB(ChunkInfo chunkInfo, int version, byte[] glbData) {
        String key = buildS3Key(GLB_AFFIX, chunkInfo, version, GLB_AFFIX);
        String latestKey = buildLatestS3Key(GLB_AFFIX, chunkInfo, GLB_AFFIX);
        String chunkKey = ChunkInfo.toChunkKey(chunkInfo);
        try {
            return s3Service.uploadFile(key, latestKey, glbData);
        } catch (S3Exception e) {
            if (e.statusCode() == 403 || e.statusCode() == 401) {
                // 권한 오류
                log.error("GLB 업로드 권한 없음: {}", key, e);
                throw new GlbUploadException(
                        S3_PERMISSION_DENIED,
                        "GLB 업로드 권한 없음: " + chunkKey
                );
            }
            // 기타 S3 오류 -> 재시도
            log.error("GLB 업로드 오류: {}", key, e);
            throw new GlbUploadException(
                    S3_NETWORK_ERROR,
                    "GLB 업로드 네트워크 오류: " + chunkKey,
                    e
            );

        } catch (SdkException e) {
            // SDK 네트워크 오류 -> 재시도
            log.error("GLB 업로드 SDK 오류: {}", key, e);
            throw new GlbUploadException(
                    S3_NETWORK_ERROR,
                    "GLB 업로드 네트워크 오류: " + chunkKey,
                    e
            );

        } catch (Exception e) {
            log.error("GLB 업로드 실패: {}", key, e);
            throw new GlbUploadException(S3_GLB_UPLOAD_FAILED, "GLB 업로드 실패: " + chunkKey, e);
        }
    }

    private String buildS3Key(String prefix, ChunkInfo info, int version, String ext) {
        return String.format("%s/%s/l%d/tx%d/ty%d/x%d/y%d/z%d/v%d.%s",
                prefix, info.worldName(), info.lod(),
                info.tx(), info.ty(),
                info.x(), info.y(), info.z(), version, ext
        );
    }

    private String buildLatestS3Key(String prefix, ChunkInfo info, String ext) {
        return String.format("%s/%s/latest/l%d/tx%d/ty%d/x%d/y%d/z%d.%s",
                prefix, info.worldName(), info.lod(),
                info.tx(), info.ty(),
                info.x(), info.y(), info.z(), ext
        );
    }
}

