package com.colombus.user.command;

import java.util.Objects;
import java.util.UUID;
import com.colombus.common.utility.text.Texts;
import com.colombus.user.model.type.AuthProvider;

public record UnlinkIdentityCommand(
    UUID userId,
    AuthProvider provider,
    String providerTenant,
    String providerSub
) {
    public UnlinkIdentityCommand {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(provider, "provider is required");
        providerTenant = Texts.trimToNull(providerTenant);
        providerSub    = Texts.requireNonBlank(providerSub, "providerSub");
    }

    /** 문자열 입력을 enum으로 정규화하는 팩토리 */
    public static UnlinkIdentityCommand of(
        UUID userId, AuthProvider provider, String tenant, String sub
    ) {
        return new UnlinkIdentityCommand(
            userId,
            provider,
            tenant,
            sub
        );
    }
}