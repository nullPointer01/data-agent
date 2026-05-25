package com.ai.agent.specialist;

import com.ai.mcp.McpModelService;
import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.ai.agent.AgentType;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.AgentPromptComposer;

class ReportExpertSpecialistTest {

    @Test
    void executeGeneratesReportWithMetadata() {
        McpModelService modelService = mock(McpModelService.class);
        when(modelService.callModel(contains("请输出 Markdown 报告"), isNull()))
                .thenReturn("# 摘要\n报告内容");
        ReportExpertSpecialist specialist = new ReportExpertSpecialist(
                modelService,
                new AgentPromptComposer(new MemoryContextPromptFormatter()));

        AnalysisResponse response = specialist.execute(new AgentExecutionRequest(
                profile(), request("生成销售报告"), "销售数据"));

        assertTrue(response.isSuccess());
        assertEquals("# 摘要\n报告内容", response.getResult());
        assertEquals(true, response.getExecutionMetadata().get("reportReady"));
        assertEquals("# 摘要", response.getExecutionMetadata().get("summary"));
    }

    private AgentProfile profile() {
        AgentProfile profile = new AgentProfile();
        profile.setName("报告专家");
        profile.setType(AgentType.REPORT);
        return profile;
    }

    private AnalysisRequest request(String question) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion(question);
        return request;
    }
}
