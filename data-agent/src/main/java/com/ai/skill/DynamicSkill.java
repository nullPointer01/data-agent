package com.ai.skill;

import com.ai.mcp.MCPModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

public class DynamicSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(DynamicSkill.class);
    private static final int MAX_DATA_CHARS = 3000;

    private final String name;
    private final String description;
    private final Map<String, Object> config;
    private final RestTemplate restTemplate;

    public DynamicSkill(String name, String description, Map<String, Object> config) {
        this.name = name;
        this.description = description;
        this.config = config;
        this.restTemplate = new RestTemplate();
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
        String keywords = (String) config.get("keywords");
        if (keywords != null && !keywords.isEmpty()) {
            String[] keywordArr = keywords.split("[,，\\s]+");
            for (String keyword : keywordArr) {
                if (!keyword.trim().isEmpty() && query.toLowerCase().contains(keyword.trim().toLowerCase())) {
                    return true;
                }
            }
        }
        return query.toLowerCase().contains(name.toLowerCase());
    }

    @Override
    public String process(String query, Object data) {
        String apiUrl = (String) config.get("apiUrl");
        if (apiUrl != null && !apiUrl.isEmpty()) {
            return callExternalApi(apiUrl, (String) config.getOrDefault("apiMethod", "POST"), query, data);
        }
        return "DynamicSkill[" + name + "] 处理了查询: " + query;
    }

    @Override
    public String processWithContext(String query, Object data, String contextId, MCPModelService modelService) {
        return processWithContext(query, data, contextId, modelService, null, null);
    }

    @Override
    public String processWithContext(String query, Object data, String contextId, MCPModelService modelService,
            String modelId) {
        return processWithContext(query, data, contextId, modelService, modelId, null);
    }

    public String processWithContext(String query, Object data, String contextId, MCPModelService modelService,
            String modelId, String conversationContext) {
        String prompt = buildPrompt(query, data, conversationContext);
        return modelService.callModelWithContext(contextId, prompt, modelId, name);
    }

    private String buildPrompt(String query, Object data, String conversationContext) {
        String promptTemplate = (String) config.get("promptTemplate");
        String steps = (String) config.get("steps");
        StringBuilder prompt = new StringBuilder();

        if (steps != null && !steps.isEmpty() && !"[]".equals(steps.trim())) {
            prompt.append("# 工作流\n");
            try {
                String stepsJson = steps.trim();
                if (stepsJson.startsWith("[")) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.List<String> stepList = mapper.readValue(stepsJson,
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
                    for (int i = 0; i < stepList.size(); i++) {
                        prompt.append(i + 1).append(". ").append(stepList.get(i)).append("\n");
                    }
                }
            } catch (Exception e) {
                prompt.append(steps).append("\n");
            }
            prompt.append("\n");
        }

        if (promptTemplate != null && !promptTemplate.isEmpty()) {
            prompt.append(promptTemplate
                    .replace("{{query}}", query)
                    .replace("{{data}}", truncateData(data)));
        } else {
            prompt.append(query);
            if (data != null)
                prompt.append("\n\n数据: ").append(truncateData(data));
        }

        if (conversationContext != null && !conversationContext.isEmpty()) {
            prompt.insert(0, conversationContext + "\n");
        }

        String apiUrl = (String) config.get("apiUrl");
        if (apiUrl != null && !apiUrl.isEmpty()) {
            String apiResult = callExternalApi(apiUrl, (String) config.getOrDefault("apiMethod", "POST"), query, data);
            prompt.append("\n\n参考数据:\n").append(truncateString(apiResult, MAX_DATA_CHARS));
        }

        return prompt.toString();
    }

    private String truncateData(Object data) {
        if (data == null)
            return "";
        return truncateString(data.toString(), MAX_DATA_CHARS);
    }

    private String truncateString(String str, int maxChars) {
        if (str == null)
            return "";
        if (str.length() <= maxChars)
            return str;
        String truncated = str.substring(0, maxChars);
        int lastNewline = truncated.lastIndexOf('\n');
        if (lastNewline > maxChars * 0.7) {
            truncated = truncated.substring(0, lastNewline);
        }
        return truncated + "\n...[数据已截断，共" + str.length() + "字符，仅展示前" + truncated.length() + "字符]";
    }

    private String callExternalApi(String apiUrl, String method, String query, Object data) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String apiHeadersStr = (String) config.get("apiHeaders");
            if (apiHeadersStr != null && !apiHeadersStr.isEmpty()) {
                String[] headerPairs = apiHeadersStr.split(";");
                for (String pair : headerPairs) {
                    String[] kv = pair.split(":", 2);
                    if (kv.length == 2) {
                        headers.set(kv[0].trim(), kv[1].trim());
                    }
                }
            }

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("query", query);
            if (data != null)
                body.put("data", data);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response;
            if ("GET".equalsIgnoreCase(method)) {
                response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, String.class);
            } else {
                response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            }

            log.info("External API called: {} {}, status: {}", method, apiUrl, response.getStatusCode());
            return response.getBody();
        } catch (Exception e) {
            log.warn("External API call failed: {} - {}", apiUrl, e.getMessage());
            return "API调用失败: " + e.getMessage();
        }
    }
}
