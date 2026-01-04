package com.colombus.common.jooq;

import org.jooq.Converter;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * jOOQ 컨버터: OffsetDateTime을 Instant로 변환합니다.
 * 데이터베이스가 TIMESTAMPTZ를 저장할 때 (jOOQ 기본값: OffsetDateTime)
 * 자바 POJO/Record가 java.time.Instant를 기대하는 경우 유용합니다.
 */
public class OffsetDateTimeToInstantConverter implements Converter<OffsetDateTime, Instant> {

    @Override
    public Instant from(OffsetDateTime databaseObject) {
        return databaseObject != null ? databaseObject.toInstant() : null;
    }

    @Override
    public OffsetDateTime to(Instant userObject) {
        // Instant를 데이터베이스 저장을 위해 OffsetDateTime으로 변환할 때,
        // 데이터베이스의 TIMESTAMPTZ 컬럼이 UTC를 기준으로 시간을 저장하고 처리한다고 가정합니다.
        // 애플리케이션 전반의 시간대 전략과 일치하는지 확인해야 합니다.
        return userObject != null ? OffsetDateTime.ofInstant(userObject, java.time.ZoneOffset.UTC) : null;
    }

    @Override
    public Class<OffsetDateTime> fromType() {
        return OffsetDateTime.class;
    }

    @Override
    public Class<Instant> toType() {
        return Instant.class;
    }
}
