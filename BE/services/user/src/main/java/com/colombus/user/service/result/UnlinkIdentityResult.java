package com.colombus.user.service.result;

import jakarta.annotation.Nullable;

/**
 * {@code UserAuthService.unlinkIdentity} 메서드의 결과를 나타내는 레코드.
 *
 * @param unlinked ID가 성공적으로 연결 해제되었는지 여부.
 * @param finalResult 이 작업이 최종 상태에 도달하여 더 이상 재시도할 필요가 없는지 여부.
 * @param retry 이 작업이 일시적인 오류로 인해 재시도될 수 있는지 여부.
 * @param error 오류 코드 또는 메시지 (오류 발생 시).
 * @param status HTTP 상태 코드 (오류 발생 시).
 */
public record UnlinkIdentityResult(
    boolean unlinked,
    boolean finalResult,
    boolean retry,
    @Nullable String error,
    @Nullable Integer status
) {
    public static UnlinkIdentityResult success() {
        return new UnlinkIdentityResult(true, true, false, null, null);
    }

    public static UnlinkIdentityResult error(String error, boolean finalResult, @Nullable Integer status) {
        return new UnlinkIdentityResult(false, finalResult, !finalResult, error, status);
    }
}
