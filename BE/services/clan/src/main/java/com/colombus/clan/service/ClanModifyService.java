package com.colombus.clan.service;

import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colombus.clan.command.UpdateClanProfileCommand;
import com.colombus.clan.exception.ClanErrorCode;
import com.colombus.clan.model.ClanDetail;
import com.colombus.clan.model.ClanMemberDetail;
import com.colombus.clan.model.type.ClanMemberRole;
import com.colombus.clan.model.type.ClanMemberStatus;
import com.colombus.clan.repository.ClanMemberQueryRepository;
import com.colombus.clan.repository.ClanMemberCommandRepository;
import com.colombus.clan.repository.ClanQueryRepository;
import com.colombus.clan.repository.ClanCommandRepository;
import com.colombus.common.kafka.subject.event.SubjectAssetUpsertEvent;
import com.colombus.common.kafka.subject.model.type.AssetPurpose;
import com.colombus.common.web.core.exception.ForbiddenException;
import com.colombus.common.web.core.exception.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * 클랜 프로필/멤버 정보 수정용 도메인 서비스.
 * <p>
 * - 클랜 기본 정보/정책 변경
 * - 오너 및 멤버 역할/상태 변경
 * - 페인트 누적 카운트 및 프로필/배너 에셋 업서트 처리
 */
@Service
@RequiredArgsConstructor
public class ClanModifyService {

    private final ClanCommandRepository clanCommandRepo;
    private final ClanQueryRepository clanQueryRepo;
    private final ClanMemberCommandRepository clanMemberCommandRepo;
    private final ClanMemberQueryRepository clanMemberQueryRepo;

    private final ObjectMapper om;
    
    // ======================
    // 클랜 프로필 / 정책 변경
    // ======================

    /**
     * 클랜 기본 정보 및 가입 정책 변경.
     * <p>
     * - 이름, 설명, 메타데이터(metadataPatch), 가입 정책(joinPolicy) 등을 변경한다.
     * - actor는 해당 클랜의 OFFICER 이상이어야 한다.
     * - 낙관 잠금을 사용하며, 동시 수정 시 예외가 발생한다.
     *
     * - cmd.clanId: 수정할 클랜 ID
     * - cmd.name: 새 클랜 이름 (null 이면 변경 없음)
     * - cmd.description: 새 클랜 설명 (null 이면 변경 없음)
     * - cmd.metadataPatch: JSON Patch (merge) 형태의 메타데이터 변경
     * - cmd.joinPolicy: 새 가입 정책 (null 이면 변경 없음)
     * - actorId: 수정 요청자 유저 ID
     */
    public Mono<ClanDetail> updateClanProfile(UpdateClanProfileCommand cmd, UUID actorId) {
        UUID clanId = cmd.clanId();

        return ensureCanEditClan(clanId, actorId)
            .then(
                clanCommandRepo.updateClan(
                    clanId,
                    null,
                    cmd.name(),
                    cmd.description(),
                    cmd.metadataPatch(),
                    cmd.joinPolicy()
                )
            )
            .flatMap(updated -> {
                if (!updated) {
                    return Mono.error(new OptimisticLockingFailureException(
                        "Clan was modified by another transaction"
                    ));
                }
                return clanQueryRepo.requireClanDetail(clanId);
            });
    }

    // ======================
    // 오너 / 역할 / 멤버 상태 변경
    // ======================

    /**
     * 클랜 오너 변경.
     * <p>
     * - actor는 현재 클랜 오너여야 한다.
     * - targetUser는 해당 클랜의 ACTIVE 멤버여야 한다.
     * - 클랜의 ownerId를 targetUser로 변경하고,
     *   기존 오너 → OFFICER, 새 오너 → MASTER 로 역할도 함께 변경한다.
     * - actor와 targetUser가 동일하면 아무 변경 없이 true 반환.
     *
     * - clanId: 클랜 ID
     * - targetUserId: 새 오너로 지정할 멤버 ID
     * - actorId: 기존 오너(요청자) 유저 ID
     *
     * @return 변경 성공 여부 (true: 성공/무변경, 예외: 실패)
     */
    public Mono<Boolean> changeClanOwner(UUID clanId, UUID targetUserId, UUID actorId) {
        if (actorId.equals(targetUserId)) {
            return Mono.just(true);
        }

        return clanQueryRepo.isOwner(clanId, actorId)
            .flatMap(isOwner -> {
                if (!isOwner) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                return clanMemberQueryRepo.requireActiveMember(clanId, targetUserId);
            })
            .flatMap(target ->
                clanCommandRepo.updateClan(
                        clanId,
                        targetUserId, // newOwnerId
                        null,
                        null,
                        null,
                        null
                    )
                    .flatMap(updated -> {
                        if (!updated) {
                            return Mono.error(new OptimisticLockingFailureException(
                                "Clan was modified by another transaction"
                            ));
                        }

                        // 기존 오너 -> OFFICER, 새 오너 -> MASTER
                        return clanMemberCommandRepo.updateMemberRole(
                                    clanId, actorId, ClanMemberRole.OFFICER
                                )
                                .then(clanMemberCommandRepo.updateMemberRole(
                                    clanId, targetUserId, ClanMemberRole.MASTER
                                ))
                                .thenReturn(true);
                    })
            );
    }

    /**
     * 특정 멤버의 역할 변경.
     * <p>
     * - actor/target 모두 ACTIVE 멤버여야 하며, 같은 클랜에 속해야 한다.
     * - actorRole.canManage(targetRole) 를 만족해야 대상의 역할을 변경할 수 있다.
     * - actor 자신의 권한보다 높은 role로 변경하는 것은 불가하다.
     *
     * - clanId: 클랜 ID
     * - targetUserId: 역할이 변경될 대상 멤버 ID
     * - newRole: 새로 부여할 역할(MASTER/OFFICER/MEMBER)
     * - actorId: 역할 변경을 수행하는 유저 ID
     *
     * @return true: 역할 변경 성공
     */
    public Mono<Boolean> changeMemberRole(
        UUID clanId,
        UUID targetUserId,
        ClanMemberRole newRole,
        UUID actorId
    ) {
        Mono<ClanMemberDetail> actorMono  = clanMemberQueryRepo.findActiveMemberByUser(actorId);
        Mono<ClanMemberDetail> targetMono = clanMemberQueryRepo.findActiveMemberByUser(targetUserId);

        return Mono.zip(actorMono, targetMono)
            .switchIfEmpty(Mono.error(new NotFoundException(ClanErrorCode.CLAN_MEMBER_NOT_FOUND)))
            .flatMap(tuple -> {
                ClanMemberDetail actor  = tuple.getT1();
                ClanMemberDetail target = tuple.getT2();

                if (!actor.clanId().equals(clanId) || !target.clanId().equals(clanId)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                ClanMemberRole actorRole  = actor.role();
                ClanMemberRole targetRole = target.role();

                if (!actorRole.canManage(targetRole)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                if (newRole.isHigherThan(actorRole)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                return clanMemberCommandRepo.updateMemberRole(clanId, targetUserId, newRole);
            });
    }

    /**
     * 페인트 누적 카운트 증가.
     * <p>
     * - paint_count_total: clan_info, clan_member 양쪽 카운트를 증가시킨다.
     * - 해당 유저가 클랜의 ACTIVE 멤버인지 확인 후 증가.
     * - delta가 0 이하인 경우 아무 작업도 수행하지 않는다.
     *
     * - clanId: 클랜 ID
     * - userId: 페인트를 누적할 멤버 ID
     * - delta: 증가시킬 페인트 수
     */
    public void increasePaintCount(UUID clanId, UUID userId, long delta) {
        if (delta <= 0) return;

        var member = clanMemberQueryRepo.requireActiveMember(clanId, userId);
        if (member == null) {
            throw new NotFoundException(ClanErrorCode.CLAN_MEMBER_NOT_FOUND);
        }

        clanCommandRepo.incrementPaintCount(clanId, delta);
        clanMemberCommandRepo.incrementMemberPaintCount(clanId, userId, delta);
    }

    /**
     * 멤버 자발적 탈퇴 처리.
     * <p>
     * - 요청자가 해당 멤버 본인인지 확인한다.
     * - soft delete 후 낙관 락 실패 시 예외를 던진다.
     * - 기존 상태가 ACTIVE 였다면 클랜 멤버 수를 감소시킨다.
     *
     * - clanId: 탈퇴할 클랜 ID
     * - userId: 탈퇴를 수행하는 유저 ID
     */
    public Mono<Void> leaveClan(UUID clanId, UUID userId) {
        return clanMemberQueryRepo.requireActiveMember(clanId, userId)
            .flatMap(member -> {
                if (!member.userId().equals(userId)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                return clanMemberCommandRepo.softDeleteMember(clanId, userId)
                    .flatMap(ok -> {
                        if (!ok) {
                            return Mono.error(new OptimisticLockingFailureException("Member modified"));
                        }

                        if (ClanMemberStatus.ACTIVE.equals(member.status())) {
                            return clanCommandRepo.incrementMemberCount(clanId, -1);
                        }

                        return Mono.<Void>empty();
                    });
            })
            .then();
    }

    /**
     * 멤버 강퇴 처리.
     * <p>
     * - actor/target 모두 ACTIVE 멤버여야 하고, 같은 클랜에 속해야 한다.
     * - actorRole.canManage(targetRole)를 만족해야 강퇴 가능.
     * - 대상 멤버 상태를 KICKED 로 변경하고, ACTIVE → KICKED 인 경우 멤버 수 감소.
     *
     * - clanId: 클랜 ID
     * - targetUserId: 강퇴할 멤버 ID
     * - actorId: 강퇴를 수행하는 유저 ID
     */
    public Mono<Void> kickMember(UUID clanId, UUID targetUserId, UUID actorId) {
        Mono<ClanMemberDetail> actorMono  = clanMemberQueryRepo.findActiveMemberByUser(actorId);
        Mono<ClanMemberDetail> targetMono = clanMemberQueryRepo.findActiveMemberByUser(targetUserId);

        return Mono.zip(actorMono, targetMono)
            .switchIfEmpty(Mono.error(new NotFoundException(ClanErrorCode.CLAN_MEMBER_NOT_FOUND)))
            .flatMap(tuple -> {
                ClanMemberDetail actor  = tuple.getT1();
                ClanMemberDetail target = tuple.getT2();

                if (!actor.clanId().equals(clanId) || !target.clanId().equals(clanId)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                ClanMemberRole actorRole  = actor.role();
                ClanMemberRole targetRole = target.role();

                if (!actorRole.canManage(targetRole)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                return clanMemberCommandRepo.updateMemberStatus(
                        clanId, targetUserId, ClanMemberStatus.KICKED
                    )
                    .flatMap(ok -> {
                        if (!ok) {
                            return Mono.error(new OptimisticLockingFailureException("Member modified"));
                        }

                        if (ClanMemberStatus.ACTIVE.equals(target.status())) {
                            return clanCommandRepo.incrementMemberCount(clanId, -1);
                        }

                        return Mono.<Void>empty();
                    });
            })
            .then();
    }

    /**
     * 멤버 차단 처리.
     * <p>
     * - actor/target 모두 ACTIVE 멤버여야 하고, 같은 클랜에 속해야 한다.
     * - actorRole.canManage(targetRole)를 만족해야 차단 가능.
     * - 대상 멤버 상태를 BANNED 로 변경하고, ACTIVE → BANNED 인 경우 멤버 수 감소.
     *
     * - clanId: 클랜 ID
     * - targetUserId: 차단할 멤버 ID
     * - actorId: 차단을 수행하는 유저 ID
     */
    public Mono<Void> banMember(UUID clanId, UUID targetUserId, UUID actorId) {
        Mono<ClanMemberDetail> actorMono  = clanMemberQueryRepo.findActiveMemberByUser(actorId);
        Mono<ClanMemberDetail> targetMono = clanMemberQueryRepo.findActiveMemberByUser(targetUserId);

        return Mono.zip(actorMono, targetMono)
            .switchIfEmpty(Mono.error(new NotFoundException(ClanErrorCode.CLAN_MEMBER_NOT_FOUND)))
            .flatMap(tuple -> {
                ClanMemberDetail actor  = tuple.getT1();
                ClanMemberDetail target = tuple.getT2();

                if (!actor.clanId().equals(clanId) || !target.clanId().equals(clanId)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                ClanMemberRole actorRole  = actor.role();
                ClanMemberRole targetRole = target.role();

                if (!actorRole.canManage(targetRole)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                return clanMemberCommandRepo.updateMemberStatus(
                        clanId, targetUserId, ClanMemberStatus.BANNED
                    )
                    .flatMap(ok -> {
                        if (!ok) {
                            return Mono.error(new OptimisticLockingFailureException("Member modified"));
                        }

                        if (ClanMemberStatus.ACTIVE.equals(target.status())) {
                            return clanCommandRepo.incrementMemberCount(clanId, -1);
                        }

                        return Mono.<Void>empty();
                    });
            })
            .then();
    }

    // ======================
    // 에셋 업서트 적용
    // ======================

    /**
     * 프로필/배너 에셋 업서트 이벤트 반영.
     * <p>
     * - SubjectAssetUpsertEvent 를 받아 해당 클랜의 메타데이터에 에셋 정보를 반영한다.
     * - 목적(purpose)에 따라 clanProfile 또는 clanBanner 영역에 저장.
     * - 클랜이 존재하지 않거나 삭제된 경우 에러.
     * - 낙관 잠금 실패 시 예외 발생.
     *
     * - evt.subjectId: 클랜 ID
     * - evt.assetId: 업서트된 에셋 ID
     * - evt.publicUrl: 에셋 퍼블릭 URL
     * - evt.version: 에셋 버전
     * - evt.purpose: PROFILE / BANNER 등 에셋 목적
     */
    @Transactional
    public Mono<Void> applyAssetUpsert(SubjectAssetUpsertEvent evt) {
        UUID clanId = evt.subjectId();
        AssetPurpose purpose = evt.purpose();

        JsonNode patch = om.createObjectNode()
            .putObject(purpose == AssetPurpose.PROFILE ? "clanProfile" : "clanBanner")
                .put("assetId", evt.assetId().toString())
                .put("url", evt.publicUrl())
                .put("version", evt.version());

        return ensureAlive(clanId)
            .then(
                clanCommandRepo.updateClan(
                        clanId,
                        null,
                        null,
                        null,
                        patch,  
                        null
                    )
                    .flatMap(updated -> {
                        if (!updated) {
                            return Mono.error(new OptimisticLockingFailureException(
                                "Profile was modified by another transaction (asset upsert)"
                            ));
                        }
                        return Mono.<Void>empty();
                    })
            );
    }

    // ======================
    // 내부 헬퍼
    // ======================

    /**
     * 클랜이 활성 상태인지 확인.
     * <p>
     * - existsAlive(clanId)가 true 인지 확인.
     * - 아니라면 {@link NotFoundException} 발생.
     *
     * - clanId: 존재/활성 여부를 확인할 클랜 ID
     */
    private Mono<Void> ensureAlive(UUID clanId) {
        return clanQueryRepo.existsAlive(clanId)
            .filter(Boolean::booleanValue)
            .switchIfEmpty(Mono.error(new NotFoundException(ClanErrorCode.CLAN_NOT_FOUND)))
            .then();
    }

    /**
     * 클랜 수정 권한 검증.
     * <p>
     * - actorId가 해당 클랜의 ACTIVE 멤버인지 확인.
     * - MEMBER 는 수정 권한이 없으며, OFFICER 이상만 허용.
     * - ACTIVE 멤버가 아니면 NotFound → Forbidden 으로 매핑하여 외부에 권한 오류로 보이게 한다.
     *
     * - clanId: 수정 대상 클랜 ID
     * - actorId: 권한을 검증할 유저 ID
     */
    private Mono<Void> ensureCanEditClan(UUID clanId, UUID actorId) {
        return clanMemberQueryRepo.requireActiveMember(clanId, actorId)
            // ACTIVE 멤버가 아니면 접근 불가 (NotFound → Forbidden 으로 매핑)
            .onErrorMap(NotFoundException.class,
                e -> new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED))
            .flatMap(member -> {
                ClanMemberRole role = member.role();
                if (role == ClanMemberRole.MEMBER) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }
                return Mono.<Void>empty();
            });
    }
}