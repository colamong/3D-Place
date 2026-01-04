package com.colombus.bff.web.api.world;

import com.colombus.common.domain.dto.EraseResponse;
import com.colombus.common.domain.dto.PaintResponse;
import com.colombus.common.web.core.response.CommonResponse;
import com.colombus.common.web.core.tracing.TraceIdProvider;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/world")
public class WorldVIewController {

    private final WebClient worldClient;
    private final TraceIdProvider trace;

    public record UrlResponse(String snapshotUrl, String glbUrl, List<PaintResponse> paintResponses, List<EraseResponse> eraseResponses) {}

    @GetMapping("/{world}/{lod}/{chunkId}")
    public Mono<ResponseEntity<CommonResponse<UrlResponse>>> get(
        ServerHttpRequest request,
        @PathVariable String world,
        @PathVariable int lod,
        @PathVariable String chunkId
    ) {

        String path    = request.getPath().value();
        String traceId = trace.currentTraceId();

        Mono<UrlResponse> response = worldClient.get()
                .uri("/{world}/{lod}/{chunkId}", world, lod, chunkId)
                .retrieve()
                .bodyToMono(UrlResponse.class);

        return response.map(res -> ResponseEntity.ok(CommonResponse.success(path, traceId, res)));
    }
}
