package com.colombus.clan.contract.dto;

import java.util.UUID;

public record LeaveClanRequest(
    UUID actorId
) {}