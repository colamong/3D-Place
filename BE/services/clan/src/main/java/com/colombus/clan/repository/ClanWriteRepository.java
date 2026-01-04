package com.colombus.clan.repository;

import java.util.UUID;

import com.colombus.clan.command.CreateClanCommand;
import com.colombus.clan.command.UpdateClanProfileCommand;

public class ClanWriteRepository {

    UUID insertClan(CreateClanCommand cmd){
        return null;
    }

    void incrementPaintCount(UUID clanId, long delta){
        
    }

    void incrementMemberCount(UUID clanId, int delta){

    }

    void updateClan(UpdateClanProfileCommand cmd){

    }
    
    void softDeleteClan(UUID clanId){
        
    }
}
