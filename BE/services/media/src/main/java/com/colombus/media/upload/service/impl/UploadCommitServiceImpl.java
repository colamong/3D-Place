package com.colombus.media.upload.service.impl;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.colombus.common.kafka.subject.model.type.AssetPurpose;
import com.colombus.common.utility.time.TimeConv;
import com.colombus.media.contract.dto.UploadCommitRequest;
import com.colombus.media.contract.dto.UploadCommitResponse;
import com.colombus.media.messaging.outbox.repository.OutboxPublishRepository;
import com.colombus.media.subject.dto.AssetDto;
import com.colombus.media.subject.util.FileInspector;
import com.colombus.media.upload.model.AssetUpsert;
import com.colombus.media.upload.props.UploadProps;
import com.colombus.media.upload.repository.AssetLinkRepository;
import com.colombus.media.upload.repository.AssetRepository;
import com.colombus.media.upload.repository.StagingTicketRepository;
import com.colombus.media.upload.service.UploadCommitService;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Service
@RequiredArgsConstructor
public class UploadCommitServiceImpl implements UploadCommitService {

    private final S3Client s3;
    private final UploadProps props;
    private final FileInspector inspector;
    private final AssetRepository assetRepo;
    private final AssetLinkRepository assetLinkRepo;
    private final StagingTicketRepository ticketRepo;
    private final OutboxPublishRepository outboxPubRepo;

    @Value("${media.s3.bucket}")
    private String bucket;

    @Value("${media.s3.staging-prefix:staging/}")
    private String stagingPrefix;

    @Value("${media.s3.final-prefix:assets/}")
    private String finalPrefix;

    @Value("${service.gateway.base-url}")
    private String cdn;
    
    @Override
    @Transactional
    public UploadCommitResponse commit(UploadCommitRequest req) {
        final String stagingKey = req.objectKey();
        if (!stagingKey.startsWith(stagingPrefix)) {
            throw new IllegalArgumentException("Invalid staging key");
        }

        var t = ticketRepo.findPending(req.assetId())
            .orElseThrow(() -> new IllegalStateException("No pending ticket"));

        if (!"PENDING".equals(t.status())) {
            throw new IllegalStateException("Ticket is not PENDING: " + t.status());
        }

        var now = Instant.now();
        
        if (t.expiresAt().isBefore(now)) {
            ticketRepo.expireDue(now);
            throw new IllegalStateException("Ticket expired");
        }
        
        HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
            .bucket(props.bucket())
            .key(stagingKey)
            .build()
        );

        long size = head.contentLength();
        inspector.assertSizeLimit(size);

        String headEtag = normalizeEtag(head.eTag());
        String reqEtag  = normalizeEtag(req.etag());
        if (headEtag == null || !headEtag.equalsIgnoreCase(reqEtag)) {
            throw new IllegalStateException("ETag mismatch");
        }

        byte[] bytes = s3.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(props.bucket())
            .key(stagingKey)
            .build()).asByteArray();

        inspector.assertSizeLimit(bytes);
        String mime = inspector.detectMime(bytes, req.originalFilename());
        inspector.assertNotExecutableOrForbidden(mime);
        inspector.assertAllowedImage(mime);

        var dim = inspector.probeImageDimensionOrThrow(bytes);
        var img = inspector.decodeImageOrThrow(bytes);
        int width = img.getWidth();
        int height = img.getHeight();

        String sha256 = inspector.sha256(bytes);
        String pixelSha256 = inspector.pixelSha256(img);

        String ext = switch (mime) {
            case "image/jpeg" -> "jpg";
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported image: " + mime);
        };

        // 최종 키 (sha 기반 + 확장자, 필요 시 가로x세로 포함)
        String assetKey = String.format("%s%s/%s/%s.%s",
            props.finalPrefix(), req.purpose().name().toLowerCase(), sha256.substring(0, 2), sha256, ext);
        
        // S3 승격: 서버사이드 Copy + 메타 교체
        s3.copyObject(CopyObjectRequest.builder()
            .sourceBucket(props.bucket())
            .sourceKey(stagingKey)
            .destinationBucket(props.bucket())
            .destinationKey(assetKey)
            .copySourceIfMatch(req.etag())
            .metadataDirective(MetadataDirective.REPLACE)
            .contentType(mime)
            .cacheControl("public, max-age=31536000, immutable")
            .metadata(Map.of(
                "sha256", sha256,
                "pixel-sha256", pixelSha256,
                "purpose", req.purpose().name(),
                "width",  String.valueOf(width),
                "height", String.valueOf(height)
            ))
            .serverSideEncryption(ServerSideEncryption.AES256)
            .build());
            
        String publicUrl = urlBuilder(assetKey);
        UUID assetId = upsertAndMarkTicket(
            t.purpose(), sha256, pixelSha256, mime, size, width, height, publicUrl, bucket, assetKey, t.assetId()
        );

        AssetDto linked = assetLinkRepo.replaceReturningDto(t.subjectKind(),t.subjectId(), t.purpose(), assetId, Instant.now());

        outboxPubRepo.appendAssetUpsert(
            t.subjectKind(),
            t.subjectId(),
            t.purpose(),
            assetId,
            linked.url()
        );
        
        // staging 정리
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(props.bucket())
                .key(stagingKey)
                .build());
        } catch (Exception e) {
            // TODO: dlq 추가
        }


        return new UploadCommitResponse(
            assetId, assetKey,
            bytes.length, dim.getWidth(), dim.getHeight(),
            mime, sha256, pixelSha256, publicUrl
        );
    }

    @Transactional
    protected UUID upsertAndMarkTicket(
        AssetPurpose purpose,
        String sha256, String pixelSha256, String mime,
        long size, int width, int height, String publicUrl,
        String bucket, String assetKey, UUID ticketId
    ) {
        UUID assetId = assetRepo.upsertAndGetId(new AssetUpsert(
            purpose, sha256, pixelSha256, mime, size, width, height, bucket, assetKey, publicUrl
        ));

        ticketRepo.markUsed(ticketId, TimeConv.nowUtc());
        return assetId;
    }


    private static String normalizeEtag(String etag) {
        return etag == null ? null : etag.replace("\"", "");
    }

    private String urlBuilder(String key) {
        return "%s/%s".formatted(
            cdn, key
        );
    }
}
