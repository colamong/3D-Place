package com.colombus.auth.infra.session;

import java.time.Duration;
import java.util.UUID;

import reactor.core.publisher.Mono;

public interface LinkSessionStore {

    /** state를 키로 1회성 세션 발급 */
    Mono<LinkSession> issue(UUID userId, String provider, String redirectUri,
                      Duration ttl, String ipHash, String uaHash);

    /** state로 세션 1회 소모(get & delete). 없거나 만료면 empty */
    Mono<LinkSession> consume(String state);

    /** 필요 시 조회(디버깅 등). 소비는 하지 않음 */
    Mono<LinkSession> peek(String state);

    /** 특정 userId와 관련된 모든 LinkSession을 삭제 */
    Mono<Void> deleteAllByUserId(UUID userId);
}
