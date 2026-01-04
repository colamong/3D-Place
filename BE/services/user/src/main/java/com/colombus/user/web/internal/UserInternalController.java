package com.colombus.user.web.internal;

import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.colombus.common.web.core.clientip.ClientIp;
import com.colombus.user.command.ConfirmEmailChangeCommand;
import com.colombus.user.command.EnsureUserCommand;
import com.colombus.user.contract.dto.EnsureUserRequest;
import com.colombus.user.service.UserAuthService;
import com.colombus.user.service.UserWriteService;
import com.colombus.user.util.Locales;
import com.colombus.user.web.internal.mapper.UserTypeMapper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserAuthService authService;
    private final UserWriteService writeService;

    @PostMapping("/email/confirm")
    public ResponseEntity<Map<String,Object>> confirmEmailChange(
        @RequestParam UUID userId,
        @RequestParam String verifiedEmail,
        @ClientIp String ip,
        HttpServletRequest req,
        @RequestHeader(name="User-Agent", required=false) String ua
    ) {
        ConfirmEmailChangeCommand cmd = new ConfirmEmailChangeCommand(
            userId,
            verifiedEmail,
            null
        );
        var dto = writeService.confirmEmailChange(cmd);
        boolean ok = (dto != null);

        return ResponseEntity.ok(Map.of("confirmed", ok));
    }

    @PostMapping("/ensure")
    public Map<String,Object> ensure(
        @RequestBody EnsureUserRequest body,
        @ClientIp String ip,
        HttpServletRequest req,
        @RequestHeader(name="User-Agent", required=false) String ua,
        @RequestHeader(name = "Accept-Language", required = false) String acceptLang
    ) {
        String locale = firstNonNull(
            Locales.canonicalOrNull(body.locale()),
            Locales.fromAcceptLanguage(acceptLang, null),
            (req.getLocale() != null ? req.getLocale().toLanguageTag() : null),
            "en"
        );

        var provider = UserTypeMapper.fromCode(body.provider());

        var cmd = EnsureUserCommand.of(
            provider,
            body.providerTenant(),
            body.providerSub(),
            body.email(),
            body.emailVerified(),
            locale,
            body.avatarUrl()
        );

        var r = authService.ensureUserAndIdentity(cmd, ip, ua);
        return Map.of(
            "userId", r.userId().toString(),
            "signup", r.signup(),
            "link", r.link(),
            "sync", r.sync(),
            "login", r.login()
        );
    }

    private static String firstNonNull(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP");
    }
}
