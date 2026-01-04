package com.colombus.auth.services;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.session.Session;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.stereotype.Service;

import com.colombus.user.contract.dto.ExternalIdentityPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTerminationService {
    
    private final ReactiveFindByIndexNameSessionRepository<? extends Session> indexedSessionRepository;
    private final ReactiveSessionRepository<? extends Session> sessionRepository;

    /**
     * 로그인 시점에 principalName 인덱스에 들어간 값(authentication.getName()) = identity.sub 이라고 가정하고,
     * 연결된 모든 identity 들의 sub로 세션을 조회해서 삭제한다.
     */
    public Mono<Void> terminateSessionsByIdentities(List<ExternalIdentityPayload> identities) {
        if (identities == null || identities.isEmpty()) {
            log.info("No identities provided for session termination");
            return Mono.empty();
        }

        // 같은 sub 중복으로 안 지우게 Set으로 정리
        Set<String> principals = identities.stream()
            .map(ExternalIdentityPayload::sub)
            .filter(Objects::nonNull)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());

        return Flux.fromIterable(principals)
            .flatMap(principal ->
                indexedSessionRepository
                    .findByIndexNameAndIndexValue(
                        ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                        principal
                    )
                    .flatMapMany(sessionsMap -> {
                        if (sessionsMap.isEmpty()) {
                            log.info("No active sessions found for principal={}", principal);
                            return Flux.empty();
                        }

                        log.info("Terminating {} sessions for principal={}", sessionsMap.size(), principal);
                        return Flux.fromIterable(sessionsMap.keySet());
                    })
                    .flatMap(sessionId -> {
                        log.debug("Deleting sessionId={} for principal={}", sessionId, principal);
                        return sessionRepository.deleteById(sessionId);
                    })
            )
            .then();
    }
}
