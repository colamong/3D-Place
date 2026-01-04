package com.colombus.snapshot.chunk.service;

import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.common.domain.dto.ChunkInfo;
import com.colombus.snapshot.exception.DataCorruptionException;
import com.colombus.snapshot.s3.service.S3StorageService;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.colombus.snapshot.exception.SnapshotErrorCode.SNAPSHOT_PARSE_FAILED;

@Service
@RequiredArgsConstructor
public class ChunkMergeService {

    private static final Logger log = LoggerFactory.getLogger(ChunkMergeService.class);

    private final S3StorageService s3Storage;
    private final ObjectMapper objectMapper;

    public List<DeltaDTO> mergeChunk(ChunkInfo chunkInfo,
                                        Map<UUID, DeltaDTO> currentDeltas,
                                        Set<String> tombstoneOpIds,
                                        int curVersion) {
        Map<UUID, DeltaDTO> mergedMap = loadLatestSnapshot(chunkInfo, curVersion);

        mergedMap.putAll(currentDeltas);
        log.info("Delta 병합 후 크기: {}", mergedMap.size());

        if (tombstoneOpIds != null && !tombstoneOpIds.isEmpty()) {
            Set<UUID> tombstoneUuids = tombstoneOpIds.stream()
                    .map(opIdStr -> {
                        try {
                            return UUID.fromString(opIdStr);
                        } catch (IllegalArgumentException e) {
                            log.warn("잘못된 UUID: {}", opIdStr);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            tombstoneUuids.forEach(mergedMap::remove);
            log.info("Tombstone 적용 완료. 제거된 수: {} (전체: {})",
                    tombstoneUuids.size(), tombstoneOpIds.size());
        }

        return new ArrayList<>(mergedMap.values());
    }

    private Map<UUID, DeltaDTO> loadLatestSnapshot(ChunkInfo chunkInfo, int curVersion) {
            Optional<String> snapshotOpt = s3Storage.getLatestSnapshot(chunkInfo, curVersion);

            if (snapshotOpt.isEmpty()) {
                log.info("신규 스냅샷 생성. 청크: {}, 버전: {}", chunkInfo, curVersion);
                return new HashMap<>();
            }

            String snapshotJson = snapshotOpt.get();
            String chunkKey = ChunkInfo.toChunkKey(chunkInfo);
            Map<UUID, DeltaDTO> snapMap = parseSnapshot(snapshotJson, chunkKey, curVersion);

            log.info("스냅샷 로드 완료. Delta 수: {}, 청크: {}", snapMap.size(), chunkInfo);
            return snapMap;
    }

    private Map<UUID, DeltaDTO> parseSnapshot(String snapshotJson, String chunkKey, int version) {
        Map<UUID, DeltaDTO> snapMap = new HashMap<>();

        try (JsonParser parser = objectMapper.getFactory().createParser(snapshotJson)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                log.error("스냅샷 형식 오류 (배열 아님). chunkKey: {}, version: {}", chunkKey, version);
                throw new DataCorruptionException(
                        SNAPSHOT_PARSE_FAILED,
                        "스냅샷 형식 오류 (배열 아님): " + chunkKey + ", version: " + version
                );
            }

            while (parser.nextToken() == JsonToken.START_OBJECT) {
                try {
                    DeltaDTO delta = parser.readValueAs(DeltaDTO.class);
                    snapMap.put(delta.opId(), delta);
                } catch (JsonProcessingException e) {
                    log.error("개별 Delta 파싱 실패. chunkKey: {}, version: {}", chunkKey, version, e);
                    throw new DataCorruptionException(
                            SNAPSHOT_PARSE_FAILED,
                            "개별 Delta 파싱 실패: " + chunkKey + ", version: " + version,
                            e
                    );
                }
            }

        } catch (JsonParseException e) {
            log.error("스냅샷 JSON 파싱 실패. chunkKey: {}, version: {}", chunkKey, version, e);
            throw new DataCorruptionException(
                    SNAPSHOT_PARSE_FAILED,
                    "스냅샷 JSON 파싱 실패: " + chunkKey + ", version: " + version,
                    e
            );

        } catch (IOException e) {
            log.error("스냅샷 읽기 중 IO 실패. chunkKey: {}, version: {}", chunkKey, version, e);
            throw new DataCorruptionException(
                    SNAPSHOT_PARSE_FAILED,
                    "스냅샷 읽기 중 IO 실패: " + chunkKey + ", version: " + version,
                    e
            );
        }
        return snapMap;
    }
}
