package com.ai.agent.specialist;

import com.ai.knowledge.dto.KnowledgeSearchResponse;
import com.ai.knowledge.dto.KnowledgeSearchResult;
import com.ai.mcp.McpModelService;
import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.service.knowledge.KnowledgeService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ai.agent.AgentType;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.AgentPromptComposer;

class KnowledgeExpertSpecialistTest {

    @Test
    void executeSearchesKnowledgeAndBuildsCitationAwareAnswer() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        McpModelService modelService = mock(McpModelService.class);
        KnowledgeExpertSpecialist specialist = new KnowledgeExpertSpecialist(
                knowledgeService,
                modelService,
                new AgentPromptComposer(new MemoryContextPromptFormatter()));
        when(knowledgeService.searchKnowledge("报销制度是什么", 5))
                .thenReturn(KnowledgeSearchResponse.success(List.of(result("R1", "报销制度内容"))));
        when(modelService.callModel(contains("[R1 | knowledge | knowledge-1"), isNull()))
                .thenReturn("报销制度内容 [R1]");

        AnalysisResponse response = specialist.execute(new AgentExecutionRequest(
                profile(), request("报销制度是什么"), null));

        assertTrue(response.isSuccess());
        assertEquals("报销制度内容 [R1]", response.getResult());
        assertEquals(List.of("R1"), response.getExecutionMetadata().get("citations"));
        verify(knowledgeService).searchKnowledge("报销制度是什么", 5);
        verify(modelService).callModel(contains("报销制度内容"), eq(null));
    }

    @Test
    void executeFailsWhenKnowledgeIsEmpty() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        KnowledgeExpertSpecialist specialist = new KnowledgeExpertSpecialist(
                knowledgeService,
                mock(McpModelService.class),
                new AgentPromptComposer(new MemoryContextPromptFormatter()));
        when(knowledgeService.searchKnowledge("不存在的制度", 5))
                .thenReturn(KnowledgeSearchResponse.empty("未找到相关知识"));

        AnalysisResponse response = specialist.execute(new AgentExecutionRequest(
                profile(), request("不存在的制度"), null));

        assertEquals(false, response.isSuccess());
        assertEquals("未找到相关知识", response.getError());
    }

    private AgentProfile profile() {
        AgentProfile profile = new AgentProfile();
        profile.setName("知识专家");
        profile.setType(AgentType.KNOWLEDGE);
        return profile;
    }

    private AnalysisRequest request(String question) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion(question);
        return request;
    }

    private KnowledgeSearchResult result(String referenceId, String content) {
        return new KnowledgeSearchResult(
                referenceId,
                "knowledge",
                "knowledge-1",
                "chunk-1",
                0.91D,
                0.88D,
                0.72D,
                List.of("vector", "full_text"),
                content,
                "财务/报销",
                0,
                content.length(),
                false,
                false,
                false);
    }
}
