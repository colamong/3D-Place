package com.colombus.clan.web.internal.mapper;

import java.util.UUID;

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
import com.colombus.clan.contract.dto.ClanMemberDetailResponse;
import com.colombus.clan.contract.dto.ClanSummaryResponse;
import com.colombus.clan.contract.dto.CreateClanRequest;
import com.colombus.clan.contract.dto.IssueInviteRequest;
import com.colombus.clan.contract.dto.JoinClanByInviteRequest;
import com.colombus.clan.contract.dto.UpdateClanProfileRequest;
import com.colombus.clan.model.ClanDetail;
import com.colombus.clan.model.ClanInviteLink;
import com.colombus.clan.model.ClanMemberDetail;
import com.colombus.clan.model.ClanSummary;
import com.colombus.clan.model.type.ClanJoinPolicy;
import com.colombus.clan.model.type.ClanJoinRequestStatus;
import com.colombus.clan.model.type.ClanMemberRole;
import com.colombus.clan.model.type.ClanMemberStatus;

public class ClanContractMapper {
    
    private ClanContractMapper() {}

    // ===============
    // request -> command
    // ===============

    public static CreateClanCommand toCommand(CreateClanRequest req, UUID ownerId) {
        ClanJoinPolicy policy = ClanTypeMapper.fromCode(req.policy());
        return new CreateClanCommand(ownerId, req.name(), req.description(), policy);
    }

    public static UpdateClanProfileCommand toCommand(UUID clanId, UpdateClanProfileRequest req) {
        ClanJoinPolicy policy = req.joinPolicy() != null
            ? ClanTypeMapper.fromCode(req.joinPolicy())
            : null;

        return new UpdateClanProfileCommand(
            clanId,
            req.name(),
            req.description(),
            req.metadataPatch(),
            policy
        );
    }

    public static DeleteClanCommand toDeleteClanCommand(UUID clanId, UUID actorId) {
        return new DeleteClanCommand(clanId, actorId);
    }

    public static JoinClanCommand toJoinClanCommand(UUID clanId, UUID actorId) {
        return new JoinClanCommand(clanId, actorId);
    }

    public static LeaveClanCommand toLeaveClanCommand(UUID clanId, UUID actorId) {
        return new LeaveClanCommand(clanId, actorId);
    }

    public static ChangeClanOwnerCommand toCommand(UUID clanId, UUID actorId, ChangeClanOwnerRequest req) {
        return new ChangeClanOwnerCommand(clanId, actorId, req.targetUserId());
    }

    public static ChangeMemberRoleCommand toCommand(
        UUID clanId,
        UUID targetUserId,
        UUID actorId,
        ChangeMemberRoleRequest req
    ) {
        ClanMemberRole role = ClanTypeMapper.fromCode(req.newRole());
        return new ChangeMemberRoleCommand(clanId, actorId, targetUserId, role);
    }

    public static ChangeMemberStatusCommand toCommand(
        UUID clanId,
        UUID targetUserId,
        UUID actorId,
        ChangeMemberStatusRequest req
    ) {
        ClanMemberStatus status = ClanTypeMapper.fromCode(req.newStatus());
        return new ChangeMemberStatusCommand(clanId, actorId, targetUserId, status);
    }

    public static ReviewJoinRequestCommand toApproveCommand(
        UUID clanId,
        UUID targetUserId,
        UUID actorId
    ) {
        return new ReviewJoinRequestCommand(
            clanId,
            targetUserId,
            actorId,
            ClanJoinRequestStatus.APPROVED
        );
    }

    public static ReviewJoinRequestCommand toRejectCommand(
        UUID clanId,
        UUID targetUserId,
        UUID actorId
    ) {
        return new ReviewJoinRequestCommand(
            clanId,
            targetUserId,
            actorId,
            ClanJoinRequestStatus.REJECTED
        );
    }

    public static CancelJoinClanCommand toCommand(
        UUID clanId,
        UUID actorId
    ) {
        return new CancelJoinClanCommand(
            clanId,
            actorId
        );
    }

    public static IssueInviteLinkCommand toCommand(UUID clanId, UUID actorId, IssueInviteRequest body) {
        int maxUses = body.maxUses() == null ? 0 : body.maxUses();
        return new IssueInviteLinkCommand(
            clanId,
            actorId,
            maxUses
        );
    }

    public static JoinClanByInviteCommand toCommand(UUID actorId, JoinClanByInviteRequest body) {
        return new JoinClanByInviteCommand(
            actorId,
            body.inviteCode()
        );
    }

    // ===============
    // domain model -> response
    // ===============

    public static ClanDetailResponse toResponse(ClanDetail d) {
        return new ClanDetailResponse(
            d.id(),
            d.name(),
            d.description(),
            d.ownerId(),
            ClanTypeMapper.toCode(d.joinPolicy()),
            d.isPublic(),
            d.memberCount(),
            d.paintCountTotal(),
            d.metadata(),
            d.createdAt(),
            d.updatedAt()
        );
    }

    public static ClanMemberDetailResponse toResponse(ClanMemberDetail m) {
        return new ClanMemberDetailResponse(
            m.id(),
            m.clanId(),
            m.userId(),
            ClanTypeMapper.toCode(m.role()),
            ClanTypeMapper.toCode(m.status()),
            m.paintCountTotal(),
            m.joinedAt(),
            m.leftAt()
        );
    }

    public static ClanSummaryResponse toResponse(ClanSummary s) {
        return new ClanSummaryResponse(
            s.id(),
            s.name(),
            s.description(),
            s.isPublic(),
            ClanTypeMapper.toCode(s.joinPolicy()),
            s.memberCount(),
            s.paintCountTotal()
        );
    }

    public static ClanInviteTicketResponse toResponse(ClanInviteLink ticket) {
        return new ClanInviteTicketResponse(
            ticket.code(),
            ticket.url(),
            ticket.expiresAt()
        );
    }

}
