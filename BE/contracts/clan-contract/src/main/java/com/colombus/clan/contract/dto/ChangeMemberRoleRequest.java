package com.colombus.clan.contract.dto;

import com.colombus.clan.contract.enums.ClanMemberRoleCode;

public record ChangeMemberRoleRequest(
    ClanMemberRoleCode newRole
) {}