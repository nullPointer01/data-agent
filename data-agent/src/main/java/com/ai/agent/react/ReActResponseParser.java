package com.ai.agent.react;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 ReAct 提示词协议下的模型输出。
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
    private static final Map<String, List<String>> PARAMETER_FIELD_ORDER = Map.ofEntries(
            Map.entry("useSkill", List.of("skillName", "query")),
            Map.entry("getFileContent", List.of("fileId")),
            Map.entry("getConversationHistory", List.of("sessionId")),
            Map.entry("askUserForInfo", List.of("message")),
            Map.entry("searchMemory", List.of("query")),
            Map.entry("calculate", List.of("expression")),
            Map.entry("analyzeFileData", List.of("fileId")),
            Map.entry("searchKnowledge", List.of("query")),
            Map.entry("getDatabaseSchema", List.of("datasourceName")),
            Map.entry("executeSQL", List.of("datasourceName", "sql")),
            Map.entry("previewDataSource", List.of("datasourceName")),
            Map.entry("generateChart", List.of("chartType", "dataJson", "title")));

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

public ReActToolCall parseToolCall(String llmResponse) {
        if (llmResponse == null) {
            return null;
        }
        return parseStructuredToolCall(llmResponse);
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
        return removeStructuredToolCall(response).trim();
    }

    private ReActToolCall parseStructuredToolCall(String response) {
        for (String candidate : extractJsonObjects(response)) {
            ReActToolCall toolCall = parseStructuredToolCallCandidate(candidate);
            if (toolCall != null) {
                return toolCall;
            }
        }
        return null;
    }

    private ReActToolCall parseStructuredToolCallCandidate(String candidate) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(candidate);
            if (!root.isObject()) {
                return null;
            }
            return parseToolObject(root);
        } catch (Exception e) {
            return null;
        }
    }

    private ReActToolCall parseToolObject(JsonNode root) {
        ReActToolCall standardToolCall = parseStandardToolCall(root);
        if (standardToolCall != null) {
            return standardToolCall;
        }
        JsonNode argumentsNode = firstExisting(root, "arguments", "args", "parameters", "params");
        String toolName = resolveStructuredToolName(root, argumentsNode);
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        JsonNode normalizedArguments = normalizeArguments(argumentsNode);
        List<String> arguments = parseStructuredArguments(toolName, normalizedArguments);
        return new ReActToolCall(toolName, rawArguments(normalizedArguments), arguments);
    }

    private ReActToolCall parseStandardToolCall(JsonNode root) {
        JsonNode functionCall = root.get("function_call");
        if (functionCall != null && functionCall.isObject()) {
            return parseFunctionNode(functionCall);
        }
        JsonNode toolCalls = root.get("tool_calls");
        if (toolCalls == null || !toolCalls.isArray()) {
            return null;
        }
        for (JsonNode toolCall : toolCalls) {
            JsonNode functionNode = toolCall.path("function");
            ReActToolCall parsed = parseFunctionNode(functionNode);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private ReActToolCall parseFunctionNode(JsonNode functionNode) {
        if (functionNode == null || !functionNode.isObject()) {
            return null;
        }
        String toolName = text(functionNode, "name");
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        JsonNode argumentsNode = normalizeArguments(functionNode.get("arguments"));
        return new ReActToolCall(toolName, rawArguments(argumentsNode),
                parseStructuredArguments(toolName, argumentsNode));
    }

    private String resolveStructuredToolName(JsonNode root, JsonNode argumentsNode) {
        String explicitToolName = text(root, "tool", "toolName");
        if (explicitToolName != null && !explicitToolName.isBlank()) {
            return explicitToolName;
        }
        // 只有对象形态符合工具调用时，才接受 name 作为工具标识。
        return argumentsNode == null ? null : text(root, "name");
    }

    private List<String> parseStructuredArguments(String toolName, JsonNode argumentsNode) {
        if (argumentsNode == null || argumentsNode.isNull()) {
            return List.of();
        }
        if (argumentsNode.isArray()) {
            List<String> arguments = new ArrayList<>();
            argumentsNode.forEach(node -> arguments.add(nodeToArgument(node)));
            return arguments;
        }
        if (argumentsNode.isObject()) {
            return parseObjectArguments(toolName, argumentsNode);
        }
        if (argumentsNode.isTextual()) {
            return List.of(argumentsNode.asText());
        }
        return List.of(nodeToArgument(argumentsNode));
    }

    private JsonNode normalizeArguments(JsonNode argumentsNode) {
        if (argumentsNode == null || !argumentsNode.isTextual()) {
            return argumentsNode;
        }
        String text = argumentsNode.asText();
        if (text.isBlank()) {
            return argumentsNode;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return argumentsNode;
        }
        try {
            return OBJECT_MAPPER.readTree(trimmed);
        } catch (Exception e) {
            return argumentsNode;
        }
    }

    private List<String> parseObjectArguments(String toolName, JsonNode argumentsNode) {
        List<String> arguments = new ArrayList<>();
        Set<String> consumedFields = new LinkedHashSet<>();
        for (String field : PARAMETER_FIELD_ORDER.getOrDefault(toolName, List.of())) {
            JsonNode value = argumentsNode.get(field);
            if (value != null && !value.isNull()) {
                arguments.add(nodeToArgument(value));
                consumedFields.add(field);
            }
        }
        argumentsNode.fields().forEachRemaining(entry -> {
            if (!consumedFields.contains(entry.getKey())) {
                arguments.add(nodeToArgument(entry.getValue()));
            }
        });
        return arguments;
    }

    private String nodeToArgument(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    private JsonNode firstExisting(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = root.get(fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode root, String... fieldNames) {
        JsonNode value = firstExisting(root, fieldNames);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private String removeStructuredToolCall(String response) {
        List<String> jsonObjects = extractJsonObjects(response);
        String result = response;
        for (String jsonObject : jsonObjects) {
            if (parseStructuredToolCallCandidate(jsonObject) != null) {
                result = result.replace(jsonObject, "");
            }
        }
        return result;
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

    private String rawArguments(JsonNode argumentsNode) {
        return argumentsNode == null || argumentsNode.isNull() ? "" : argumentsNode.toString();
    }
}
