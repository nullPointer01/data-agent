package com.ai.rag.fulltext;
import com.ai.rag.fulltext.FullTextSearchResult;
import com.ai.rag.RagQueryAnalysis;

import java.util.List;

/**
 * 全文检索 SPI。
 *
 * <p>当前唯一实现为 Elasticsearch BM25；接口用于隔离检索编排与搜索引擎客户端细节。</p>
 *
 * @author data-agent
 */
public interface FullTextRetriever {

    /**
     * 按租户和用户检索全文候选。
     *
     * @param analysis 查询分析结果
     * @param tenantId 租户编号
     * @param userId 用户编号
     * @param topK 候选数量
     * @param sourceTypes 来源类型过滤
     * @return 全文检索候选
     */
    List<FullTextSearchResult> retrieve(RagQueryAnalysis analysis, String tenantId, String userId, int topK,
            List<String> sourceTypes);
}
