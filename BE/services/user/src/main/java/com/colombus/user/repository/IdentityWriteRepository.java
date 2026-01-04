package com.colombus.user.repository;

import static com.colombus.user.jooq.tables.UserIdentity.USER_IDENTITY;

import com.colombus.user.model.UserIdentity;
import com.colombus.user.model.type.AuthProvider;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IdentityWriteRepository {

  private final DSLContext dsl;

  /** unlink 성공 시 내부 soft delete */
  public Boolean unlink(UUID userId, AuthProvider provider, String tenant, String sub) {
    int rows =
        dsl.update(USER_IDENTITY)
            .set(USER_IDENTITY.DELETED_AT, Instant.now())
            .where(
                USER_IDENTITY
                    .USER_ID
                    .eq(userId)
                    .and(USER_IDENTITY.PROVIDER.eq(provider))
                    .and(USER_IDENTITY.PROVIDER_TENANT.eq(tenant))
                    .and(USER_IDENTITY.PROVIDER_SUB.eq(sub))
                    .and(USER_IDENTITY.DELETED_AT.isNull()))
            .execute();

    return rows > 0;
  }

  public UUID link(UserIdentity idt) {
    return dsl.insertInto(USER_IDENTITY)
        .set(USER_IDENTITY.USER_ID, idt.userId())
        .set(USER_IDENTITY.PROVIDER, idt.provider())
        .set(USER_IDENTITY.PROVIDER_TENANT, idt.providerTenant())
        .set(USER_IDENTITY.PROVIDER_SUB, idt.providerSub())
        .set(USER_IDENTITY.EMAIL_AT_PROVIDER, idt.emailAtProvider())
        .set(USER_IDENTITY.DISPLAY_NAME_AT_PROVIDER, idt.displayNameAtProvider())
        .set(USER_IDENTITY.LAST_SYNC_AT, idt.lastSyncAt())
        .returning(USER_IDENTITY.ID)
        .fetchOne(USER_IDENTITY.ID);
  }

  public int touchLastSync(UUID id) {
    return dsl.update(USER_IDENTITY)
        .set(USER_IDENTITY.LAST_SYNC_AT, Instant.now())
        .where(USER_IDENTITY.ID.eq(id).and(USER_IDENTITY.DELETED_AT.isNull()))
        .execute();
  }

  public int softDeleteAllByUserId(UUID userId) {
    return dsl.update(USER_IDENTITY)
        .set(USER_IDENTITY.DELETED_AT, Instant.now())
        .where(USER_IDENTITY.USER_ID.eq(userId).and(USER_IDENTITY.DELETED_AT.isNull()))
        .execute();
  }
}
