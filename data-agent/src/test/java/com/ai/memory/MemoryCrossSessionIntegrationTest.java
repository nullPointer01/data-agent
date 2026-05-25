package com.ai.memory;

import com.ai.agent.orchestrator.ParallelTaskExecutor;
import com.ai.agent.react.ReActRequestContext;
import com.ai.agent.react.ReActRequestContextBuilder;
import com.ai.memory.dto.MemoryCaptureRequest;
import com.ai.model.AnalysisRequest;
import com.ai.rag.RagRetrievalService;
import com.ai.rag.dto.RagContextResponse;
import com.ai.repository.MemoryEntryRepository;
import com.ai.repository.UserProfileMemoryRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.service.VectorMemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MemoryCrossSessionIntegrationTest {

    @Autowired
    private MemoryEntryRepository memoryEntryRepository;

    @Autowired
    private UserProfileMemoryRepository userProfileMemoryRepository;

    @Test
    void explicitLongTermMemoryIsAvailableInNextSessionPrompt() {
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        MemoryManager memoryManager = memoryManager(vectorMemoryService);
        memoryManager.capture(new MemoryCaptureRequest(
                MemoryTier.LONG_TERM,
                MemoryType.PREFERENCE,
                MemorySource.USER_EXPLICIT,
                "session-1",
                "请记住，我叫张三，我偏好表格和简洁直接的分析结论",
                null,
                null,
                null,
                null,
                5));
        String nextQuestion = "我是谁，输出格式按我的偏好来";
        when(vectorMemoryService.searchRelevant(eq(nextQuestion), eq(5), eq(0.45D),
                eq("tenant-1"), eq("user-1"), eq(List.of("memory"))))
                .thenReturn("记忆: 用户明确要求记住：我偏好表格和简洁直接的分析结论");

        ReActRequestContext context = requestContextBuilder(memoryManager).build(request(nextQuestion), null);

        assertTrue(context.userQuery().contains("[记忆上下文]"));
        assertTrue(context.userQuery().contains("称呼=张三"));
        assertTrue(context.userQuery().contains("偏好格式=表格优先"));
        assertTrue(context.userQuery().contains("沟通风格=简洁直接"));
        assertTrue(context.userQuery().contains("长期记忆"));
        assertTrue(context.userQuery().contains("表格和简洁直接"));
    }

    private MemoryManager memoryManager(VectorMemoryService vectorMemoryService) {
        MemoryProperties memoryProperties = new MemoryProperties();
        MemoryJsonCodec memoryJsonCodec = new MemoryJsonCodec(new ObjectMapper());
        MemoryEntryFactory memoryEntryFactory = new MemoryEntryFactory(memoryJsonCodec, memoryProperties);
        LongTermMemory longTermMemory = new LongTermMemory(memoryEntryRepository, memoryEntryFactory,
                vectorMemoryService, memoryProperties);
        UserProfileMemoryService userProfileMemoryService = new UserProfileMemoryService(
                userProfileMemoryRepository, memoryJsonCodec);
        UserProfileMemoryRefreshService refreshService = new UserProfileMemoryRefreshService(
                longTermMemory, new UserMemoryProfileExtractor(), userProfileMemoryService);
        return new MemoryManager(
                mock(WorkingMemory.class),
                mock(ShortTermMemory.class),
                longTermMemory,
                identity(),
                new SimpleMeterRegistry(),
                new MemoryQuotaService(memoryEntryRepository, vectorMemoryService, memoryProperties,
                        new SimpleMeterRegistry()),
                userProfileMemoryService,
                refreshService);
    }

    private ReActRequestContextBuilder requestContextBuilder(MemoryManager memoryManager) {
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        when(ragRetrievalService.retrieve("我是谁，输出格式按我的偏好来"))
                .thenReturn(RagContextResponse.empty());
        return new ReActRequestContextBuilder(
                ragRetrievalService,
                memoryManager);
    }

    private AnalysisRequest request(String question) {
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion(question);
        request.setSessionId("session-2");
        return request;
    }

    private SecurityContextHelper identity() {
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-1");
        return securityContextHelper;
    }
}
