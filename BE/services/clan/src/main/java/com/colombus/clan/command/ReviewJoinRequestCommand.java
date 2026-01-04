package com.colombus.clan.command;

import java.util.UUID;

import com.colombus.clan.model.type.ClanJoinRequestStatus;

public record ReviewJoinRequestCommand(
    UUID clanId,
    UUID targetUserId,           // 가입을 신청한 유저
    UUID actorId,                // 승인/거절하는 관리자
    ClanJoinRequestStatus status // APPROVED 또는 REJECTED
) {}