package com.colombus.world.service;

import com.colombus.common.domain.dto.ChunkInfo;
import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.common.domain.dto.EraseResponse;
import com.colombus.common.domain.dto.PaintResponse;
import com.colombus.common.web.core.exception.ServiceUnavailableException;
import com.colombus.world.dto.response.UrlResponse;
import com.colombus.world.exception.S3ObjectNotFoundException;
import com.colombus.world.exception.WorldErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorldService {

    private final S3Service s3Service;
    private final RedisService redisService;

    @Cacheable(
            value = "chunkUrls",
            key = "#world + ':' + #lod + ':' + #chunkId",
            unless = "#result == null || (#result.snapshotUrl() == null && #result.glbUrl() == null)"
    )
    public UrlResponse get(String world, int lod, String chunkId) {
        ChunkInfo chunkInfo = ChunkInfo.fromKey(world, lod, chunkId);

        String snapshotUrl = null;
        String glbUrl = null;

        // 실제 에러인지 판단하기 위한 변수
        boolean snapshotServiceError = false;
        boolean glbServiceError = false;

        // Snapshot URL
        try {
            snapshotUrl = s3Service.getSnapshotUrl(
                    world, lod, chunkInfo.tx(), chunkInfo.ty(),
                    chunkInfo.x(), chunkInfo.y(), chunkInfo.z()
            );

        } catch (Exception e) {
            log.error("스냅샷 URL 생성 중. 청크 ID: {}, 오류: {}", chunkId, e.getMessage());
            snapshotServiceError = true;
        }

        // GLB URL
        try {
            glbUrl = s3Service.getGLBUrl(
                    world, lod, chunkInfo.tx(), chunkInfo.ty(),
                    chunkInfo.x(), chunkInfo.y(), chunkInfo.z()
            );

        } catch (Exception e) {
            log.error("GLB URL 생성 중. 청크 ID: {}, 오류: {}", chunkId, e.getMessage());
            glbServiceError = true;
        }

        // 둘 다 서비스 오류인 경우 -> S3 장애
        if (snapshotServiceError && glbServiceError) {
            log.error("스냅샷과 GLB URL 생성 실패. 청크 ID: {}", chunkId);
            throw new ServiceUnavailableException(
                    WorldErrorCode.S3_URL_GENERATION_FAILED,
                    "S3 서비스 장애"
            );
        }

        // 둘 다 null -> 실제 파일 없음
        if (snapshotUrl == null && glbUrl == null && !snapshotServiceError && !glbServiceError) {
            log.info("스냅샷과 GLB 파일이 모두 존재하지 않음. 청크 ID: {}", chunkId);}

        if (snapshotUrl == null) {
            log.info("스냅샷 없이 GLB만 반환. 청크 ID: {}", chunkId);
        }
        if (glbUrl == null) {
            log.info("GLB 없이 스냅샷만 반환. 청크 ID: {}", chunkId);
        }

        List<DeltaDTO> deltaDTOs = redisService.getDeltas(world, lod, chunkInfo).orElse(List.of());
        Set<UUID> tombstones = redisService.getTombstones(world, lod, chunkInfo).orElse(Set.of());

        Set<UUID> deltaSet = deltaDTOs.stream()
                .map(DeltaDTO::opId)
                .collect(Collectors.toSet());

        List<PaintResponse> paintResponses = deltaDTOs.stream()
                .filter(delta -> !tombstones.contains(delta.opId()))
                .map(delta -> PaintResponse.fromDelta(delta, chunkInfo))
                .toList();

        List<EraseResponse> eraseResponses = tombstones.stream()
                .filter(t -> !deltaSet.contains(t))
                .map(EraseResponse::new)
                .toList();

        return new UrlResponse(snapshotUrl, glbUrl, paintResponses, eraseResponses);
    }
}
