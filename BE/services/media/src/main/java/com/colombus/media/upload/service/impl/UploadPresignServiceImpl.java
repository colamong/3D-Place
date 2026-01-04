package com.colombus.media.upload.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

import com.colombus.media.contract.dto.PresignPutRequest;
import com.colombus.media.contract.dto.PresignPutResponse;
import com.colombus.media.upload.props.UploadProps;
import com.colombus.media.upload.repository.StagingTicketRepository;
import com.colombus.media.upload.service.UploadPresignService;
import com.colombus.media.web.internal.mapper.MediaTypeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadPresignServiceImpl implements UploadPresignService {

    private final S3Presigner presigner;
    private final UploadProps props;
    private final StagingTicketRepository tickets;

    @Override
    public PresignPutResponse createPresignUrl(PresignPutRequest req) {
        UUID assetId = UUID.randomUUID();
        String ext = switch (req.contentType()) {
            case "image/jpeg" -> "jpg";
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            default -> "bin";
        };

        String stagingPrefix = withoutTrailingSlash(props.stagingPrefix());

        // 스테이징 키 강제: staging/{kind}/{id}/{purpose}/{assetId}.{ext}
        String key = "%s/%s/%s/%s/%s.%s".formatted(
            stagingPrefix, req.kind().name(), req.subjectId(), req.purpose().name(), assetId, ext
        );

        // 필수 헤더/메타를 서명에 포함 → 클라가 동일 값으로 업로드해야 유효
        PutObjectRequest.Builder put = PutObjectRequest.builder()
            .bucket(props.bucket())
            .key(key)
            .contentType(req.contentType())
            .serverSideEncryption(ServerSideEncryption.AES256)
            .metadata(Map.of(
                "asset-id", assetId.toString(),
                "subject-kind", req.kind().name(),
                "subject-id", req.subjectId().toString(),
                "purpose", req.purpose().name()
            ));

        if (props.requireChecksum()) {
            put = put.checksumAlgorithm(ChecksumAlgorithm.SHA256)
                    .checksumSHA256(req.checksumSHA256Base64());
        }

        Duration ttl = Duration.ofMinutes(props.ttlMinutes());
        PresignedPutObjectRequest pre = presigner.presignPutObject(
        PutObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .putObjectRequest(put.build())
            .build()
        );

        Instant expiresAt = Instant.now().plus(ttl);

        // 업로드 티켓 저장(PENDING) → 이후 링크(ingest) 단계에서 1회성 검증/사용
        tickets.insertPending(assetId, MediaTypeMapper.toDomain(req.kind()), req.subjectId(), MediaTypeMapper.toDomain(req.purpose()), key, expiresAt);
        log.info("[insertedPending] assetId={}, kind={}, subjectId={}, purpose={}, key={}, expiresAt={}", assetId, req.kind(), req.subjectId(), req.purpose(), key, expiresAt);

        log.info("presignedUrl={}", pre.url());
        return new PresignPutResponse(
            assetId,
            "PUT",
            pre.url().toString(),
            pre.signedHeaders(), // 이 헤더들을 그대로 포함해서 PUT 해야 함
            key,
            expiresAt
        );
    }

    private static String withoutTrailingSlash(String p) {
        if (p == null || p.isBlank()) return "";
        return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }
}
