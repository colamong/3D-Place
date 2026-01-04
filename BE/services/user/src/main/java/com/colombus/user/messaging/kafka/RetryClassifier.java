package com.colombus.user.messaging.kafka;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

public final class RetryClassifier {

    private RetryClassifier() {}

    public static boolean isPermanent(Throwable t) {
        // 중복키/무결성 위반 등은 재시도 무의미
        if (t instanceof DuplicateKeyException || t instanceof DataIntegrityViolationException) return true;
        
        // 클라이언트 요청 문제(검증 실패 등) -> 재시도 무의미
        if (t instanceof IllegalArgumentException || t instanceof IllegalStateException) return true;

        // 4xx는 보통 재시도 무의미
        if (t instanceof HttpClientErrorException) return true;
        if (t instanceof RestClientResponseException r) {
            int s = r.getStatusCode().value();
            if (s >= 400 && s < 500) return true;     // 4xx
            // 5xx는 재시도 대상 (false)
        }
        
        return false;
    }
}
