package com.colombus.user.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colombus.user.contract.dto.PublicProfileResponse;
import com.colombus.user.contract.dto.UserProfileResponse;
import com.colombus.user.contract.dto.UserSummaryResponse;
import com.colombus.user.model.AuthEvent;
import com.colombus.user.model.UserIdentity;
import com.colombus.user.model.type.AuthProvider;
import com.colombus.user.repository.IdentityReadRepository;
import com.colombus.user.repository.UserReadRepository;
import com.colombus.user.web.internal.mapper.UserIdentityMapper;
import com.colombus.user.web.internal.mapper.UserTypeMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserInfoService {

    private final IdentityReadRepository identityReadRepo;
    private final UserReadRepository userReadRepo;

    public @Nullable UserProfileResponse getProfile(UUID userId) {
        var acc = userReadRepo.findBasicUserById(userId);
        if (acc == null) return null;
        
        var roles = userReadRepo.findRoles(userId).stream()
                                .map(UserTypeMapper::toCode)
                                .toList();

        var identities = identityReadRepo.findIdentities(userId).stream()
                                         .map(UserIdentityMapper::toDto)
                                         .toList();

        return new UserProfileResponse(
            acc.id(),
            acc.email(),
            acc.emailVerified(),
            acc.nickname(),
            acc.nicknameSeq(),
            acc.nicknameHandle(),
            acc.isActive(),
            acc.loginCount(),
            acc.metadata(),
            acc.paintCountTotal(),
            acc.lastLoginAt(),
            acc.blockedAt(),
            acc.blockedReason(),
            acc.createdAt(),
            acc.updatedAt(),
            roles,
            identities
        );
    }

    public @Nullable UserProfileResponse getProfileByIdentity(AuthProvider provider, String tenant, String sub) {
        var userId = identityReadRepo.findUserIdByIdentity(provider, tenant, sub);
        return (userId == null) ? null : getProfile(userId);
    }

    public @Nullable PublicProfileResponse getPublicProfile(String nicknameHandler) {
        var acc = userReadRepo.findBasicUserByHandler(nicknameHandler);
        if (acc == null) return null;

        return new PublicProfileResponse(
            acc.id(),
            acc.nickname(),
            acc.nicknameSeq(),
            acc.nicknameHandle(),
            acc.metadata(),
            acc.paintCountTotal(),
            acc.createdAt()
        );
    }

    public List<AuthEvent> getAuthEvents(UUID userId, int limit, @Nullable Instant beforeCreatedExclusive, @Nullable UUID beforeUuidExclusive) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return userReadRepo.findAuthEvents(userId, safeLimit, beforeCreatedExclusive, beforeUuidExclusive);
    }

    public List<UserIdentity> getIdentities(UUID userId) {
        return identityReadRepo.findIdentities(userId);
    }

    public List<UserSummaryResponse> findSummaries(List<UUID> ids) {
        return userReadRepo.findByIdIn(ids).stream()
            .map(user -> new UserSummaryResponse(
                user.id(),
                user.nickname(),
                user.nicknameHandle()
            ))
            .toList();
    }
}