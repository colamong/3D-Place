package com.colombus.clan.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colombus.clan.exception.ClanErrorCode;
import com.colombus.clan.model.ClanDetail;
import com.colombus.clan.model.ClanMemberDetail;
import com.colombus.clan.model.ClanSummary;
import com.colombus.clan.model.type.ClanJoinPolicy;
import com.colombus.clan.model.type.ClanMemberRole;
import com.colombus.clan.model.type.ClanMemberStatus;
import com.colombus.clan.repository.ClanMemberQueryRepository;
import com.colombus.clan.repository.ClanQueryRepository;
import com.colombus.common.web.core.exception.InvalidInputException;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 클랜 조회/검색 비즈니스 로직.
 * <p>
 * - 클랜 기본 정보, 멤버, 공개 클랜 검색, 가입 요청 조회 등의 읽기 전용 기능 제공.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanInfoService {
    
    private final ClanQueryRepository clanQueryRepo;
    private final ClanMemberQueryRepository clanMemberQueryRepo;

    // ======================
    // 클랜 기본 정보 조회
    // ======================

    /**
     * 클랜 상세 정보 조회.
     *
     * - clanId: 조회할 클랜 ID
     */
    public Mono<ClanDetail> getClanDetail(UUID clanId) {
        return clanQueryRepo.requireClanDetail(clanId);
    }

    /**
     * 내가 속한 단일 클랜 조회.
     * <p>
     * 한 유저는 동시에 하나의 클랜에만 가입할 수 있다는 전제를 가정.
     * 가입한 클랜이 없으면 empty Mono 반환.
     *
     * - userId: 자신의 클랜을 조회할 유저 ID
     */
    public Mono<ClanSummary> findMyClan(UUID userId) {
        return clanQueryRepo.findClanByMember(userId);
    }

    /**
     * 특정 클랜의 활성 멤버 목록 조회.
     *
     * - clanId: 멤버 목록을 조회할 클랜 ID
     */
    public Flux<ClanMemberDetail> getClanMembers(UUID clanId) {
        return getClanMembers(clanId, ClanMemberStatus.ACTIVE);
    }

    public Flux<ClanMemberDetail> getClanMembers(UUID clanId, @Nullable ClanMemberStatus status) {
        return clanMemberQueryRepo.findMembersInClan(clanId, status);
    }

    public Flux<ClanMemberDetail> getClanMembers(UUID clanId, UUID actorId, @Nullable ClanMemberStatus status) {
        return ensureMember(clanId, actorId)
            .thenMany(clanMemberQueryRepo.findMembersInClan(clanId, status));
    }

    public Mono<ClanMemberDetail> getActiveMembership(UUID userId) {
        return clanMemberQueryRepo.findActiveMemberByUser(userId);
    }

    // ======================
    // 공개 클랜 검색
    // ======================

    /**
     * 공개 클랜 검색 (이름 / 핸들 기준).
     * <p>
     * - is_public = true 인 클랜만 대상.
     * - 정책/키워드/페이징 조건에 따라 필터링.
     *
     * - policies: 포함할 가입 정책 목록 (null/빈 경우 정책 필터 없음)
     * - keyword: 이름/핸들 등에 대한 검색 키워드 (null 허용)
     * - page: 페이지 번호(0-base)
     * - size: 페이지 크기
     */
    public Flux<ClanSummary> searchPublicClans(
        @Nullable List<ClanJoinPolicy> policies,
        @Nullable String keyword,
        int page,
        int size
    ) {
        int offset = page * size;
        return clanQueryRepo.findPublicClans(policies, keyword, size, offset);
    }

    // ======================
    // 가입 요청 조회
    // ======================

    /**
     * 특정 클랜의 가입 대기(PENDING) 중인 유저 ID 목록 조회.
     * <p>
     * 조회자는 해당 클랜의 MASTER 또는 OFFICER 여야 한다.
     * 페이지네이션을 위해 offset 계산 후, 부분 목록만 반환한다.
     *
     * - clanId: 가입 요청 목록을 조회할 클랜 ID
     * - actorId: 조회 요청자(권한 검증 대상 유저 ID)
     * - page: 페이지 번호(0-base)
     * - size: 페이지 크기
     */
    public Flux<UUID> getPendingJoinUserIds(
        UUID clanId,
        UUID actorId,
        int page,
        int size
    ) {
        int offset = page * size;

        return ensureCanManageClan(clanId, actorId)
            .thenMany(clanMemberQueryRepo.findPendingJoinRequestsByClan(clanId, size, offset));
    }

    /**
     * 현재 유저의 클랜 가입 요청(PENDING) 상태가 있는 클랜 ID 목록 조회.
     * <p>
     * 주어진 클랜 ID 목록 중, actorId 가 가입 대기 중인(PENDING) 클랜 ID만 필터링해서 반환한다.
     *
     * - actorId: 본인(가입 신청자) 유저 ID
     * - clanIds: 가입 요청 상태를 확인할 클랜 ID 목록
     */
    public Flux<UUID> getMyPendingJoinRequests(
        UUID actorId,
        List<UUID> clanIds
    ) {
        return clanMemberQueryRepo.findMyPendingJoinRequests(actorId, clanIds);
    }

    /**
     * 다수의 클랜을 한 번에 요약 조회한다.
     */
    public Flux<ClanSummary> getClansByIds(List<UUID> clanIds) {
        if (clanIds == null || clanIds.isEmpty()) {
            return Flux.empty();
        }
        return clanQueryRepo.findClansByIds(clanIds);
    }

    // ======================
    // 권한 검증 (내부 헬퍼)
    // ======================

    /**
     * 주어진 유저가 해당 클랜을 관리할 수 있는지 확인.
     * <p>
     * - 클랜 내 활성 멤버인지 확인.
     * - 멤버 역할이 MASTER 또는 OFFICER 인지 검증.
     * 조건을 만족하지 못하면 {@link InvalidInputException} 발생.
     *
     * - clanId: 권한을 확인할 클랜 ID
     * - actorId: 권한 검증 대상 유저 ID
     */
    private Mono<Void> ensureCanManageClan(UUID clanId, UUID actorId) {
        return clanMemberQueryRepo.findActiveByClanAndUser(clanId, actorId)
            .switchIfEmpty(Mono.error(
                new InvalidInputException(ClanErrorCode.CLAN_ACCESS_DENIED)
            ))
            .flatMap(member -> {
                ClanMemberRole role = member.role();

                if (role == ClanMemberRole.MASTER || role == ClanMemberRole.OFFICER) {
                    return Mono.empty(); // 통과
                }

                return Mono.error(new InvalidInputException(ClanErrorCode.CLAN_ACCESS_DENIED));
            });
    }

    private Mono<ClanMemberDetail> ensureMember(UUID clanId, UUID actorId) {
        return clanMemberQueryRepo.findActiveByClanAndUser(clanId, actorId)
            .switchIfEmpty(Mono.error(
                new InvalidInputException(ClanErrorCode.CLAN_MEMBER_NOT_FOUND)
            ));
    }
}
