package com.colombus.world.service;

import com.colombus.common.web.core.exception.ServiceUnavailableException;
import com.colombus.world.exception.WorldErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket-name}")
    private String bucketName;

    @Retryable(
            retryFor = SdkException.class,
            noRetryFor = NoSuchKeyException.class,
            backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());

            log.debug("S3 객체 존재 확인: {}", key);
            return true;

        } catch (NoSuchKeyException e) {
            // 파일이 실제로 없음
            log.debug("S3 객체 없음: {}", key);
            return false;

        } catch (S3Exception e) {
            int status = e.statusCode();

            // 403: 권한 문제 - 재시도 불필요
            if (status == 403) {
                log.error("S3 접근 거부: {}", key);
                throw new ServiceUnavailableException(
                        WorldErrorCode.S3_URL_GENERATION_FAILED,
                        "S3 객체 접근 거부: " + key,
                        e
                );
            }

            // 404: 파일 없음
            if (status == 404) {
                log.debug("S3 객체 없음 (404): {}", key);
                return false;
            }

            log.warn("S3 서버 오류 (status={}), 재시도 예정: {}", status, key);
            throw e;

        } catch (SdkException e) {
            // 네트워크 오류 -> 재시도
            log.warn("네트워크 오류 발생, 재시도 예정: {} - {}", key, e.getMessage());
            throw e;
        }
    }

    @Retryable(
            retryFor = {SdkException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public String generatePresignedUrl(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(builder -> builder
                    .signatureDuration(Duration.ofMinutes(10))
                    .getObjectRequest(request));

            log.info("Presigned URL 생성 완료: {}", key);
            return presigned.url().toString();

        } catch (SdkException e) {
            log.error("Presigned URL 생성 실패: {}", key, e);
            throw new ServiceUnavailableException(
                    WorldErrorCode.S3_URL_GENERATION_FAILED,
                    "Presigned URL 생성 실패: " + key,
                    e
            );
        }
    }

    private String getObjectKey(String type, String world, int lod, int tx, int ty, int x, int y, int z) {
        String extension = "glb".equals(type) ? "glb" : "json";
        return String.format("%s/%s/latest/l%d/tx%d/ty%d/x%d/y%d/z%d.%s",
                type, world, lod, tx, ty, x, y, z, extension);
    }


    public String getSnapshotUrl(String world, int lod, int tx, int ty, int x, int y, int z) {
        String objectKey = getObjectKey("snapshot", world, lod, tx, ty, x, y, z);

        // 파일 존재 여부 확인
        if (!objectExists(objectKey)) {
            log.warn("스냅샷을 찾을 수 없음: {}", objectKey);
            return null;
        }

        return generatePresignedUrl(objectKey);
    }

    public String getGLBUrl(String world, int lod, int tx, int ty, int x, int y, int z) {
        String objectKey = getObjectKey("glb", world, lod, tx, ty, x, y, z);

        // 파일 존재 여부 확인
        if (!objectExists(objectKey)) {
            log.warn("GLB를 찾을 수 없음: {}", objectKey);
            return null;
        }

        return generatePresignedUrl(objectKey);
    }
}

