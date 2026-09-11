package com.ai.agent;

import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.memory.dto.MemoryContext;
import com.ai.model.AgentProfile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Agent Prompt 组装器，负责将系统提示词、用户问题、文件内容、
 * 数据源预览和记忆上下文拼装成完整 Prompt。
 *
 * @author data-agent
 */
@Component
public class AgentPromptComposer {

    private static final String DATASOURCE_PREVIEW_HEADER = "\n\n## Agent 绑定数据源预览\n";
    private static final String MEMORY_SECTION_SEPARATOR = "\n\n";
    private static final String FILE_CONTENT_HEADER = "\n\n## 参考数据\n";
    private static final String QUESTION_HEADER = "\n\n## 用户问题\n";

    private final MemoryContextPromptFormatter memoryFormatter;

    /**
     * 构造 Prompt 组装器。
     *
     * @param memoryFormatter 记忆上下文格式化器
     */
    public AgentPromptComposer(MemoryContextPromptFormatter memoryFormatter) {
        this.memoryFormatter = memoryFormatter;
    }

    /**
     * 合并文件内容与数据源预览。
     *
     * @param fileContent 用户上传的文件内容，可为空
     * @param datasourcePreview 数据源预览内容，可为空
     * @return 合并后的数据文本
     */
    public String mergeFileContent(String fileContent, String datasourcePreview) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(fileContent)) {
            builder.append(fileContent);
        }
        if (StringUtils.hasText(datasourcePreview)) {
            builder.append(DATASOURCE_PREVIEW_HEADER).append(datasourcePreview);
        }
        return builder.toString();
    }

    /**
     * 将记忆上下文追加到已有的数据文本中。
     *
     * @param dataContent 已有数据内容
     * @param memoryContext 聚合记忆上下文，可为空
     * @return 追加记忆章节后的文本
     */
    public String mergeMemoryContext(String dataContent, MemoryContext memoryContext) {
        String section = memoryFormatter.toSection(memoryContext);
        if (!StringUtils.hasText(section)) {
            return dataContent == null ? "" : dataContent;
        }
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(dataContent)) {
            builder.append(dataContent);
        }
        builder.append(MEMORY_SECTION_SEPARATOR).append(section);
        return builder.toString();
    }

    /**
     * 组装完整的 Prompt，包含系统提示词、用户问题和参考数据。
     *
     * @param profile Agent 配置，提供系统提示词
     * @param question 用户问题
     * @param dataContent 合并后的参考数据，可为空
     * @return 完整 Prompt 文本
     */
    public String buildPrompt(AgentProfile profile, String question, String dataContent) {
        StringBuilder builder = new StringBuilder();
        if (profile != null && StringUtils.hasText(profile.getSystemPrompt())) {
            builder.append(profile.getSystemPrompt());
        }
        if (StringUtils.hasText(dataContent)) {
            builder.append(FILE_CONTENT_HEADER).append(dataContent);
        }
        if (StringUtils.hasText(question)) {
            builder.append(QUESTION_HEADER).append(question);
        }
        return builder.toString();
    }
}
