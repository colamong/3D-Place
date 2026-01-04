package com.colombus.media.web.internal;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.colombus.media.contract.dto.UploadCommitRequest;
import com.colombus.media.contract.dto.UploadCommitResponse;
import com.colombus.media.upload.service.UploadCommitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(path = "/internal/uploads", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class UploadCommitController {

    private final UploadCommitService service;

    @PostMapping(path = "/commit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UploadCommitResponse commit(
        @Valid @RequestBody UploadCommitRequest body
    ) {
        return service.commit(body);
    }
}
