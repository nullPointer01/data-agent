package com.ai.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 常见 API 响应包装的工厂方法。
 *
 * @author data-agent
 */
public final class ApiResponse {

    private ApiResponse() {
    }

    public static Map<String, Object> error(String message) {
        return error(message, null);
    }

    public static Map<String, Object> success() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("timestamp", Instant.now().toString());
        return response;
    }

    public static Map<String, Object> success(Object data) {
        Map<String, Object> response = success();
        if (data != null) {
            response.put("data", data);
        }
        return response;
    }

    public static Map<String, Object> error(String message, String code) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        if (code != null && !code.isBlank()) {
            response.put("code", code);
        }
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
