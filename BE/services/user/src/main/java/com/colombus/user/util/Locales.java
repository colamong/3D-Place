package com.colombus.user.util;

import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import com.colombus.common.utility.text.Texts;
import jakarta.annotation.Nullable;

public final class Locales {

    private Locales() {}

    public static @Nullable String canonicalOrNull(@Nullable String tag) {
        tag = Texts.trimToNull(tag);
        if (tag == null) return null;
        try {
            var canonical = new Locale.Builder().setLanguageTag(tag).build().toLanguageTag();
            return Texts.trimToNull(canonical);
        } catch (IllformedLocaleException e) {
            return null;
        }
    }

    /**
     * Accept-Language 파싱 → BCP-47 태그 반환.
     * supported가 주어지면 그 안에서 best-match, 없으면 첫 번째 range를 정규화해서 반환.
     */
    public static @Nullable String fromAcceptLanguage(@Nullable String header, @Nullable List<Locale> supported) {
        if (header == null || header.isBlank()) return null;
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
            if (supported != null && !supported.isEmpty()) {
                Locale match = Locale.lookup(ranges, supported);
                if (match != null) return match.toLanguageTag();
            }
            // supported 목록이 없으면 가장 우선순위 높은 range 사용
            String first = ranges.get(0).getRange(); // "ko-KR" 또는 "ko"
            return new Locale.Builder().setLanguageTag(first).build().toLanguageTag();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}