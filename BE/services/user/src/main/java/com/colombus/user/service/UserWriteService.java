package com.colombus.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.colombus.common.kafka.subject.event.SubjectAssetUpsertEvent;
import com.colombus.common.web.core.exception.InvalidInputException;
import com.colombus.common.web.core.exception.NotFoundException;
import com.colombus.user.client.AuthSvcClient;
import com.colombus.user.command.ActivateUserCommand;
import com.colombus.user.command.BlockUserCommand;
import com.colombus.user.command.ConfirmEmailChangeCommand;
import com.colombus.user.command.RestoreUserCommand;
import com.colombus.user.command.StartEmailChangeCommand;
import com.colombus.user.command.SoftDeleteUserCommand;
import com.colombus.user.command.UnblockUserCommand;
import com.colombus.user.command.UpdateProfileCommand;
import com.colombus.user.contract.dto.UserProfileResponse;
import com.colombus.user.repository.IdentityReadRepository;
import com.colombus.user.repository.UserReadRepository;
import com.colombus.user.repository.UserWriteRepository;
import com.colombus.user.web.internal.mapper.UserIdentityMapper;
import com.colombus.user.web.internal.mapper.UserTypeMapper;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 쓰기 서비스.
 * - 읽기 반환은 최신 상태를 위해 UserReadRepository로 조회해서 DTO로 리턴
 */
@Service
@RequiredArgsConstructor
public class UserWriteService {

    private final UserWriteRepository writeRepo;
    private final UserReadRepository userReadRepo;
    private final IdentityReadRepository identityReadRepository;

    private final AuthSvcClient authClient;

    private final ObjectMapper om;

    /** 사용자가 프로필 업데이트 */
    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileCommand cmd) {
        ensureAlive(cmd.userId());

        var updated = writeRepo.updateProfile(
            cmd.userId(),
            cmd.nickname(),
            cmd.metadataPatch()
        );
        if(!updated) throw new OptimisticLockingFailureException("Profile was modified by another transaction");

        return requireProfile(cmd.userId());
    }

    /** AssetUpsert 이벤트로 프로필 이미지 업데이트 동기화 */
    @Transactional
    public void applyAssetUpsert(SubjectAssetUpsertEvent evt) {
        UUID userId = evt.subjectId();
        ensureAlive(userId);

        ObjectNode patch = om.createObjectNode()
            .putObject("profileImage")
                .put("assetId", evt.assetId().toString())
                .put("url", evt.publicUrl())
                .put("version", evt.version());

        boolean updated = writeRepo.updateProfile(
            userId,
            null,
            patch
        );

        
        if (!updated) {
            throw new OptimisticLockingFailureException("Profile was modified by another transaction (asset upsert)");
        }

        authClient.updateAvatar(userId, evt.publicUrl());
    }

    /** 이메일 변경 시작: pendingEmail에 저장 (이메일 검증 메일 발송은 상위 레이어에서 처리) */
    @Transactional
    public void startEmailChange(StartEmailChangeCommand cmd) {
        ensureAlive(cmd.userId());

        boolean updated = writeRepo.setPendingEmail(cmd.userId(), cmd.newEmail());
        if (!updated) throw new OptimisticLockingFailureException("Email change conflict (stale updatedAt)");
    }

    /** 이메일 변경 확정: 검증 완료 콜백(웹훅 등)에서 호출 */
    @Transactional
    public UserProfileResponse confirmEmailChange(ConfirmEmailChangeCommand cmd) {
        ensureAlive(cmd.userId());

        boolean confirmed = writeRepo.confirmEmail(cmd.userId(), cmd.verifiedEmail());
        if (!confirmed) {
            // 조건 불일치: pendingEmail 미존재/불일치 or 낙관적 잠금 실패
            // throw new InvalidState("Pending email not found or does not match verifiedEmail");
        }
        return requireProfile(cmd.userId());
    }

    // ===== Admin ops =====

    @Transactional
    public UserProfileResponse block(BlockUserCommand cmd) {
        ensureAlive(cmd.userId());

        var updated = writeRepo.block(cmd.userId(), cmd.reason());
        if (!updated) throw new OptimisticLockingFailureException("Block failed due to stale updatedAt");

        return requireProfile(cmd.userId());
    }

    @Transactional
    public UserProfileResponse unblock(UnblockUserCommand cmd) {
        ensureAlive(cmd.userId());

        boolean updated = writeRepo.unblock(cmd.userId());
        if (!updated) throw new OptimisticLockingFailureException("Unblock failed due to stale updatedAt");

        return requireProfile(cmd.userId());
    }

    @Transactional
    public UserProfileResponse setActive(ActivateUserCommand cmd) {
        ensureAlive(cmd.userId());

        boolean updated = writeRepo.setActive(cmd.userId(), cmd.active());
        if (!updated) throw new OptimisticLockingFailureException("Activate/deactivate failed due to stale updatedAt");

        return requireProfile(cmd.userId());
    }

    @Transactional
    public void softDelete(SoftDeleteUserCommand cmd) {
        ensureAlive(cmd.userId());

        boolean updated = writeRepo.softDelete(cmd.userId());
        if (!updated) throw new OptimisticLockingFailureException("Soft delete failed due to stale updatedAt");
    }

    @Transactional
    public UserProfileResponse restore(RestoreUserCommand cmd) {
        // 삭제 상태에서만 복구
        boolean updated = (Boolean.TRUE.equals(cmd.nullifyEmailIfConflict()))
            ? writeRepo.restoreNullifyEmailOnConflict(cmd.userId())
            : writeRepo.restore(cmd.userId());
        if (!updated) throw new InvalidInputException("User is not soft-deleted or restore failed");

        return requireProfile(cmd.userId());
    }

    // ===== helpers =====

    private void ensureAlive(UUID userId) {
        if (!userReadRepo.existsAlive(userId)) {
            throw new NotFoundException("User not found or deleted: " + userId);
        }
    }
    private UserProfileResponse requireProfile(UUID userId) {
        var acc = userReadRepo.findBasicUserById(userId);
        if (acc == null) throw new NotFoundException("User not found: " + userId);
        
        var roleCodes = userReadRepo.findRoles(userId).stream()
                                    .map(UserTypeMapper::toCode)
                                    .toList();

        var identityDtos = identityReadRepository.findIdentities(userId).stream()
                                                 .map(UserIdentityMapper::toDto)
                                                 .toList();

        return new UserProfileResponse(
            acc.id(),
            acc.email(),
            acc.emailVerified(),
            acc.nickname(),
            acc.nicknameSeq(),
            acc.nicknameHandle(),
            acc.isActive(),
            acc.loginCount(),
            acc.metadata(),
            acc.paintCountTotal(),
            acc.lastLoginAt(),
            acc.blockedAt(),
            acc.blockedReason(),
            acc.createdAt(),
            acc.updatedAt(),
            roleCodes,
            identityDtos
        );
    }

    
}