package com.colombus.user.repository;

import static com.colombus.user.jooq.tables.UserIdentity.USER_IDENTITY;

import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import com.colombus.user.model.UserIdentity;
import com.colombus.user.model.type.AuthProvider;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class IdentityReadRepository {

    private final DSLContext dsl;

    /** identity로 사용자 찾기 */
    public @Nullable UUID findUserIdByIdentity(AuthProvider provider, String tenant, String sub) {
        return dsl.select(USER_IDENTITY.USER_ID)
                .from(USER_IDENTITY)
                .where(USER_IDENTITY.PROVIDER.eq(provider)
                    .and(USER_IDENTITY.PROVIDER_TENANT.eq(tenant))
                    .and(USER_IDENTITY.PROVIDER_SUB.eq(sub)))
                .limit(1)
                .fetchOne(USER_IDENTITY.USER_ID);
    }

    public @Nullable UUID findUserIdByProviderAndSub(AuthProvider provider, String sub) {
        return dsl.select(USER_IDENTITY.USER_ID)
            .from(USER_IDENTITY)
            .where(USER_IDENTITY.PROVIDER.eq(provider)
                .and(USER_IDENTITY.PROVIDER_SUB.eq(sub)))
            .limit(1)
            .fetchOne(USER_IDENTITY.USER_ID);
    }

    /** 사용자 identity 목록 조회 */
    public List<UserIdentity> findIdentities(UUID userId) {
        return dsl.selectFrom(USER_IDENTITY)
                .where(USER_IDENTITY.USER_ID.eq(userId)
                    .and(USER_IDENTITY.DELETED_AT.isNull()))
                .orderBy(USER_IDENTITY.CREATED_AT.desc())
                .fetchInto(UserIdentity.class);
    }

    /** 특정 identity가 사용자에 속하는지 확인 (활성만) */
    public boolean identityExists(UUID userId, AuthProvider provider, String tenant, String sub) {
        return dsl.fetchExists(
            dsl.selectOne()
               .from(USER_IDENTITY)
               .where(USER_IDENTITY.USER_ID.eq(userId)
                   .and(USER_IDENTITY.PROVIDER.eq(provider))
                   .and(USER_IDENTITY.PROVIDER_TENANT.eq(tenant))
                   .and(USER_IDENTITY.PROVIDER_SUB.eq(sub))
                   .and(USER_IDENTITY.DELETED_AT.isNull()))
        );
    }

    /** auth0 provider를 사용하는 활성 identity 존재 여부 */
    public boolean hasAuth0Identity(UUID userId) {
        return dsl.fetchExists(
            dsl.selectOne()
               .from(USER_IDENTITY)
               .where(USER_IDENTITY.USER_ID.eq(userId)
                   .and(USER_IDENTITY.PROVIDER.eq(AuthProvider.AUTH0))
                   .and(USER_IDENTITY.DELETED_AT.isNull()))
        );
    }

    /** 사용자 활성 identity 개수 조회 */
    public int countActiveIdentities(UUID userId) {
        return dsl.selectCount()
                .from(USER_IDENTITY)
                .where(USER_IDENTITY.USER_ID.eq(userId)
                    .and(USER_IDENTITY.DELETED_AT.isNull()))
                .fetchOne(0, int.class);
    }


    /** SSO(identity provider가 auth0가 아닌) 활성 identity 개수 조회 */
    public int countActiveSsoIdentities(UUID userId) {
        return dsl.selectCount()
                .from(USER_IDENTITY)
                .where(USER_IDENTITY.USER_ID.eq(userId)
                    .and(USER_IDENTITY.PROVIDER.ne(AuthProvider.AUTH0))
                    .and(USER_IDENTITY.DELETED_AT.isNull()))
                .fetchOne(0, int.class);
    }

    public String findAuth0UserId(UUID userId) {
        return dsl.select(USER_IDENTITY.PROVIDER_SUB)
                .from(USER_IDENTITY)
                .where(USER_IDENTITY.USER_ID.eq(userId)
                    .and(USER_IDENTITY.PROVIDER.eq(AuthProvider.AUTH0))
                    .and(USER_IDENTITY.DELETED_AT.isNull()))
                .fetchOne(USER_IDENTITY.PROVIDER_SUB);
    }
}
