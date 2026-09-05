package com.ai.memory.dto;

import java.util.List;

/**
 * 当前用户记忆画像响应。
 *
 * @param success 是否成功
 * @param profile 结构化用户画像
 * @param preferences 用户偏好记忆
 * @param profileFacts 用户画像事实
 * @param conclusions 关键结论
 * @param totalMemories 记忆总数
 * @author data-agent
 */
public record UserMemoryProfileResponse(
        boolean success,
        UserMemoryProfileSnapshotResponse profile,
        List<MemoryEntryResponse> preferences,
        List<MemoryEntryResponse> profileFacts,
        List<MemoryEntryResponse> conclusions,
        long totalMemories) {
}
