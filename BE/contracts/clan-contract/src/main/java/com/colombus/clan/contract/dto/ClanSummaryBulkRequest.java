package com.colombus.clan.contract.dto;

import java.util.List;
import java.util.UUID;

public record ClanSummaryBulkRequest(
    List<UUID> clanIds
) {}
