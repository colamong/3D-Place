package com.colombus.bff.web.api.clan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import com.colombus.bff.util.ActorIdResolver;
import com.colombus.clan.contract.dto.ChangeClanOwnerRequest;
import com.colombus.clan.contract.dto.ChangeMemberRoleRequest;
import com.colombus.clan.contract.dto.ChangeMemberStatusRequest;
import com.colombus.clan.contract.dto.ClanDetailResponse;
import com.colombus.clan.contract.dto.ClanInviteTicketResponse;
import com.colombus.clan.contract.dto.ClanMemberDetailResponse;
import com.colombus.clan.contract.dto.ClanSummaryResponse;
import com.colombus.clan.contract.dto.MyClanResponse;
import com.colombus.clan.contract.dto.CreateClanRequest;
import com.colombus.clan.contract.dto.DeleteClanRequest;
import com.colombus.clan.contract.dto.IssueInviteRequest;
import com.colombus.clan.contract.dto.JoinClanByInviteRequest;
import com.colombus.clan.contract.dto.UpdateClanProfileRequest;
import com.colombus.clan.contract.enums.ClanJoinPolicyCode;
import com.colombus.clan.contract.enums.ClanJoinRequestStatusCode;
import com.colombus.clan.contract.enums.ClanMemberRoleCode;
import com.colombus.clan.contract.enums.ClanMemberStatusCode;
import com.colombus.common.web.core.response.CommonResponse;
import com.colombus.common.web.core.tracing.TraceIdProvider;
import com.colombus.user.contract.dto.UserSummaryBulkRequest;
import com.colombus.user.contract.dto.UserSummaryResponse;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clans")
public class ClanViewController {
    
    private final WebClient clanClient;
    private final WebClient userClient;
    private final TraceIdProvider trace;

    // ---------------------------------------------------------------------
    // Read APIs
    // ---------------------------------------------------------------------

    // 공개 클랜 목록을 키워드/페이지 조건으로 조회한다.
    @GetMapping("/public")
    public Mono<ResponseEntity<CommonResponse<List<PublicClanSummaryResponse>>>> getPublicClan(
        @RequestParam(name = "policies[]", required = false) List<ClanJoinPolicyCode> policies,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size,
        @AuthenticationPrincipal Jwt jwt,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = ActorIdResolver.resolveUserId(jwt);

        var type = new ParameterizedTypeReference<List<ClanSummaryResponse>>() {};

        Mono<List<ClanSummaryResponse>> summaryMono = clanClient.get()
            .uri(b -> {
                var ub = b.pathSegment("public")
                        .queryParam("page", page)
                        .queryParam("size", size);
                if (keyword != null && !keyword.isBlank()) {
                    ub.queryParam("keyword", keyword);
                }
                if (policies != null && !policies.isEmpty()) {
                    ub.queryParam("policies[]", policies.toArray());
                }
                return ub.build();
            })
            .retrieve()
            .bodyToMono(type);

        Mono<List<PublicClanSummaryResponse>> resultMono = summaryMono.flatMap(clans -> {
            // 게스트거나, 클랜이 없으면 그냥 상태 없이 내려줌
            if (actorId == null || clans.isEmpty()) {
                return Mono.just(
                    clans.stream()
                        .map(c -> PublicClanSummaryResponse.of(c, null))
                        .toList()
                );
            }

            // 이 페이지에 노출된 클랜 id 목록
            var clanIds = clans.stream()
                .map(ClanSummaryResponse::id)
                .toList();

            // 내 가입 요청 상태 목록 조회
            Mono<Set<UUID>> pendingIdsMono =
                clanClient.get()
                    .uri(b -> {
                        var ub = b.pathSegment("internal", "join-requests")
                            .queryParam("clanIds", clanIds.toArray());
                        return ub.build();
                    })
                    .header("X-Internal-Actor-Id", actorId.toString())
                    .retrieve()
                    .bodyToFlux(UUID.class)
                    .collect(Collectors.toSet());

            return pendingIdsMono.map(pendingIds ->
                clans.stream()
                    .map(c -> {
                        ClanJoinRequestStatusCode status =
                            pendingIds.contains(c.id())
                                ? ClanJoinRequestStatusCode.PENDING
                                : null;

                        return PublicClanSummaryResponse.of(c, status);
                    })
                    .toList()
            );
        });

        return respond(path, traceId, resultMono);
    }
    
    // 단일 클랜의 상세/멤버/가입요청 데이터를 key 값에 따라 반환한다.
    // key=overview(기본), members, banned, requests
    @GetMapping("/{clanId}/detail")
    public Mono<ResponseEntity<CommonResponse<Object>>> getClanDetail(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        @RequestParam(name = "key", defaultValue = "overview") String key,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = requireActor(jwt);
        Mono<MemberContext> membershipMono = requireMembership(clanId, actorId);

        String normalizedKey = key.toLowerCase(Locale.ROOT);
        Mono<?> payload;

        switch (normalizedKey) {
            case "members" -> payload = membershipMono.then(
                fetchMemberSection(clanId, actorId, ClanMemberStatusCode.ACTIVE)
            );
            case "banned" -> payload = membershipMono.then(
                fetchMemberSection(clanId, actorId, ClanMemberStatusCode.BANNED)
            );
            case "requests" -> payload = membershipMono.flatMap(ctx -> {
                if (!canManage(ctx.role())) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "CLAN_PERMISSION_DENIED"));
                }
                return fetchJoinRequestSection(clanId, actorId);
            });
            default -> payload = membershipMono.then(fetchClanOverview(clanId, actorId));
        }

        return respondAny(path, traceId, payload);
    }

    // 사용자 토큰을 기반으로 내가 속한 클랜 정보를 조회한다.
    @GetMapping("/me")
    public Mono<ResponseEntity<CommonResponse<MyClanResponse>>> getMyClan(
        @AuthenticationPrincipal Jwt jwt,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID userId = UUID.fromString(jwt.getClaimAsString("uid"));

        Mono<ResponseEntity<CommonResponse<MyClanResponse>>> response = respond(
            path,
            traceId,
            clanClient.get()
            .uri("/member/{userId}", userId)
            .retrieve()
            .bodyToMono(MyClanResponse.class)
        );

        return response.switchIfEmpty(Mono.fromSupplier(() -> ResponseEntity.noContent().build()));
    }

    // 특정 클랜에 들어온 가입 요청 식별자 목록을 조회한다.
    @GetMapping("/{clanId}/join-requests")
    public Mono<ResponseEntity<CommonResponse<List<UUID>>>> getJoinRequests(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = requireActor(jwt);

        Mono<List<UUID>> userIds = requireMembership(clanId, actorId)
            .flatMap(ctx -> {
                if (!canManage(ctx.role())) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "CLAN_PERMISSION_DENIED"));
                }
                return clanClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/{id}/join-requests")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build(clanId)
                    )
                    .header("X-Internal-Actor-Id", actorId.toString())
                    .retrieve()
                    .bodyToFlux(UUID.class)
                    .collectList();
            });

        return respond(path, traceId, userIds);
    }

    // ---------------------------------------------------------------------
    // Create APIs
    // ---------------------------------------------------------------------

    // 신규 클랜을 생성하고 상세 정보를 반환한다.
    @PostMapping
    public Mono<ResponseEntity<CommonResponse<ClanDetailResponse>>> create(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody CreateClanRequest body,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));

        Mono<ClanDetailResponse> clanDetail = clanClient.post()
            .uri("")
            .header("X-Internal-Actor-Id", actorId.toString())
            .bodyValue(body)
            .retrieve()
            .bodyToMono(ClanDetailResponse.class);

        return respond(path, traceId, clanDetail);
    }

    // 클랜 관리자가 초대장을 발급해 초대 코드를 만든다.
    @PostMapping("/{clanId}/invites")
    public Mono<ResponseEntity<CommonResponse<ClanInviteTicketResponse>>> issueInvite(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        @RequestBody(required = false) IssueInviteApiRequest body,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));
        int maxUses = (body == null || body.maxUses() == null) ? 0 : body.maxUses();

        IssueInviteRequest internalReq =
            new IssueInviteRequest(maxUses);

        Mono<ClanInviteTicketResponse> ticket = clanClient.post()
            .uri("/{id}/invites", clanId)
            .header("X-Internal-Actor-Id", actorId.toString())
            .bodyValue(internalReq)
            .retrieve()
            .bodyToMono(ClanInviteTicketResponse.class);

        return respond(path, traceId, ticket);
    }

    // 사용자가 특정 클랜에 가입을 요청한다.
    @PostMapping("/{clanId}/join")
    public Mono<ResponseEntity<CommonResponse<Void>>> join(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));

        Mono<Void> action = clanClient.post()
            .uri("/{id}/join", clanId)
            .header("X-Internal-Actor-Id", actorId.toString())
            .retrieve()
            .toBodilessEntity()
            .then();

        return respondEmpty(path, traceId, action);
    }

    // 초대 코드로 클랜에 가입한다.
    @PostMapping("/join-by-invite")
    public Mono<ResponseEntity<CommonResponse<Void>>> joinByInvite(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody JoinByInviteApiRequest body,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));

        JoinClanByInviteRequest internalReq =
            new JoinClanByInviteRequest(body.inviteCode());

        Mono<Void> action = clanClient.post()
            .uri("/join-by-invite")
            .header("X-Internal-Actor-Id", actorId.toString())
            .bodyValue(internalReq)
            .retrieve()
            .toBodilessEntity()
            .then();

        return respondEmpty(path, traceId, action);
    }

    // ---------------------------------------------------------------------
    // Update APIs
    // ---------------------------------------------------------------------

    // 클랜 프로필 정보를 수정한다.
    @PostMapping("/{clanId}")
    public Mono<ResponseEntity<CommonResponse<ClanDetailResponse>>> update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        @RequestBody UpdateClanProfileRequest body,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));

        Mono<ClanDetailResponse> clanDetail = clanClient.patch()
            .uri("/{id}", clanId)
            .header("X-Internal-Actor-Id", actorId.toString())
            .bodyValue(body)
            .retrieve()
            .bodyToMono(ClanDetailResponse.class);

        return respond(path, traceId, clanDetail);
    }

    // 가입 요청을 승인한다.
    @PostMapping("/{clanId}/join-requests/{targetUserId}/approve")
    public Mono<ResponseEntity<CommonResponse<Void>>> approveJoinRequest(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        @PathVariable UUID targetUserId,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));

        Mono<Void> action = requireMembership(clanId, actorId)
            .flatMap(ctx -> {
                if (!canManage(ctx.role())) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "CLAN_PERMISSION_DENIED"));
                }
                return clanClient.post()
                    .uri("/{id}/join-requests/{targetUserId}/approve",
                        clanId, targetUserId)
                    .header("X-Internal-Actor-Id", actorId.toString())
                    .retrieve()
                    .toBodilessEntity()
                    .then();
            });

        return respondEmpty(path, traceId, action);
    }

    // 가입 요청을 거절한다.
    @PostMapping("/{clanId}/join-requests/{targetUserId}/reject")
    public Mono<ResponseEntity<CommonResponse<Void>>> rejectJoinRequest(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        @PathVariable UUID targetUserId,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));

        Mono<Void> action = requireMembership(clanId, actorId)
            .flatMap(ctx -> {
                if (!canManage(ctx.role())) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "CLAN_PERMISSION_DENIED"));
                }
                return clanClient.post()
                    .uri("/{id}/join-requests/{targetUserId}/reject",
                        clanId, targetUserId)
                    .header("X-Internal-Actor-Id", actorId.toString())
                    .retrieve()
                    .toBodilessEntity()
                    .then();
            });

        return respondEmpty(path, traceId, action);
    }

    @PostMapping("/{clanId}/join-requests/cancel")
    public Mono<ResponseEntity<CommonResponse<Void>>> cancelJoinRequest(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));

        Mono<Void> action = clanClient.post()
            .uri("/{id}/join-requests/cancel",
                clanId)
            .header("X-Internal-Actor-Id", actorId.toString())
            .retrieve()
            .toBodilessEntity()
            .then();

        return respondEmpty(path, traceId, action);
    }

    // 클랜 소유권을 다른 멤버에게 위임한다.
    @PostMapping("/{clanId}/owner")
    public Mono<ResponseEntity<CommonResponse<Void>>> changeOwner(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        @RequestBody ChangeClanOwnerRequest body,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));

        ChangeClanOwnerRequest internalReq =
            new ChangeClanOwnerRequest(body.targetUserId());

        Mono<Void> action = clanClient.post()
            .uri("/{id}/owner", clanId)
            .header("X-Internal-Actor-Id", actorId.toString())
            .bodyValue(internalReq)
            .retrieve()
            .toBodilessEntity()
            .then();

        return respondEmpty(path, traceId, action);
    }

    // 멤버의 역할을 변경한다.
    @PostMapping("/{clanId}/members/{targetUserId}/role")
    public Mono<ResponseEntity<CommonResponse<Void>>> changeMemberRole(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        @PathVariable UUID targetUserId,
        @RequestBody ChangeMemberRoleRequest body,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));

        ChangeMemberRoleRequest internalReq =
            new ChangeMemberRoleRequest(body.newRole());

        Mono<Void> action = requireMembership(clanId, actorId)
            .flatMap(ctx -> {
                if (!canManage(ctx.role())) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "CLAN_PERMISSION_DENIED"));
                }
                return clanClient.post()
                    .uri("/{id}/members/{targetId}/role", clanId, targetUserId)
                    .header("X-Internal-Actor-Id", actorId.toString())
                    .bodyValue(internalReq)
                    .retrieve()
                    .toBodilessEntity()
                    .then();
            });

        return respondEmpty(path, traceId, action);
    }

    // 멤버의 상태(활성/추방 등)를 변경한다.
    @PostMapping("/{clanId}/members/{targetUserId}/status")
    public Mono<ResponseEntity<CommonResponse<Void>>> changeMemberStatus(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        @PathVariable UUID targetUserId,
        @RequestBody ChangeMemberStatusRequest body,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));

        ClanMemberStatusCode newStatus = body.newStatus();

        ChangeMemberStatusRequest internalReq =
            new ChangeMemberStatusRequest(newStatus);

        Mono<Void> action = requireMembership(clanId, actorId)
            .flatMap(ctx -> {
                if (newStatus == ClanMemberStatusCode.ACTIVE) {
                    if (!isMaster(ctx.role())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "CLAN_PERMISSION_DENIED"));
                    }
                } else if (newStatus == ClanMemberStatusCode.KICKED
                    || newStatus == ClanMemberStatusCode.BANNED) {
                    if (!canManage(ctx.role())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "CLAN_PERMISSION_DENIED"));
                    }
                } else {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEMBER_STATUS"));
                }

                return clanClient.post()
                    .uri("/{id}/members/{targetId}/status", clanId, targetUserId)
                    .header("X-Internal-Actor-Id", actorId.toString())
                    .bodyValue(internalReq)
                    .retrieve()
                    .toBodilessEntity()
                    .then();
            });

        return respondEmpty(path, traceId, action);
    }

    // ---------------------------------------------------------------------
    // Delete APIs
    // ---------------------------------------------------------------------

    // 클랜을 삭제해 더 이상 조회되지 않도록 만든다.
    @DeleteMapping("/{clanId}")
    public Mono<ResponseEntity<CommonResponse<Void>>> delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));
        DeleteClanRequest body = new DeleteClanRequest(actorId);

        Mono<Void> action = clanClient.method(HttpMethod.DELETE) 
            .uri("/{id}", clanId)
            .header("X-Internal-Actor-Id", actorId.toString())
            .bodyValue(body)
            .retrieve()
            .toBodilessEntity()
            .then();

        return respondEmpty(path, traceId, action);
    }

    // 사용자가 자발적으로 클랜을 탈퇴한다.
    @PostMapping("/{clanId}/leave")
    public Mono<ResponseEntity<CommonResponse<Void>>> leave(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID clanId,
        ServerHttpRequest req
    ) {
        String path    = req.getPath().value();
        String traceId = trace.currentTraceId();

        UUID actorId = UUID.fromString(jwt.getClaimAsString("uid"));

        Mono<Void> action = clanClient.post()
            .uri("/{id}/leave", clanId)
            .header("X-Internal-Actor-Id", actorId.toString())
            .retrieve()
            .toBodilessEntity()
            .then();

        return respondEmpty(path, traceId, action);
    }

    // ---------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------

    private Mono<ClanDetailView> fetchClanOverview(UUID clanId, UUID actorId) {
        Mono<ClanDetailResponse> clanMono = clanClient.get()
            .uri("/{id}", clanId)
            .retrieve()
            .bodyToMono(ClanDetailResponse.class);

        Mono<List<ClanMemberDetailResponse>> membersMono = clanClient.get()
            .uri("/{id}/members", clanId)
            .header("X-Internal-Actor-Id", actorId.toString())
            .retrieve()
            .bodyToFlux(ClanMemberDetailResponse.class)
            .collectList();

        return clanMono
            .zipWith(membersMono)
            .flatMap(tuple -> {
                ClanDetailResponse clan = tuple.getT1();
                List<ClanMemberDetailResponse> members = tuple.getT2();

                List<UUID> userIds = members.stream()
                    .map(ClanMemberDetailResponse::userId)
                    .collect(Collectors.toCollection(ArrayList::new));
                userIds.add(clan.ownerId());
                List<UUID> distinct = userIds.stream().distinct().toList();

                if (distinct.isEmpty()) {
                    return Mono.just(new ClanDetailView(
                        clan.id(),
                        clan.name(),
                        clan.description(),
                        "(알 수 없음)",
                        null,
                        clan.joinPolicy(),
                        clan.isPublic(),
                        clan.memberCount(),
                        clan.paintCountTotal(),
                        clan.metadata(),
                        clan.createdAt(),
                        clan.updatedAt(),
                        List.of()
                    ));
                }

                return fetchUserSummaries(distinct)
                    .map(userMap -> ClanDetailView.of(clan, members, userMap));
            });
    }

    private Mono<List<ClanMemberView>> fetchMemberSection(
        UUID clanId,
        UUID actorId,
        ClanMemberStatusCode status
    ) {
        Mono<List<ClanMemberDetailResponse>> membersMono = clanClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/{id}/members")
                .queryParam("status", status)
                .build(clanId))
            .header("X-Internal-Actor-Id", actorId.toString())
            .retrieve()
            .bodyToFlux(ClanMemberDetailResponse.class)
            .collectList();

        return membersMono.flatMap(members -> {
            if (members.isEmpty()) {
                return Mono.just(List.of());
            }

            List<UUID> userIds = members.stream()
                .map(ClanMemberDetailResponse::userId)
                .distinct()
                .toList();

            return fetchUserSummaries(userIds)
                .map(userMap -> members.stream()
                    .map(member -> toMemberView(member, userMap))
                    .toList());
        });
    }

    private Mono<List<ClanJoinRequestView>> fetchJoinRequestSection(UUID clanId, UUID actorId) {
        Mono<List<UUID>> pendingIdsMono = clanClient.get()
            .uri("/{id}/join-requests", clanId)
            .header("X-Internal-Actor-Id", actorId.toString())
            .retrieve()
            .bodyToFlux(UUID.class)
            .collectList();

        return pendingIdsMono.flatMap(userIds -> {
            if (userIds.isEmpty()) {
                return Mono.just(List.of());
            }
            return fetchUserSummaries(userIds)
                .map(userMap -> userIds.stream()
                    .map(userId -> {
                        UserSummaryResponse user = userMap.get(userId);
                        String nickname = user != null ? user.nickname() : "(알 수 없음)";
                        String handle = user != null ? user.nicknameHandle() : null;
                        return new ClanJoinRequestView(userId, nickname, handle);
                    })
                    .toList());
        });
    }

    private Mono<Map<UUID, UserSummaryResponse>> fetchUserSummaries(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        return userClient.post()
            .uri("/bulk-summary")
            .bodyValue(new UserSummaryBulkRequest(userIds))
            .retrieve()
            .bodyToFlux(UserSummaryResponse.class)
            .collectMap(UserSummaryResponse::id);
    }

    private ClanMemberView toMemberView(
        ClanMemberDetailResponse member,
        Map<UUID, UserSummaryResponse> userMap
    ) {
        UserSummaryResponse user = userMap.get(member.userId());
        String nickname = user != null ? user.nickname() : "(알 수 없음)";
        String handle   = user != null ? user.nicknameHandle() : null;
        return new ClanMemberView(
            member.id(),
            member.userId(),
            nickname,
            handle,
            member.role(),
            member.status(),
            member.paintCountTotal(),
            member.joinedAt(),
            member.leftAt()
        );
    }

    private UUID requireActor(@Nullable Jwt jwt) {
        UUID actorId = ActorIdResolver.resolveUserId(jwt);
        if (actorId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED");
        }
        return actorId;
    }

    private Mono<MemberContext> requireMembership(UUID clanId, UUID actorId) {
        return clanClient.get()
            .uri("/member/{userId}", actorId)
            .retrieve()
            .bodyToMono(MyClanResponse.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "CLAN_NOT_MEMBER")))
            .flatMap(resp -> {
                if (!resp.clan().id().equals(clanId)) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "CLAN_NOT_MEMBER"));
                }
                return Mono.just(new MemberContext(resp.role()));
            });
    }

    private boolean canManage(ClanMemberRoleCode role) {
        return role == ClanMemberRoleCode.MASTER || role == ClanMemberRoleCode.OFFICER;
    }

    private boolean isMaster(ClanMemberRoleCode role) {
        return role == ClanMemberRoleCode.MASTER;
    }

    // 공통 응답 형태로 데이터를 감싼다.
    private <T> Mono<ResponseEntity<CommonResponse<T>>> respond(
        String path,
        String traceId,
        Mono<T> dataMono
    ) {
        return dataMono.map(data -> ResponseEntity.ok(CommonResponse.success(path, traceId, data)));
    }

    private Mono<ResponseEntity<CommonResponse<Object>>> respondAny(
        String path,
        String traceId,
        Mono<?> dataMono
    ) {
        return dataMono.map(data ->
            ResponseEntity.ok(CommonResponse.success(path, traceId, data))
        );
    }

    // 보디가 없는 액션을 성공 응답으로 치환한다.
    private Mono<ResponseEntity<CommonResponse<Void>>> respondEmpty(
        String path,
        String traceId,
        Mono<?> action
    ) {
        return action.then(Mono.fromSupplier(() ->
            ResponseEntity.ok(CommonResponse.success(path, traceId, null))
        ));
    }

    public record PublicClanSummaryResponse(
        ClanSummaryResponse clan,
        @Nullable ClanJoinRequestStatusCode myJoinRequestStatus
    ) {
        public static PublicClanSummaryResponse of(
            ClanSummaryResponse clan,
            @Nullable ClanJoinRequestStatusCode status
        ) {
            return new PublicClanSummaryResponse(clan, status);
        }
    }

    public record ClanDetailView(
        UUID id,
        String name,
        @Nullable String description,
        String ownerNickname,
        @Nullable String ownerNicknameHandle,
        ClanJoinPolicyCode joinPolicy,
        boolean isPublic,
        int memberCount,
        long paintCountTotal,
        JsonNode metadata,
        Instant createdAt,
        Instant updatedAt,
        List<ClanMemberView> members
    ) {

        // Clan/멤버/사용자 정보를 합성하여 화면에서 쓰는 뷰를 만든다.
        public static ClanDetailView of(
            ClanDetailResponse clan,
            List<ClanMemberDetailResponse> memberDetails,
            Map<UUID, UserSummaryResponse> userMap
        ) {
            // owner 정보 찾기
            UserSummaryResponse owner = userMap.get(clan.ownerId());
            String ownerNickname = owner != null ? owner.nickname() : "(알 수 없음)";
            String ownerHandle   = owner != null ? owner.nicknameHandle() : null;

            // 멤버 view로 변환
            List<ClanMemberView> memberViews = memberDetails.stream()
                .map(m -> {
                    UserSummaryResponse u = userMap.get(m.userId());

                    String nickname = u != null ? u.nickname() : "(알 수 없음)";
                    String handle   = u != null ? u.nicknameHandle() : null;

                    return new ClanMemberView(
                        m.id(),
                        m.userId(),
                        nickname,
                        handle,
                        m.role(),
                        m.status(),
                        m.paintCountTotal(),
                        m.joinedAt(),
                        m.leftAt()
                    );
                })
                .toList();

            // 최종 View 조립
            return new ClanDetailView(
                clan.id(),
                clan.name(),
                clan.description(),
                ownerNickname,
                ownerHandle,
                clan.joinPolicy(),
                clan.isPublic(),
                clan.memberCount(),
                clan.paintCountTotal(),
                clan.metadata(),
                clan.createdAt(),
                clan.updatedAt(),
                memberViews
            );
        }
    }

    public record ClanMemberView(
        UUID id,
        UUID userId,
        String nickname,
        @Nullable String nicknameHandle,
        ClanMemberRoleCode role,
        ClanMemberStatusCode status,
        long paintCountTotal,
        Instant joinedAt,
        @Nullable Instant leftAt
    ) {}

    public record ClanJoinRequestView(
        UUID userId,
        String nickname,
        @Nullable String nicknameHandle
    ) {}

    private record MemberContext(ClanMemberRoleCode role) {}

    public record IssueInviteApiRequest(
        Integer maxUses
    ) {}

    public record JoinByInviteApiRequest(
        String inviteCode
    ) {}
}
