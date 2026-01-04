package com.colombus.clan.web.internal;

import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.colombus.clan.command.CancelJoinClanCommand;
import com.colombus.clan.command.ChangeClanOwnerCommand;
import com.colombus.clan.command.ChangeMemberRoleCommand;
import com.colombus.clan.command.ChangeMemberStatusCommand;
import com.colombus.clan.command.CreateClanCommand;
import com.colombus.clan.command.DeleteClanCommand;
import com.colombus.clan.command.IssueInviteLinkCommand;
import com.colombus.clan.command.JoinClanByInviteCommand;
import com.colombus.clan.command.JoinClanCommand;
import com.colombus.clan.command.LeaveClanCommand;
import com.colombus.clan.command.ReviewJoinRequestCommand;
import com.colombus.clan.command.UpdateClanProfileCommand;
import com.colombus.clan.contract.dto.ChangeClanOwnerRequest;
import com.colombus.clan.contract.dto.ChangeMemberRoleRequest;
import com.colombus.clan.contract.dto.ChangeMemberStatusRequest;
import com.colombus.clan.contract.dto.ClanDetailResponse;
import com.colombus.clan.contract.dto.ClanInviteTicketResponse;
import com.colombus.clan.contract.dto.CreateClanRequest;
import com.colombus.clan.contract.dto.IssueInviteRequest;
import com.colombus.clan.contract.dto.JoinClanByInviteRequest;
import com.colombus.clan.contract.dto.UpdateClanProfileRequest;
import com.colombus.clan.service.ClanService;
import com.colombus.clan.service.ClanMemberService;
import com.colombus.clan.service.ClanModifyService;
import com.colombus.clan.web.internal.mapper.ClanContractMapper;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * 클랜 생성/수정/삭제 + 멤버십 관련 명령용 internal API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/clans")
public class ClanCommandController {
    
    private final ClanService clanService;
    private final ClanMemberService clanMemberService;
    private final ClanModifyService clanModifyService;

    // ======================
    // 클랜 생성/수정/삭제
    // ======================

    /**
     * 클랜 생성.
     * - X-Internal-Actor-Id: <UUID>
     */
    @PostMapping
    public Mono<ClanDetailResponse> createClan(
        @RequestHeader("X-Internal-Actor-Id") UUID ownerId,
        @RequestBody CreateClanRequest body
    ) {
        CreateClanCommand cmd = ClanContractMapper.toCommand(body, ownerId);
        return clanService.createClan(cmd)
            .map(ClanContractMapper::toResponse);
    }

    /**
     * 클랜 프로필 수정 (이름/설명/정책/메타데이터).
     * - X-Internal-Actor-Id: <UUID>
     */
    @PatchMapping("/{clanId}")
    public Mono<ClanDetailResponse> updateClanProfile(
        @PathVariable UUID clanId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId,
        @RequestBody UpdateClanProfileRequest body
    ) {
        UpdateClanProfileCommand cmd = ClanContractMapper.toCommand(clanId, body);
        return clanModifyService.updateClanProfile(cmd, actorId)
            .map(ClanContractMapper::toResponse);
    }

    /**
     * 클랜 삭제(해체).
     */
    @DeleteMapping("/{clanId}")
    public Mono<Void> deleteClan(
        @PathVariable UUID clanId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId
    ) {
        DeleteClanCommand cmd = ClanContractMapper.toDeleteClanCommand(clanId, actorId);
        return clanService.deleteClan(cmd);
    }

    // ======================
    // 멤버십: 가입/탈퇴
    // ======================

    /**
     * 클랜 가입 (정책에 따라 즉시 가입 or 가입 요청 생성).
     */
    @PostMapping("/{clanId}/join")
    public Mono<Void> joinClan(
        @PathVariable UUID clanId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId
    ) {
        JoinClanCommand cmd = ClanContractMapper.toJoinClanCommand(clanId, actorId);
        return clanMemberService.joinClan(cmd);
    }

    /**
     * 클랜 탈퇴 (LEFT).
     */
    @PostMapping("/{clanId}/leave")
    public Mono<Void> leaveClan(
        @PathVariable UUID clanId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId
    ) {
        LeaveClanCommand cmd = ClanContractMapper.toLeaveClanCommand(clanId, actorId);
        return clanMemberService.leaveClan(cmd);
    }

    // ======================
    // 가입 요청: 승인 / 거절 / 취소
    // ======================

    /**
     * 가입 요청 승인.
     * - clanId: 클랜 ID
     * - targetUserId: 가입을 신청한 유저 ID
     * - body.actorId: 승인하는 관리자/오피서 ID
     */
    @PostMapping("/{clanId}/join-requests/{targetUserId}/approve")
    public Mono<Void> approveJoinRequest(
        @PathVariable UUID clanId,
        @PathVariable UUID targetUserId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId
    ) {
        ReviewJoinRequestCommand cmd =
            ClanContractMapper.toApproveCommand(clanId, targetUserId, actorId);
        return clanMemberService.approveJoinRequest(cmd);
    }

    /**
     * 가입 요청 거절.
     * - clanId: 클랜 ID
     * - targetUserId: 가입을 신청한 유저 ID
     * - body.actorId: 거절하는 관리자/오피서 ID
     */
    @PostMapping("/{clanId}/join-requests/{targetUserId}/reject")
    public Mono<Void> rejectJoinRequest(
        @PathVariable UUID clanId,
        @PathVariable UUID targetUserId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId
    ) {
        ReviewJoinRequestCommand cmd =
            ClanContractMapper.toRejectCommand(clanId, targetUserId, actorId);
        return clanMemberService.rejectJoinRequest(cmd);
    }

    /**
     * 본인의 가입 요청 취소 (PENDING → CANCELLED).
     * - clanId: 클랜 ID
     * - actorId: 자기 자신(가입 신청자) ID
     */
    @PostMapping("/{clanId}/join-requests/cancel")
    public Mono<Void> cancelMyJoinRequest(
        @PathVariable UUID clanId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId
    ) {
        CancelJoinClanCommand cmd = ClanContractMapper.toCommand(clanId, actorId);
        return clanMemberService.cancelJoinRequest(cmd);
    }

    // ======================
    // 초대 링크 발급
    // ======================

    /**
     * 클랜 초대 링크 발급.
     * - path: {clanId}
     * - body.actorId: 발급하는 관리자/오피서
     * - body.maxUses: 사용 가능 횟수 (null or 0 이면 무제한)
     */
    @PostMapping("/{clanId}/invites")
    public Mono<ClanInviteTicketResponse> issueInvite(
        @PathVariable UUID clanId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId,
        @RequestBody IssueInviteRequest body
    ) {
        IssueInviteLinkCommand cmd = ClanContractMapper.toCommand(clanId, actorId, body);
        return clanMemberService.issueInviteLink(cmd)
            .map(ClanContractMapper::toResponse);
    }

    // ======================
    // 초대 코드로 가입
    // ======================

    /**
     * 초대 코드로 클랜 가입.
     * - body.userId: 가입하는 유저 ID
     * - body.inviteCode: 초대 코드
     */
    @PostMapping("/join-by-invite")
    public Mono<Void> joinByInvite(
        @RequestHeader("X-Internal-Actor-Id") UUID actorId,
        @RequestBody JoinClanByInviteRequest body
    ) {
        JoinClanByInviteCommand cmd = ClanContractMapper.toCommand(actorId, body);
        return clanMemberService.joinClanByInvite(cmd);
    }

    // ======================
    // 멤버 관리: 오너 변경 / 역할 / 상태
    // ======================

    /**
     * 클랜 오너 변경.
     */
    @PostMapping("/{clanId}/owner")
    public Mono<Void> changeOwner(
        @PathVariable UUID clanId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId,
        @RequestBody ChangeClanOwnerRequest body
    ) {
        ChangeClanOwnerCommand cmd = ClanContractMapper.toCommand(clanId, actorId, body);
        return clanMemberService.changeClanOwner(cmd);
    }

    /**
     * 멤버 역할 변경 (MASTER/OFFICER/MEMBER).
     */
    @PostMapping("/{clanId}/members/{targetUserId}/role")
    public Mono<Void> changeMemberRole(
        @PathVariable UUID clanId,
        @PathVariable UUID targetUserId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId,
        @RequestBody ChangeMemberRoleRequest body
    ) {
        ChangeMemberRoleCommand cmd = ClanContractMapper.toCommand(clanId, targetUserId, actorId, body);
        return clanMemberService.changeMemberRole(cmd);
    }

    /**
     * 멤버 상태 변경 (LEFT/KICKED/BANNED 등).
     */
    @PostMapping("/{clanId}/members/{targetUserId}/status")
    public Mono<Void> changeMemberStatus(
        @PathVariable UUID clanId,
        @PathVariable UUID targetUserId,
        @RequestHeader("X-Internal-Actor-Id") UUID actorId,
        @RequestBody ChangeMemberStatusRequest body
    ) {
        ChangeMemberStatusCommand cmd = ClanContractMapper.toCommand(clanId, targetUserId, actorId, body);
        return clanMemberService.changeMemberStatus(cmd);
    }

}
