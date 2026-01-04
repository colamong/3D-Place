package com.colombus.snapshot.redis.service;

import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.snapshot.exception.DataCorruptionException;
import com.colombus.snapshot.exception.LockAcquisitionException;
import com.colombus.snapshot.exception.RedisOperationException;
import com.colombus.snapshot.redis.dto.CleanupResult;
import com.colombus.snapshot.redis.dto.DeltaCollectionResult;
import com.colombus.snapshot.util.ScriptLoader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.colombus.snapshot.exception.SnapshotErrorCode.*;

@Service
@RequiredArgsConstructor
public class RedisOperationService {

    private static final Logger log = LoggerFactory.getLogger(RedisOperationService.class);
    private static final int LOCK_WAIT_TIME = 0;
    private static final int LOCK_LEASE_TIME = 30;
    private static final String DELTAS_PREFIX = "deltas:";
    private static final String TOMBSTONE_PREFIX = "tombstone:";
    private static final String OPID_PATTERN = "op_ids:*";
    private static final String OPID_PREFIX = "op_ids:";
    private final int maxRetries = 3;

    private final RedissonLockService lockService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final RedisScript<List> cleanupScript = RedisScript.of(
            ScriptLoader.loadLuaScript("redis/clean_up_script.lua"),
            List.class
    );


    @Retryable(
            retryFor = {LockAcquisitionException.class, RedisOperationException.class},
            maxAttempts = 3,
            backoff = @Backoff(
                    delay = 100L,
                    multiplier = 2.0,
                    maxDelay = 5000L,
                    random = true
            )
    )
    public DeltaCollectionResult collectDeltasWithLock(String chunkKey, Instant batchStartTime) {
        double maxScore = (double) batchStartTime.toEpochMilli();
        RLock readLock = lockService.getLock(chunkKey);

        try {
            boolean acquired = readLock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("[{}] 읽기 락 획득 실패 - 재시도 예정", chunkKey);
                throw new LockAcquisitionException(FAILED_TO_ACQUIRE_LOCK, "락 획득 실패: " + chunkKey);
            }

            log.debug("[{}] 읽기 락 획득 성공", chunkKey);
            return collectDeltas(chunkKey, maxScore);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] 락 획득 중 인터럽트", chunkKey, e);
            throw new LockAcquisitionException(FAILED_TO_ACQUIRE_LOCK, "락 획득 중 인터럽트: " + chunkKey, e);
        } catch (LockAcquisitionException e) {
            throw e; // 재시도 대상
        } catch (DataCorruptionException e) {
            throw e; // Fast Fail, 재시도 안함
        } catch (RedisConnectionFailureException e) {
            log.error("[{}] Redis 연결 실패", chunkKey, e);
            throw new RedisOperationException(REDIS_CONNECTION_FAILED, "Redis 연결 실패: " + chunkKey, e);
        } catch (Exception e) {
            log.error("[{}] Delta 수집 중 예외", chunkKey, e);
            throw new RedisOperationException(REDIS_OPERATION_FAILED, "Delta 수집 실패: " + chunkKey, e);
        } finally {
            lockService.unlock(readLock);
        }
    }

    public Set<String> findChunkKeysForBatch() {
        return redisTemplate.keys(OPID_PATTERN);
    }

    public DeltaCollectionResult collectDeltas(String chunkKey, double maxScore) {
        try {
            // 처리 대상 op_id 조회
            Set<String> opIds = redisTemplate.opsForZSet()
                    .rangeByScore(chunkKey, Double.NEGATIVE_INFINITY, maxScore);

            if (opIds == null || opIds.isEmpty()) {
                return new DeltaCollectionResult(Map.of(), opIds, Set.of());
            }

            // Delta 데이터 조회 및 파싱
            String deltaKey = buildDeltaKey(chunkKey);
            Map<UUID, DeltaDTO> currentDeltas = opIds.stream()
                    .map(opId -> fetchAndParseDelta(deltaKey, opId, chunkKey))  // chunkKey 전달
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(DeltaDTO::opId, Function.identity()));

            // Tombstone 조회
            String tombKey = buildTombstoneKey(chunkKey);
            Set<String> tombstoneOpIds = redisTemplate.opsForZSet()
                    .rangeByScore(tombKey, Double.NEGATIVE_INFINITY, maxScore);

            if (tombstoneOpIds == null) {
                tombstoneOpIds = Set.of();
            }

            log.info("Delta 수집 완료. 현재: {}, Tombstone: {}",
                    currentDeltas.size(), tombstoneOpIds.size());

            return new DeltaCollectionResult(currentDeltas, opIds, tombstoneOpIds);

        } catch (RedisConnectionFailureException e) {
            log.error("[{}] Redis 연결 실패", chunkKey, e);
            throw new RedisOperationException(REDIS_CONNECTION_FAILED, "Redis 연결 실패: " + chunkKey, e);

        } catch (DataCorruptionException e) {
            throw e; // 이미 처리됨

        } catch (Exception e) {
            log.error("[{}] Delta 수집 중 예외", chunkKey, e);
            throw new RedisOperationException(REDIS_OPERATION_FAILED, "Delta 수집 실패: " + chunkKey, e);
        }
    }

    private DeltaDTO fetchAndParseDelta(String deltaKey, String opId, String chunkKey) {
        try {
            String deltaJson = (String) redisTemplate.opsForHash().get(deltaKey, opId);

            if (deltaJson == null) {
                log.warn("Delta 데이터 없음. opId: {}", opId);
                return null;
            }
            return objectMapper.readValue(deltaJson, DeltaDTO.class);
        } catch (JsonProcessingException e) {
            log.error("[snapshot] Delta JSON 파싱 실패. opId: {}", opId, e);
            backupCorruptedDelta(deltaKey, opId);
            throw new DataCorruptionException(
                    DELTA_PARSE_FAILED,
                    "Delta 파싱 실패. chunkKey: " + chunkKey + ", opId: " + opId,
                    e
            );
        } catch (RedisConnectionFailureException e) {
            throw new RedisOperationException(REDIS_CONNECTION_FAILED, "Redis 연결 실패: " + deltaKey, e);
        }
    }


    @Retryable(
            retryFor = LockAcquisitionException.class,
            maxAttempts = 3,
            backoff = @Backoff(
                    delay = 100L,
                    multiplier = 2.0,
                    maxDelay = 5000L,
                    random = true
            )
    )
    public void cleanupProcessedDataWithLock(String chunkKey, Set<String> opIds,
                                             Set<String> tombstoneOpIds, Instant batchStartTime) {
        double maxScore = (double) batchStartTime.toEpochMilli();
        RLock deleteLock = lockService.getLock(chunkKey);

        try {
            boolean acquired = deleteLock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("[{}] 삭제 락 획득 실패 - 재시도 예정", chunkKey);
                throw new LockAcquisitionException(FAILED_TO_ACQUIRE_LOCK);
            }

            log.debug("[{}] 삭제 락 획득 성공", chunkKey);
            cleanupProcessedData(chunkKey, opIds, tombstoneOpIds, maxScore);
            log.info("[{}] Redis 정리 완료", chunkKey);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] 정리 중 인터럽트", chunkKey, e);
            throw new LockAcquisitionException(FAILED_TO_ACQUIRE_LOCK, "락 획득 중 인터럽트: " + chunkKey, e);

        } catch (LockAcquisitionException e) {
            throw e; // 재시도 대상

        } catch (RedisConnectionFailureException e) {
            log.error("[{}] Redis 연결 실패", chunkKey, e);
            throw new RedisOperationException(REDIS_CONNECTION_FAILED, "Redis 연결 실패: " + chunkKey, e);

        } catch (Exception e) {
            log.error("[{}] Redis 정리 중 예외", chunkKey, e);
            throw new RedisOperationException(REDIS_OPERATION_FAILED, "Redis 정리 실패: " + chunkKey, e);

        } finally {
            lockService.unlock(deleteLock);
        }
    }

    public void cleanupProcessedData(String chunkKey, Set<String> opIds,
                                     Set<String> tombstoneOpIds, double maxScore) {
        try {
            log.debug("Redis 정리 시작. 청크: {}", chunkKey);
            int opSize = opIds.size();
            int tombSize = tombstoneOpIds.size();
            List<CleanupResult> results = new ArrayList<>();

            for(int attempt = 1; attempt <= maxRetries; attempt++) {
                CleanupResult result = executeCleanupLua(chunkKey, maxScore);
                results.add(result);

                boolean isCleaned = verifyClean(results, opSize, tombSize);

                if (isCleaned) {
                    log.info("chunkKey [{}] 정리 완료 ({}회 시도)", chunkKey, attempt);
                    return;
                }

                try {
                    Thread.sleep((long) (100L * Math.pow(2, attempt)));
                } catch (InterruptedException ignored) {}
            }

            long totalOp = results.stream().mapToLong(CleanupResult::opIdsRemoved).sum();
            long totalDelta = results.stream().mapToLong(CleanupResult::deltasRemoved).sum();
            long totalTomb = results.stream().mapToLong(CleanupResult::tombstonesRemoved).sum();

            log.warn("chunkKey [{}] 부분 정리. 총 시도: {}, opIds={}/{}, deltas={}/{}, tombstones={}/{}",
                    chunkKey, maxRetries, totalOp, opSize, totalDelta, opSize, totalTomb, tombSize);

        } catch (RedisConnectionFailureException e) {
            log.error("chunkKey [{}] Redis 연결 실패", chunkKey, e);
            throw new RedisOperationException(REDIS_CONNECTION_FAILED, "Redis 연결 실패: " + chunkKey, e);

        } catch (Exception e) {
            log.error("chunkKey [{}] 정리 실패", chunkKey, e);
            throw new RedisOperationException(REDIS_OPERATION_FAILED, "Redis 정리 실패: " + chunkKey, e);
        }
    }

    private CleanupResult executeCleanupLua(String chunkKey, double maxScore) {
        try {
            String opIdsKey = chunkKey;
            String deltaKey = buildDeltaKey(chunkKey);
            String tombKey = buildTombstoneKey(chunkKey);

            List<Long> results = redisTemplate.execute(
                    cleanupScript,
                    List.of(opIdsKey, deltaKey, tombKey),
                    String.valueOf(maxScore)
            );

            return new CleanupResult(
                    results.get(0),
                    results.get(1),
                    results.get(2)
            );
        } catch (RedisConnectionFailureException e) {
            throw new RedisOperationException(REDIS_CONNECTION_FAILED, "Lua 스크립트 실행 실패: " + chunkKey, e);
        } catch (Exception e) {
            throw new RedisOperationException(REDIS_OPERATION_FAILED, "Lua 스크립트 실행 실패: " + chunkKey, e);
        }
    }
    private boolean verifyClean(List<CleanupResult> results, int opidsSize, int tombSize) {
        long totalOpRemoved = 0;
        long totalDeltaRemoved = 0;
        long totalTombRemoved = 0;

        for (CleanupResult r : results) {
            totalOpRemoved += r.opIdsRemoved();
            totalDeltaRemoved += r.deltasRemoved();
            totalTombRemoved += r.tombstonesRemoved();
        }

        return totalOpRemoved == opidsSize
                && totalDeltaRemoved == opidsSize
                && totalTombRemoved == tombSize;
    }

    private String buildDeltaKey(String chunkKey) {
        return DELTAS_PREFIX + chunkKey.substring(OPID_PREFIX.length());
    }

    private String buildTombstoneKey(String chunkKey) {
        return TOMBSTONE_PREFIX + chunkKey.substring(OPID_PREFIX.length());
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