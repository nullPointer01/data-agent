package com.ai.agent;

import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.memory.MemorySource;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;
import com.ai.memory.dto.MemoryContext;
import com.ai.memory.dto.MemoryEntrySummary;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.model.AgentProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptComposerTest {

    private final AgentPromptComposer composer = new AgentPromptComposer(new MemoryContextPromptFormatter());

    @Test
    void mergeFileContentAppendsBoundDataSourcePreview() {
        String merged = composer.mergeFileContent("file-data", "datasource-preview");

        assertTrue(merged.contains("file-data"));
        assertTrue(merged.contains("Agent 绑定数据源预览"));
        assertTrue(merged.contains("datasource-preview"));
    }

    @Test
    void buildPromptUsesProfileSystemPrompt() {
        AgentProfile profile = new AgentProfile();
        profile.setSystemPrompt("你是客服专家");

        String prompt = composer.buildPrompt(profile, "怎么退款", "退款规则");

        assertTrue(prompt.contains("你是客服专家"));
        assertTrue(prompt.contains("怎么退款"));
        assertTrue(prompt.contains("退款规则"));
    }

    @Test
    void mergeMemoryContextAppendsFormattedMemorySection() {
        MemoryContext memoryContext = new MemoryContext(
                "正在分析会员复购",
                List.of(new MemoryEntrySummary("m-1", MemoryTier.SHORT_TERM, MemoryType.SUMMARY,
                        MemorySource.SYSTEM_GENERATED, "用户上次关注华东区域", 0.7D, null)),
                "长期偏好: 先给表格",
                new UserMemoryProfileSnapshotResponse("张三", null, null, null, null, "表格优先",
                        List.of(), List.of(), List.of(), 0.8D));

        String merged = composer.mergeMemoryContext("业务数据", memoryContext);

        assertTrue(merged.contains("业务数据"));
        assertTrue(merged.contains("[记忆上下文]"));
        assertTrue(merged.contains("称呼=张三"));
        assertTrue(merged.contains("用户上次关注华东区域"));
        assertTrue(merged.contains("长期偏好"));
    }
}
