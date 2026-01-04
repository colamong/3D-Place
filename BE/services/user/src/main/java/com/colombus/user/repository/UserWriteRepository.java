package com.colombus.user.repository;

import static com.colombus.user.jooq.tables.UserAccount.USER_ACCOUNT;
import com.colombus.common.utility.json.Jsons;
import com.colombus.user.exception.EmailAlreadyInUseException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.exception.DataChangedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * user_account 쓰기 전용 JDBC 리포지토리. - 모든 업데이트는 deleted_at IS NULL 조건을 포함 - 낙관적 잠금: updated_at = :expected
 */
@Repository
@RequiredArgsConstructor
public class UserWriteRepository {

    private final DSLContext dsl;

    // =============================
    // 유저 상태 변경
    // ==============================

    /** 관리자: 사용자 차단 */
    public boolean block(UUID userId, String reason) {
        var r = dsl.fetchOne(USER_ACCOUNT, USER_ACCOUNT.ID.eq(userId));
        if (r == null || r.getDeletedAt() != null) return false;

        r.setBlockedAt(Instant.now());
        r.setBlockedReason(reason);
        try {
            r.store();
            return true;
        } catch (DataChangedException e) {

            return false;
        }
    }

    /** 관리자: 사용자 차단 해제 */
    public boolean unblock(UUID userId) {
        var r = dsl.fetchOne(USER_ACCOUNT, USER_ACCOUNT.ID.eq(userId));
        if (r == null || r.getDeletedAt() != null) return false;

        r.setBlockedAt(null);
        r.setBlockedReason(null);
        try {
            r.store();
            return true;
        } catch (DataChangedException e) {

            return false;
        }
    }

    /** 활성/비활성 전환 */
    public boolean setActive(UUID userId, boolean active) {
        var r = dsl.fetchOne(USER_ACCOUNT, USER_ACCOUNT.ID.eq(userId));
        if (r == null || r.getDeletedAt() != null) return false;

        r.setIsActive(active);
        try {
            r.store();
            return true;
        } catch (DataChangedException e) {

            return false;
        }
    }

    /** 소프트 삭제: is_active=false, deleted_at=now() (체크 제약 충족) */
    public Boolean softDelete(UUID userId) {
        var r = dsl.fetchOne(USER_ACCOUNT, USER_ACCOUNT.ID.eq(userId));
        if (r == null || r.getDeletedAt() != null) return false;

        r.setIsActive(false);
        r.setDeletedAt(Instant.now());
        try {
            r.store();
            return true;
        } catch (DataChangedException e) {

            return false;
        }
    }

    /** 복구: deleted_at=NULL. email 충돌 시 DuplicateKeyException 발생 */
    public Boolean restore(UUID userId) {
        var r = dsl.fetchOne(USER_ACCOUNT, USER_ACCOUNT.ID.eq(userId));
        if (r == null || r.getDeletedAt() == null) return false;

        r.setDeletedAt(null);
        try {
            r.store(); // UNIQUE(email) 충돌 시 DataIntegrityViolationException
            return true;
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyInUseException(e);
        } catch (DataChangedException e) {
            return false;
        }
    }

    /** 복구(충돌 시 email NULL로 재시도) */
    public Boolean restoreNullifyEmailOnConflict(UUID userId) {
        try {
            return restore(userId);
        } catch (EmailAlreadyInUseException ex) {
            var r = dsl.fetchOne(USER_ACCOUNT, USER_ACCOUNT.ID.eq(userId));
            if (r == null || r.getDeletedAt() == null) return false;

            r.setEmail(null);
            r.setDeletedAt(null);
            try {
                r.store();
                return true;
            } catch (DataChangedException e) {

                return false;
            }
        }
    }

    // =============================
    // 프로필 업데이트
    // =============================

    /** nickname 및 metadata 업데이트 */
    public boolean updateProfile(
        UUID userId, @Nullable String nickname, @Nullable JsonNode metadataPatch) {
        var r = dsl.fetchOne(USER_ACCOUNT, USER_ACCOUNT.ID.eq(userId));
        if (r == null || r.getDeletedAt() != null) return false;

        if (nickname != null && !nickname.equals(r.getNickname())) {
            r.setNickname(nickname);
            r.setNicknameSeq(null); // 트리거가 새 seq 부여
        }

        if (metadataPatch != null) {
            var current = r.getMetadata(); // JsonNode 컨버터 전제
            var merged = Jsons.mergePatchObject(current, metadataPatch);
            r.setMetadata(merged);
        }

        try {
            r.store();
            return true;
        } catch (DataChangedException e) {

            return false;
        }
    }

    // ============================
    // 이메일 관리
    // ============================

    /** 이메일 변경 "요청" 단계: metadata.pendingEmail 세팅(중복 방지 목적상 단순 오버라이트) */
    public boolean setPendingEmail(UUID userId, String newEmail) {
        var r = dsl.fetchOne(USER_ACCOUNT, USER_ACCOUNT.ID.eq(userId));
        if (r == null || r.getDeletedAt() != null) return false;

        var meta = r.getMetadata();
        var merged = Jsons.put(meta, "pendingEmail", newEmail);
        r.setMetadata(merged);

        try {
            r.store();
            return true;
        } catch (DataChangedException e) {

            return false;
        }
    }

    /**
     * 이메일 변경 "확정": metadata.pendingEmail가 verifiedEmail과 일치할 때만 적용. email=?, email_verified=TRUE,
     * metadata에서 pendingEmail 제거.
     */
    public boolean confirmEmail(UUID userId, String verifiedEmail) {
        var r = dsl.fetchOne(USER_ACCOUNT, USER_ACCOUNT.ID.eq(userId));
        if (r == null || r.getDeletedAt() != null) return false;

        var pending = Jsons.textAt(r.getMetadata(), "pendingEmail");
        if (pending == null || !pending.equalsIgnoreCase(verifiedEmail)) return false;

        r.setEmail(verifiedEmail);
        r.setEmailVerified(true);
        r.setMetadata(Jsons.remove(r.getMetadata(), "pendingEmail"));

        try {
            r.store();
            return true;
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyInUseException(e);
        } catch (DataChangedException e) {
            return false;
        }
    }
}