package com.ai.controller;

import com.ai.mcp.McpContextManager;
import com.ai.mcp.McpModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Model context and direct model call API.
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/mcp")
public class McpController {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpController.class);
    private static final String DEFAULT_MODEL_TYPE = "default";
    private static final String KEY_SUCCESS = "success";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_SKILL_ID = "skillId";
    private static final String KEY_MODEL_TYPE = "modelType";
    private static final String KEY_CONTEXT_ID = "contextId";
    private static final String KEY_CONTEXT = "context";
    private static final String KEY_CREATE_TIME = "createTime";
    private static final String KEY_LAST_ACCESS_TIME = "lastAccessTime";
    private static final String KEY_METADATA = "metadata";
    private static final String KEY_PROMPT = "prompt";
    private static final String KEY_RESPONSE = "response";
    private static final String MESSAGE_SKILL_ID_REQUIRED = "Skill ID is required";
    private static final String MESSAGE_CONTEXT_CREATED = "Context created successfully";
    private static final String MESSAGE_CONTEXT_NOT_FOUND = "Context not found";
    private static final String MESSAGE_CONTEXT_UPDATED = "Context updated successfully";
    private static final String MESSAGE_CONTEXT_DELETED = "Context deleted successfully";
    private static final String MESSAGE_PROMPT_REQUIRED = "Prompt is required";
    private static final String MESSAGE_MODEL_CALL_FAILED = "Model call failed: ";

    private final McpContextManager contextManager;
    private final McpModelService modelService;

    public McpController(McpContextManager contextManager, McpModelService modelService) {
        this.contextManager = contextManager;
        this.modelService = modelService;
    }

    @PostMapping("/context")
    public Map<String, Object> createContext(@RequestBody Map<String, Object> request) {
        String skillId = (String) request.get(KEY_SKILL_ID);
        String modelType = (String) request.getOrDefault(KEY_MODEL_TYPE, DEFAULT_MODEL_TYPE);
        if (skillId == null) {
            return Map.of(KEY_SUCCESS, false, KEY_MESSAGE, MESSAGE_SKILL_ID_REQUIRED);
        }
        String contextId = contextManager.createContext(skillId, modelType);
        return Map.of(KEY_SUCCESS, true, KEY_CONTEXT_ID, contextId, KEY_MESSAGE, MESSAGE_CONTEXT_CREATED);
    }

    @GetMapping("/context/{contextId}")
    public Map<String, Object> getContext(@PathVariable String contextId) {
        var context = contextManager.getContext(contextId);
        if (context == null) {
            return Map.of(KEY_SUCCESS, false, KEY_MESSAGE, MESSAGE_CONTEXT_NOT_FOUND);
        }
        return Map.of(KEY_SUCCESS, true, KEY_CONTEXT, Map.of(
                KEY_CONTEXT_ID, context.getContextId(),
                KEY_SKILL_ID, context.getSkillId(),
                KEY_MODEL_TYPE, context.getModelType(),
                KEY_CREATE_TIME, context.getCreateTime(),
                KEY_LAST_ACCESS_TIME, context.getLastAccessTime(),
                KEY_METADATA, context.getMetadata()));
    }

    @PutMapping("/context/{contextId}")
    public Map<String, Object> updateContext(@PathVariable String contextId, @RequestBody Map<String, Object> request) {
        var context = contextManager.getContext(contextId);
        if (context == null) {
            return Map.of(KEY_SUCCESS, false, KEY_MESSAGE, MESSAGE_CONTEXT_NOT_FOUND);
        }
        request.forEach((key, value) -> {
            if (!KEY_CONTEXT_ID.equals(key)) {
                contextManager.updateContext(contextId, key, value);
            }
        });
        return Map.of(KEY_SUCCESS, true, KEY_MESSAGE, MESSAGE_CONTEXT_UPDATED);
    }

    @DeleteMapping("/context/{contextId}")
    public Map<String, Object> deleteContext(@PathVariable String contextId) {
        contextManager.destroyContext(contextId);
        return Map.of(KEY_SUCCESS, true, KEY_MESSAGE, MESSAGE_CONTEXT_DELETED);
    }

    @PostMapping("/call")
    public Map<String, Object> callModel(@RequestBody Map<String, Object> request) {
        String contextId = (String) request.get(KEY_CONTEXT_ID);
        String prompt = (String) request.get(KEY_PROMPT);
        if (prompt == null) {
            return Map.of(KEY_SUCCESS, false, KEY_MESSAGE, MESSAGE_PROMPT_REQUIRED);
        }
        try {
            String response;
            if (contextId != null) {
                response = modelService.callModelWithContext(contextId, prompt);
            } else {
                response = modelService.callModel(prompt);
            }
            return Map.of(KEY_SUCCESS, true, KEY_RESPONSE, response);
        } catch (Exception e) {
            LOGGER.error("Model call failed", e);
            return Map.of(KEY_SUCCESS, false, KEY_MESSAGE, MESSAGE_MODEL_CALL_FAILED + e.getMessage());
        }
    }
}
