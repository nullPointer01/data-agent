package com.ai.skill;

import com.ai.mcp.McpModelService;

/**
 * Agent 工具执行所依赖的技能契约。
 *
 * @author data-agent
 */
public interface Skill {

    /**
     * 获取技能名称。
     *
     * @return 技能名称
     */
    String getName();

    /**
     * 获取技能描述。
     *
     * @return 技能描述
     */
    String getDescription();

    /**
     * 判断技能是否可以处理当前查询。
     *
     * @param query 用户查询
     * @return 可以处理时返回 true
     */
    boolean canHandle(String query);

    /**
     * 使用可选数据处理查询。
     *
     * @param query 用户查询
     * @param data 可选数据
     * @return 处理结果
     */
    String process(String query, Object data);

    /**
     * 使用模型上下文处理查询。
     *
     * @param query 用户查询
     * @param data 可选数据
     * @param contextId 上下文 ID
     * @param modelService 模型服务
     * @return 处理结果
     */
    String processWithContext(String query, Object data, String contextId, McpModelService modelService);

    /**
     * 使用模型上下文和指定模型处理查询。
     *
     * @param query 用户查询
     * @param data 可选数据
     * @param contextId 上下文 ID
     * @param modelService 模型服务
     * @param modelId 模型 ID
     * @return 处理结果
     */
    String processWithContext(String query, Object data, String contextId, McpModelService modelService, String modelId);
}
