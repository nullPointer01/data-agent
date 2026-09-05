package com.ai.memory.dto;

import java.util.List;

/**
 * 当前用户的结构化记忆画像。
 *
 * @param displayName 用户称呼
 * @param role 用户角色
 * @param company 所属公司
 * @param industry 所属行业
 * @param communicationStyle 沟通风格
 * @param preferredFormat 偏好输出格式
 * @param expertiseAreas 专业领域
 * @param frequentlyAskedTopics 高频话题
 * @param dataSources 常用数据源
 * @param confidence 画像置信度
 * @author data-agent
 */
public record UserMemoryProfileSnapshotResponse(String displayName,
        String role,
        String company,
        String industry,
        String communicationStyle,
        String preferredFormat,
        List<String> expertiseAreas,
        List<String> frequentlyAskedTopics,
        List<String> dataSources,
        double confidence) {

    /**
     * 创建空画像。
     *
     * @return 空画像
     */
    public static UserMemoryProfileSnapshotResponse empty() {
        return new UserMemoryProfileSnapshotResponse(null, null, null, null, null, null,
                List.of(), List.of(), List.of(), 0D);
    }
}
