package com.ai.memory.dto;

import java.util.List;

/**
 * 记忆列表响应。
 *
 * @param success 请求是否成功
 * @param memories 记忆条目列表
 * @param total 匹配总数
 * @author data-agent
 */
public record MemoryListResponse(boolean success, List<MemoryEntryResponse> memories, long total) {
}
