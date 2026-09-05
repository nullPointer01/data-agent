package com.ai.skill;

import com.ai.mcp.McpModelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于数据库技能配置创建的运行时技能。
 *
 * @author data-agent
 */
public class DynamicSkill implements Skill {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicSkill.class);
    private static final int MAX_DATA_CHARS = 3000;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int HEADER_NAME_VALUE_LIMIT = 2;
    private static final int REQUEST_BODY_INITIAL_CAPACITY = 2;
    private static final int STEP_NUMBER_OFFSET = 1;
    private static final double TRUNCATE_NEWLINE_RATIO = 0.7D;
    private static final String CONFIG_KEY_KEYWORDS = "keywords";
    private static final String CONFIG_KEY_API_URL = "apiUrl";
    private static final String CONFIG_KEY_API_METHOD = "apiMethod";
    private static final String CONFIG_KEY_API_HEADERS = "apiHeaders";
    private static final String CONFIG_KEY_PROMPT_TEMPLATE = "promptTemplate";
    private static final String CONFIG_KEY_STEPS = "steps";
    private static final String DEFAULT_API_METHOD = "POST";
    private static final String HTTP_GET_METHOD = "GET";
    private static final String REQUEST_KEY_QUERY = "query";
    private static final String REQUEST_KEY_DATA = "data";
    private static final String EMPTY_STEP_LIST = "[]";
    private static final String STEP_JSON_ARRAY_PREFIX = "[";
    private static final String KEYWORD_SPLIT_REGEX = "[,，\\s]+";
    private static final String HEADER_PAIR_DELIMITER = ";";
    private static final String HEADER_NAME_VALUE_DELIMITER = ":";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String name;
    private final String description;
    private final Map<String, Object> config;
    private final RestTemplate restTemplate;

    public DynamicSkill(String name, String description, Map<String, Object> config) {
        this(name, description, config, defaultRestTemplate());
    }

    public DynamicSkill(String name, String description, Map<String, Object> config, RestTemplate restTemplate) {
        this.name = name;
        this.description = description;
        this.config = config;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean canHandle(String query) {
        if (query == null) {
            return false;
        }
        String keywords = (String) config.get(CONFIG_KEY_KEYWORDS);
        if (keywords != null && !keywords.isEmpty()) {
            String[] keywordArr = keywords.split(KEYWORD_SPLIT_REGEX);
            for (String keyword : keywordArr) {
                if (!keyword.trim().isEmpty()
                        && query.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return query.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public String process(String query, Object data) {
        String apiUrl = (String) config.get(CONFIG_KEY_API_URL);
        if (apiUrl != null && !apiUrl.isEmpty()) {
            return callExternalApi(apiUrl, resolveApiMethod(), query, data);
        }
        return "DynamicSkill[" + name + "] 处理了查询: " + query;
    }

    @Override
    public String processWithContext(String query, Object data, String contextId, McpModelService modelService) {
        return processWithContext(query, data, contextId, modelService, null, null);
    }

    @Override
    public String processWithContext(String query, Object data, String contextId, McpModelService modelService,
            String modelId) {
        return processWithContext(query, data, contextId, modelService, modelId, null);
    }

    public String processWithContext(String query, Object data, String contextId, McpModelService modelService,
            String modelId, String conversationContext) {
        String prompt = buildPrompt(query, data, conversationContext);
        return modelService.callModelWithContext(contextId, prompt, modelId, name);
    }

    private String buildPrompt(String query, Object data, String conversationContext) {
        String promptTemplate = (String) config.get(CONFIG_KEY_PROMPT_TEMPLATE);
        String steps = (String) config.get(CONFIG_KEY_STEPS);
        StringBuilder prompt = new StringBuilder();

        if (steps != null && !steps.isEmpty() && !EMPTY_STEP_LIST.equals(steps.trim())) {
            prompt.append("# 工作流\n");
            appendWorkflowSteps(prompt, steps);
            prompt.append("\n");
        }

        if (promptTemplate != null && !promptTemplate.isEmpty()) {
            prompt.append(promptTemplate
                    .replace("{{query}}", query)
                    .replace("{{data}}", truncateData(data)));
        } else {
            prompt.append(query);
            if (data != null) {
                prompt.append("\n\n数据: ").append(truncateData(data));
            }
        }

        if (conversationContext != null && !conversationContext.isEmpty()) {
            prompt.insert(0, conversationContext + "\n");
        }

        String apiUrl = (String) config.get(CONFIG_KEY_API_URL);
        if (apiUrl != null && !apiUrl.isEmpty()) {
            String apiResult = callExternalApi(apiUrl, resolveApiMethod(), query, data);
            prompt.append("\n\n参考数据:\n").append(truncateString(apiResult, MAX_DATA_CHARS));
        }

        return prompt.toString();
    }

    private void appendWorkflowSteps(StringBuilder prompt, String steps) {
        try {
            String stepsJson = steps.trim();
            if (!stepsJson.startsWith(STEP_JSON_ARRAY_PREFIX)) {
                prompt.append(steps).append("\n");
                return;
            }
            List<String> stepList = OBJECT_MAPPER.readValue(stepsJson, new TypeReference<List<String>>() {
            });
            for (int i = 0; i < stepList.size(); i++) {
                prompt.append(i + STEP_NUMBER_OFFSET).append(". ").append(stepList.get(i)).append("\n");
            }
        } catch (Exception e) {
            prompt.append(steps).append("\n");
        }
    }

    private String truncateData(Object data) {
        if (data == null) {
            return "";
        }
        return truncateString(data.toString(), MAX_DATA_CHARS);
    }

    private String truncateString(String str, int maxChars) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxChars) {
            return str;
        }
        String truncated = str.substring(0, maxChars);
        int lastNewline = truncated.lastIndexOf('\n');
        if (lastNewline > maxChars * TRUNCATE_NEWLINE_RATIO) {
            truncated = truncated.substring(0, lastNewline);
        }
        return truncated + "\n...[数据已截断，共" + str.length() + "字符，仅展示前" + truncated.length() + "字符]";
    }

    private String callExternalApi(String apiUrl, String method, String query, Object data) {
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(buildRequestBody(query, data),
                    buildRequestHeaders());
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, resolveHttpMethod(method), entity,
                    String.class);
            LOGGER.info("外部技能 API 调用完成: {} {}, 状态: {}", method, apiUrl, response.getStatusCode());
            return response.getBody();
        } catch (Exception e) {
            LOGGER.warn("外部技能 API 调用失败: {} - {}", apiUrl, e.getMessage());
            return "API调用失败: " + e.getMessage();
        }
    }

    private String resolveApiMethod() {
        return (String) config.getOrDefault(CONFIG_KEY_API_METHOD, DEFAULT_API_METHOD);
    }

    private HttpMethod resolveHttpMethod(String method) {
        if (HTTP_GET_METHOD.equalsIgnoreCase(method)) {
            return HttpMethod.GET;
        }
        return HttpMethod.POST;
    }

    private HttpHeaders buildRequestHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String apiHeadersStr = (String) config.get(CONFIG_KEY_API_HEADERS);
        if (apiHeadersStr == null || apiHeadersStr.isEmpty()) {
            return headers;
        }
        String[] headerPairs = apiHeadersStr.split(HEADER_PAIR_DELIMITER);
        for (String pair : headerPairs) {
            String[] kv = pair.split(HEADER_NAME_VALUE_DELIMITER, HEADER_NAME_VALUE_LIMIT);
            if (kv.length == HEADER_NAME_VALUE_LIMIT) {
                headers.set(kv[0].trim(), kv[1].trim());
            }
        }
        return headers;
    }

    private Map<String, Object> buildRequestBody(String query, Object data) {
        Map<String, Object> body = new HashMap<>(REQUEST_BODY_INITIAL_CAPACITY);
        body.put(REQUEST_KEY_QUERY, query);
        if (data != null) {
            body.put(REQUEST_KEY_DATA, data);
        }
        return body;
    }
}
