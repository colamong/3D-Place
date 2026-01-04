package com.colombus.media.upload.repository;

import static com.colombus.media.jooq.tables.ImageAsset.IMAGE_ASSET;
import static com.colombus.media.jooq.tables.AssetLink.ASSET_LINK;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import com.colombus.common.kafka.subject.model.type.AssetPurpose;
import com.colombus.common.kafka.subject.model.type.SubjectKind;
import com.colombus.common.utility.time.TimeConv;
import com.colombus.media.subject.dto.AssetDto;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AssetLinkRepository {
    
    private final DSLContext dsl;

    /** 현재 활성 링크를 AssetDto로 조회 */
    public Optional<AssetDto> findActiveDto(SubjectKind kind, UUID subjectId, AssetPurpose purpose) {
        return dsl.select(IMAGE_ASSET.ID, IMAGE_ASSET.PUBLIC_URL, IMAGE_ASSET.WIDTH, IMAGE_ASSET.HEIGHT,
                          IMAGE_ASSET.CONTENT_TYPE, IMAGE_ASSET.RAW_SHA256, IMAGE_ASSET.CONTENT_SHA256)
            .from(ASSET_LINK.join(IMAGE_ASSET).on(ASSET_LINK.ASSET_ID.eq(IMAGE_ASSET.ID)))
            .where(ASSET_LINK.SUBJECT_KIND.eq(kind)
                .and(ASSET_LINK.SUBJECT_ID.eq(subjectId))
                .and(ASSET_LINK.PURPOSE.eq(purpose))
                .and(ASSET_LINK.DELETED_AT.isNull()))
            .fetchOptional(r -> new AssetDto(
                r.get(IMAGE_ASSET.ID),
                r.get(IMAGE_ASSET.PUBLIC_URL),
                r.get(IMAGE_ASSET.WIDTH),
                r.get(IMAGE_ASSET.HEIGHT),
                r.get(IMAGE_ASSET.CONTENT_TYPE),
                r.get(IMAGE_ASSET.RAW_SHA256),
                r.get(IMAGE_ASSET.CONTENT_SHA256)
            ));
    }

    /** 기존 활성 링크 소프트삭제 */
    public int softDeleteActive(SubjectKind kind, UUID subjectId, AssetPurpose purpose, Instant deletedAt) {
        return dsl.update(ASSET_LINK)
            .set(ASSET_LINK.DELETED_AT, TimeConv.toUtcOdt(deletedAt))
            .where(ASSET_LINK.SUBJECT_KIND.eq(kind)
                .and(ASSET_LINK.SUBJECT_ID.eq(subjectId))
                .and(ASSET_LINK.PURPOSE.eq(purpose))
                .and(ASSET_LINK.DELETED_AT.isNull()))
            .execute();
    }

    /** 새 링크 생성 후 해당 AssetDto 반환 */
    public AssetDto insertAndFetchDto(SubjectKind kind, UUID subjectId, AssetPurpose purpose, UUID assetId) {
        dsl.insertInto(ASSET_LINK)
            .set(ASSET_LINK.SUBJECT_KIND, SubjectKind.valueOf(kind.name()))
            .set(ASSET_LINK.SUBJECT_ID, subjectId)
            .set(ASSET_LINK.PURPOSE, AssetPurpose.valueOf(purpose.name()))
            .set(ASSET_LINK.ASSET_ID, assetId)
            .returning(ASSET_LINK.ID)
            .fetchOne(ASSET_LINK.ID);

        // 막 연결한 링크에 매핑된 이미지를 읽어서 DTO로
        var r = dsl.selectFrom(IMAGE_ASSET)
            .where(IMAGE_ASSET.ID.eq(assetId))
            .fetchOne();

        return new AssetDto(
            r.getId(),
            r.getPublicUrl(),
            r.getWidth(),
            r.getHeight(),
            r.getContentType(),
            r.getRawSha256(),
            r.getContentSha256()
        );
    }

    /** 교체 (Service에서 @Transactional 권장) */
    public AssetDto replaceReturningDto(SubjectKind kind, UUID subjectId, AssetPurpose purpose, UUID newAssetId, Instant now) {
        softDeleteActive(kind, subjectId, purpose, now);
        return insertAndFetchDto(kind, subjectId, purpose, newAssetId);
    }
}
