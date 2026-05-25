package com.ai.security.auth;

public record AuthResult<T>(boolean success, T data, String message, String code) {

    public static <T> AuthResult<T> success(T data) {
        return new AuthResult<>(true, data, null, null);
    }

    public static <T> AuthResult<T> failure(String message, String code) {
        return new AuthResult<>(false, null, message, code);
    }
}
