package com.ai.memory;

import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final MemoryWriteGuard writeGuard;

    public UserProfileMemoryRefreshService(LongTermMemory longTermMemory,
            UserMemoryProfileExtractor userMemoryProfileExtractor,
            UserProfileMemoryService userProfileMemoryService, MemoryWriteGuard writeGuard) {
        this.longTermMemory = longTermMemory;
        this.userMemoryProfileExtractor = userMemoryProfileExtractor;
        this.userProfileMemoryService = userProfileMemoryService;
        this.writeGuard = writeGuard;
    }

    /**
     * 刷新指定租户用户的画像。画像是当前有效语义记忆的可重建投影。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 刷新后的画像
     */
    @Transactional(rollbackFor = Exception.class)
    public UserMemoryProfileSnapshotResponse refresh(String tenantId, String userId) {
        // 读取证据与替换快照在同一用户写锁内，防止并发重建把重置前的画像写回来。
        writeGuard.lock(tenantId, userId);
        List<MemoryEntry> memories = longTermMemory.listProfileEvidence(tenantId, userId);
        UserMemoryProfileSnapshotResponse profile = userMemoryProfileExtractor.extract(memories);
        return userProfileMemoryService.replaceSnapshot(tenantId, userId, profile);
    }
}
