package com.ai.agent.react;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct 模型输出的文本辅助处理：提取思考内容、清洗最终答案。
 *
 * <p>工具调用已走 LangChain4j 原生 Function Calling，本类不再解析文本协议工具调用；
 * 保留的 JSON 识别逻辑仅用于清除模型偶发写入答案文本的工具调用残留。</p>
 *
 * @author data-agent
 */
@Component
public class ReActResponseParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern CHINESE_THINKING_PATTERN = Pattern.compile(
            "\\[思考]\\s*(.+?)(?=\\{|$)", Pattern.DOTALL);
    private static final Pattern THOUGHT_PATTERN = Pattern.compile(
            "(?:Thought|思考)[：:]\\s*(.+?)(?=\\{|Action|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_THINKING_CLEAN_PATTERN = Pattern.compile("\\[思考]\\s*.*?(?=\\n|$)");
    private static final Pattern THOUGHT_CLEAN_PATTERN = Pattern.compile(
            "(?:Thought|思考)[：:]\\s*.*?(?=\\n|$)", Pattern.CASE_INSENSITIVE);

    public String extractThinking(String llmResponse) {
        if (llmResponse == null) {
            return null;
        }
        Matcher matcher = CHINESE_THINKING_PATTERN.matcher(llmResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        matcher = THOUGHT_PATTERN.matcher(llmResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    public String cleanAssistantAnswer(String response) {
        return cleanThinkingSyntax(cleanToolCallSyntax(response));
    }

    private String cleanThinkingSyntax(String response) {
        if (response == null) {
            return "";
        }
        String clean = CHINESE_THINKING_CLEAN_PATTERN.matcher(response).replaceAll("");
        return THOUGHT_CLEAN_PATTERN.matcher(clean).replaceAll("").trim();
    }

    private String cleanToolCallSyntax(String response) {
        if (response == null) {
            return "";
        }
        String result = response;
        for (String jsonObject : extractJsonObjects(response)) {
            if (isToolCallJson(jsonObject)) {
                result = result.replace(jsonObject, "");
            }
        }
        return result.trim();
    }

    /**
     * 判断一段 JSON 文本是否为工具调用形态（用于从答案中清除残留）。
     */
    private boolean isToolCallJson(String candidate) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(candidate);
            if (!root.isObject()) {
                return false;
            }
            if (root.has("function_call") || root.has("tool_calls")) {
                return true;
            }
            boolean hasToolName = hasText(root, "tool") || hasText(root, "toolName");
            boolean hasArguments = root.has("arguments") || root.has("args")
                    || root.has("parameters") || root.has("params");
            return hasToolName || (hasText(root, "name") && hasArguments);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value != null && value.isTextual() && !value.asText().isBlank();
    }

    private List<String> extractJsonObjects(String response) {
        List<String> candidates = new ArrayList<>();
        int index = 0;
        while (index < response.length()) {
            int start = response.indexOf('{', index);
            if (start < 0) {
                break;
            }
            int end = findMatching(response, start, '{', '}');
            if (end < 0) {
                break;
            }
            candidates.add(response.substring(start, end + 1));
            index = end + 1;
        }
        return candidates;
    }

    private int findMatching(String value, int start, char opening, char closing) {
        char quote = 0;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && quote != 0) {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '"' || current == '\'') {
                quote = current;
                continue;
            }
            if (current == opening) {
                depth++;
            } else if (current == closing) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
