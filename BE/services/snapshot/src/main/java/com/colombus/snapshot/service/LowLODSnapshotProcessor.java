package com.colombus.snapshot.service;

import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.snapshot.chunk.dto.ChunkProcessResult;
import com.colombus.snapshot.chunk.service.ChunkProcessorService;
import com.colombus.snapshot.exception.*;
import com.colombus.snapshot.redis.service.RedisOperationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class LowLODSnapshotProcessor {

    private static final Logger log = LoggerFactory.getLogger(LowLODSnapshotProcessor.class);

    private final RedisOperationService redisService;
    private final ChunkProcessorService  chunkProcessor;

    public void executeSnapshotBatch() {
        Instant batchStartTime = Instant.now();
        log.info("스냅샷 배치 시작. 시간: {}", batchStartTime);

        Set<String> chunkKeys = redisService.findChunkKeysForBatch();
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            log.info("처리할 청크 없음");
            return;
        }

        log.info("처리 대상 청크 수: {}", chunkKeys.size());

        List<CompletableFuture<ChunkProcessResult>> futures = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            /**
                 chunkKey : {world:”worldname”}:l0:tx1:ty2:x-123:y123:z123
                 전체 맵 기준 타일의 개수: 512×512 = 262,144개
                 한 타일 당 약 16개의 청크(4×4)로 구성
                 하나의 청크는 xyz 각각 256개의 voxel로 구성
                 2 4 8 16
            */
            for (String chunkKey : chunkKeys) {
                CompletableFuture<ChunkProcessResult> future = CompletableFuture.supplyAsync(
                        () -> chunkProcessor.processChunk(chunkKey, batchStartTime),
                        executor
                ).exceptionally(ex -> handleChunkException(chunkKey, ex));
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            /// 결과 확인용 코드
            List<ChunkProcessResult> results = futures.stream()
                    .map(f -> f.getNow(null))
                    .filter(Objects::nonNull)
                    .toList();

            long successCount = results.stream().filter(ChunkProcessResult::success).count();
            long failedCount = results.stream().filter(r -> !r.success()).count();
            long skippedCount = chunkKeys.size() - results.size();

            log.info("스냅샷 배치 완료. 성공: {}, 실패: {}, 스킵: {}, 전체: {}",
                    successCount, failedCount, skippedCount, chunkKeys.size());
            ///  결과 확인용 코드
        } catch (Exception e) {
            log.error("스냅샷 배치 처리 중 오류 발생", e);
        }
    }
    private ChunkProcessResult handleChunkException(String chunkKey, Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

        // BusinessException 처리 (DLQ 대상)
        if (cause instanceof BusinessException be) {
            return handleBusinessException(chunkKey, be);
        }

        // RuntimeException 처리
        if (cause instanceof RuntimeException re) {
            log.error("[{}] 런타임 예외로 청크 처리 실패", chunkKey, re);
            // TODO: DLQ 전송
            return ChunkProcessResult.failure(chunkKey, "런타임 예외: " + re.getMessage());
        }

        // 기타 예외
        log.error("[{}] 예상치 못한 예외로 청크 처리 실패", chunkKey, cause);
        return ChunkProcessResult.failure(chunkKey, "예상치 못한 예외: " + cause.getMessage());
    }

    private ChunkProcessResult handleBusinessException(String chunkKey, BusinessException be) {
        String errorCode = be.getCode();
        String errorMessage = be.getMessage();

        if (be instanceof DataCorruptionException) {
            log.error("[{}] 데이터 손상 - DLQ 전송. 에러코드: {}",
                    chunkKey, errorCode);
            // TODO: DLQ 로직 추가
            return ChunkProcessResult.failure(chunkKey, "데이터 손상: " + errorMessage);
        }

        if (be instanceof DatabaseOperationException) {
            log.error("[{}] DB 작업 실패 - DLQ 전송. 에러코드: {}",
                    chunkKey, errorCode);
            // TODO: DLQ 로직 추가
            return ChunkProcessResult.failure(chunkKey, "DB 작업 실패: " + errorMessage);
        }

        // 재시도 실패 예외들
        if (be instanceof LockAcquisitionException) {
            log.error("[{}] 락 획득 재시도 실패 - DLQ 전송. 에러코드: {}",
                    chunkKey, errorCode);
            // TODO: DLQ 로직 추가
            return ChunkProcessResult.failure(chunkKey, "락 획득 실패: " + errorMessage);
        }

        if (be instanceof RedisOperationException) {
            log.error("[{}] Redis 작업 재시도 실패 - DLQ 전송. 에러코드: {}",
                    chunkKey, errorCode);
            // TODO: DLQ 로직 추가
            return ChunkProcessResult.failure(chunkKey, "Redis 작업 실패: " + errorMessage);
        }

        if (be instanceof StorageException) {
            log.error("[{}] Storage 작업 실패 - DLQ 전송. 에러코드: {}",
                    chunkKey, errorCode);
            // TODO: DLQ 로직 추가
            return ChunkProcessResult.failure(chunkKey, "Storage 작업 실패: " + errorMessage);
        }

        // 기타 BusinessException
        log.error("[{}] 비즈니스 예외 - DLQ 전송. 에러코드: {}",
                chunkKey, errorCode);
        // TODO: DLQ 로직 추가
        return ChunkProcessResult.failure(chunkKey, errorMessage);
    }
}
