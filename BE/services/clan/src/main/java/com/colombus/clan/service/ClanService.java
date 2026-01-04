package com.colombus.clan.service;

import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colombus.clan.command.CreateClanCommand;
import com.colombus.clan.command.DeleteClanCommand;
import com.colombus.clan.exception.ClanErrorCode;
import com.colombus.clan.messaging.outbox.repository.OutboxPublishRepository;
import com.colombus.clan.model.ClanDetail;
import com.colombus.clan.model.type.ClanMemberRole;
import com.colombus.clan.model.type.ClanMemberStatus;
import com.colombus.clan.repository.ClanMemberQueryRepository;
import com.colombus.clan.repository.ClanMemberCommandRepository;
import com.colombus.clan.repository.ClanQueryRepository;
import com.colombus.common.web.core.exception.InvalidInputException;
import com.colombus.clan.repository.ClanCommandRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 클랜 생성/삭제 도메인 서비스.
 * <p>
 * - 새 클랜 생성 및 오너 멤버십 등록
 * - 클랜 삭제(soft delete) 및 멤버십/subject outbox 발행 처리
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClanService {
    
    private final ClanCommandRepository clanCommandRepo;
    private final ClanQueryRepository clanQueryRepo;
    private final ClanMemberCommandRepository clanMemberCommandRepo;
    private final ClanMemberQueryRepository clanMemberReadRepo;
    private final OutboxPublishRepository clanOutboxPublisher;

    // ======================
    // 클랜 생성
    // ======================

    /**
     * 클랜 생성 + 오너 멤버십 등록.
     * <p>
     * - 유저당 1클랜 제약:
     *   - ownerId가 이미 다른 클랜의 ACTIVE 멤버이면 생성 불가.
     * - 플로우:
     *   1) 클랜 레코드 생성 (name, ownerId, description, policy)
     *   2) 오너 멤버십(MASTER, ACTIVE) 생성
     *   3) 클랜 멤버 수 +1
     *   4) subject upsert outbox 발행 (활성 = true)
     *   5) 최종적으로 ClanDetail 조회 후 반환
     *
     * - cmd.ownerId: 새 클랜의 오너가 될 유저 ID
     * - cmd.name: 클랜 이름
     * - cmd.description: 클랜 설명 (선택)
     * - cmd.policy: 클랜 가입 정책
     */
    public Mono<ClanDetail> createClan(CreateClanCommand cmd) {
        UUID ownerId = cmd.ownerId();

        return clanMemberReadRepo.findActiveMemberByUser(ownerId)
            // 이미 클랜에 속해 있으면 에러
            .flatMap(m -> Mono.<ClanDetail>error(
                new InvalidInputException(
                    ClanErrorCode.CLAN_ALREADY_MEMBER,
                    "이미 가입된 클랜이 있습니다."
                )
            ))
            // 없으면 클랜 생성 플로우로
            .switchIfEmpty(Mono.defer(() ->
                clanCommandRepo.insertClan(
                        cmd.name(),
                        ownerId,
                        cmd.description(),
                        cmd.policy()
                    )
                    .flatMap(clanId ->
                        clanMemberCommandRepo.insertMember(
                                clanId,
                                ownerId,
                                ClanMemberRole.MASTER,
                                ClanMemberStatus.ACTIVE
                            )
                            // 멤버 수 +1
                            .then(clanCommandRepo.incrementMemberCount(clanId, +1))
                            // subject outbox
                            .then(clanOutboxPublisher.appendSubjectUpsert(clanId, true))
                            .then(clanOutboxPublisher.appendClanMembershipUpsert(ownerId, clanId, true))
                            // 최종적으로 ClanDetail 조회해서 리턴
                            .then(clanQueryRepo.requireClanDetail(clanId))
                    )
            ));
    }

    // ======================
    // 클랜 삭제
    // ======================

    /**
     * 클랜 삭제 (soft delete).
     * <p>
     * - 오너(owner)만 삭제 가능.
     * - 플로우:
     *   1) 오너 여부 검증 (actorId가 해당 클랜의 owner인지 확인)
     *   2) 현재 ACTIVE 멤버들의 userId를 전부 조회
     *   3) 클랜 soft delete (낙관 락 포함)
     *   4) 클랜 내 전체 멤버 soft delete
     *   5) 각 멤버별로 clan.membership.current.v1 outbox 발행 (clanId = null, active=false)
     *   6) 마지막으로 subject upsert outbox 발행 (active=false)
     *
     * - cmd.clanId: 삭제할 클랜 ID
     * - cmd.actorId: 삭제를 수행하는 요청자 유저 ID (오너여야 함)
     */
    public Mono<Void> deleteClan(DeleteClanCommand cmd) {
        UUID clanId  = cmd.clanId();
        UUID actorId = cmd.actorId();

        // 오너 체크
        return clanQueryRepo.isOwner(clanId, actorId)
            .flatMap(isOwner -> {
                if (!isOwner) {
                    return Mono.error(new IllegalStateException("Only owner can delete clan"));
                }
                // 현재 ACTIVE 멤버들 사용자 ID 미리 조회 (리스트로)
                return clanMemberReadRepo.findActiveUsersInClan(clanId)
                    .collectList();
            })
            // memberUserIds 가지고 실제 삭제 + outbox 처리
            .flatMap(memberUserIds ->
                // 클랜 및 멤버 soft delete
                clanCommandRepo.softDeleteClan(clanId)
                    .flatMap(ok -> {
                        if (!ok) {
                            return Mono.error(new OptimisticLockingFailureException("Clan modified"));
                        }
                        return clanMemberCommandRepo.softDeleteAllByClanId(clanId);
                    })
                    // 각 멤버에 대해 멤버십 outbox 발행
                    .thenMany(Flux.fromIterable(memberUserIds))
                    .flatMap(userId ->
                        clanOutboxPublisher.appendClanMembershipUpsert(userId, null, false)
                            .then()
                    )
                    // 마지막으로 subject upsert outbox
                    .then(clanOutboxPublisher.appendSubjectUpsert(clanId, false))
                    .then()
            );
    }
}