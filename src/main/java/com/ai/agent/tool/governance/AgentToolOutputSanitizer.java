package com.ai.agent.tool.governance;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 在工具结果进入模型、事件、日志和 Trace 前执行统一脱敏与截断。
 *
 * @author data-agent
 */
@Component
public class AgentToolOutputSanitizer {

    private static final String REDACTED = "***";
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[a-z0-9._~+/-]+=*");
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(?i)((?:api[_-]?key|password|passwd|secret|access[_-]?token|refresh[_-]?token|token)"
                    + "\\s*[\\\"']?\\s*[:=]\\s*[\\\"']?)([^\\s,\\\"'}&]+)");
    private static final Pattern OPENAI_KEY = Pattern.compile("(?i)\\bsk-[a-z0-9_-]{8,}");
    private static final Pattern URI_CREDENTIAL = Pattern.compile(
            "(?i)([a-z][a-z0-9+.-]*://[^\\s:/@]+:)([^\\s@/]+)(@)");

    private final AgentToolGovernanceProperties properties;

    public AgentToolOutputSanitizer(AgentToolGovernanceProperties properties) {
        this.properties = properties;
    }

    public AgentToolSafePayload sanitize(String raw, AgentToolDescriptor descriptor) {
        String original = raw == null ? "" : raw;
        String safe = BEARER.matcher(original).replaceAll("$1" + REDACTED);
        safe = SECRET_FIELD.matcher(safe).replaceAll("$1" + REDACTED);
        safe = OPENAI_KEY.matcher(safe).replaceAll(REDACTED);
        safe = URI_CREDENTIAL.matcher(safe).replaceAll("$1" + REDACTED + "$3");
        boolean sanitized = !safe.equals(original);
        int descriptorLimit = descriptor == null ? properties.getMaxResultLength() : descriptor.maxResultLength();
        int limit = Math.min(descriptorLimit, properties.getMaxResultLength());
        boolean truncated = safe.length() > limit;
        if (truncated) {
            String marker = "\n...[结果已截断]";
            int contentLimit = Math.max(0, limit - marker.length());
            safe = safe.substring(0, contentLimit) + marker.substring(0, Math.min(marker.length(), limit));
        }
        return new AgentToolSafePayload(safe, sanitized, truncated, original.length());
    }
}
