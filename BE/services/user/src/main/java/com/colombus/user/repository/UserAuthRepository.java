package com.colombus.user.repository;

import static com.colombus.user.jooq.tables.AuthEvent.AUTH_EVENT;
import static com.colombus.user.jooq.tables.UserAccount.USER_ACCOUNT;
import static com.colombus.user.jooq.tables.UserIdentity.USER_IDENTITY;

import com.colombus.user.model.type.AuthEventKind;
import com.colombus.user.model.type.AuthProvider;
import jakarta.annotation.Nullable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.postgres.extensions.types.Inet;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserAuthRepository {

    private final DSLContext dsl;

    public record UpsertUserByEmailResult(
        UUID userId, boolean created, boolean emailBackfilled, boolean verifiedPromoted) {}

    public enum IdentityInsertResult {
        INSERTED,
        ALREADY_EXISTS_FOR_USER,
        CONFLICT_EXISTS_FOR_ANOTHER_USER
    }

    public void xactLockByIdentity(AuthProvider provider, String tenant, String sub) {
        String key = provider + "|" + tenant + "|" + sub;
        dsl.query("select pg_advisory_xact_lock(hashtext(?))", key).execute();
    }

    /** identity 조회 (Advisory Lock 사용 시 FOR UPDATE 불필요) */
    public @Nullable UUID findUserIdByIdentity(AuthProvider provider, String tenant, String sub) {
        return dsl.select(USER_IDENTITY.USER_ID)
            .from(USER_IDENTITY)
            .where(
                USER_IDENTITY
                    .PROVIDER
                    .eq(provider)
                    .and(USER_IDENTITY.PROVIDER_TENANT.eq(tenant))
                    .and(USER_IDENTITY.PROVIDER_SUB.eq(sub))
                    .and(USER_IDENTITY.DELETED_AT.isNull()))
            .fetchOne(USER_IDENTITY.USER_ID);
    }

    /** identity 조회 (FOR UPDATE) */
    public @Nullable UUID findUserIdByIdentityForUpdate(
        AuthProvider provider, String tenant, String sub) {
        return dsl.select(USER_IDENTITY.USER_ID)
            .from(USER_IDENTITY)
            .where(
                USER_IDENTITY
                    .PROVIDER
                    .eq(provider)
                    .and(USER_IDENTITY.PROVIDER_TENANT.eq(tenant))
                    .and(USER_IDENTITY.PROVIDER_SUB.eq(sub))
                    .and(USER_IDENTITY.DELETED_AT.isNull()))
            .forUpdate()
            .fetchOne(USER_IDENTITY.USER_ID);
    }

    /** 이메일 기반 사용자 Upsert (Advisory Lock 사용 시 FOR UPDATE 불필요) */
    public UpsertUserByEmailResult upsertUserByEmailDetailed(String email, boolean emailVerified) {

        var inserted =
            dsl.insertInto(USER_ACCOUNT)
                .columns(USER_ACCOUNT.EMAIL, USER_ACCOUNT.EMAIL_VERIFIED, USER_ACCOUNT.IS_ACTIVE)
                .values(email, emailVerified, true)
                .onConflict(USER_ACCOUNT.EMAIL)
                .where(USER_ACCOUNT.DELETED_AT.isNull())
                .doNothing()
                .returning(USER_ACCOUNT.ID, USER_ACCOUNT.EMAIL_VERIFIED)
                .fetchOne();

        if (inserted != null) {
            return new UpsertUserByEmailResult(inserted.get(USER_ACCOUNT.ID), true, false, false);
        }

        var before =
            dsl.select(USER_ACCOUNT.ID, USER_ACCOUNT.EMAIL_VERIFIED)
                .from(USER_ACCOUNT)
                .where(USER_ACCOUNT.EMAIL.eq(email).and(USER_ACCOUNT.DELETED_AT.isNull()))
                .fetchOne();

        var id = before.get(USER_ACCOUNT.ID);
        var wasVerified = before.get(USER_ACCOUNT.EMAIL_VERIFIED);
        boolean promoted = false;

        if (emailVerified && !Boolean.TRUE.equals(wasVerified)) {
            dsl.update(USER_ACCOUNT)
                .set(USER_ACCOUNT.EMAIL_VERIFIED, true)
                .set(USER_ACCOUNT.UPDATED_AT, Instant.now())
                .where(USER_ACCOUNT.ID.eq(id).and(USER_ACCOUNT.DELETED_AT.isNull()))
                .execute();
            promoted = true;
        }

        return new UpsertUserByEmailResult(id, false, false, promoted);
    }

    /** 이메일 기반 사용자 Upsert(상세 결과) */
    public UpsertUserByEmailResult upsertUserByEmailDetailedForUpdate(
        String email, boolean emailVerified) {
        // 존재 확인 + 락
        UUID existing =
            dsl.select(USER_ACCOUNT.ID)
                .from(USER_ACCOUNT)
                .where(USER_ACCOUNT.EMAIL.eq(email).and(USER_ACCOUNT.DELETED_AT.isNull()))
                .forUpdate()
                .fetchOne(USER_ACCOUNT.ID);

        if (existing != null) {
            Boolean before =
                dsl.select(USER_ACCOUNT.EMAIL_VERIFIED)
                    .from(USER_ACCOUNT)
                    .where(USER_ACCOUNT.ID.eq(existing))
                    .fetchOne(USER_ACCOUNT.EMAIL_VERIFIED);
            boolean promote = emailVerified && !Boolean.TRUE.equals(before);
            if (promote) {
                dsl.update(USER_ACCOUNT)
                    .set(USER_ACCOUNT.EMAIL_VERIFIED, true)
                    .where(USER_ACCOUNT.ID.eq(existing))
                    .execute();
            }
            return new UpsertUserByEmailResult(existing, false, false, promote);
        }

        // 없으면 생성 (활성 사용자)
        UUID createdId =
            dsl.insertInto(USER_ACCOUNT)
                .set(USER_ACCOUNT.EMAIL, email)
                .set(USER_ACCOUNT.EMAIL_VERIFIED, emailVerified)
                .set(USER_ACCOUNT.IS_ACTIVE, true)
                .returning(USER_ACCOUNT.ID)
                .fetchOne(USER_ACCOUNT.ID);

        return new UpsertUserByEmailResult(createdId, true, false, false);
    }

    /** 이메일이 비어있으면 채우고, verified 승격 */
    public boolean backfillEmailIfEmpty(UUID userId, @Nullable String email, boolean emailVerified) {
        if (email == null || email.isBlank()) return false;
        try {
            int updated =
                dsl.update(USER_ACCOUNT)
                    .set(USER_ACCOUNT.EMAIL, DSL.coalesce(USER_ACCOUNT.EMAIL, DSL.val(email)))
                    .set(
                        USER_ACCOUNT.EMAIL_VERIFIED,
                        emailVerified ? DSL.val(true) : USER_ACCOUNT.EMAIL_VERIFIED)
                    .set(USER_ACCOUNT.UPDATED_AT, Instant.now())
                    .where(USER_ACCOUNT.ID.eq(userId))
                    .execute();
            return updated > 0;
        } catch (DataIntegrityViolationException e) {
            // TODO: handle exception
            return false;
        }
    }

    public UUID createActiveUser() {
        return dsl.insertInto(USER_ACCOUNT)
            .set(USER_ACCOUNT.IS_ACTIVE, true)
            .returning(USER_ACCOUNT.ID)
            .fetchOne(USER_ACCOUNT.ID);
    }

    /** identity 멱등 link (삽입 여부 리턴) */
    public IdentityInsertResult insertIdentityIfAbsent(
        UUID userId,
        AuthProvider provider,
        String tenant,
        String sub,
        @Nullable String emailAtProvider) {
        // 먼저 identity가 존재하는지 확인
        var found =
            dsl.select(USER_IDENTITY.USER_ID)
                .from(USER_IDENTITY)
                .where(
                    USER_IDENTITY
                        .PROVIDER
                        .eq(provider)
                        .and(USER_IDENTITY.PROVIDER_TENANT.eq(tenant))
                        .and(USER_IDENTITY.PROVIDER_SUB.eq(sub))
                        .and(USER_IDENTITY.DELETED_AT.isNull()))
                .fetchOne();

        if (found != null) {
            UUID owner = found.get(USER_IDENTITY.USER_ID);
            if (owner.equals(userId)) {
                return IdentityInsertResult.ALREADY_EXISTS_FOR_USER;
            } else {
                return IdentityInsertResult.CONFLICT_EXISTS_FOR_ANOTHER_USER;
            }
        }

        // 존재하지 않으면 삽입 시도
        try {
            dsl.insertInto(USER_IDENTITY)
                .set(USER_IDENTITY.USER_ID, userId)
                .set(USER_IDENTITY.PROVIDER, provider)
                .set(USER_IDENTITY.PROVIDER_TENANT, tenant)
                .set(USER_IDENTITY.PROVIDER_SUB, sub)
                .set(USER_IDENTITY.EMAIL_AT_PROVIDER, emailAtProvider)
                .execute();
            return IdentityInsertResult.INSERTED;
        } catch (DataIntegrityViolationException dup) {
            // 경합 상태(Race Condition)로 인해 삽입이 실패한 경우, 다시 조회하여 최종 상태를 확인
            var retry =
                dsl.select(USER_IDENTITY.USER_ID)
                    .from(USER_IDENTITY)
                    .where(
                        USER_IDENTITY
                            .PROVIDER
                            .eq(provider)
                            .and(USER_IDENTITY.PROVIDER_TENANT.eq(tenant))
                            .and(USER_IDENTITY.PROVIDER_SUB.eq(sub))
                            .and(USER_IDENTITY.DELETED_AT.isNull()))
                    .fetchOne(USER_IDENTITY.USER_ID);

            if (retry != null) {
                if (retry.equals(userId)) {
                    return IdentityInsertResult.ALREADY_EXISTS_FOR_USER;
                } else {
                    return IdentityInsertResult.CONFLICT_EXISTS_FOR_ANOTHER_USER;
                }
            }
            // 재조회에도 없다면, 다른 제약조건 위반일 수 있으므로 예외를 다시 던짐
            throw dup;
        }
    }

    public void upsertIdentity(
        UUID userId,
        AuthProvider provider,
        String tenant,
        String sub,
        @Nullable String emailAtProvider) {
        var result = insertIdentityIfAbsent(userId, provider, tenant, sub, emailAtProvider);
        if (result == IdentityInsertResult.ALREADY_EXISTS_FOR_USER) {
            dsl.update(USER_IDENTITY)
                .set(USER_IDENTITY.EMAIL_AT_PROVIDER, emailAtProvider)
                .where(
                    USER_IDENTITY
                        .PROVIDER
                        .eq(provider)
                        .and(USER_IDENTITY.PROVIDER_TENANT.eq(tenant))
                        .and(USER_IDENTITY.PROVIDER_SUB.eq(sub))
                        .and(USER_IDENTITY.USER_ID.eq(userId))
                        .and(USER_IDENTITY.DELETED_AT.isNull()))
                .execute();
        }
    }

    public void bumpLoginStats(UUID userId) {
        dsl.update(USER_ACCOUNT)
            .set(USER_ACCOUNT.LAST_LOGIN_AT, Instant.now())
            .set(USER_ACCOUNT.LOGIN_COUNT, USER_ACCOUNT.LOGIN_COUNT.add(1L))
            .where(USER_ACCOUNT.ID.eq(userId))
            .execute();
    }

    public int softDeleteUser(UUID userId) {
        return dsl.update(USER_ACCOUNT)
            .set(USER_ACCOUNT.IS_ACTIVE, false)
            .set(USER_ACCOUNT.DELETED_AT, Instant.now())
            .where(USER_ACCOUNT.ID.eq(userId).and(USER_ACCOUNT.DELETED_AT.isNull()))
            .execute();
    }

    public void insertAuthEvent(
        UUID userId,
        AuthProvider provider,
        AuthEventKind kind,
        @Nullable String detail,
        @Nullable String ip,
        @Nullable String ua
    ) { 
        dsl.insertInto(AUTH_EVENT)
            .set(AUTH_EVENT.USER_ID, userId)
            .set(AUTH_EVENT.PROVIDER, provider)
            .set(AUTH_EVENT.KIND, kind)
            .set(AUTH_EVENT.DETAIL, detail)
            .set(AUTH_EVENT.IP_ADDR, toInet(ip))
            .set(AUTH_EVENT.USER_AGENT, ua)
            .execute();
    }

    private Inet toInet(String ip) {
        if (ip == null || ip.isBlank()) return null;
        try {
            return Inet.valueOf(InetAddress.getByName(ip));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP: " + ip, e);
        }
    }
}
