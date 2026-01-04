package com.colombus.clan.web.internal.mapper;

import java.util.List;

import com.colombus.clan.contract.enums.ClanJoinPolicyCode;
import com.colombus.clan.contract.enums.ClanJoinRequestStatusCode;
import com.colombus.clan.contract.enums.ClanMemberRoleCode;
import com.colombus.clan.contract.enums.ClanMemberStatusCode;
import com.colombus.clan.model.type.ClanJoinPolicy;
import com.colombus.clan.model.type.ClanJoinRequestStatus;
import com.colombus.clan.model.type.ClanMemberRole;
import com.colombus.clan.model.type.ClanMemberStatus;

import jakarta.annotation.Nullable;


public final class ClanTypeMapper {

    private ClanTypeMapper() {}

    // =====================
    // ClanJoinPolicy <-> Code
    // =====================
    public static ClanJoinPolicyCode toCode(ClanJoinPolicy policy) {
        return switch (policy) {
            case OPEN        -> ClanJoinPolicyCode.OPEN;
            case APPROVAL    -> ClanJoinPolicyCode.APPROVAL;
            case INVITE_ONLY -> ClanJoinPolicyCode.INVITE_ONLY;
        };
    }

    public static ClanJoinPolicy fromCode(ClanJoinPolicyCode code) {
        return switch (code) {
            case OPEN        -> ClanJoinPolicy.OPEN;
            case APPROVAL    -> ClanJoinPolicy.APPROVAL;
            case INVITE_ONLY -> ClanJoinPolicy.INVITE_ONLY;
        };
    }

    public static List<ClanJoinPolicyCode> toCodes(@Nullable List<ClanJoinPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            return List.of();
        }
        return policies.stream()
            .map(ClanTypeMapper::toCode)
            .toList();
    }

    public static List<ClanJoinPolicy> fromCodes(@Nullable List<ClanJoinPolicyCode> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return codes.stream()
            .map(ClanTypeMapper::fromCode)
            .toList();
    }

    // =====================
    // ClanJoinRequestStatus <-> Code
    // =====================
    public static ClanJoinRequestStatusCode toCode(ClanJoinRequestStatus status) {
        return switch (status) {
            case APPROVED  -> ClanJoinRequestStatusCode.APPROVED;
            case CANCELLED -> ClanJoinRequestStatusCode.CANCELLED;
            case EXPIRED   -> ClanJoinRequestStatusCode.EXPIRED;
            case PENDING   -> ClanJoinRequestStatusCode.PENDING;
            case REJECTED  -> ClanJoinRequestStatusCode.REJECTED;
        };
    }

    public static ClanJoinRequestStatus fromCode(ClanJoinRequestStatusCode code) {
        return switch (code) {
            case APPROVED  -> ClanJoinRequestStatus.APPROVED;
            case CANCELLED -> ClanJoinRequestStatus.CANCELLED;
            case EXPIRED   -> ClanJoinRequestStatus.EXPIRED;
            case PENDING   -> ClanJoinRequestStatus.PENDING;
            case REJECTED  -> ClanJoinRequestStatus.REJECTED;
        };
    }

    // =====================
    // ClanMemberRole <-> Code
    // =====================
    public static ClanMemberRoleCode toCode(ClanMemberRole role) {
        return switch (role) {
            case MASTER  -> ClanMemberRoleCode.MASTER;
            case OFFICER -> ClanMemberRoleCode.OFFICER;
            case MEMBER  -> ClanMemberRoleCode.MEMBER;
        };
    }

    public static ClanMemberRole fromCode(ClanMemberRoleCode code) {
        return switch (code) {
            case MASTER  -> ClanMemberRole.MASTER;
            case OFFICER -> ClanMemberRole.OFFICER;
            case MEMBER  -> ClanMemberRole.MEMBER;
        };
    }

    // =====================
    // ClanMemberStatus <-> Code
    // =====================
    public static ClanMemberStatusCode toCode(ClanMemberStatus status) {
        return switch (status) {
            case ACTIVE  -> ClanMemberStatusCode.ACTIVE;
            case INVITED -> ClanMemberStatusCode.INVITED;
            case PENDING -> ClanMemberStatusCode.PENDING;
            case LEFT    -> ClanMemberStatusCode.LEFT;
            case KICKED  -> ClanMemberStatusCode.KICKED;
            case BANNED  -> ClanMemberStatusCode.BANNED;
        };
    }

    public static ClanMemberStatus fromCode(ClanMemberStatusCode code) {
        return switch (code) {
            case ACTIVE  -> ClanMemberStatus.ACTIVE;
            case INVITED -> ClanMemberStatus.INVITED;
            case PENDING -> ClanMemberStatus.PENDING;
            case LEFT    -> ClanMemberStatus.LEFT;
            case KICKED  -> ClanMemberStatus.KICKED;
            case BANNED  -> ClanMemberStatus.BANNED;
        };
    }
}