package com.ai.agent;

import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.memory.MemoryManager;
import com.ai.memory.MemorySource;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;
import com.ai.memory.dto.MemoryContext;
import com.ai.memory.dto.MemoryEntrySummary;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.mcp.McpContextManager;
import com.ai.mcp.McpModelService;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.model.ConversationSession;
import com.ai.skill.Skill;
import com.ai.skill.SkillManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ai.agent.tool.AgentConversationRecorder;

class SkillExecutionServiceTest {

    @Test
    void executeReturnsNullWhenNoSkillCanBeResolved() {
        SkillManager skillManager = mock(SkillManager.class);
        SkillExecutionService service = newService(skillManager, mock(McpContextManager.class),
                mock(AgentConversationRecorder.class));
        AnalysisRequest request = request("skill-1");

        AnalysisResponse response = service.execute(request, null, null);

        assertNull(response);
    }

    @Test
    void executeDestroysMcpContextAndRecordsConversation() {
        SkillManager skillManager = mock(SkillManager.class);
        McpContextManager contextManager = mock(McpContextManager.class);
        AgentConversationRecorder recorder = mock(AgentConversationRecorder.class);
        McpModelService modelService = mock(McpModelService.class);
        Skill skill = mock(Skill.class);
        ConversationSession session = new ConversationSession("session-1");
        AnalysisRequest request = request("skill-1");
        request.setModelId("model-1");

        when(skillManager.findSkillByName("skill-1")).thenReturn(skill);
        when(skill.getName()).thenReturn("销售分析");
        when(contextManager.createContext("销售分析", "model-1")).thenReturn("ctx-1");
        when(skill.processWithContext(org.mockito.ArgumentMatchers.eq("hello"),
                org.mockito.ArgumentMatchers.argThat(data -> data.toString().contains("file")
                        && data.toString().contains("[记忆上下文]")
                        && data.toString().contains("称呼=张三")),
                org.mockito.ArgumentMatchers.eq("ctx-1"), org.mockito.ArgumentMatchers.eq(modelService),
                org.mockito.ArgumentMatchers.eq("model-1"))).thenReturn("result");

        SkillExecutionService service = new SkillExecutionService(skillManager, modelService, contextManager, recorder);

        AnalysisResponse response = service.execute(request, "file", session);

        assertEquals("result", response.getResult());
        assertEquals("销售分析", response.getSkillUsed());
        verify(recorder).recordSessionConversation(session, request, "result", "销售分析", "model-1");
        verify(contextManager).destroyContext("ctx-1");
    }

    private SkillExecutionService newService(SkillManager skillManager,
            McpContextManager contextManager,
            AgentConversationRecorder recorder) {
        return new SkillExecutionService(skillManager, mock(McpModelService.class), contextManager, recorder);
    }

    private AnalysisRequest request(String skillId) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("hello");
        request.setSkillId(skillId);
        return request;
    }

    private MemoryManager memoryManager() {
        MemoryManager memoryManager = mock(MemoryManager.class);
        when(memoryManager.buildContext(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(memoryContext());
        return memoryManager;
    }

    private MemoryContext memoryContext() {
        return new MemoryContext(
                "",
                List.of(new MemoryEntrySummary("m-1", MemoryTier.SHORT_TERM, MemoryType.SUMMARY,
                        MemorySource.SYSTEM_GENERATED, "用户关注复购率", 0.8D, null)),
                "",
                new UserMemoryProfileSnapshotResponse("张三", null, null, null, null, null,
                        List.of(), List.of(), List.of(), 0.8D));
    }
}
