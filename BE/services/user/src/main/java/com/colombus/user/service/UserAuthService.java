package com.colombus.user.service;

import com.colombus.common.utility.text.Texts;
import com.colombus.user.client.AuthSvcClient;
import com.colombus.user.command.EnsureUserCommand;
import com.colombus.user.command.LinkIdentityCommand;
import com.colombus.user.command.UnlinkIdentityCommand;
import com.colombus.user.contract.dto.AuthEventByIdentityRequest;
import com.colombus.user.contract.dto.ExternalIdentity;
import com.colombus.user.exception.IdentityAlreadyLinkedException;
import com.colombus.user.messaging.outbox.repository.OutboxPublishRepository;
import com.colombus.user.model.UserIdentity;
import com.colombus.user.model.type.AuthEventKind;
import com.colombus.user.model.type.AuthProvider;
import com.colombus.user.repository.IdentityReadRepository;
import com.colombus.user.repository.IdentityWriteRepository;
import com.colombus.user.repository.UserAuthRepository;
import com.colombus.user.repository.UserAuthRepository.IdentityInsertResult;
import com.colombus.user.repository.UserReadRepository;
import com.colombus.user.service.event.UserOnboardedEvent;
import com.colombus.user.service.result.LinkIdentityResult;
import com.colombus.user.service.result.UnlinkIdentityResult;
import com.colombus.user.web.internal.mapper.UserTypeMapper;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService {

    private final UserAuthRepository authRepo;
    private final UserReadRepository userReadRepo;
    private final IdentityReadRepository identityReadRepo;
    private final IdentityWriteRepository identityWriteRepo;
    private final OutboxPublishRepository outboxPubRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthSvcClient authClient;

    public record EnsureResult(UUID userId, boolean signup, boolean link, boolean sync, boolean login) {}

    // ============================
    // 사용자 및 identity 관리
    // ============================

    /** 사용자 및 identity 보장 (존재하지 않으면 생성, 존재하면 정보 동기화) */
    @Transactional
    public EnsureResult ensureUserAndIdentity(
        EnsureUserCommand req, @Nullable String ip, @Nullable String ua) {
        // 직렬화 락
        authRepo.xactLockByIdentity(req.provider(), req.providerTenant(), req.providerSub());

        boolean didSignup = false;
        boolean didLink = false;
        boolean didSync = false;

        // identity로 기존 사용자 찾기
        UUID userId = authRepo.findUserIdByIdentity(req.provider(), req.providerTenant(), req.providerSub());

        // 없고 email 있으면 email 기반 upsert
        if (userId == null) {
            if (Texts.hasText(req.email())) {
                var r = authRepo.upsertUserByEmailDetailed(req.email(), req.emailVerified());
                userId = r.userId();
                didSignup = r.created();
                didSync = r.verifiedPromoted();
            } else {
                userId = authRepo.createActiveUser();
                didSignup = true;
            }
        } else {
            // 기존 유저면 email 백필 + verified 승격(필요 시)
            if (Texts.hasText(req.email())) {
                boolean backfilled = authRepo.backfillEmailIfEmpty(userId, req.email(), req.emailVerified());
                if (backfilled) didSync = true;
            }
        }

        // identity link (새로 붙었으면 link)
        var insertResult =
            authRepo.insertIdentityIfAbsent(
                userId, req.provider(), req.providerTenant(), req.providerSub(), req.email());

        switch (insertResult) {
            case INSERTED:
                if (!didSignup) {
                    // 기존 사용자에 새 identity 연결된 상황
                    didLink = true;
                }
                break;
            case CONFLICT_EXISTS_FOR_ANOTHER_USER:
                // 이 identity는 이미 다른 사용자가 사용 중입니다.
                throw new IdentityAlreadyLinkedException();
            case ALREADY_EXISTS_FOR_USER:
                // 멱등성: 이미 이 사용자에게 연결되어 있으므로 아무것도 하지 않음
                break;
        }

        // --- 부가 로직을 이벤트로 발행 ---
        // 이 메서드의 트랜잭션이 성공적으로 커밋된 후에 리스너가 비동기적으로 처리합니다.
        eventPublisher.publishEvent(
            new UserOnboardedEvent(
                userId, req.provider(), didSignup, didLink, didSync, req.locale(), req.avatarUrl(), ip, ua));

        return new EnsureResult(userId, didSignup, didLink, didSync, true);
    }

    /** 외부 IDP 정보와 sub를 기반으로 내부 사용자 UUID를 찾아 반환합니다. */
    public UUID findUserIdByIdentity(AuthProvider provider, String tenant, String sub) {
        return authRepo.findUserIdByIdentity(provider, tenant, sub);
    }

    public UUID findUserIdByProviderAndSub(AuthProvider provider, String sub) {
        return identityReadRepo.findUserIdByProviderAndSub(provider, sub);
    }

    @Transactional
    public void deactivateUser(UUID userId, String reason) {
        // Linked identities 전체 조회 (없어도 탈퇴는 진행)
        List<UserIdentity> identities = identityReadRepo.findIdentities(userId);

        // DB soft-delete
        int row = authRepo.softDeleteUser(userId);
        if (row == 0) {
            log.warn("User {} not found or already deactivated.", userId);
            return;
        } else {
            log.info("User {} deactivated in DB.", userId);
        }
        row = identityWriteRepo.softDeleteAllByUserId(userId);
        if (row > 0) {
            log.info("Deleted {} identities for user {}.", row, userId);
        } else {
            log.info("No identities to delete for user {}.", userId);
        }

        // Outbox 패턴으로 이벤트 발행
        outboxPubRepo.appendSubjectUpsert(userId, false);

        List<ExternalIdentity> externalIdentities = identities.stream()
            .map(i -> new ExternalIdentity(
                UserTypeMapper.toCode(i.provider()),
                i.providerTenant(),
                i.providerSub()
            ))
            .toList();
        
        // auth 서비스에 Auth0 계정 삭제 및 세션 정리 요청
        if (!externalIdentities.isEmpty()) {
            authClient.deactivateIdentities(userId, externalIdentities, reason);
        }
    }

    // ============================
    // identity 관리
    // ============================

    @Transactional
    public LinkIdentityResult linkIdentity(LinkIdentityCommand req, String ip, String ua) {
        final var userId = req.userId();
        final var provider = req.provider();
        final var tenant = req.providerTenant();
        final var sub = req.providerSub();

        authRepo.xactLockByIdentity(provider, tenant, sub);

        // 사용자 활성 검증
        if (!userReadRepo.existsAlive(userId)) {
            return LinkIdentityResult.error("user_not_found_or_deleted", true, null);
        }

        // 동일 identity가 이미 같은 사용자에 존재? -> idempotent 성공
        if (identityReadRepo.identityExists(userId, provider, tenant, sub)) {
            return LinkIdentityResult.success();
        }

        // 동일 identity가 다른 사용자에 존재? -> 충돌(final)
        UUID owner = identityReadRepo.findUserIdByIdentity(provider, tenant, sub);
        if (owner != null && !owner.equals(userId)) {
            return LinkIdentityResult.error("identity_belongs_to_another_user", true, null);
        }

        // auth0 링크 금지 (패스워드 추가는 별도 flow)
        if (provider.equals(AuthProvider.AUTH0)) {
            return LinkIdentityResult.error("cannot_link_auth0_default", true, null);
        }

        boolean hasAuth0 = identityReadRepo.hasAuth0Identity(userId);
        if (!hasAuth0) {
            return LinkIdentityResult.error("must_link_auth0_first", true, null);
        }

        // 외부 링크
        int auth0Status = 0;
        try {
            boolean ok = authClient.linkAtAuth0(userId, provider, tenant, sub);
            if (!ok) {
                return LinkIdentityResult.error("auth0_rejected", true, null);
            }
            auth0Status = 200; // 성공 시
        } catch (RestClientResponseException r) {
            int s = r.getStatusCode().value();
            auth0Status = s;
            if (s >= 400 && s < 500) {
                // secondary가 다른 primary에 묶임/존재하지 않음 등 -> 영구 실패
                String code =
                    (s == 409)
                        ? "auth0_conflict"
                        : (s == 404 ? "identity_not_found_at_provider" : "auth0_4xx");
                return LinkIdentityResult.error(code, true, s);
            }
            // 5xx -> 재시도
            return LinkIdentityResult.error(null, false, s);
        } catch (Exception e) {
            // 네트워크/타임아웃 -> 재시도
            return LinkIdentityResult.error(null, false, null);
        }

        // 내부 link (insert if absent, 경쟁 내성)
        var insertResult = authRepo.insertIdentityIfAbsent(userId, provider, tenant, sub, null);

        if (insertResult == IdentityInsertResult.CONFLICT_EXISTS_FOR_ANOTHER_USER) {
            // 이 경우는 이미 위에서 확인했지만, race condition에 의해 발생할 수 있음
            // TODO: Consider rollback logic for the external link if needed
            return LinkIdentityResult.error("identity_belongs_to_another_user", true, null);
        }

        // auth0 링크가 성공했고, 내부적으로도 삽입되었거나 이미 존재했다면 성공
        boolean auth0Success = auth0Status >= 200 && auth0Status < 300;
        if (auth0Success) {
            String detail =
                "{\"provider\":\""
                    + provider
                    + "\",\"tenant\":\""
                    + tenant
                    + "\",\"sub\":\""
                    + sub
                    + "\"}";
            authRepo.insertAuthEvent(userId, provider, AuthEventKind.LINK, detail, ip, ua);
            return LinkIdentityResult.success();
        } else {
            return LinkIdentityResult.error("auth0_rejected", true, null);
        }
    }

    @Transactional
    public UnlinkIdentityResult unlinkIdentity(UnlinkIdentityCommand req, String ip, String ua) {
        final var userId = req.userId();
        final var provider = req.provider();
        final var tenant = req.providerTenant();
        final var sub = req.providerSub();

        authRepo.xactLockByIdentity(provider, tenant, sub);

        // 사용자 활성 검증
        if (!userReadRepo.existsAlive(userId)) {
            return UnlinkIdentityResult.error("user_not_found_or_deleted", true, null);
        }

        // identity 존재/활성 검증
        if (!identityReadRepo.identityExists(userId, provider, tenant, sub)) {
            return UnlinkIdentityResult.error("identity_not_found", true, null);
        }

        // auth0 기본 identity 차단
        if (provider.equals(AuthProvider.AUTH0)) {
            return UnlinkIdentityResult.error("cannot_unlink_auth0_default", true, null);
        }

        // auth0 identity 존재 여부 확인
        boolean hasAuth0 = identityReadRepo.hasAuth0Identity(userId);
        if (!hasAuth0) {
            // SSO-only 계정 → 활성 SSO가 2개 이상일 때만 허용
            int ssoCount = identityReadRepo.countActiveSsoIdentities(userId);
            if (ssoCount <= 1) {
                return UnlinkIdentityResult.error("last_sso_identity", true, null);
            }
        } else {
            // auth0 포함 계정 → 전체 활성 identity가 2개 이상일 때만 허용
            int totalCount = identityReadRepo.countActiveIdentities(userId);
            if (totalCount <= 1) {
                return UnlinkIdentityResult.error("last_identity", true, null);
            }
        }

        // 외부 unlink (auth-svc)
        int auth0Status = 0;
        try {
            boolean ok = authClient.unlinkAtAuth0(userId, provider, tenant, sub);
            if (!ok) {
                return UnlinkIdentityResult.error("auth0_rejected", true, null);
            }
            auth0Status = 200;
        } catch (RestClientResponseException r) {
            int s = r.getStatusCode().value();
            auth0Status = s;
            if (s == 404) {
                // 이미 없는 상태 -> 내부 처리만 진행
            } else if (s >= 400 && s < 500) {
                // 정책/요청 문제 -> 영구 실패
                return UnlinkIdentityResult.error("auth0_4xx", true, s);
            } else {
                // 5xx -> 일시 실패 (재시도)
                return UnlinkIdentityResult.error(null, false, s);
            }
        } catch (Exception e) {
            // 네트워크/타임아웃 등 -> 일시 실패
            return UnlinkIdentityResult.error(null, false, null);
        }

        // 내부 soft delete
        boolean deleted = identityWriteRepo.unlink(userId, provider, tenant, sub);
        boolean inRange = auth0Status >= 200 && auth0Status < 300 || auth0Status == 404;

        // 이벤트 로그
        String detail =
            "{\"provider\":\""
                + provider
                + "\",\"tenant\":\""
                + tenant
                + "\",\"sub\":\""
                + sub
                + "\"}";
        authRepo.insertAuthEvent(userId, provider, AuthEventKind.UNLINK, detail, ip, ua);

        return UnlinkIdentityResult.success();
    }

    /** identity 기준 인증 이벤트 기록 */
    @Transactional
    public void recordEventByIdentity(AuthEventByIdentityRequest req, String ip, String ua) {

        final var provider = UserTypeMapper.fromCode(req.provider());
        final var tenant = req.providerTenant();
        final var sub = req.providerSub();
        final var kind = UserTypeMapper.fromCode(req.kind());
        final var detail = req.detail();

        UUID userId = authRepo.findUserIdByIdentityForUpdate(provider, tenant, sub);
        if (userId == null) return;

        authRepo.insertAuthEvent(userId, provider, kind, detail, ip, ua);
    }
}
