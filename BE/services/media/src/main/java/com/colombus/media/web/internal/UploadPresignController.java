package com.colombus.media.web.internal;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.colombus.media.contract.dto.PresignPutRequest;
import com.colombus.media.contract.dto.PresignPutResponse;
import com.colombus.media.upload.service.UploadPresignService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(path = "/internal/uploads", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class UploadPresignController {

    private final UploadPresignService service;

    @PostMapping(path = "/presign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PresignPutResponse presign(
        @Valid @RequestBody PresignPutRequest body
    ) {
        return service.createPresignUrl(body);
    }
}
