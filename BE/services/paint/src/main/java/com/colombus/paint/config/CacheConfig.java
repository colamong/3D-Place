package com.colombus.paint.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.colombus.paint.dto.FailedPaintOperation;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caffeine 로컬 캐시 설정
 * - 실패한 Paint/Erase 작업 임시 저장 → 재시도
 */
@Configuration
public class CacheConfig {

	@Bean
	public Cache<String, FailedPaintOperation> failedPaintOperationCache() {
		return Caffeine.newBuilder()
			.maximumSize(10_000)					// 최대 1만개 저장
			.expireAfterWrite(Duration.ofHours(1))	// 1시간 후 자동 만료
			.build();
	}
}
