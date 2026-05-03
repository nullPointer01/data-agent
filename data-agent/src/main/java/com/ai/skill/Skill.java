package com.ai.skill;

import com.ai.mcp.MCPModelService;

public interface Skill {
    String getName();
    String getDescription();
    boolean canHandle(String query);
    String process(String query, Object data);
    String processWithContext(String query, Object data, String contextId, MCPModelService modelService);
    String processWithContext(String query, Object data, String contextId, MCPModelService modelService, String modelId);
}
