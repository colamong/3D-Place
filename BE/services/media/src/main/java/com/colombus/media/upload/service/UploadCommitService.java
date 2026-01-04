package com.colombus.media.upload.service;

import com.colombus.media.contract.dto.UploadCommitRequest;
import com.colombus.media.contract.dto.UploadCommitResponse;

public interface UploadCommitService {
    UploadCommitResponse commit(UploadCommitRequest req);
}