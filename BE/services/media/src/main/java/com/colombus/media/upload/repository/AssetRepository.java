package com.colombus.media.upload.repository;

import static com.colombus.media.jooq.tables.ImageAsset.IMAGE_ASSET;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import com.colombus.media.subject.dto.AssetDto;
import com.colombus.media.upload.model.AssetUpsert;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AssetRepository {

    private final DSLContext dsl;

    public AssetDto registerAndReturnDto(
        String storageBucket, String storageKey, String publicUrl,
        String contentType, int width, int height,
        String rawSha256Hex, String contentSha256Hex, long sizeBytes
    ){
        UUID id = dsl.insertInto(IMAGE_ASSET)
            .set(IMAGE_ASSET.RAW_SHA256, rawSha256Hex)
            .set(IMAGE_ASSET.CONTENT_SHA256, contentSha256Hex)
            .set(IMAGE_ASSET.CONTENT_TYPE, contentType)
            .set(IMAGE_ASSET.WIDTH, width)
            .set(IMAGE_ASSET.HEIGHT, height)
            .set(IMAGE_ASSET.SIZE_BYTES, sizeBytes)
            .set(IMAGE_ASSET.STORAGE_BUCKET, storageBucket)
            .set(IMAGE_ASSET.STORAGE_KEY, storageKey)
            .set(IMAGE_ASSET.PUBLIC_URL, publicUrl)
            .onConflict(IMAGE_ASSET.RAW_SHA256)
            .doNothing()
            .returning(IMAGE_ASSET.ID)
            .fetchOne(IMAGE_ASSET.ID);

        if (id == null) {
            id = dsl.select(IMAGE_ASSET.ID)
                    .from(IMAGE_ASSET)
                    .where(IMAGE_ASSET.RAW_SHA256.eq(rawSha256Hex))
                    .fetchOne(IMAGE_ASSET.ID);
        }

        return findByIdOrThrow(id);

    }

    public AssetDto findByIdOrThrow(UUID assetId) {
        var r = dsl.selectFrom(IMAGE_ASSET)
            .where(IMAGE_ASSET.ID.eq(assetId))
            .fetchOne();
        if (r == null) throw new IllegalStateException("image_asset not found: " + assetId);

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

    /** sha256 UNIQUE 기준으로 upsert하고, 최종 asset UUID를 반환 */
    public UUID upsertAndGetId(AssetUpsert u) {
        return dsl.insertInto(IMAGE_ASSET)
                .set(IMAGE_ASSET.RAW_SHA256, u.rawSha256())
                .set(IMAGE_ASSET.CONTENT_SHA256, u.contentSha256())
                .set(IMAGE_ASSET.CONTENT_TYPE, u.contentType())
                .set(IMAGE_ASSET.SIZE_BYTES, u.size())
                .set(IMAGE_ASSET.WIDTH, u.width())
                .set(IMAGE_ASSET.HEIGHT, u.height())
                .set(IMAGE_ASSET.STORAGE_BUCKET, u.storageBucket())
                .set(IMAGE_ASSET.STORAGE_KEY, u.storageKey())
                .set(IMAGE_ASSET.PUBLIC_URL, u.publicUrl())
                .onConflict(IMAGE_ASSET.RAW_SHA256)
                .doUpdate()
                .set(IMAGE_ASSET.RAW_SHA256, DSL.excluded(IMAGE_ASSET.RAW_SHA256))
                .set(IMAGE_ASSET.CONTENT_SHA256, DSL.excluded(IMAGE_ASSET.CONTENT_SHA256))
                .set(IMAGE_ASSET.CONTENT_TYPE, DSL.excluded(IMAGE_ASSET.CONTENT_TYPE))
                .set(IMAGE_ASSET.SIZE_BYTES, DSL.excluded(IMAGE_ASSET.SIZE_BYTES))
                .set(IMAGE_ASSET.WIDTH, DSL.excluded(IMAGE_ASSET.WIDTH))
                .set(IMAGE_ASSET.HEIGHT, DSL.excluded(IMAGE_ASSET.HEIGHT))
                .set(IMAGE_ASSET.STORAGE_BUCKET, DSL.excluded(IMAGE_ASSET.STORAGE_BUCKET))
                .set(IMAGE_ASSET.STORAGE_KEY, DSL.excluded(IMAGE_ASSET.STORAGE_KEY))
                .set(IMAGE_ASSET.PUBLIC_URL, DSL.excluded(IMAGE_ASSET.PUBLIC_URL))
                .returning(IMAGE_ASSET.ID)
                .fetchOne(IMAGE_ASSET.ID);
    }
}