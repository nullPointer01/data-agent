package com.ai.memory.dto;

import java.util.List;

/**
 * Memory list response.
 *
 * @param success whether request succeeded
 * @param memories memory entries
 * @param total total matched count
 * @author data-agent
 */
public record MemoryListResponse(boolean success, List<MemoryEntryResponse> memories, long total) {
}
