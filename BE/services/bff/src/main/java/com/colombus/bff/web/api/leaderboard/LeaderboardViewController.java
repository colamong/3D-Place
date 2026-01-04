package com.colombus.bff.web.api.leaderboard;

import com.colombus.clan.contract.dto.ClanSummaryBulkRequest;
import com.colombus.clan.contract.dto.ClanSummaryResponse;
import com.colombus.common.web.core.response.CommonResponse;
import com.colombus.common.web.core.tracing.TraceIdProvider;
import com.colombus.leaderboard.contract.dto.LeaderboardEntryResponse;
import com.colombus.leaderboard.contract.dto.LeaderboardResponse;
import com.colombus.leaderboard.contract.enums.LeaderboardPeriodKey;
import com.colombus.leaderboard.contract.enums.LeaderboardSubjectType;
import com.colombus.user.contract.dto.UserSummaryBulkRequest;
import com.colombus.user.contract.dto.UserSummaryResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * 리더보드 데이터를 조회해 사용자/클랜 이름과 함께 합성하여 반환하는 BFF API.
 * <p>
 * subject(대상: user|clan)와 key(기간: day|week|month)를 기준으로 단일 엔드포인트에서
 * 필요한 랭킹 페이지를 내려준다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leaderboard")
public class LeaderboardViewController {

    private final WebClient leaderboardClient;
    private final WebClient userClient;
    private final WebClient clanClient;
    private final TraceIdProvider trace;

    /**
     * 리더보드 목록 조회.
     *
     * @param subject user/clan 중 하나
     * @param key     day/week/month 중 하나
     * @param size    조회할 랭킹 수 (최대 100)
     */
    @GetMapping
    public Mono<ResponseEntity<CommonResponse<LeaderboardView>>> getLeaderboard(
        @RequestParam String subject,
        @RequestParam String key,
        @RequestParam(name = "size", defaultValue = "50") int size,
        ServerHttpRequest request
    ) {
        LeaderboardSubjectType subjectType = parseSubject(subject);
        LeaderboardPeriodKey periodKey = parseKey(key);
        int limit = Math.max(1, Math.min(size, 100));

        Mono<LeaderboardResponse> leaderboardMono = leaderboardClient.get()
            .uri(uriBuilder -> uriBuilder
                .queryParam("subject", subjectType.name())
                .queryParam("key", periodKey.name())
                .queryParam("size", limit)
                .build())
            .retrieve()
            .bodyToMono(LeaderboardResponse.class);

        Mono<LeaderboardView> viewMono = leaderboardMono.flatMap(response -> {
            List<UUID> ids = response.entries().stream()
                .map(LeaderboardEntryResponse::id)
                .toList();

            return resolveNames(subjectType, ids)
                .map(nameMap -> toView(response, nameMap));
        });

        String path = request.getPath().value();
        String traceId = trace.currentTraceId();

        return viewMono.map(view ->
            ResponseEntity.ok(CommonResponse.success(path, traceId, view))
        );
    }

    /**
     * subject 타입에 맞춰 사용자/클랜 이름을 조회한다.
     */
    private Mono<Map<UUID, String>> resolveNames(LeaderboardSubjectType subject, List<UUID> ids) {
        if (ids.isEmpty()) {
            return Mono.just(Map.of());
        }
        return switch (subject) {
            case USER -> fetchUserNames(ids);
            case CLAN -> fetchClanNames(ids);
        };
    }

    /**
     * 사용자 요약 정보를 bulk API로 받아 (userId -> nickname) 맵을 만든다.
     */
    private Mono<Map<UUID, String>> fetchUserNames(List<UUID> userIds) {
        var type = new ParameterizedTypeReference<List<UserSummaryResponse>>() {};
        return userClient.post()
            .uri("/bulk-summary")
            .bodyValue(new UserSummaryBulkRequest(userIds))
            .retrieve()
            .bodyToMono(type)
            .onErrorResume(ex -> {
                log.warn("Failed to fetch user summaries", ex);
                return Mono.just(List.of());
            })
            .map(list -> list.stream()
                .collect(Collectors.toMap(
                    UserSummaryResponse::id,
                    UserSummaryResponse::nickname,
                    (existing, ignored) -> existing
                )));
    }

    /**
     * 클랜 요약 정보를 bulk API로 받아 (clanId -> name) 맵을 만든다.
     */
    private Mono<Map<UUID, String>> fetchClanNames(List<UUID> clanIds) {
        var type = new ParameterizedTypeReference<List<ClanSummaryResponse>>() {};
        return clanClient.post()
            .uri("/bulk-summary")
            .bodyValue(new ClanSummaryBulkRequest(clanIds))
            .retrieve()
            .bodyToMono(type)
            .onErrorResume(ex -> {
                log.warn("Failed to fetch clan summaries", ex);
                return Mono.just(List.of());
            })
            .map(list -> list.stream()
                .collect(Collectors.toMap(
                    ClanSummaryResponse::id,
                    ClanSummaryResponse::name,
                    (existing, ignored) -> existing,
                    LinkedHashMap::new
                )));
    }

    /**
     * 서버에서 내려준 엔트리를 rank/이름이 포함된 뷰 모델로 변환한다.
     */
    private LeaderboardView toView(LeaderboardResponse response, Map<UUID, String> nameMap) {
        List<LeaderboardView.LeaderboardEntryView> rows = new ArrayList<>();
        List<LeaderboardEntryResponse> entries = response.entries();
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntryResponse entry = entries.get(i);
            String name = nameMap.getOrDefault(entry.id(), "(알 수 없음)");
            rows.add(new LeaderboardView.LeaderboardEntryView(
                entry.id(),
                name,
                entry.paints(),
                i + 1
            ));
        }
        return new LeaderboardView(response.subject(), response.key(), rows);
    }

    private LeaderboardSubjectType parseSubject(String raw) {
        try {
            return LeaderboardSubjectType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid subject: " + raw);
        }
    }

    private LeaderboardPeriodKey parseKey(String raw) {
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key is required");
        }
        try {
            return LeaderboardPeriodKey.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid key: " + raw);
        }
    }
}
