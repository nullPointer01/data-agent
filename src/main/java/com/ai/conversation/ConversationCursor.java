package com.ai.conversation;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/** 会话列表使用时间和 ID 双游标，避免时间相同时漏项；不在客户端暴露分页实现。 */
public record ConversationCursor(LocalDateTime time, String id) {
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((time + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static ConversationCursor decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.length() > 256) throw new IllegalArgumentException();
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2 || parts[1].isBlank() || parts[1].length() > 64) throw new IllegalArgumentException();
            return new ConversationCursor(LocalDateTime.parse(parts[0]), parts[1]);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("会话游标无效");
        }
    }
}
