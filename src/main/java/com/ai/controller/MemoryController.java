package com.ai.controller;

import com.ai.memory.MemoryGovernanceService;
import com.ai.memory.MemoryTier;
import com.ai.memory.dto.MemoryListResponse;
import com.ai.memory.dto.MemoryMutationResponse;
import com.ai.memory.dto.MemoryStatsResponse;
import com.ai.memory.dto.UserMemoryProfileResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户记忆治理接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/memories")
public class MemoryController {

    private static final int DEFAULT_LIMIT = 50;

    private final MemoryGovernanceService memoryGovernanceService;

    public MemoryController(MemoryGovernanceService memoryGovernanceService) {
        this.memoryGovernanceService = memoryGovernanceService;
    }

    @GetMapping
    public MemoryListResponse listMemories(
            @RequestParam(value = "tier", required = false) MemoryTier tier,
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return memoryGovernanceService.listCurrentUserMemories(tier, limit);
    }

    @GetMapping("/profile")
    public UserMemoryProfileResponse getProfile() {
        return memoryGovernanceService.getCurrentUserProfile();
    }

    @GetMapping("/stats")
    public MemoryStatsResponse getStats() {
        return memoryGovernanceService.getCurrentUserStats();
    }

    @DeleteMapping("/{memoryId}")
    public MemoryMutationResponse deleteMemory(@PathVariable String memoryId) {
        return memoryGovernanceService.deleteCurrentUserMemory(memoryId);
    }

    @DeleteMapping
    public MemoryMutationResponse clearMemories(@RequestParam(value = "tier", required = false) MemoryTier tier) {
        return memoryGovernanceService.clearCurrentUserMemories(tier);
    }
}
