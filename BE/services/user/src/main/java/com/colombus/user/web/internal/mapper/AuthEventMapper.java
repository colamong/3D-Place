package com.colombus.user.web.internal.mapper;

import com.colombus.user.contract.dto.AuthEventResponse;
import com.colombus.user.contract.enums.AuthEventKindCode;
import com.colombus.user.contract.enums.AuthProviderCode;
import com.colombus.user.model.AuthEvent;
import com.colombus.user.model.type.AuthEventKind;
import com.colombus.user.model.type.AuthProvider;

public final class AuthEventMapper {

    private AuthEventMapper() {}

    public static AuthEventResponse toDto(AuthEvent e) {
        return new AuthEventResponse(
            e.id(),
            toProviderCode(e.provider()),
            toKindCode(e.kind()),
            e.detail(),
            e.ipAddr(),
            e.userAgent(),
            e.createdAt()
        );
    }

    private static AuthProviderCode toProviderCode(AuthProvider provider) {
        if (provider == null) return null;
        return UserTypeMapper.toCode(provider);
    }

    private static AuthEventKindCode toKindCode(AuthEventKind kind) {
        if (kind == null) return null;
        return UserTypeMapper.toCode(kind);
    }
}