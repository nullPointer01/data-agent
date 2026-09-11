package com.ai.memory;

import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.repository.UserProfileMemoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户画像持久化服务。
 *
 * @author data-agent
 */
@Service
public class UserProfileMemoryService {

    private final UserProfileMemoryRepository userProfileMemoryRepository;
    private final MemoryJsonCodec memoryJsonCodec;

    public UserProfileMemoryService(UserProfileMemoryRepository userProfileMemoryRepository,
            MemoryJsonCodec memoryJsonCodec) {
        this.userProfileMemoryRepository = userProfileMemoryRepository;
        this.memoryJsonCodec = memoryJsonCodec;
    }

    /**
     * 查询用户画像快照。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 用户画像
     */
    @Transactional(readOnly = true)
    public UserMemoryProfileSnapshotResponse getSnapshot(String tenantId, String userId) {
        return userProfileMemoryRepository.findByTenantIdAndUserId(tenantId, userId)
                .map(this::toSnapshot)
                .orElseGet(UserMemoryProfileSnapshotResponse::empty);
    }

    /**
     * 使用当前有效语义记忆完整替换持久画像。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param snapshot 新画像快照
     * @return 重建后的画像快照
     */
    @Transactional(rollbackFor = Exception.class)
    public UserMemoryProfileSnapshotResponse replaceSnapshot(String tenantId, String userId,
            UserMemoryProfileSnapshotResponse snapshot) {
        if (snapshot.evidenceCount() <= 0) {
            userProfileMemoryRepository.findByTenantIdAndUserId(tenantId, userId)
                    .ifPresent(userProfileMemoryRepository::delete);
            return UserMemoryProfileSnapshotResponse.empty();
        }
        UserProfileMemory profile = userProfileMemoryRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> createProfile(tenantId, userId));
        replace(profile, snapshot);
        return toSnapshot(userProfileMemoryRepository.save(profile));
    }

    private UserProfileMemory createProfile(String tenantId, String userId) {
        UserProfileMemory profile = new UserProfileMemory();
        profile.setTenantId(tenantId);
        profile.setUserId(userId);
        return profile;
    }

    private void replace(UserProfileMemory profile, UserMemoryProfileSnapshotResponse snapshot) {
        profile.setDisplayName(snapshot.displayName());
        profile.setRole(snapshot.role());
        profile.setCompany(snapshot.company());
        profile.setIndustry(snapshot.industry());
        profile.setCommunicationStyle(snapshot.communicationStyle());
        profile.setPreferredFormat(snapshot.preferredFormat());
        profile.setExpertiseAreasJson(memoryJsonCodec.toJson(snapshot.expertiseAreas()));
        profile.setFrequentlyAskedTopicsJson(memoryJsonCodec.toJson(snapshot.frequentlyAskedTopics()));
        profile.setDataSourcesJson(memoryJsonCodec.toJson(snapshot.dataSources()));
        profile.setConfidence(snapshot.confidence());
        profile.setEvidenceCount(snapshot.evidenceCount());
        profile.setLastActiveAt(LocalDateTime.now());
    }

    private UserMemoryProfileSnapshotResponse toSnapshot(UserProfileMemory profile) {
        return new UserMemoryProfileSnapshotResponse(
                profile.getDisplayName(),
                profile.getRole(),
                profile.getCompany(),
                profile.getIndustry(),
                profile.getCommunicationStyle(),
                profile.getPreferredFormat(),
                memoryJsonCodec.toStringList(profile.getExpertiseAreasJson()),
                memoryJsonCodec.toStringList(profile.getFrequentlyAskedTopicsJson()),
                memoryJsonCodec.toStringList(profile.getDataSourcesJson()),
                profile.getConfidence(),
                profile.getEvidenceCount());
    }

}
