package com.ai.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/** 记录列表按创建时间与唯一键定位；兼容历史空时间，身份始终另由服务端约束。 */
public record RecordCursor(LocalDateTime time, String id) {
    /** 游标只表达位置，不提供租户或用户授权。 */
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                ((time == null ? "-" : time.toString()) + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    /** 校验游标结构，错误游标不静默回到第一页。 */
    public static RecordCursor decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.length() > 256) throw new IllegalArgumentException();
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2 || !parts[1].matches("[a-zA-Z0-9_-]{1,64}")) {
                throw new IllegalArgumentException();
            }
            return new RecordCursor("-".equals(parts[0]) ? null : LocalDateTime.parse(parts[0]), parts[1]);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("列表游标无效");
        }
    }
}
