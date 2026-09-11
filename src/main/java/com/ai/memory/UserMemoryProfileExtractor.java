package com.ai.memory;

import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 从已校验的长期语义记忆构建当前用户画像。
 *
 * @author data-agent
 */
@Component
public class UserMemoryProfileExtractor {

    private static final int MAX_LIST_SIZE = 6;

    /**
     * 根据按更新时间倒序排列的语义记忆生成结构化画像。
     *
     * @param memories 偏好和画像事实记忆
     * @return 用户画像
     */
    public UserMemoryProfileSnapshotResponse extract(List<MemoryEntry> memories) {
        if (memories == null || memories.isEmpty()) {
            return UserMemoryProfileSnapshotResponse.empty();
        }
        ProfileAccumulator accumulator = new ProfileAccumulator();
        memories.stream()
                .filter(this::isProfileEvidence)
                .forEach(accumulator::observe);
        return accumulator.toResponse();
    }

    private boolean isProfileEvidence(MemoryEntry memory) {
        return memory != null
                && StringUtils.hasText(memory.getSemanticKey())
                && (MemoryType.PREFERENCE.equals(memory.getType()) || MemoryType.ENTITY.equals(memory.getType()));
    }

    /**
     * 用户画像投影过程中的临时累加器。
     */
    private static final class ProfileAccumulator {

        private String displayName;
        private String role;
        private String company;
        private String industry;
        private String communicationStyle;
        private String preferredFormat;
        private final List<String> expertiseAreas = new ArrayList<>();
        private final List<String> topics = new ArrayList<>();
        private final List<String> dataSources = new ArrayList<>();
        private double confidenceTotal;
        private int evidenceCount;

        private void observe(MemoryEntry memory) {
            String key = memory.getSemanticKey();
            String content = resolveContent(memory);
            if (!StringUtils.hasText(content)) {
                return;
            }
            boolean projected = true;
            switch (key) {
                case SemanticMemoryKey.DISPLAY_NAME -> displayName = firstValue(displayName, content);
                case SemanticMemoryKey.ROLE -> role = firstValue(role, content);
                case SemanticMemoryKey.COMPANY -> company = firstValue(company, content);
                case SemanticMemoryKey.INDUSTRY -> industry = firstValue(industry, content);
                case SemanticMemoryKey.COMMUNICATION_STYLE -> communicationStyle = firstValue(
                        communicationStyle, content);
                case SemanticMemoryKey.OUTPUT_FORMAT -> preferredFormat = firstValue(preferredFormat, content);
                default -> projected = collectListValue(key, content);
            }
            if (projected) {
                confidenceTotal += normalizedConfidence(memory);
                evidenceCount++;
            }
        }

        private boolean collectListValue(String key, String content) {
            if (key.startsWith("profile.expertise_area.")) {
                addDistinct(expertiseAreas, content);
                return true;
            } else if (key.startsWith("profile.data_source.")) {
                addDistinct(dataSources, content);
                return true;
            } else if (key.startsWith("profile.topic.")) {
                addDistinct(topics, content);
                return true;
            }
            return false;
        }

        private UserMemoryProfileSnapshotResponse toResponse() {
            double confidence = evidenceCount == 0
                    ? 0D
                    : Math.round((confidenceTotal / evidenceCount) * 100D) / 100D;
            return new UserMemoryProfileSnapshotResponse(
                    displayName,
                    role,
                    company,
                    industry,
                    communicationStyle,
                    preferredFormat,
                    List.copyOf(expertiseAreas),
                    List.copyOf(topics),
                    List.copyOf(dataSources),
                    confidence,
                    evidenceCount);
        }

        private String resolveContent(MemoryEntry memory) {
            return StringUtils.hasText(memory.getCompressedContent())
                    ? memory.getCompressedContent()
                    : memory.getContent();
        }

        private String firstValue(String current, String candidate) {
            return StringUtils.hasText(current) ? current : candidate;
        }

        private double normalizedConfidence(MemoryEntry memory) {
            if (memory.getConfidence() > 0D) {
                return Math.min(1D, memory.getConfidence());
            }
            return MemorySource.USER_EXPLICIT.equals(memory.getSource()) ? 1D : 0D;
        }

        private void addDistinct(List<String> target, String value) {
            if (target.size() < MAX_LIST_SIZE && !target.contains(value)) {
                target.add(value);
            }
        }
    }
}
