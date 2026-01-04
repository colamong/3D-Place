package com.colombus.world.service;

import com.colombus.common.domain.dto.ChunkInfo;
import com.colombus.common.domain.dto.DeltaDTO;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String DELTAS_KEY_TEMPLATE = "deltas:{world:%s}:l%d:tx%d:ty%d:x%d:y%d:z%d";
    private static final String TOMBSTONES_KEY_TEMPLATE = "tombstones:{world:%s}:l%d:tx%d:ty%d:x%d:y%d:z%d";

    public Optional<List<DeltaDTO>> getDeltas(String world, int lod, ChunkInfo chunkInfo) {

        String key = getKey(DELTAS_KEY_TEMPLATE, world, lod, chunkInfo);
        Map<Object, Object> hashValues = redisTemplate.opsForHash().entries(key);

        if (hashValues == null || hashValues.isEmpty()) {
            log.debug("Redis에 해당 키에 대한 데이터가 없습니다: {}", key);
            return Optional.empty();
        }

        List<DeltaDTO> deltas = hashValues.entrySet().stream()
                .map(entry -> {
                    String opId = String.valueOf(entry.getKey());
                    String value = (String) entry.getValue();
                    try {
                        return Optional.of(objectMapper.readValue(value, DeltaDTO.class));
                    } catch (JsonProcessingException e) {
                        log.error("[snapshot] Delta JSON 파싱 실패. key: {}, opId: {}", key, opId, e);
                        backupCorruptedDelta(key, opId);
                        return Optional.<DeltaDTO>empty();
                    }
                })
                .flatMap(Optional::stream)
                .toList();

        if (deltas.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(deltas);
    }

    public Optional<Set<UUID>> getTombstones(String world, int lod, ChunkInfo chunkInfo) {

        String key = getKey(TOMBSTONES_KEY_TEMPLATE, world, lod, chunkInfo);
        Set<String> hashValues = redisTemplate.opsForZSet().range(key, 0, -1);

        return Optional.ofNullable(hashValues)
                .filter(set -> !set.isEmpty())
                .map(set -> set.stream()
                        .map(UUID::fromString)
                        .collect(Collectors.toSet())
                );
    }

    private String getKey(String template, String world, int lod, ChunkInfo chunkInfo) {
        return String.format(template,
                world,
                lod,
                chunkInfo.tx(),
                chunkInfo.ty(),
                chunkInfo.x(),
                chunkInfo.y(),
                chunkInfo.z());
    }

    private void backupCorruptedDelta(String deltaKey, String opId) {
        // TODO: DLQ 적용, 현재는 실패데이터 redis에 'error:delta:' 형식으로
        try {
            String corruptedJson = (String) redisTemplate.opsForHash().get(deltaKey, opId);
            if (corruptedJson != null) {
                redisTemplate.opsForHash().put("error:delta:" + deltaKey, opId, corruptedJson);
            }
        } catch (Exception ex) {
            log.error("손상된 Delta 백업 실패. deltaKey: {}, opId: {}", deltaKey, opId, ex);
        }
    }
}
