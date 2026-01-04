package com.colombus.common.utility.text;

/**
 * 가벼운 텍스트 유틸리티 클래스
 * String 대신 CharSequence를 사용하여 불필요한 할당을 줄입니다.
 */
public final class Texts {
    private Texts() {}

    /**
     * 공백이 아닌 문자를 1개 이상 포함하면 true를 반환합니다.
     * null 안전합니다.
     *
     * @param cs 검사할 문자 시퀀스
     * @return 공백이 아닌 문자가 하나라도 있으면 true, 아니면 false
     */
    public static boolean hasText(CharSequence cs) {
        if (cs == null) return false;
        final int len = cs.length();
        for (int i = 0; i < len; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) return true;
        }
        return false;
    }

    /**
     * null이 아니고 길이가 1 이상이면 true를 반환합니다. (공백 허용)
     *
     * @param cs 검사할 문자 시퀀스
     * @return 길이가 1 이상이면 true
     */
    public static boolean hasLength(CharSequence cs) {
        return cs != null && cs.length() > 0;
    }

    /**
     * null 또는 공백뿐이면 true를 반환합니다. (hasText의 반대)
     *
     * @param cs 검사할 문자 시퀀스
     * @return 비어있거나 공백뿐이면 true
     */
    public static boolean isBlank(CharSequence cs) {
        return !hasText(cs);
    }

    /**
     * 공백뿐이면 null을 반환하고, 그렇지 않으면 trim한 값을 반환합니다.
     *
     * @param s 입력 문자열
     * @return trim 결과 또는 null
     */
    public static String trimToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }

    /**
     * 공백뿐이면 null을 반환합니다. (trim 미수행)
     *
     * @param s 입력 문자열
     * @return 공백뿐이면 null, 아니면 원본 문자열
     */
    public static char[] nullIfBlank(String s) {
        return hasText(s) ? s.toCharArray() : null;
    }

    /**
     * 공백뿐이면 IllegalArgumentException을 던집니다.
     *
     * @param s 검사할 문자열
     * @param argName 예외 메시지용 인자 이름 (null 허용)
     * @return 원본 문자열 s
     * @throws IllegalArgumentException s가 null이거나 공백뿐인 경우
     */
    public static String requireNonBlank(String s, String argName) {
        if (!hasText(s)) {
            throw new IllegalArgumentException(
                (argName == null ? "text" : argName) + " must not be blank"
            );
        }
        return s;
    }

    public static String firstNonBlank(String... arr) {
        for (String s : arr) if (s != null && !s.isBlank()) return s;
        return "unknown";
    }
}