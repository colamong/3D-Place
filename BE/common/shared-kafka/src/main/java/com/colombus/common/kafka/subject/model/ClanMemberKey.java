package com.colombus.common.kafka.subject.model;

import java.util.UUID;

public record ClanMemberKey(
    UUID clanId,
    UUID userId
) {}