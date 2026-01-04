package com.colombus.clan.service;

import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colombus.clan.command.CancelJoinClanCommand;
import com.colombus.clan.command.ChangeClanOwnerCommand;
import com.colombus.clan.command.ChangeMemberRoleCommand;
import com.colombus.clan.command.ChangeMemberStatusCommand;
import com.colombus.clan.command.IssueInviteLinkCommand;
import com.colombus.clan.command.JoinClanByInviteCommand;
import com.colombus.clan.command.JoinClanCommand;
import com.colombus.clan.command.LeaveClanCommand;
import com.colombus.clan.command.ReviewJoinRequestCommand;
import com.colombus.clan.exception.ClanErrorCode;
import com.colombus.clan.messaging.outbox.repository.OutboxPublishRepository;
import com.colombus.clan.model.ClanDetail;
import com.colombus.clan.model.ClanInviteLink;
import com.colombus.clan.model.ClanMemberDetail;
import com.colombus.clan.model.type.ClanJoinPolicy;
import com.colombus.clan.model.type.ClanJoinRequestStatus;
import com.colombus.clan.model.type.ClanMemberRole;
import com.colombus.clan.model.type.ClanMemberStatus;
import com.colombus.clan.repository.ClanCommandRepository;
import com.colombus.clan.repository.ClanMemberCommandRepository;
import com.colombus.clan.repository.ClanMemberQueryRepository;
import com.colombus.clan.repository.ClanQueryRepository;
import com.colombus.common.web.core.exception.ConflictException;
import com.colombus.common.web.core.exception.ForbiddenException;
import com.colombus.common.web.core.exception.InvalidInputException;
import com.colombus.common.web.core.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * 클랜 멤버십 변경/관리 도메인 서비스.
 * <p>
 * - 클랜 가입/탈퇴 및 초대 코드 가입
 * - 가입 요청 승인/거절/취소
 * - 클랜 오너 변경, 멤버 역할/상태 변경 등 쓰기 연산을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClanMemberService {
    
    private final ClanCommandRepository clanCommandRepo;
    private final ClanQueryRepository clanQueryRepo;
    private final ClanMemberCommandRepository clanMemberCommandRepo;
    private final ClanMemberQueryRepository clanMemberQueryRepo;
    private final OutboxPublishRepository outboxPubRepo;

    // ======================
    // 클랜 가입 / 가입 요청
    // ======================

    /**
     * 클랜 가입 처리.
     * <p>
     * - 유저당 1클랜 제약: 이미 다른 클랜 ACTIVE 멤버면 가입 불가.
     * - 클랜 정책에 따라 동작:
     *   - OPEN     : 즉시 가입 (ACTIVE 멤버 생성 + 멤버 수 증가 + outbox 발행)
     *   - APPROVAL : 가입 요청(PENDING)만 생성
     *   - INVITE_ONLY 등: 일반 가입 차단
     *
     * - cmd.clanId: 가입하려는 클랜 ID
     * - cmd.userId: 가입을 시도하는 유저 ID
     */
    public Mono<Void> joinClan(JoinClanCommand cmd) {
        UUID clanId = cmd.clanId();
        UUID userId = cmd.userId();

        // 이미 다른 클랜에 ACTIVE 멤버인지 체크
        return clanMemberQueryRepo.findActiveMemberByUser(userId)
            .flatMap(m -> Mono.<Void>error(
                new InvalidInputException(
                    ClanErrorCode.CLAN_ALREADY_MEMBER,
                    "이미 가입된 클랜이 있습니다."
                )
            ))
            .switchIfEmpty(
                Mono.defer(() ->
                    // 클랜 상세 조회
                    clanQueryRepo.requireClanDetail(clanId)
                        .flatMap(clan -> {
                            ClanJoinPolicy policy = clan.joinPolicy();

                            if (policy == ClanJoinPolicy.OPEN) {
                                // 즉시 가입
                                return joinClanImmediately(clanId, userId);
                            }

                            if (policy == ClanJoinPolicy.APPROVAL) {
                                // 가입 요청 생성
                                return requestClanJoin(clanId, userId);
                            }

                            // INVITE_ONLY or 기타 (안전하게 막기)
                            return Mono.error(new InvalidInputException(
                                ClanErrorCode.CLAN_JOIN_NOT_ALLOWED,
                                "초대 전용 클랜이므로 직접 가입할 수 없습니다."
                            ));
                        })
                )
            );
    }

    /**
     * OPEN 정책 또는 초대 코드에 의한 즉시 가입 처리.
     * <p>
     * - ACTIVE 멤버 레코드 생성
     * - 클랜 멤버 수 증가
     * - clan.membership.current.v1 outbox 이벤트 발행
     *
     * - clanId: 가입 대상 클랜 ID
     * - userId: 가입할 유저 ID
     */
    private Mono<Void> joinClanImmediately(UUID clanId, UUID userId) {
        return clanMemberCommandRepo.insertMember(
                    clanId,
                    userId,
                    ClanMemberRole.MEMBER,
                    ClanMemberStatus.ACTIVE
                )
                .then(clanCommandRepo.incrementMemberCount(clanId, +1))
                .then(outboxPubRepo.appendClanMembershipUpsert(userId, clanId, true))
                .then(); // Mono<Void>
    }

    /**
     * APPROVAL 정책 클랜의 가입 요청 생성.
     * <p>
     * - 이미 PENDING 가입 요청이 존재하면 에러.
     * - 신규 PENDING 요청을 생성한다.
     *
     * - clanId: 가입 요청을 생성할 클랜 ID
     * - userId: 가입을 신청하는 유저 ID
     */
    private Mono<Void> requestClanJoin(UUID clanId, UUID userId) {
        return clanMemberQueryRepo.findPendingByClanAndUser(clanId, userId)
            .flatMap(exist ->
                Mono.<Void>error(new InvalidInputException(ClanErrorCode.CLAN_JOIN_REQUEST_ALREADY_PENDING))
            )
            .switchIfEmpty(
                Mono.defer(() ->
                    clanMemberCommandRepo.insertJoinRequest(clanId, userId)
                        .then() // Mono<UUID> → Mono<Void>
                )
            );
    }

    // ======================
    // 초대 링크 / 초대 코드 가입
    // ======================

    /**
     * 클랜 초대 링크 발급.
     * <p>
     * - MASTER/OFFICER 만 발급 가능.
     * - maxUses, TTL 등은 내부 티켓 정책에 따름.
     *
     * - cmd.clanId: 초대 링크를 발급할 클랜 ID
     * - cmd.actorId: 초대 링크를 발급하는 관리자/오피서 ID
     * - cmd.maxUses: 사용 가능 횟수 (null/0 이면 무제한 등 구현 정책에 따름)
     */
    public Mono<ClanInviteLink> issueInviteLink(IssueInviteLinkCommand cmd) {
        UUID clanId = cmd.clanId();
        UUID actorId = cmd.actorId();

        return ensureCanManageClan(clanId, actorId) // MASTER / OFFICER 권한 체크
            .then(clanMemberCommandRepo.issueInviteTicket(clanId, actorId, cmd.maxUses()))
            .map(ticket -> {
                String code = ticket.code();
                // TODO: 프론트 기준으로 맞춰서 수정 해야할 수도 있음
                String url = code;

                return new ClanInviteLink(code, url, ticket.expiresAt());
            });
    }

    /**
     * 초대 코드로 클랜 가입.
     * <p>
     * - 이미 다른 클랜 ACTIVE 멤버면 가입 불가.
     * - 초대 티켓 유효성/TTL/사용 횟수 검증 후 처리.
     * - 현재 구현에서는 정책과 무관하게 초대가 유효하면 즉시 가입 허용.
     *
     * - cmd.userId: 초대를 통해 가입할 유저 ID
     * - cmd.inviteCode: 초대 코드
     */
    public Mono<Void> joinClanByInvite(JoinClanByInviteCommand cmd) {
        String code = cmd.inviteCode();
        UUID userId = cmd.userId();

        // 이미 다른 클랜 ACTIVE 멤버인지 체크
        return clanMemberQueryRepo.findActiveMemberByUser(userId)
            .flatMap(m -> Mono.<Void>error(
                new InvalidInputException(
                    ClanErrorCode.CLAN_ALREADY_MEMBER,
                    "이미 가입된 클랜이 있습니다."
                )
            ))
            .switchIfEmpty(
                Mono.defer(() ->
                    // 초대 티켓 소비 (유효성 + TTL + 사용횟수 체크)
                    clanMemberCommandRepo.consumeValidTicket(code)
                        // 해당 클랜 정보 조회
                        .flatMap(ticket ->
                            clanQueryRepo.requireClanDetail(ticket.clanId())
                                .flatMap(clan -> {
                                    // 정책에 따라 초대 허용 여부 체크
                                    return handleJoinByInvite(clan, userId);
                                })
                        )
                )
            );
    }

    /**
     * 초대 코드로 들어온 경우의 가입 처리.
     * <p>
     * - 클랜이 살아 있는지 확인 (삭제/비활성 클랜 방지).
     * - 유효하면 즉시 가입 로직으로 위임.
     *
     * - clan: 초대 티켓이 가리키는 클랜 상세 정보
     * - userId: 초대로 가입하는 유저 ID
     */
    private Mono<Void> handleJoinByInvite(ClanDetail clan, UUID userId) {
        UUID clanId = clan.id();

        return clanQueryRepo.existsAlive(clanId)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.<Void>error(
                        new InvalidInputException(ClanErrorCode.CLAN_NOT_FOUND));
                }

                return joinClanImmediately(clanId, userId);
            });
    }

    // ======================
    // 가입 요청 승인 / 거절 / 취소
    // ======================

    /**
     * 가입 요청 승인.
     * <p>
     * - actor는 해당 클랜의 MASTER/OFFICER 여야 한다.
     * - 승인 시점에 targetUser가 다른 클랜 ACTIVE 멤버인지 다시 한 번 검증.
     * - PENDING → APPROVED 로 상태 전환 후, 실제 멤버 생성 + 멤버 수 증가 + outbox 발행.
     *
     * - cmd.clanId: 클랜 ID
     * - cmd.targetUserId: 가입 요청을 보낸 유저 ID
     * - cmd.actorId: 승인하는 관리자/오피서 ID
     */
    public Mono<Void> approveJoinRequest(ReviewJoinRequestCommand cmd) {
        UUID clanId      = cmd.clanId();
        UUID targetUserId = cmd.targetUserId();
        UUID actorId     = cmd.actorId();

        return ensureCanManageClan(clanId, actorId)
                .then(
                    // 해당 유저의 PENDING 가입 요청 조회
                    clanMemberQueryRepo.findPendingByClanAndUser(clanId, targetUserId)
                        .switchIfEmpty(Mono.error(
                            new InvalidInputException(
                                ClanErrorCode.CLAN_JOIN_REQUEST_NOT_FOUND,
                                "승인 가능한 가입 요청이 없습니다."
                            )
                        ))
                        .flatMap(req ->
                            // 승인 시점에 이미 다른 클랜 ACTIVE 멤버인지 한 번 더 체크
                            clanMemberQueryRepo.findActiveMemberByUser(targetUserId)
                                .flatMap(m -> Mono.<Void>error(
                                    new InvalidInputException(
                                        ClanErrorCode.CLAN_ALREADY_MEMBER,
                                        "이미 다른 클랜에 가입된 상태입니다."
                                    )
                                ))
                                .switchIfEmpty(
                                    Mono.defer(() ->
                                        // 가입 요청 상태를 APPROVED로 변경 (낙관 락 포함)
                                        clanMemberCommandRepo.updateJoinRequestStatus(
                                                req.id(),
                                                ClanJoinRequestStatus.APPROVED,
                                                actorId
                                            )
                                            .flatMap(updated -> {
                                                if (!updated) {
                                                    // updated_at 충돌 등으로 인한 동시성 실패
                                                    return Mono.<Void>error(new InvalidInputException(ClanErrorCode.CLAN_JOIN_REQUEST_CONFLICT));
                                                }
                                                // 실제 멤버 추가 + 멤버 수 증가 + outbox 발행
                                                return joinClanImmediately(clanId, targetUserId);
                                            })
                                    )
                                )
                        )
                );
    }

    /**
     * 가입 요청 거절.
     * <p>
     * - actor는 해당 클랜의 MASTER/OFFICER 여야 한다.
     * - PENDING 가입 요청이 없으면 에러.
     * - PENDING → REJECTED 로 상태 전환만 수행.
     *
     * - cmd.clanId: 클랜 ID
     * - cmd.targetUserId: 가입 요청을 보낸 유저 ID
     * - cmd.actorId: 거절하는 관리자/오피서 ID
     */
    public Mono<Void> rejectJoinRequest(ReviewJoinRequestCommand cmd) {
        UUID clanId       = cmd.clanId();
        UUID targetUserId = cmd.targetUserId();
        UUID actorId      = cmd.actorId();

        return ensureCanManageClan(clanId, actorId)
                .then(
                    clanMemberQueryRepo.findPendingByClanAndUser(clanId, targetUserId)
                        .switchIfEmpty(Mono.error(
                            new InvalidInputException(
                                ClanErrorCode.CLAN_JOIN_REQUEST_NOT_FOUND,
                                "거절 가능한 가입 요청이 없습니다."
                            )
                        ))
                        .flatMap(req ->
                            clanMemberCommandRepo.updateJoinRequestStatus(
                                    req.id(),
                                    ClanJoinRequestStatus.REJECTED,
                                    actorId
                                )
                                .flatMap(updated -> {
                                    if (!updated) {
                                        return Mono.<Void>error(
                                            new InvalidInputException(
                                                ClanErrorCode.CLAN_JOIN_REQUEST_CONFLICT,
                                                "가입 요청 상태가 이미 변경되었습니다."
                                            )
                                        );
                                    }
                                    return Mono.empty();
                                })
                        )
                );
    }

    /**
     * 본인의 가입 요청 취소.
     * <p>
     * - PENDING 상태의 자신의 가입 요청을 CANCELLED 로 변경.
     * - 이미 처리된 요청이면 {@link ConflictException} 발생.
     *
     * - cmd.clanId: 가입 요청을 취소할 클랜 ID
     * - cmd.actorId: 가입 요청을 취소하는 본인 유저 ID
     */
    public Mono<Void> cancelJoinRequest(CancelJoinClanCommand cmd) {
        UUID clanId = cmd.clanId();
        UUID actorId = cmd.actorId();

        return clanMemberCommandRepo.cancelOwnJoinRequest(clanId, actorId)
                .flatMap(success -> {
                if (!success) {
                    return Mono.error(new ConflictException(ClanErrorCode.CLAN_JOIN_REQUEST_CONFLICT));
                }
                return Mono.empty();
            });
    }

    // ======================
    // 멤버 탈퇴
    // ======================

    /**
     * 멤버 자발적 탈퇴 (LEFT).
     * <p>
     * - 클랜 오너는 오너를 다른 멤버에게 양도하지 않으면 탈퇴할 수 없다.
     * - ACTIVE 멤버를 soft-delete 하고, 멤버 수 감소 + outbox 발행은 내부 헬퍼에서 처리.
     *
     * - cmd.clanId: 탈퇴할 클랜 ID
     * - cmd.userId: 탈퇴를 수행하는 유저 ID
     */
    public Mono<Void> leaveClan(LeaveClanCommand cmd) {
        UUID clanId = cmd.clanId();
        UUID userId = cmd.userId();

        return clanQueryRepo.isOwner(clanId, userId)
            .flatMap(isOwner -> {
                if (isOwner) {
                    return Mono.error(new InvalidInputException(ClanErrorCode.CLAN_OWNER_CANNOT_LEAVE));
                }
                // 오너 아니면 멤버 로딩
                return clanMemberQueryRepo.requireActiveMember(clanId, userId);
            })
            .flatMap(member -> applyLeave(clanId, userId, member))
            .then(); // Mono<Void>
    }

    /**
     * 탈퇴 공통 처리.
     * <p>
     * - soft delete 후 낙관 락 실패 시 예외.
     * - 기존 상태가 ACTIVE 였으면 멤버 수 감소 + membership outbox 발행.
     *
     * - clanId: 클랜 ID
     * - userId: 탈퇴하는 유저 ID
     * - member: 탈퇴 전 멤버 상세 정보
     */
    private Mono<Void> applyLeave(UUID clanId, UUID userId, ClanMemberDetail member) {
        return clanMemberCommandRepo.softDeleteMember(clanId, userId)
            .flatMap(ok -> {
                if (!ok) {
                    return Mono.error(new OptimisticLockingFailureException("Member modified"));
                }

                if (member.status() != ClanMemberStatus.ACTIVE) {
                    return Mono.empty();
                }

                return clanCommandRepo.incrementMemberCount(clanId, -1)
                .then(outboxPubRepo.appendClanMembershipUpsert(userId, null, false))
                .then();
            });
    }

    // ======================
    // 오너 / 역할 / 상태 변경
    // ======================

    /**
     * 클랜 오너 변경.
     * <p>
     * - actor는 현재 클랜 오너여야 한다.
     * - targetUser는 해당 클랜의 ACTIVE 멤버여야 한다.
     * - 클랜 레코드에 newOwnerId를 반영(낙관 락)하고,
     *   기존 오너 → OFFICER, 신규 오너 → MASTER 로 역할을 변경한다.
     * - actor와 targetUser가 동일하면 아무 변경 없이 종료.
     *
     * - cmd.clanId: 클랜 ID
     * - cmd.actorId: 기존 오너(요청자) ID
     * - cmd.targetUserId: 새 오너로 지정할 멤버 ID
     */
    public Mono<Void> changeClanOwner(ChangeClanOwnerCommand cmd) {
        UUID clanId     = cmd.clanId();
        UUID actorId    = cmd.actorId();
        UUID targetUser = cmd.targetUserId();

        if (actorId.equals(targetUser)) {
            return Mono.empty();
        }

        return clanQueryRepo.isOwner(clanId, actorId)
            .flatMap(isOwner -> {
                if (!isOwner) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                // 타깃 멤버가 활성 멤버인지 확인
                return clanMemberQueryRepo.requireActiveMember(clanId, targetUser)
                    .switchIfEmpty(Mono.error(new NotFoundException(ClanErrorCode.CLAN_MEMBER_NOT_FOUND)))
                    // 클랜 오너 변경 (낙관락 포함, false면 동시 수정 실패)
                    .flatMap(member ->
                        clanCommandRepo.updateClan(
                            clanId,
                            targetUser, // newOwnerId
                            null,
                            null,
                            null,
                            null
                        )
                    )
                    .flatMap(updated -> {
                        if (!updated) {
                            return Mono.error(new OptimisticLockingFailureException(
                                "Clan was modified by another transaction"
                            ));
                        }

                        // 역할 변경: 기존 오너 -> OFFICER
                        return clanMemberCommandRepo.updateMemberRole(
                                clanId,
                                actorId,
                                ClanMemberRole.OFFICER
                            )
                            // 새 오너 -> MASTER
                            .then(clanMemberCommandRepo.updateMemberRole(
                                clanId,
                                targetUser,
                                ClanMemberRole.MASTER
                            ));
                    });
            })
            .then();
    }

    /**
     * 특정 멤버의 역할 변경.
     * <p>
     * - actor/target 둘 다 ACTIVE 멤버여야 하며, 같은 클랜에 속해야 한다.
     * - actorRole.canManage(targetRole) 를 만족해야 target을 관리할 수 있다.
     * - actor 자신의 권한보다 높은 role로 설정하는 것은 불가.
     *
     * - cmd.clanId: 클랜 ID
     * - cmd.actorId: 역할 변경을 수행하는 유저 ID
     * - cmd.targetUserId: 역할이 변경될 대상 멤버 ID
     * - cmd.newRole: 새로 부여할 역할(MASTER/OFFICER/MEMBER)
     */
    public Mono<Void> changeMemberRole(ChangeMemberRoleCommand cmd) {
        UUID clanId     = cmd.clanId();
        UUID actorId    = cmd.actorId();
        UUID targetUser = cmd.targetUserId();
        ClanMemberRole newRole = cmd.newRole();

        Mono<ClanMemberDetail> actorMono  = clanMemberQueryRepo.findActiveMemberByUser(actorId);
        Mono<ClanMemberDetail> targetMono = clanMemberQueryRepo.findActiveMemberByUser(targetUser);

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

                // target을 관리할 수 있는지
                if (!actorRole.canManage(targetRole)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                // 자기 권한보다 높은 role로 변경 불가
                if (newRole.isHigherThan(actorRole)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                return clanMemberCommandRepo.updateMemberRole(clanId, targetUser, newRole)
                                            .then(); // Mono<Void>
            });
    }

    /**
     * 멤버 상태 변경 (강퇴/차단).
     * <p>
     * - LEFT 는 여기서 다루지 않고 {@link #leaveClan(LeaveClanCommand)} 를 통해 처리.
     * - 허용 상태: KICKED, BANNED 만 처리한다.
     * - actor/target 둘 다 해당 클랜의 ACTIVE 멤버여야 하며,
     *   actorRole.canManage(targetRole)를 만족해야 한다.
     * - 자기 자신을 KICKED/BANNED 로 변경하는 것은 허용하지 않는다.
     * - ACTIVE → KICKED/BANNED 로 바뀌면 멤버 수를 감소시킨다.
     *
     * - cmd.clanId: 클랜 ID
     * - cmd.actorId: 상태 변경을 수행하는 유저 ID
     * - cmd.targetUserId: 상태가 변경될 대상 멤버 ID
     * - cmd.newStatus: 새 멤버 상태(KICKED/BANNED)
     */
    public Mono<Void> changeMemberStatus(ChangeMemberStatusCommand cmd) {
        UUID clanId         = cmd.clanId();
        UUID actorId        = cmd.actorId();
        UUID targetUser     = cmd.targetUserId();
        ClanMemberStatus newStatus = cmd.newStatus();

        // 허용 상태만 처리
        if (newStatus != ClanMemberStatus.KICKED
            && newStatus != ClanMemberStatus.BANNED) {
            return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
        }

        // 대상 멤버 (ACTIVE) 조회
        Mono<ClanMemberDetail> targetMono =
            clanMemberQueryRepo.requireActiveMember(clanId, targetUser);

        // ───────── KICKED / BANNED (강퇴/차단) 케이스 ─────────
        return targetMono
            .zipWith(
                clanMemberQueryRepo.findActiveMemberByUser(actorId)
                    .switchIfEmpty(Mono.error(new NotFoundException(ClanErrorCode.CLAN_MEMBER_NOT_FOUND)))
            )
            .flatMap(tuple -> {
                ClanMemberDetail target = tuple.getT1();
                ClanMemberDetail actor  = tuple.getT2();

                // 같은 클랜이어야 함
                if (!actor.clanId().equals(clanId)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }
                // 자기 자신을 강퇴/차단 불가
                if (actorId.equals(targetUser)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                ClanMemberRole actorRole  = actor.role();
                ClanMemberRole targetRole = target.role();

                if (!actorRole.canManage(targetRole)) {
                    return Mono.error(new ForbiddenException(ClanErrorCode.CLAN_ACCESS_DENIED));
                }

                return clanMemberCommandRepo.updateMemberStatus(
                        clanId, targetUser, newStatus
                    )
                    .flatMap(ok -> {
                        if (!ok) {
                            return Mono.error(new OptimisticLockingFailureException("Member modified"));
                        }

                        // ACTIVE → KICKED/BANNED 면 멤버 수만 감소
                        if (target.status() == ClanMemberStatus.ACTIVE) {
                            return clanCommandRepo.incrementMemberCount(clanId, -1);
                        }

                        return Mono.<Void>empty();
                    });
            })
            .then();
    }

    // ======================
    // 내부 권한 검증
    // ======================

    /**
     * 특정 유저가 해당 클랜을 관리할 수 있는지 검증.
     * <p>
     * - clanId + actorId로 ACTIVE 멤버를 조회.
     * - 역할이 MASTER 또는 OFFICER 여야 통과.
     * - 조건을 만족하지 못하면 {@link InvalidInputException} 발생.
     *
     * - clanId: 권한 확인 대상 클랜 ID
     * - actorId: 권한을 검증할 유저 ID
     */
    private Mono<Void> ensureCanManageClan(UUID clanId, UUID actorId) {
        return clanMemberQueryRepo.findActiveByClanAndUser(clanId, actorId)
        .switchIfEmpty(Mono.error(
            new InvalidInputException(ClanErrorCode.CLAN_ACCESS_DENIED)
        ))
        .flatMap(member -> {
            ClanMemberRole role = member.role();

            if (role == ClanMemberRole.MASTER || role == ClanMemberRole.OFFICER) {
                return Mono.empty(); // 통과
            }

            return Mono.error(new InvalidInputException(ClanErrorCode.CLAN_ACCESS_DENIED));
        });
    }
}