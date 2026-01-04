package com.colombus.user.web.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.colombus.user.contract.dto.AuthEventResponse;
import com.colombus.user.contract.dto.ExternalIdentityPayload;
import com.colombus.user.contract.dto.PublicProfileResponse;
import com.colombus.user.contract.dto.UserProfileResponse;
import com.colombus.user.contract.dto.UserSummaryBulkRequest;
import com.colombus.user.contract.dto.UserSummaryResponse;
import com.colombus.user.model.AuthEvent;
import com.colombus.user.service.UserInfoService;
import com.colombus.user.web.internal.mapper.AuthEventMapper;
import com.colombus.user.web.internal.mapper.UserTypeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = "/internal/users", method = {RequestMethod.GET, RequestMethod.HEAD})
@RequiredArgsConstructor
public class UserQueryController {

    private final UserInfoService userInfoService;

    @GetMapping("/me")
    public UserProfileResponse profile(
        @RequestHeader("X-Internal-Actor-Id") UUID actorId
    ) {
        return userInfoService.getProfile(actorId);
    }

    @GetMapping("/profile/{nicknameHandle}")
    public PublicProfileResponse publicProfile(@PathVariable String nicknameHandle) {
        return userInfoService.getPublicProfile(nicknameHandle);
    }

    @GetMapping("/{userId}/identities")
    public List<ExternalIdentityPayload> identities(@PathVariable UUID userId) {
        
        return userInfoService.getIdentities(userId).stream()
            .map(i -> new ExternalIdentityPayload(
                UserTypeMapper.toCode(i.provider()),
                i.providerTenant(),
                i.providerSub()
            ))
            .toList();
    }

    @GetMapping("/{userId}/events")
    public List<AuthEventResponse> events(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) Instant beforeCreatedExclusive,
        @RequestParam(required = false) UUID beforeUuidExclusive
    ) {
        List<AuthEvent> events = userInfoService.getAuthEvents(
            userId, limit, beforeCreatedExclusive, beforeUuidExclusive
        );

        return events.stream()
                     .map(AuthEventMapper::toDto)
                     .toList();
    }

    @PostMapping("/bulk-summary")
    public List<UserSummaryResponse> getUserSummaries(
        @RequestBody UserSummaryBulkRequest body
    ) {
        return userInfoService.findSummaries(body.userIds());
    }
}