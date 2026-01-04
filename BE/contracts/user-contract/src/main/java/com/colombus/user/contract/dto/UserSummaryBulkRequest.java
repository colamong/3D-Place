package com.colombus.user.contract.dto;

import java.util.List;
import java.util.UUID;

public record UserSummaryBulkRequest(
    List<UUID> userIds
) {}