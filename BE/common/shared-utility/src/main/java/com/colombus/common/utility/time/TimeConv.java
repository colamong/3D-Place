package com.colombus.common.utility.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import jakarta.annotation.Nullable;

/**
 * UTC 기준의 시간 변환 유틸리티 클래스.
 * <p>
 * JPA/JDBC(JOOQ) 매핑 시 주로 {@code OffsetDateTime ↔ Instant(UTC)} 변환이 필요합니다.
 * 본 클래스의 모든 메서드는 <b>null-안전</b>하게 동작합니다(입력이 {@code null}이면 {@code null} 반환).
 */
public final class TimeConv {
    private TimeConv() {}

    /* ================================
     * OffsetDateTime ↔ Instant (UTC)
     * ================================ */

    /**
     * {@code OffsetDateTime}을 {@code Instant}(UTC)로 변환합니다.
     * 입력이 {@code null}이면 {@code null}을 반환합니다.
     *
     * @param odt 변환할 {@code OffsetDateTime}
     * @return 변환된 {@code Instant}(UTC) 또는 {@code null}
     */
    public static @Nullable Instant toInstant(@Nullable OffsetDateTime odt) {
        return (odt == null) ? null : odt.toInstant();
    }

    /**
     * {@code Instant}(UTC)를 {@code OffsetDateTime}(UTC)로 변환합니다.
     * 입력이 {@code null}이면 {@code null}을 반환합니다.
     *
     * @param i 변환할 {@code Instant}(UTC)
     * @return 변환된 {@code OffsetDateTime}(UTC) 또는 {@code null}
     */
    public static @Nullable OffsetDateTime toUtcOdt(@Nullable Instant i) {
        return (i == null) ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /* ======================================
     * LocalDateTime / ZonedDateTime 보조 변환
     * ====================================== */

    /**
     * {@code LocalDateTime}을 지정한 타임존으로 간주하여 {@code Instant}(UTC)로 변환합니다.
     * 입력이 {@code null}이면 {@code null}을 반환합니다.
     *
     * @param ldt  변환할 {@code LocalDateTime}
     * @param zone {@code ldt}가 속한 {@code ZoneId}
     * @return 변환된 {@code Instant}(UTC) 또는 {@code null}
     */
    public static @Nullable Instant toInstant(@Nullable LocalDateTime ldt, ZoneId zone) {
        return (ldt == null) ? null : ldt.atZone(zone).toInstant();
    }

    /**
     * {@code ZonedDateTime}을 {@code Instant}(UTC)로 변환합니다.
     * 입력이 {@code null}이면 {@code null}을 반환합니다.
     *
     * @param zdt 변환할 {@code ZonedDateTime}
     * @return 변환된 {@code Instant}(UTC) 또는 {@code null}
     */
    public static @Nullable Instant toInstant(@Nullable ZonedDateTime zdt) {
        return (zdt == null) ? null : zdt.toInstant();
    }

    /* =========
     * 편의 함수
     * ========= */

    /**
     * 현재 시각을 {@code Instant}(UTC)로 반환합니다.
     *
     * @return 현재 {@code Instant}(UTC)
     */
    public static Instant nowUtc() {
        return Instant.now();
    }

    /**
     * 현재 시각을 {@code OffsetDateTime}(UTC)로 반환합니다.
     *
     * @return 현재 {@code OffsetDateTime}(UTC)
     */
    public static OffsetDateTime nowUtcOdt() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}