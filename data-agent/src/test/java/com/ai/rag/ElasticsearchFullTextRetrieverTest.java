package com.ai.rag;
import com.ai.rag.fulltext.FullTextSearchResult;
import com.ai.rag.elasticsearch.ElasticsearchFullTextRetriever;
import com.ai.rag.elasticsearch.ElasticsearchFullTextClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElasticsearchFullTextRetrieverTest {

    @Test
    void retrieveParsesHitsIntoFullTextResults() throws Exception {
        ElasticsearchFullTextClient client = mock(ElasticsearchFullTextClient.class);
        ElasticsearchFullTextRetriever retriever = new ElasticsearchFullTextRetriever(client);
        RagQueryAnalysis analysis = new RagQueryAnalysis("客户流失", "客户流失",
                List.of("客户", "流失"), "ANALYSIS", List.of());
        ObjectMapper objectMapper = new ObjectMapper();
        when(client.search(any(RagQueryAnalysis.class), eq("tenant-1"), eq(3), eq(List.of("knowledge"))))
                .thenReturn(objectMapper.readTree("""
                        {
                          "hits": {
                            "hits": [
                              {
                                "_score": 12.5,
                                "_source": {
                                  "sourceType": "knowledge",
                                  "sourceId": "knowledge-1",
                                  "chunkId": "chunk-1",
                                  "content": "客户流失来自售后响应慢",
                                  "sectionPath": "年度报告 > 客户分析",
                                  "charStart": 100,
                                  "charEnd": 130,
                                  "containsList": true,
                                  "parentChunkId": "knowledge-1_parent_customer",
                                  "parentCharStart": 80,
                                  "parentCharEnd": 160,
                                  "parentContext": "完整客户分析上下文"
                                }
                              }
                            ]
                          }
                        }
                        """));

        List<FullTextSearchResult> results = retriever.retrieve(analysis, "tenant-1", 3, List.of("knowledge"));

        assertEquals(1, results.size());
        assertEquals("knowledge-1", results.get(0).sourceId());
        assertEquals(12.5D, results.get(0).score());
        assertEquals("年度报告 > 客户分析", results.get(0).sectionPath());
        assertEquals(100, results.get(0).charStart());
        assertEquals("knowledge-1_parent_customer", results.get(0).metadata().parentChunkId());
        assertEquals("完整客户分析上下文", results.get(0).parentContext());
        assertTrue(results.get(0).containsList());
    }
}
