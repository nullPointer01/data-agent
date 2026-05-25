package com.ai.agent.react;

import com.ai.memory.MemoryManager;
import com.ai.memory.MemorySource;
import com.ai.memory.MemoryTier;
import com.ai.memory.MemoryType;
import com.ai.memory.dto.MemoryContext;
import com.ai.memory.dto.MemoryEntrySummary;
import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.model.AnalysisRequest;
import com.ai.rag.RagRetrievalService;
import com.ai.rag.dto.RagContextResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReActRequestContextBuilderTest {

    @Test
    void buildAddsFileHintAndRagContext() {
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("分析销售趋势");
        when(ragRetrievalService.retrieve("分析销售趋势"))
                .thenReturn(new RagContextResponse("企业资料内容", 1));
        ReActRequestContextBuilder builder = new ReActRequestContextBuilder(ragRetrievalService);

        ReActRequestContext context = builder.build(request, "csv-content");

        assertTrue(context.userQuery().contains("[检索上下文]"));
        assertTrue(context.userQuery().contains("企业资料内容"));
        assertTrue(context.userQuery().contains("引用编号"));
        assertTrue(context.userQuery().contains("[已加载上下文]"));
        assertTrue(context.ragContext().hasContext());
    }

    @Test
    void buildKeepsPlainQuestionWhenRagIsEmpty() {
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("你好");
        when(ragRetrievalService.retrieve("你好"))
                .thenReturn(new RagContextResponse("", 0));
        ReActRequestContextBuilder builder = new ReActRequestContextBuilder(ragRetrievalService);

        ReActRequestContext context = builder.build(request, null);

        assertTrue("你好".equals(context.userQuery()));
    }

    @Test
    void buildAddsMemoryContextWhenAvailable() {
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        MemoryManager memoryManager = mock(MemoryManager.class);
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("公司Q3卖了多少");
        request.setSessionId("session-1");
        when(ragRetrievalService.retrieve("公司Q3卖了多少"))
                .thenReturn(new RagContextResponse("", 0));
        when(memoryManager.buildContext("session-1", "公司Q3卖了多少"))
                .thenReturn(new MemoryContext(
                        "用户正在做电商运营分析",
                        List.of(new MemoryEntrySummary("m-1", MemoryTier.SHORT_TERM, MemoryType.SUMMARY,
                                MemorySource.SYSTEM_GENERATED, "用户叫张三，负责XX电商", 0.8D, null)),
                        "长期偏好: 喜欢图表和简短结论",
                        new UserMemoryProfileSnapshotResponse("张三", "运营", "星河电商", "电商",
                                "简洁直接", "表格优先", List.of("销售分析"), List.of("报表生成"),
                                List.of("MySQL"), 0.85D)));
        ReActRequestContextBuilder builder = new ReActRequestContextBuilder(ragRetrievalService, memoryManager);

        ReActRequestContext context = builder.build(request, null);

        assertTrue(context.userQuery().contains("[记忆上下文]"));
        assertTrue(context.userQuery().contains("用户画像"));
        assertTrue(context.userQuery().contains("称呼=张三"));
        assertTrue(context.userQuery().contains("偏好格式=表格优先"));
        assertTrue(context.userQuery().contains("用户正在做电商运营分析"));
        assertTrue(context.userQuery().contains("用户叫张三"));
        assertTrue(context.userQuery().contains("长期偏好"));
    }

    @Test
    void buildUsesFallbackWhenRagFails() {
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        AnalysisRequest request = new AnalysisRequest();
        request.setQuestion("你好");
        when(ragRetrievalService.retrieve("你好")).thenThrow(new IllegalStateException("vector down"));
        ReActRequestContextBuilder builder = new ReActRequestContextBuilder(ragRetrievalService);

        ReActRequestContext context = builder.build(request, null);

        assertTrue("你好".equals(context.userQuery()));
        assertTrue(!context.ragContext().hasContext());
    }
}
