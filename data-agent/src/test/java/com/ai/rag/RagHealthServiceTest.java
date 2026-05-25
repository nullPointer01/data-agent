package com.ai.rag;
import com.ai.rag.elasticsearch.ElasticsearchFullTextClient;

import com.ai.rag.dto.RagRetrievalTrace;
import com.ai.service.VectorMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagHealthServiceTest {

    @Test
    void getHealthPassesLatencyChecksWhenRecentTraceIsWithinThreshold() {
        RagProperties properties = new RagProperties();
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        EnhancedRagPipeline pipeline = mock(EnhancedRagPipeline.class);
        when(vectorMemoryService.isVectorStoreAvailable()).thenReturn(true);
        when(pipeline.getLastTrace()).thenReturn(trace(30L, 40L, 300L));
        RagHealthService service = new RagHealthService(properties, vectorMemoryService, pipeline, emptyProvider());

        var response = service.getHealth();

        assertTrue(response.healthy());
        assertTrue(response.acceptanceChecks().stream().allMatch(check -> check.passed()));
    }

    @Test
    void getHealthMarksLatencyCheckPendingWhenNoRecentTraceExists() {
        RagProperties properties = new RagProperties();
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        EnhancedRagPipeline pipeline = mock(EnhancedRagPipeline.class);
        when(vectorMemoryService.isVectorStoreAvailable()).thenReturn(true);
        when(pipeline.getLastTrace()).thenReturn(RagRetrievalTrace.empty());
        RagHealthService service = new RagHealthService(properties, vectorMemoryService, pipeline, emptyProvider());

        var response = service.getHealth();

        assertFalse(response.healthy());
        assertTrue(response.issues().stream().anyMatch(issue -> issue.contains("暂无检索样本")));
    }

    private RagRetrievalTrace trace(long vectorTimeMs, long fullTextTimeMs, long totalTimeMs) {
        return new RagRetrievalTrace(true, "jpa", "milvus", 20, 6, 5000, 0.45D, 2, 2, 1, 1,
                1, 1, 1, vectorTimeMs + fullTextTimeMs, vectorTimeMs, fullTextTimeMs, 1L,
                2L, 1L, 1L, totalTimeMs, 300, false);
    }

    private ObjectProvider<ElasticsearchFullTextClient> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public ElasticsearchFullTextClient getObject(Object... args) {
                return null;
            }

            @Override
            public ElasticsearchFullTextClient getIfAvailable() {
                return null;
            }

            @Override
            public ElasticsearchFullTextClient getIfUnique() {
                return null;
            }

            @Override
            public ElasticsearchFullTextClient getObject() {
                return null;
            }
        };
    }
}
