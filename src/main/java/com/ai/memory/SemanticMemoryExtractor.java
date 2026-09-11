package com.ai.memory;

import com.ai.mcp.McpModelService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提取并校验用户明确表达的偏好、画像事实和结论。
 *
 * @author data-agent
 */
@Component
public class SemanticMemoryExtractor {

    private static final int MAX_CANDIDATES = 3;
    private static final int MAX_CONTENT_LENGTH = 240;
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "(?:我叫|我的名字是|请称呼我为|称呼我为)([\\u4e00-\\u9fa5A-Za-z0-9_]{2,24})");
    private static final Pattern ROLE_PATTERN = Pattern.compile(
            "(?:我的职位是|我的职业是|我担任|我是(?:一名|一个)?)([\\u4e00-\\u9fa5A-Za-z0-9_]{2,24})");
    private static final Pattern COMPANY_PATTERN = Pattern.compile(
            "(?:我的公司是|我们公司是|我在)([\\u4e00-\\u9fa5A-Za-z0-9_\\-]{2,40})(?:工作|任职|[，。,.]|$)");
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(api[_ -]?key|access[_ -]?token|secret|password|密码|密钥|身份证|银行卡)\\s*[:=：]?\\s*\\S+");
    private static final Set<String> INVALID_IDENTITY_VALUES = Set.of(
            "你的", "你们的", "一个", "一名", "助手", "模型", "机器人");
    private static final Set<MemoryType> ALLOWED_TYPES = Set.of(
            MemoryType.PREFERENCE, MemoryType.ENTITY, MemoryType.CONCLUSION);
    private static final String MODEL_ERROR_PREFIX = "模型调用失败";

    private final MemoryWorthinessEvaluator worthinessEvaluator;
    private final McpModelService modelService;
    private final ObjectMapper objectMapper;
    private final MemoryProperties memoryProperties;

    public SemanticMemoryExtractor(MemoryWorthinessEvaluator worthinessEvaluator,
            McpModelService modelService,
            ObjectMapper objectMapper,
            MemoryProperties memoryProperties) {
        this.worthinessEvaluator = worthinessEvaluator;
        this.modelService = modelService;
        this.objectMapper = objectMapper;
        this.memoryProperties = memoryProperties;
    }

    /**
     * 使用确定性规则提取用户明确要求保存的记忆。
     *
     * @param userMessage 用户消息
     * @return 通过校验的显式记忆候选
     */
    public List<SemanticMemoryCandidate> extractExplicit(String userMessage) {
        String message = normalize(userMessage);
        if (!worthinessEvaluator.hasExplicitMemoryIntent(message) || containsSensitiveInformation(message)) {
            return List.of();
        }
        List<SemanticMemoryCandidate> candidates = new ArrayList<>();
        addIdentityCandidate(candidates, message, NAME_PATTERN, SemanticMemoryKey.DISPLAY_NAME);
        addIdentityCandidate(candidates, message, COMPANY_PATTERN, SemanticMemoryKey.COMPANY);
        addIdentityCandidate(candidates, message, ROLE_PATTERN, SemanticMemoryKey.ROLE);
        addPreferenceCandidates(candidates, message);
        if (candidates.isEmpty()) {
            MemoryType type = worthinessEvaluator.classifyExplicitMemory(message);
            String prefix = switch (type) {
                case PREFERENCE -> "preference.general.";
                case ENTITY -> "profile.general.";
                default -> "conclusion.general.";
            };
            candidates.add(new SemanticMemoryCandidate(
                    type, prefix + fingerprint(message), limit(message), 1D, message, true));
        }
        return candidates.stream().distinct().limit(MAX_CANDIDATES).toList();
    }

    /**
     * 调用 JSON 模型从普通对话中提取高置信度语义记忆。
     *
     * @param userMessage 用户消息
     * @param assistantReply 助手回复，仅作为理解上下文
     * @param modelId 本轮使用的模型
     * @return 通过证据和安全校验的候选
     */
    public List<SemanticMemoryCandidate> extractImplicit(
            String userMessage, String assistantReply, String modelId) {
        String normalizedUser = normalize(userMessage);
        if (!memoryProperties.isSemanticExtractionEnabled()
                || !StringUtils.hasText(normalizedUser)
                || containsSensitiveInformation(normalizedUser)) {
            return List.of();
        }
        String response = modelService.callBackgroundModelJson(buildPrompt(normalizedUser, assistantReply), modelId);
        if (!StringUtils.hasText(response) || response.startsWith(MODEL_ERROR_PREFIX)) {
            return List.of();
        }
        return parseCandidates(response, normalizedUser);
    }

    private void addIdentityCandidate(List<SemanticMemoryCandidate> target,
            String message, Pattern pattern, String semanticKey) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return;
        }
        String value = trimStatement(matcher.group(1));
        if (!isValidIdentityValue(value)) {
            return;
        }
        target.add(new SemanticMemoryCandidate(
                MemoryType.ENTITY, semanticKey, value, 1D, matcher.group(), true));
    }

    private void addPreferenceCandidates(List<SemanticMemoryCandidate> target, String message) {
        String communicationStyle = latestPositivePreference(message,
                new PreferenceKeyword("简洁", "简洁直接"),
                new PreferenceKeyword("直接", "简洁直接"),
                new PreferenceKeyword("精简", "简洁直接"),
                new PreferenceKeyword("详细", "详细解释"),
                new PreferenceKeyword("展开说明", "详细解释"),
                new PreferenceKeyword("解释清楚", "详细解释"));
        if (StringUtils.hasText(communicationStyle)) {
            target.add(new SemanticMemoryCandidate(MemoryType.PREFERENCE,
                    SemanticMemoryKey.COMMUNICATION_STYLE, communicationStyle, 1D, message, true));
        }

        String outputFormat = latestPositivePreference(message,
                new PreferenceKeyword("表格", "表格优先"),
                new PreferenceKeyword("列表", "列表优先"),
                new PreferenceKeyword("图表", "图表优先"),
                new PreferenceKeyword("可视化", "图表优先"));
        if (StringUtils.hasText(outputFormat)) {
            target.add(new SemanticMemoryCandidate(MemoryType.PREFERENCE,
                    SemanticMemoryKey.OUTPUT_FORMAT, outputFormat, 1D, message, true));
        }
    }

    private String latestPositivePreference(String message, PreferenceKeyword... keywords) {
        int latestIndex = -1;
        String selected = null;
        for (PreferenceKeyword keyword : keywords) {
            int searchFrom = 0;
            while (searchFrom < message.length()) {
                int index = message.indexOf(keyword.keyword(), searchFrom);
                if (index < 0) {
                    break;
                }
                if (index > latestIndex && !isNegated(message, index)) {
                    latestIndex = index;
                    selected = keyword.normalizedValue();
                }
                searchFrom = index + keyword.keyword().length();
            }
        }
        return selected;
    }

    private boolean isNegated(String message, int keywordIndex) {
        int start = Math.max(0, keywordIndex - 6);
        String prefix = message.substring(start, keywordIndex);
        return containsAny(prefix, "不要", "不用", "不再", "不必", "无需", "避免", "取消", "别", "不");
    }

    private List<SemanticMemoryCandidate> parseCandidates(String response, String userMessage) {
        try {
            JsonNode memories = objectMapper.readTree(extractJsonObject(response)).path("memories");
            if (!memories.isArray()) {
                return List.of();
            }
            List<SemanticMemoryCandidate> candidates = new ArrayList<>();
            for (JsonNode node : memories) {
                SemanticMemoryCandidate candidate = parseCandidate(node, userMessage);
                if (candidate != null) {
                    candidates.add(candidate);
                }
                if (candidates.size() >= MAX_CANDIDATES) {
                    break;
                }
            }
            return candidates;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private SemanticMemoryCandidate parseCandidate(JsonNode node, String userMessage) {
        MemoryType type = parseType(node.path("type").asText());
        String key = SemanticMemoryKey.normalize(node.path("semanticKey").asText());
        String content = limit(normalize(node.path("content").asText()));
        String evidence = normalize(node.path("evidence").asText());
        double confidence = node.path("confidence").asDouble(0D);
        if (type == null || !SemanticMemoryKey.isValid(key)
                || !StringUtils.hasText(content) || !StringUtils.hasText(evidence)
                || !userMessage.contains(evidence)
                || confidence < memoryProperties.getSemanticExtractionMinConfidence()
                || containsSensitiveInformation(content)) {
            return null;
        }
        return new SemanticMemoryCandidate(type, key, content, Math.min(1D, confidence), evidence, false);
    }

    private MemoryType parseType(String value) {
        try {
            MemoryType type = MemoryType.valueOf(value.toUpperCase(Locale.ROOT));
            return ALLOWED_TYPES.contains(type) ? type : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String buildPrompt(String userMessage, String assistantReply) {
        return """
                从下面一轮对话中提取可跨会话复用的用户语义记忆，返回 JSON 对象。
                JSON 格式：{"memories":[{"type":"PREFERENCE|ENTITY|CONCLUSION","semanticKey":"...","content":"...","confidence":0.0,"evidence":"用户原话片段"}]}
                规则：
                1. 最多返回3条，没有稳定信息时返回 {"memories":[]}。
                2. 只提取用户明确表达的稳定偏好、身份事实或已确认决定，不把问题、寒暄、助手建议和助手自述当作用户记忆。
                3. evidence 必须逐字来自用户原话；无法提供原话证据就不要提取。
                4. semanticKey 使用 preference.*、profile.* 或 conclusion.*，同一属性必须使用稳定键。
                5. 不提取密码、密钥、Token、身份证、银行卡等敏感信息。

                用户原话：%s
                助手回复（仅用于理解语境）：%s
                """.formatted(userMessage, limit(normalize(assistantReply)));
    }

    private String extractJsonObject(String value) {
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end < start) {
            return "{}";
        }
        return value.substring(start, end + 1);
    }

    private boolean isValidIdentityValue(String value) {
        if (!StringUtils.hasText(value) || INVALID_IDENTITY_VALUES.contains(value)) {
            return false;
        }
        return INVALID_IDENTITY_VALUES.stream().noneMatch(value::startsWith);
    }

    private boolean containsSensitiveInformation(String value) {
        return StringUtils.hasText(value) && SENSITIVE_PATTERN.matcher(value).find();
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String trimStatement(String value) {
        return normalize(value).replaceAll("[，。,.；;].*$", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String limit(String value) {
        return value.length() <= MAX_CONTENT_LENGTH ? value : value.substring(0, MAX_CONTENT_LENGTH);
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private record PreferenceKeyword(String keyword, String normalizedValue) {
    }
}
