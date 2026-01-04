package com.colombus.clan.command;

import java.util.UUID;

import com.colombus.clan.model.type.ClanJoinPolicy;

import jakarta.annotation.Nullable;

public record CreateClanCommand(
    UUID ownerId,
    String name,
    @Nullable String description,
    ClanJoinPolicy policy
) {}