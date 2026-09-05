package com.ai.memory;

import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.repository.UserProfileMemoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

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
     * 用新提取出的画像快照合并更新持久画像。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param snapshot 新画像快照
     * @param evidenceCount 证据记忆数量
     * @return 合并后的画像快照
     */
    @Transactional(rollbackFor = Exception.class)
    public UserMemoryProfileSnapshotResponse upsertSnapshot(String tenantId, String userId,
            UserMemoryProfileSnapshotResponse snapshot, int evidenceCount) {
        UserProfileMemory profile = userProfileMemoryRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> createProfile(tenantId, userId));
        merge(profile, snapshot, evidenceCount);
        return toSnapshot(userProfileMemoryRepository.save(profile));
    }

    private UserProfileMemory createProfile(String tenantId, String userId) {
        UserProfileMemory profile = new UserProfileMemory();
        profile.setTenantId(tenantId);
        profile.setUserId(userId);
        return profile;
    }

    private void merge(UserProfileMemory profile, UserMemoryProfileSnapshotResponse snapshot, int evidenceCount) {
        profile.setDisplayName(firstText(snapshot.displayName(), profile.getDisplayName()));
        profile.setRole(firstText(snapshot.role(), profile.getRole()));
        profile.setCompany(firstText(snapshot.company(), profile.getCompany()));
        profile.setIndustry(firstText(snapshot.industry(), profile.getIndustry()));
        profile.setCommunicationStyle(firstText(snapshot.communicationStyle(), profile.getCommunicationStyle()));
        profile.setPreferredFormat(firstText(snapshot.preferredFormat(), profile.getPreferredFormat()));
        profile.setExpertiseAreasJson(memoryJsonCodec.toJson(mergeList(
                memoryJsonCodec.toStringList(profile.getExpertiseAreasJson()), snapshot.expertiseAreas())));
        profile.setFrequentlyAskedTopicsJson(memoryJsonCodec.toJson(mergeList(
                memoryJsonCodec.toStringList(profile.getFrequentlyAskedTopicsJson()),
                snapshot.frequentlyAskedTopics())));
        profile.setDataSourcesJson(memoryJsonCodec.toJson(mergeList(
                memoryJsonCodec.toStringList(profile.getDataSourcesJson()), snapshot.dataSources())));
        profile.setConfidence(Math.max(profile.getConfidence(), snapshot.confidence()));
        profile.setEvidenceCount(Math.max(profile.getEvidenceCount(), evidenceCount));
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
                profile.getConfidence());
    }

    private String firstText(String candidate, String current) {
        return StringUtils.hasText(candidate) ? candidate : current;
    }

    private List<String> mergeList(List<String> current, List<String> incoming) {
        return java.util.stream.Stream.concat(current.stream(), incoming.stream())
                .filter(StringUtils::hasText)
                .distinct()
                .limit(8)
                .toList();
    }
}
