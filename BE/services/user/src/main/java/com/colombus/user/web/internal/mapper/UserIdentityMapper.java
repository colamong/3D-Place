package com.colombus.user.web.internal.mapper;

import com.colombus.user.contract.dto.UserIdentityDto;
import com.colombus.user.model.UserIdentity;

public final class UserIdentityMapper {

    private UserIdentityMapper() {}

    public static UserIdentityDto toDto(UserIdentity identity) {
        return new UserIdentityDto(
            identity.id(),
            identity.userId(),
            UserTypeMapper.toCode(identity.provider()),
            identity.providerTenant(),
            identity.providerSub(),
            identity.emailAtProvider(),
            identity.displayNameAtProvider(),
            identity.lastSyncAt(),
            identity.createdAt(),
            identity.updatedAt(),
            identity.deletedAt()
        );
    }
}