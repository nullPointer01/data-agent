package com.ai.memory;

import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 根据长期记忆证据刷新持久化用户画像。
 *
 * @author data-agent
 */
@Service
public class UserProfileMemoryRefreshService {

    private final LongTermMemory longTermMemory;
    private final UserMemoryProfileExtractor userMemoryProfileExtractor;
    private final UserProfileMemoryService userProfileMemoryService;

    public UserProfileMemoryRefreshService(LongTermMemory longTermMemory,
            UserMemoryProfileExtractor userMemoryProfileExtractor,
            UserProfileMemoryService userProfileMemoryService) {
        this.longTermMemory = longTermMemory;
        this.userMemoryProfileExtractor = userMemoryProfileExtractor;
        this.userProfileMemoryService = userProfileMemoryService;
    }

    /**
     * 刷新指定租户用户的画像。画像是当前有效语义记忆的可重建投影。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 刷新后的画像
     */
    public UserMemoryProfileSnapshotResponse refresh(String tenantId, String userId) {
        List<MemoryEntry> memories = longTermMemory.listProfileEvidence(tenantId, userId);
        UserMemoryProfileSnapshotResponse profile = userMemoryProfileExtractor.extract(memories);
        return userProfileMemoryService.replaceSnapshot(tenantId, userId, profile);
    }
}
