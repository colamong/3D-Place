package com.colombus.user.web.webhook;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.colombus.common.web.core.clientip.ClientIp;
import com.colombus.user.messaging.kafka.dto.Auth0RegistrationPayload;
import com.colombus.user.service.RegistrationEnsureService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/ingest/auth0")
@RequiredArgsConstructor
public class Auth0RegistrationIngestController {

    private final RegistrationEnsureService ingestService;

    @PostMapping("/registration")
    public ResponseEntity<?> registration(
        @ClientIp String ip,
        @RequestBody Auth0RegistrationPayload payload,
        HttpServletRequest http
    ) {
        String ua = http.getHeader("User-Agent");

        // 즉시 ACK (202 권장), 실제 작업은 비동기
        ingestService.handleRegistration(payload, ip, ua);
        
        return ResponseEntity.accepted().body(java.util.Map.of("ok", true));
    }
}
