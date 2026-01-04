package com.colombus.clan.web.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.colombus.clan.contract.dto.ClanDetailResponse;
import com.colombus.clan.contract.dto.ClanMemberDetailResponse;
import com.colombus.clan.contract.dto.ClanSummaryBulkRequest;
import com.colombus.clan.contract.dto.ClanSummaryResponse;
import com.colombus.clan.contract.dto.MyClanResponse;
import com.colombus.clan.contract.enums.ClanMemberRoleCode;
import com.colombus.clan.contract.enums.ClanMemberStatusCode;
import com.colombus.clan.model.type.ClanJoinPolicy;
import com.colombus.clan.model.type.ClanMemberStatus;
import com.colombus.clan.service.ClanInfoService;
import com.colombus.clan.web.internal.mapper.ClanContractMapper;
import com.colombus.clan.web.internal.mapper.ClanTypeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 클랜 정보 조회/검색용 internal API.
 * <p>
 * - BFF 및 기타 내부 서비스에서만 사용.
 * - 클랜 상세/멤버/가입 요청/공개 목록 조회 등을 제공.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/clans")
public class ClanQueryController {
    
    private final ClanInfoService clanInfoService;

    // ======================
    // 클랜 기본 조회
    // ======================

    /**
     * 클랜 상세 조회.
     *
     * - path.clanId: 조회할 클랜 ID
     */
    @GetMapping("/{clanId}")
    public Mono<ClanDetailResponse> findClan(@PathVariable UUID clanId) {
        return clanInfoService.getClanDetail(clanId)
            .map(ClanContractMapper::toResponse);
    }

    /**
     * 클랜 멤버 목록 조회.
     *
     * - path.clanId: 멤버 목록을 조회할 클랜 ID
     */
    @GetMapping("/{clanId}/members")
    public Flux<ClanMemberDetailResponse> findMembers(
        @PathVariable UUID clanId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId,
        @RequestParam(name = "status", required = false) ClanMemberStatusCode statusCode
    ) {
        ClanMemberStatus status = statusCode != null
            ? ClanTypeMapper.fromCode(statusCode)
            : ClanMemberStatus.ACTIVE;
        return clanInfoService.getClanMembers(clanId, actorId, status)
            .map(ClanContractMapper::toResponse);
    }

    /**
     * 여러 클랜의 요약 정보를 한 번에 반환.
     */
    @PostMapping("/bulk-summary")
    public Mono<List<ClanSummaryResponse>> getClanSummaries(
        @RequestBody ClanSummaryBulkRequest request
    ) {
        List<UUID> clanIds = request.clanIds();
        if (clanIds == null || clanIds.isEmpty()) {
            return Mono.just(List.of());
        }
        return clanInfoService.getClansByIds(clanIds)
            .map(ClanContractMapper::toResponse)
            .collectList();
    }

    /**
     * 특정 유저가 속한 단일 클랜 조회.
     * <p>
     * 한 유저는 동시에 하나의 클랜에만 속한다는 전제를 가정.
     * 가입한 클랜이 없으면 204(No Content)를 반환.
     *
     * - path.userId: 클랜 소속을 조회할 유저 ID
     */
    @GetMapping("/member/{userId}")
    public Mono<MyClanResponse> findClanByMember(@PathVariable UUID userId) {
        Mono<ClanSummaryResponse> clanMono = clanInfoService.findMyClan(userId)
            .map(ClanContractMapper::toResponse);
        Mono<ClanMemberRoleCode> roleMono = clanInfoService.getActiveMembership(userId)
            .map(detail -> ClanTypeMapper.toCode(detail.role()));

        return clanMono.zipWith(roleMono, MyClanResponse::new)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NO_CONTENT)));
    }

    // ======================
    // 공개 클랜 검색
    // ======================

    /**
     * 공개 클랜 목록 조회.
     * <p>
     * - is_public = true 인 클랜 중에서, INVITE_ONLY 가 아닌 클랜만 대상.
     * - 정책/키워드/페이징에 따라 필터링.
     *
     * - query.policies[]: 포함할 가입 정책 목록 (null/빈 목록이면 정책 필터 없음)
     * - query.keyword: 클랜 이름/설명 등에서 검색할 키워드 (선택)
     * - query.page: 페이지 번호(0-base, 기본값 0)
     * - query.size: 페이지 크기(기본값 20)
     */
    @GetMapping("/public")
    public Flux<ClanSummaryResponse> findPublicClans(
        @RequestParam(name = "policies[]", required = false) List<ClanJoinPolicy> policies,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return clanInfoService.searchPublicClans(policies, keyword, page, size)
            .map(ClanContractMapper::toResponse);
    }

    // ======================
    // 가입 요청 조회
    // ======================

    /**
     * 특정 클랜의 가입 요청(PENDING) 유저 ID 목록 조회.
     * <p>
     * 반환 값은 가입을 신청한 유저 ID 리스트(Flux&lt;UUID&gt;)이며,
     * BFF에서 user-svc 등과 조합하여 최종 응답 DTO를 구성한다.
     *
     * - path.clanId: 가입 요청을 조회할 클랜 ID
     * - X-Internal-Actor-Id: 조회 요청자(해당 클랜의 MASTER/OFFICER 권한 확인용)
     * - query.page: 페이지 번호(0-base, 기본값 0)
     * - query.size: 페이지 크기(기본값 20)
     */
    @GetMapping("/{clanId}/join-requests")
    public Flux<UUID> findPendingJoinRequests(
        @PathVariable UUID clanId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return clanInfoService.getPendingJoinUserIds(clanId, actorId, page, size);
    }

    /**
     * 현재 유저의 클랜 가입 요청(PENDING) 상태 조회.
     * <p>
     * 주어진 클랜 ID 목록 중, 요청자(actorId)가 가입 신청(PENDING) 상태인 클랜 ID들만 반환한다.
     * <br>
     * clanIds 가 null 이거나 비어 있으면 빈 Flux 를 반환한다.
     *
     * - X-Internal-Actor-Id: 본인(가입 신청자) 유저 ID
     * - query.clanIds: 가입 요청 상태를 확인할 클랜 ID 목록
     */
    @GetMapping("/join-requests")
    public Flux<UUID> findMyPendingJoinRequests(
        @RequestHeader("X-Internal-Actor-Id") UUID actorId,
        @RequestParam("clanIds") List<UUID> clanIds
    ) {
        if (clanIds == null || clanIds.isEmpty()) {
            return Flux.empty();
        }
        return clanInfoService.getMyPendingJoinRequests(actorId, clanIds);
    }
}
