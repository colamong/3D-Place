package com.colombus.user.web.internal;

import com.colombus.user.command.LinkIdentityCommand;
import com.colombus.user.command.UnlinkIdentityCommand;
import com.colombus.user.command.UpdateProfileCommand;
import com.colombus.user.contract.dto.UpdateProfileRequest;
import com.colombus.user.contract.dto.UserProfileResponse;
import com.colombus.user.contract.enums.AuthProviderCode;
import com.colombus.user.messaging.kafka.dto.UserLinkPayload;
import com.colombus.user.messaging.kafka.dto.UserUnlinkPayload;
import com.colombus.user.service.LinkEnsureService;
import com.colombus.user.service.UnlinkEnsureService;
import com.colombus.user.service.UserAuthService;
import com.colombus.user.service.UserWriteService;
import com.colombus.user.service.result.LinkIdentityResult;
import com.colombus.user.service.result.UnlinkIdentityResult;
import com.colombus.user.web.internal.mapper.UserTypeMapper;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    value = "/internal/users",
    method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
@RequiredArgsConstructor
public class UserCommandController {

    private final UserAuthService authService;
    private final UserWriteService userWriteService;
    private final UnlinkEnsureService unlinkEnsureService;
    private final LinkEnsureService linkEnsureService;

    /**
     * 프로필 업데이트 (내부용) - BFF가 인증/권한 체크 후 userId를 path로 넘김
     */
    @PostMapping("/profile")
    public UserProfileResponse updateProfile(
        @RequestHeader("X-Internal-Actor-Id") UUID actorId,
        @RequestBody UpdateProfileRequest body
    ) {
        var cmd =
            new UpdateProfileCommand(actorId, body.nickname(), body.metadata(), body.updatedAtExpected());
        return userWriteService.updateProfile(cmd);
    }

    /**
     * 아이덴티티 링크 (내부용) - BFF가 userId, ip, ua, bearer를 헤더로 넘김
     */
    @PostMapping("/{userId}/link")
    public LinkIdentityResult link(
        @PathVariable UUID userId,
        @RequestParam AuthProviderCode provider,
        @RequestParam String providerTenant,
        @RequestParam String providerSub,
        @RequestHeader(name = "X-Client-Ip", required = false) String ip,
        @RequestHeader(name = "X-User-Agent", required = false) String ua,
        @RequestHeader(name = "X-User-Bearer", required = false) String bearer) {
        var providerDomain = UserTypeMapper.fromCode(provider);

        var cmd = LinkIdentityCommand.of(userId, providerDomain, providerTenant, providerSub);

        var result = authService.linkIdentity(cmd, ip, ua);

        if (result.retry()) {
            linkEnsureService.handleLink(
                new UserLinkPayload(userId, provider.name(), providerTenant, providerSub),
                ip,
                ua,
                bearer);
        }

        return result;
    }

    /** 아이덴티티 언링크 (내부용) */
    @PostMapping("/{userId}/unlink")
    public UnlinkIdentityResult unlink(
        @PathVariable UUID userId,
        @RequestParam AuthProviderCode provider,
        @RequestParam String providerTenant,
        @RequestParam String providerSub,
        @RequestHeader(name = "X-Client-Ip", required = false) String ip,
        @RequestHeader(name = "X-User-Agent", required = false) String ua,
        @RequestHeader(name = "X-User-Bearer", required = false) String bearer) {
        var providerDomain = UserTypeMapper.fromCode(provider);

        var cmd = UnlinkIdentityCommand.of(userId, providerDomain, providerTenant, providerSub);

        var result = authService.unlinkIdentity(cmd, ip, ua);

        if (result.retry()) {
            unlinkEnsureService.handleUnlink(
                new UserUnlinkPayload(userId, provider.name(), providerTenant, providerSub),
                ip,
                ua,
                bearer);
        }

        return result;
    }

    /** 사용자 비활성화 (탈퇴) - BFF가 userId를 path로 넘김 */
    @DeleteMapping("/deactivate")
    public ResponseEntity<Void> deactivateUser(
        @RequestHeader("X-Internal-Actor-Id") UUID actorId,
        @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = (body != null) ? body.get("reason") : null;
        authService.deactivateUser(actorId, reason);
        return ResponseEntity.noContent().build();
    }
}
