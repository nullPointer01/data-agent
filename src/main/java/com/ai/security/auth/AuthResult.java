package com.ai.security.auth;

/**
 * 认证领域内部结果，用于在 Service 与 HTTP 层之间传递成功数据或稳定错误码。
 *
 * @param <T> 成功时的数据类型
 */
public record AuthResult<T>(boolean success, T data, String message, String code) {

    public static <T> AuthResult<T> success(T data) {
        return new AuthResult<>(true, data, null, null);
    }

    public static <T> AuthResult<T> failure(String message, String code) {
        return new AuthResult<>(false, null, message, code);
    }
}
