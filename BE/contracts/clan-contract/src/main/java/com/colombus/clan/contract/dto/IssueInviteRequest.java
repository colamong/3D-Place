package com.colombus.clan.contract.dto;

public record IssueInviteRequest(
    Integer maxUses    // null 이면 0(무제한)
) {}