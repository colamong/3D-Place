package com.colombus.clan.contract.dto;

import com.colombus.clan.contract.enums.ClanMemberStatusCode;

public record ChangeMemberStatusRequest(
    ClanMemberStatusCode newStatus
) {}