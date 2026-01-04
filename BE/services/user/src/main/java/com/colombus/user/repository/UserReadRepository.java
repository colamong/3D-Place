package com.colombus.user.repository;

import static com.colombus.user.jooq.tables.UserAccount.USER_ACCOUNT;
import static com.colombus.user.jooq.tables.AuthEvent.AUTH_EVENT;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import com.colombus.user.model.AuthEvent;
import com.colombus.user.model.UserAccount;
import com.colombus.user.model.UserSummary;
import com.colombus.user.model.type.AccountRole;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserReadRepository {
    
    private final DSLContext dsl;

    /** 활성 사용자 존재 여부 */
    public boolean existsAlive(UUID userId) {
        return dsl.fetchExists(
            dsl.selectOne()
               .from(USER_ACCOUNT)
               .where(USER_ACCOUNT.ID.eq(userId)
                   .and(USER_ACCOUNT.DELETED_AT.isNull()))
        );
    }

    /** userId로 기본 사용자 정보 조회 */
    public @Nullable UserAccount findBasicUserById(UUID userId) {
        return dsl.selectFrom(USER_ACCOUNT)
                .where(USER_ACCOUNT.ID.eq(userId)
                    .and(USER_ACCOUNT.DELETED_AT.isNull()))
                .fetchOneInto(UserAccount.class);
    }

    /** 닉네임 핸들로 기본 사용자 정보 조회 */
    public @Nullable UserAccount findBasicUserByHandler(String handler) {
        return dsl.selectFrom(USER_ACCOUNT)
                .where(USER_ACCOUNT.NICKNAME_HANDLE.eq(handler)
                    .and(USER_ACCOUNT.DELETED_AT.isNull()))
                .fetchOneInto(UserAccount.class);
    }

    public List<UserSummary> findByIdIn(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return dsl.select(
                USER_ACCOUNT.ID,
                USER_ACCOUNT.NICKNAME,
                USER_ACCOUNT.NICKNAME_HANDLE
            )
            .from(USER_ACCOUNT)
            .where(USER_ACCOUNT.ID.in(ids))
            .and(USER_ACCOUNT.DELETED_AT.isNull())
            .fetch(record -> new UserSummary(
                record.get(USER_ACCOUNT.ID),
                record.get(USER_ACCOUNT.NICKNAME),
                record.get(USER_ACCOUNT.NICKNAME_HANDLE)
            ));
    }

    /** 사용자 역할 목록 조회 */
    public List<AccountRole> findRoles(UUID userId) {
        var roles = dsl.select(USER_ACCOUNT.ROLES)
                    .from(USER_ACCOUNT)
                    .where(USER_ACCOUNT.ID.eq(userId)
                        .and(USER_ACCOUNT.DELETED_AT.isNull()))
                    .fetchOne(USER_ACCOUNT.ROLES);

        return roles != null ? roles : List.of();
    }

    /** 인증 이벤트 목록 조회 (페이지네이션) */
    public List<AuthEvent> findAuthEvents(UUID userId, int limit, @Nullable Instant beforeCreatedExclusive, @Nullable UUID beforeUuidExclusive) {
        Condition cond = AUTH_EVENT.USER_ID.eq(userId)
            .and(AUTH_EVENT.DELETED_AT.isNull());

        if (beforeCreatedExclusive != null) {
            if (beforeUuidExclusive != null) {
                cond = cond.and(
                    AUTH_EVENT.CREATED_AT.lt(beforeCreatedExclusive)
                    .or(AUTH_EVENT.CREATED_AT.eq(beforeCreatedExclusive).and(AUTH_EVENT.ID.lt(beforeUuidExclusive)))
                );
            } else {
                cond = cond.and(AUTH_EVENT.CREATED_AT.lt(beforeCreatedExclusive));
            }
        }

        return dsl.selectFrom(AUTH_EVENT)
                .where(cond)
                .orderBy(AUTH_EVENT.CREATED_AT.desc(), AUTH_EVENT.ID.desc())
                .limit(limit)
                .fetchInto(AuthEvent.class);
    }
}
