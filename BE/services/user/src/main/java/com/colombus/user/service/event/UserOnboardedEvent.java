package com.colombus.user.service.event;

import com.colombus.user.model.type.AuthProvider;
import jakarta.annotation.Nullable;
import java.util.UUID;

/**
 * 사용자 및 해당 ID가 트랜잭션에서 성공적으로 보장(생성 또는 업데이트)된 후 발행되는 이벤트.
 * 이 이벤트는 프로필 초기화, 상세 이벤트 로깅 등 비동기 후처리 작업을 트리거합니다.
 *
 * @param userId 사용자의 ID.
 * @param provider 이 세션에 사용된 인증 제공자.
 * @param didSignup 새 사용자 계정이 생성되었는지 여부.
 * @param didLink 기존 사용자에게 새 ID가 연결되었는지 여부.
 * @param didSync 사용자 정보(예: 이메일)가 백필되거나 승격되었는지 여부.
 * @param locale 닉네임 생성을 위한 원래 요청의 로케일.
 * @param ip 원래 요청의 IP 주소.
 * @param ua 원래 요청의 User-Agent.
 */
public record UserOnboardedEvent(
    UUID userId,
    AuthProvider provider,
    boolean didSignup,
    boolean didLink,
    boolean didSync,
    @Nullable String locale,
    @Nullable String avatarUrl,
    @Nullable String ip,
    @Nullable String ua
) {}
