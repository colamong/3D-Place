package com.colombus.user.command;

import java.util.Objects;
import java.util.UUID;
import com.colombus.common.utility.text.Texts;
import com.colombus.user.model.type.AuthProvider;

public record LinkIdentityCommand(
    UUID userId,
    AuthProvider provider,
    String providerTenant,
    String providerSub
) {
    public LinkIdentityCommand {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(provider, "provider is required");
        providerTenant = Texts.trimToNull(providerTenant);
        providerSub    = Texts.requireNonBlank(providerSub, "providerSub");
    }

    public static LinkIdentityCommand of(
        UUID userId, AuthProvider provider, String tenant, String sub
    ) {
        return new LinkIdentityCommand(
            userId,
            provider,
            tenant,
            sub
        );
    }
}