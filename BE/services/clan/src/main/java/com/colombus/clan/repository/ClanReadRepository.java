package com.colombus.clan.repository;

import java.util.List;
import java.util.UUID;

import com.colombus.clan.model.ClanDetail;
import com.colombus.clan.model.ClanSummary;

public class ClanReadRepository {

    ClanDetail requireClanDetail(UUID clanId) {
        return null;
    }
    
    List<ClanSummary> findPublicClans(String keyword, int limit, int offset) {
        return null;
    }

    List<ClanSummary> findClansByOwner(UUID ownerId) {
        return null;
    }

    List<ClanSummary> findClansByMember(UUID userId) {
        return null;
    }
}
