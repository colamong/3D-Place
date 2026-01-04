package com.colombus.user.web.internal;

import com.colombus.user.contract.dto.AuthEventByIdentityRequest;
import com.colombus.user.service.UserAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class AuthEventInternalController {

    private final UserAuthService service;

    @PostMapping({"/auth-events"})
    public ResponseEntity<Void> record(
        @Valid @RequestBody AuthEventByIdentityRequest req,
        HttpServletRequest http
    ) {
        String ip = extractIp(http);
        String ua = http.getHeader("User-Agent");
        service.recordEventByIdentity(req, ip, ua);
        return ResponseEntity.accepted().build(); // best-effort
    }

    private static String extractIp(
        HttpServletRequest req
    ) {
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) return h.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
