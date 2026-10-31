package com.ai.controller;

import com.ai.memory.MemoryGovernanceService;
import com.ai.memory.MemoryTier;
import com.ai.api.CursorPageResponse;
import com.ai.memory.dto.MemoryEntryResponse;
import com.ai.memory.dto.MemoryMutationResponse;
import com.ai.memory.dto.MemoryStatsResponse;
import com.ai.memory.dto.SemanticMemoryUpdateRequest;
import com.ai.memory.dto.UserMemoryProfileResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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


    private final MemoryGovernanceService memoryGovernanceService;

    public MemoryController(MemoryGovernanceService memoryGovernanceService) {
        this.memoryGovernanceService = memoryGovernanceService;
    }

    /**
     * 查询当前用户的记忆，可按记忆层级过滤。
     *
     * @param tier 可选的记忆层级
     * @param size 每页数量
     * @param after 上一页游标
     * @return 记忆列表
     */
    @GetMapping
    public CursorPageResponse<MemoryEntryResponse> listMemories(
            @RequestParam(value = "tier", required = false) MemoryTier tier,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String after) {
        return memoryGovernanceService.listCurrentUserMemories(tier, size, after);
    }

    /**
     * 查询由当前用户长期记忆投影出的用户画像。
     *
     * @return 用户记忆画像
     */
    @GetMapping("/profile")
    public UserMemoryProfileResponse getProfile() {
        return memoryGovernanceService.getCurrentUserProfile();
    }

    /**
     * 重置本人结构化画像及其长期记忆依据，不接受客户端指定用户。
     *
     * @return 重置结果
     */
    @PostMapping("/profile/reset")
    public MemoryMutationResponse resetProfile() {
        return memoryGovernanceService.resetCurrentUserProfile();
    }

    /**
     * 汇总当前用户各层级的记忆数量。
     *
     * @return 记忆统计
     */
    @GetMapping("/stats")
    public MemoryStatsResponse getStats() {
        return memoryGovernanceService.getCurrentUserStats();
    }

    /**
     * 删除当前用户拥有的指定记忆并刷新相关画像。
     *
     * @param memoryId 记忆编号
     * @return 删除结果
     */
    @DeleteMapping("/{memoryId}")
    public MemoryMutationResponse deleteMemory(@PathVariable String memoryId) {
        return memoryGovernanceService.deleteCurrentUserMemory(memoryId);
    }

    /**
     * 修正当前用户拥有的语义记忆并刷新相关画像。
     *
     * @param memoryId 记忆编号
     * @param request 修正后的记忆内容和类型
     * @return 更新结果
     */
    @PutMapping("/{memoryId}")
    public MemoryMutationResponse updateMemory(
            @PathVariable String memoryId,
            @Valid @RequestBody SemanticMemoryUpdateRequest request) {
        return memoryGovernanceService.updateCurrentUserMemory(memoryId, request);
    }

    /**
     * 清空当前用户全部记忆或指定层级的记忆。
     *
     * @param tier 可选的记忆层级
     * @return 清理结果
     */
    @DeleteMapping
    public MemoryMutationResponse clearMemories(@RequestParam(value = "tier", required = false) MemoryTier tier) {
        return memoryGovernanceService.clearCurrentUserMemories(tier);
    }
}
