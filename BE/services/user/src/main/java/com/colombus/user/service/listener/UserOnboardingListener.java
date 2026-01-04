package com.colombus.user.service.listener;

import com.colombus.user.messaging.outbox.repository.OutboxPublishRepository;
import com.colombus.user.model.type.AuthEventKind;
import com.colombus.user.repository.UserAuthRepository;
import com.colombus.user.repository.UserWriteRepository;
import com.colombus.user.service.event.UserOnboardedEvent;
import com.colombus.user.util.Locales;
import com.colombus.user.util.NicknameGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserOnboardingListener {

    private final UserAuthRepository authRepo;
    private final UserWriteRepository userWriteRepo;
    private final OutboxPublishRepository outboxPubRepo;
    private final ObjectMapper om;

    /**
     * 사용자 생성 트랜잭션이 성공적으로 커밋된 후 후처리 작업을 처리합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleUserOnboarding(UserOnboardedEvent event) {
        log.info("사용자 {} 에 대한 온보딩 후처리 작업을 처리합니다.", event.userId());

        // 신규 가입자를 위한 초기 프로필 설정
        if (event.didSignup()) {
            String canonicalLocale = Locales.canonicalOrNull(event.locale());
            ObjectNode patch = om.createObjectNode();
            if (canonicalLocale != null) {
                patch.put("locale", canonicalLocale);
                patch.put("avartarUrl", event.avatarUrl());
            }

            Locale baseLoc =
                (canonicalLocale != null ? Locale.forLanguageTag(canonicalLocale) : Locale.ENGLISH);
            String baseNickname = NicknameGenerator.generateBase(baseLoc, event.userId(), true);

            userWriteRepo.updateProfile(event.userId(), baseNickname, patch);
            authRepo.insertAuthEvent(
                event.userId(), event.provider(), AuthEventKind.SIGNUP, null, event.ip(), event.ua());
        }

        // 이벤트 로깅
        if (event.didLink()) {
            // 참고: 원래 요청 컨텍스트(제공자 테넌트, 서브)는 여기에서 사용할 수 없습니다.
            // 간단한 상세 메시지가 로깅됩니다. 더 자세한 정보를 원하면 이벤트에 전달해야 합니다.
            String detail = "{\"provider\":\"" + event.provider() + "\"}";
            authRepo.insertAuthEvent(
                event.userId(), event.provider(), AuthEventKind.LINK, detail, event.ip(), event.ua());
        }

        // 이벤트 로깅
        if (event.didSync()) {
            // 참고: 동기화된 특정 세부 정보는 여기에서 사용할 수 없습니다.
            // 일반적인 동기화 이벤트를 로깅합니다.
            List<String> changes = new ArrayList<>();
            changes.add("email_backfill_or_set");
            changes.add("email_verified_promoted");
            String detail = json(Map.of("changes", changes));
            authRepo.insertAuthEvent(
                event.userId(), event.provider(), AuthEventKind.SYNC, detail, event.ip(), event.ua());
        }

        // 로그인 통계 업데이트 및 LOGIN 이벤트 로깅 (항상 발생)
        authRepo.bumpLoginStats(event.userId());
        authRepo.insertAuthEvent(
            event.userId(), event.provider(), AuthEventKind.LOGIN, null, event.ip(), event.ua());

        // 다른 서비스를 위한 Outbox 발행 (가입인 경우)
        if (event.didSignup()) {
            outboxPubRepo.appendSubjectUpsert(event.userId(), true);
        }
    }

    private String json(Object obj) {
        try {
            return om.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("인증 이벤트 상세 정보 직렬화 실패", e);
            return null;
        }
    }
}
