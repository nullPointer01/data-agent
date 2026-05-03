package com.ai.controller;

import com.ai.mcp.MCPContextManager;
import com.ai.mcp.MCPModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class MCPController {

    private static final Logger log = LoggerFactory.getLogger(MCPController.class);

    private final MCPContextManager contextManager;
    private final MCPModelService modelService;

    public MCPController(MCPContextManager contextManager, MCPModelService modelService) {
        this.contextManager = contextManager;
        this.modelService = modelService;
    }

    @PostMapping("/context")
    public Map<String, Object> createContext(@RequestBody Map<String, Object> request) {
        String skillId = (String) request.get("skillId");
        String modelType = (String) request.getOrDefault("modelType", "default");
        if (skillId == null) {
            return Map.of("success", false, "message", "Skill ID is required");
        }
        String contextId = contextManager.createContext(skillId, modelType);
        return Map.of("success", true, "contextId", contextId, "message", "Context created successfully");
    }

    @GetMapping("/context/{contextId}")
    public Map<String, Object> getContext(@PathVariable String contextId) {
        var context = contextManager.getContext(contextId);
        if (context == null) {
            return Map.of("success", false, "message", "Context not found");
        }
        return Map.of("success", true, "context", Map.of(
                "contextId", context.getContextId(),
                "skillId", context.getSkillId(),
                "modelType", context.getModelType(),
                "createTime", context.getCreateTime(),
                "lastAccessTime", context.getLastAccessTime(),
                "metadata", context.getMetadata()));
    }

    @PutMapping("/context/{contextId}")
    public Map<String, Object> updateContext(@PathVariable String contextId, @RequestBody Map<String, Object> request) {
        var context = contextManager.getContext(contextId);
        if (context == null) {
            return Map.of("success", false, "message", "Context not found");
        }
        request.forEach((key, value) -> {
            if (!key.equals("contextId")) contextManager.updateContext(contextId, key, value);
        });
        return Map.of("success", true, "message", "Context updated successfully");
    }

    @DeleteMapping("/context/{contextId}")
    public Map<String, Object> deleteContext(@PathVariable String contextId) {
        contextManager.destroyContext(contextId);
        return Map.of("success", true, "message", "Context deleted successfully");
    }

    @PostMapping("/call")
    public Map<String, Object> callModel(@RequestBody Map<String, Object> request) {
        String contextId = (String) request.get("contextId");
        String prompt = (String) request.get("prompt");
        if (prompt == null) {
            return Map.of("success", false, "message", "Prompt is required");
        }
        try {
            String response;
            if (contextId != null) {
                response = modelService.callModelWithContext(contextId, prompt);
            } else {
                response = modelService.callModel(prompt);
            }
            return Map.of("success", true, "response", response);
        } catch (Exception e) {
            log.error("Model call failed", e);
            return Map.of("success", false, "message", "Model call failed: " + e.getMessage());
        }
    }
}
