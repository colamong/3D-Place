package com.colombus.media.upload.service;

import com.colombus.media.contract.dto.PresignPutRequest;
import com.colombus.media.contract.dto.PresignPutResponse;

public interface UploadPresignService {
    PresignPutResponse createPresignUrl(PresignPutRequest req);
}
