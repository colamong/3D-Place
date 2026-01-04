package com.colombus.snapshot.s3.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@EnableRetry
public class S3Service {


    private static final Logger log = LoggerFactory.getLogger(S3Service.class);
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket-name}")
    private String bucketName;


    @Retryable(
        retryFor = SdkException.class,
        noRetryFor = NoSuchKeyException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 200, multiplier = 2)
    )
    public Optional<String> getChunkFile(String key) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            String content = s3Client.getObjectAsBytes(getRequest)
                    .asString(StandardCharsets.UTF_8);

            return Optional.ofNullable(content);

        } catch (NoSuchKeyException e) {
            log.debug("S3 파일 없음: {}", key);
            return Optional.empty();
        } catch (SdkException e) {
            // 네트워크 오류 -> 재시도
            log.error("S3 조회 실패: {}, message: {}", key, e.getMessage());
            throw e;
        } catch (Exception e) {
            // 기타 예외
            log.error("예상치 못한 오류: {}", key, e);
            throw e;
        }
    }

    @Retryable(
            retryFor = {SdkException.class, S3Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2)
    )
    public String uploadFile(String key, String latestKey, String jsonData) {
        return uploadAndCopy(key, latestKey, RequestBody.fromString(jsonData, StandardCharsets.UTF_8), "application/json");
    }

    @Retryable(
            retryFor = {SdkException.class, S3Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2)
    )
    public String uploadFile(String key, String latestKey, byte[] glbData) {
        return uploadAndCopy(key, latestKey, RequestBody.fromBytes(glbData), "model/gltf-binary");
    }

    private String uploadAndCopy(String key, String latestKey, RequestBody body, String contentType) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(putRequest, body);

        CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(key)
                .destinationBucket(bucketName)
                .destinationKey(latestKey)
                .metadataDirective(MetadataDirective.REPLACE)
                .contentType(contentType)
                .build();

        s3Client.copyObject(copyRequest);

        return generatePresignedUrl(latestKey);
    }

    @Retryable(retryFor = {SdkException.class}, maxAttempts = 2)
    private String generatePresignedUrl(String key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(r ->
                    r.signatureDuration(Duration.ofMinutes(10))
                            .getObjectRequest(getObjectRequest));

            return presigned.url().toString();
        } catch (SdkException e) {
            log.error("Presigned URL 생성 실패: {}", key, e);
            throw e;
        }
    }
}
