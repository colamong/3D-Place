package com.colombus.clan.command;

import java.util.UUID;
import com.colombus.clan.model.type.ClanJoinPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

public record UpdateClanProfileCommand(
    UUID clanId,
    @Nullable String name,
    @Nullable String description,
    @Nullable JsonNode metadataPatch,
    @Nullable ClanJoinPolicy joinPolicy
) {}