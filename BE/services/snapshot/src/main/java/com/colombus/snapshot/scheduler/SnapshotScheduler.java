package com.colombus.snapshot.scheduler;

import com.colombus.snapshot.service.HighLODSnapshotProcessor;
import com.colombus.snapshot.service.LowLODSnapshotProcessor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EnableScheduling
public class SnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotScheduler.class);
    private final LowLODSnapshotProcessor sp;
    private final HighLODSnapshotProcessor hp;

    // TODO 1분당 1번 -> 고정시간, 누적 데이터 수 하이브리드 적용
     @Scheduled(fixedRate = 60000)
    public void processLowLODSnapshots() {
        log.info("=== LowLODSnapshots 배치 스케줄 시작 ===");
        try {
            sp.executeSnapshotBatch();
            log.info("=== LowLODSnapshots 배치 스케줄 완료 ===");
        } catch (Exception e) {
            log.error("=== LowLODSnapshots 배치 스케줄 실패 ===", e);
        }
    }

    // 10분 간격
    @Scheduled(fixedRate = 600000)
    public void processHighLODSnapshots() {
        log.info("=== HighLODSnapshots 배치 스케줄 시작 ===");
        try {
            hp.processAllLODLevels();
            log.info("=== HighLODSnapshots 배치 스케줄 완료 ===");
        } catch (Exception e) {
            log.error("=== HighLODSnapshots 배치 스케줄 실패 ===", e);
        }
    }
}
