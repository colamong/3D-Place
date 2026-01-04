package com.colombus.user.web.internal.mapper;

import java.util.Locale;

import com.colombus.user.contract.enums.AccountRoleCode;
import com.colombus.user.contract.enums.AuthEventKindCode;
import com.colombus.user.contract.enums.AuthProviderCode;
import com.colombus.user.model.type.AccountRole;
import com.colombus.user.model.type.AuthEventKind;
import com.colombus.user.model.type.AuthProvider;

public final class UserTypeMapper {

    private UserTypeMapper() {}

    // =====================
    // AccountRole <-> Code
    // =====================
    public static AccountRoleCode toCode(AccountRole role) {
        return switch (role) {
            case USER      -> AccountRoleCode.USER;
            case ADMIN     -> AccountRoleCode.ADMIN;
            case MODERATOR -> AccountRoleCode.MODERATOR;
        };
    }

    public static AccountRole fromCode(AccountRoleCode code) {
        return switch (code) {
            case USER      -> AccountRole.USER;
            case ADMIN     -> AccountRole.ADMIN;
            case MODERATOR -> AccountRole.MODERATOR;
        };
    }

    // =====================
    // AuthProvider <-> Code
    // =====================
    public static AuthProviderCode toCode(AuthProvider provider) {
        return switch (provider) {
            case AUTH0  -> AuthProviderCode.AUTH0;
            case GOOGLE -> AuthProviderCode.GOOGLE;
            case KAKAO  -> AuthProviderCode.KAKAO;
            case GUEST  -> AuthProviderCode.GUEST;
        };
    }

    public static AuthProvider fromCode(AuthProviderCode code) {
        return switch (code) {
            case AUTH0  -> AuthProvider.AUTH0;
            case GOOGLE -> AuthProvider.GOOGLE;
            case KAKAO  -> AuthProvider.KAKAO;
            case GUEST  -> AuthProvider.GUEST;
        };
    }

    public static AuthProvider parseProvider(String s) {
        if (s == null) {
            throw new IllegalArgumentException("provider is required");
        }
        String key = s.trim().toUpperCase(Locale.ROOT);

        return switch (key) {
            case "AUTH0", "AUTH-0", "AUTH_0" -> AuthProvider.AUTH0;
            case "GOOGLE"                    -> AuthProvider.GOOGLE;
            case "KAKAO"                     -> AuthProvider.KAKAO;
            case "GUEST"                     -> AuthProvider.GUEST;
            default -> throw new IllegalArgumentException("Unsupported provider: " + s);
        };
    }

    // =====================
    // AuthEventKind <-> Code
    // =====================
    public static AuthEventKindCode toCode(AuthEventKind kind) {
        if (kind == null) return null;
        return switch (kind) {
            case SIGNUP  -> AuthEventKindCode.SIGNUP;
            case LOGIN   -> AuthEventKindCode.LOGIN;
            case REFRESH -> AuthEventKindCode.REFRESH;
            case LOGOUT  -> AuthEventKindCode.LOGOUT;
            case LINK    -> AuthEventKindCode.LINK;
            case UNLINK  -> AuthEventKindCode.UNLINK;
            case SYNC    -> AuthEventKindCode.SYNC;
        };
    }

    public static AuthEventKind fromCode(AuthEventKindCode code) {
        if (code == null) return null;
        return switch (code) {
            case SIGNUP  -> AuthEventKind.SIGNUP;
            case LOGIN   -> AuthEventKind.LOGIN;
            case REFRESH -> AuthEventKind.REFRESH;
            case LOGOUT  -> AuthEventKind.LOGOUT;
            case LINK    -> AuthEventKind.LINK;
            case UNLINK  -> AuthEventKind.UNLINK;
            case SYNC    -> AuthEventKind.SYNC;
        };
    }
}