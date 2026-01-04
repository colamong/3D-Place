package com.colombus.clan.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.colombus.clan.repository.ClanMemberCommandRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClanInviteTicketScheduler {

    private final ClanMemberCommandRepository clanMemberCommandRepo;

    /**
     * 만료된 초대 티켓 정리
     * 기본: 10분마다 실행 (0 *\/10 * * * *).
     * 필요하면 properties 로 빼서 조절.
     */
    @Scheduled(cron = "${app.clan.invite.expire.cron:0 */10 * * * *}")
    public void expireInvitesJob() {
        clanMemberCommandRepo.expireExpiredInvites(0)
            .doOnNext(count -> {
                if (count > 0) {
                    log.info("Expired {} clan invite tickets", count);
                }
            })
            .doOnError(e -> log.error("Failed to expire clan invite tickets", e))
            .subscribe();
    }
}
