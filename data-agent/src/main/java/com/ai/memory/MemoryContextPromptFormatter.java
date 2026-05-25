package com.ai.memory;

import com.ai.memory.dto.MemoryContext;
import com.ai.memory.dto.MemoryEntrySummary;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将结构化记忆上下文转换为模型可理解的 Prompt 片段。
 *
 * @author data-agent
 */
@Component
public class MemoryContextPromptFormatter {

    private static final String MEMORY_SECTION_TITLE = "[记忆上下文]";
    private static final String KEY_MEMORY_USED = "memoryUsed";
    private static final String KEY_HAS_WORKING_MEMORY = "hasWorkingMemory";
    private static final String KEY_SHORT_TERM_COUNT = "shortTermCount";
    private static final String KEY_HAS_LONG_TERM_CONTEXT = "hasLongTermContext";
    private static final String KEY_HAS_USER_PROFILE = "hasUserProfile";

    /**
     * 判断当前上下文是否包含可注入模型的记忆。
     *
     * @param memoryContext 记忆上下文
     * @return 是否存在有效记忆
     */
    public boolean hasMemory(MemoryContext memoryContext) {
        if (memoryContext == null) {
            return false;
        }
        return StringUtils.hasText(memoryContext.workingMemory())
                || StringUtils.hasText(memoryContext.longTermContext())
                || !safeList(memoryContext.shortTermMemories()).isEmpty()
                || hasProfile(memoryContext.userProfile());
    }

    /**
     * 生成完整的记忆 Prompt 章节。
     *
     * @param memoryContext 记忆上下文
     * @return Prompt 章节，无有效记忆时返回空字符串
     */
    public String toSection(MemoryContext memoryContext) {
        if (!hasMemory(memoryContext)) {
            return "";
        }
        return MEMORY_SECTION_TITLE + "\n" + format(memoryContext);
    }

    /**
     * 生成不含章节标题的记忆正文。
     *
     * @param memoryContext 记忆上下文
     * @return 记忆正文
     */
    public String format(MemoryContext memoryContext) {
        if (memoryContext == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendUserProfile(builder, memoryContext.userProfile());
        if (StringUtils.hasText(memoryContext.workingMemory())) {
            builder.append("- 工作记忆: ").append(memoryContext.workingMemory()).append('\n');
        }
        for (MemoryEntrySummary memory : safeList(memoryContext.shortTermMemories())) {
            if (StringUtils.hasText(memory.content())) {
                builder.append("- 短期记忆: ").append(memory.content()).append('\n');
            }
        }
        if (StringUtils.hasText(memoryContext.longTermContext())) {
            builder.append("- 长期记忆:\n").append(memoryContext.longTermContext()).append('\n');
        }
        return builder.toString();
    }

    /**
     * 生成用于审计和观测的记忆使用元数据，避免暴露记忆正文。
     *
     * @param memoryContext 记忆上下文
     * @return 记忆使用元数据
     */
    public Map<String, Object> toMetadata(MemoryContext memoryContext) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(KEY_MEMORY_USED, hasMemory(memoryContext));
        metadata.put(KEY_HAS_WORKING_MEMORY, memoryContext != null
                && StringUtils.hasText(memoryContext.workingMemory()));
        metadata.put(KEY_SHORT_TERM_COUNT, memoryContext == null ? 0 : safeList(memoryContext.shortTermMemories()).size());
        metadata.put(KEY_HAS_LONG_TERM_CONTEXT, memoryContext != null
                && StringUtils.hasText(memoryContext.longTermContext()));
        metadata.put(KEY_HAS_USER_PROFILE, memoryContext != null && hasProfile(memoryContext.userProfile()));
        return metadata;
    }

    private void appendUserProfile(StringBuilder builder, UserMemoryProfileSnapshotResponse profile) {
        if (!hasProfile(profile)) {
            return;
        }
        builder.append("- 用户画像:");
        appendProfileField(builder, "称呼", profile.displayName());
        appendProfileField(builder, "角色", profile.role());
        appendProfileField(builder, "公司", profile.company());
        appendProfileField(builder, "行业", profile.industry());
        appendProfileField(builder, "沟通风格", profile.communicationStyle());
        appendProfileField(builder, "偏好格式", profile.preferredFormat());
        appendProfileList(builder, "专业领域", profile.expertiseAreas());
        appendProfileList(builder, "高频话题", profile.frequentlyAskedTopics());
        appendProfileList(builder, "常用数据源", profile.dataSources());
        builder.append('\n');
    }

    private void appendProfileField(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(' ').append(label).append('=').append(value).append(';');
        }
    }

    private void appendProfileList(StringBuilder builder, String label, List<String> values) {
        if (values != null && !values.isEmpty()) {
            builder.append(' ').append(label).append('=').append(String.join(",", values)).append(';');
        }
    }

    private boolean hasProfile(UserMemoryProfileSnapshotResponse profile) {
        return profile != null && (StringUtils.hasText(profile.displayName())
                || StringUtils.hasText(profile.role())
                || StringUtils.hasText(profile.company())
                || StringUtils.hasText(profile.industry())
                || StringUtils.hasText(profile.communicationStyle())
                || StringUtils.hasText(profile.preferredFormat())
                || !safeList(profile.expertiseAreas()).isEmpty()
                || !safeList(profile.frequentlyAskedTopics()).isEmpty()
                || !safeList(profile.dataSources()).isEmpty());
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
