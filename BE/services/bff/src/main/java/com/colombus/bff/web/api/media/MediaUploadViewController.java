package com.colombus.bff.web.api.media;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import com.colombus.common.web.core.response.CommonResponse;
import com.colombus.common.web.core.tracing.TraceIdProvider;
import com.colombus.media.contract.dto.PresignPutRequest;
import com.colombus.media.contract.dto.PresignPutResponse;
import com.colombus.media.contract.dto.UploadCommitRequest;
import com.colombus.media.contract.dto.UploadCommitResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(path = "/api/uploads", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class MediaUploadViewController {

    private final WebClient mediaClient;
    private final TraceIdProvider trace;

    @PostMapping(path = "/presign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<CommonResponse<PresignPutResponse>>> presign(
        ServerHttpRequest req,
        @Valid @RequestBody PresignPutRequest body
    ) {
        Mono<PresignPutResponse> res = mediaClient.post()
            .uri("/presign")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(PresignPutResponse.class);

        return res.map(r -> ok(req, r));
    }

    @PostMapping(path = "/commit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<CommonResponse<UploadCommitResponse>>> commit(
        ServerHttpRequest req,
        @Valid @RequestBody UploadCommitRequest body
    ) {
        Mono<UploadCommitResponse> res = mediaClient.post()
            .uri("/commit")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(UploadCommitResponse.class);

        return res.map(r -> ok(req, r));
    }

    private <T> ResponseEntity<CommonResponse<T>> ok(ServerHttpRequest req, T data) {
        return ResponseEntity.ok(
            CommonResponse.success(req.getPath().value(), trace.currentTraceId(), data)
        );
    }
}
