package com.colombus.user.command;

import java.time.Instant;
import java.util.Objects;
import com.colombus.common.utility.text.Texts;
import com.colombus.user.model.type.AuthProvider;
import com.colombus.user.util.Locales;
import jakarta.annotation.Nullable;

public record EnsureUserCommand(
    AuthProvider provider,
    String providerTenant,
    String providerSub,
    String email,
    boolean emailVerified,
    @Nullable String locale,
    @Nullable String avatarUrl,
    Instant createdAt
) {
    public EnsureUserCommand {
        Objects.requireNonNull(provider, "provider is required");
        providerTenant = Texts.trimToNull(providerTenant);
        providerSub    = Texts.requireNonBlank(providerSub, "providerSub");
        email          = Texts.trimToNull(email);
        locale         = Locales.canonicalOrNull(locale);
        avatarUrl      = Texts.trimToNull(avatarUrl);
        createdAt      = (createdAt == null ? Instant.now() : createdAt);
    }

    public static EnsureUserCommand of(
        AuthProvider provider,
        String tenant,
        String sub,
        String email,
        boolean verified,
        @Nullable String locale,
        @Nullable String avatarUrl,
        Instant createdAt
    ) {
        return new EnsureUserCommand(provider, tenant, sub, email, verified, locale, avatarUrl, createdAt);
    }

    public static EnsureUserCommand of(
        AuthProvider provider,
        String tenant,
        String sub,
        String email,
        boolean verified,
        @Nullable String locale,
        @Nullable String avatarUrl
    ) {
        return new EnsureUserCommand(provider, tenant, sub, email, verified, locale, avatarUrl, null);
    }
}