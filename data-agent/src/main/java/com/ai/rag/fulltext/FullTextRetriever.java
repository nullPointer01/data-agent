package com.ai.rag.fulltext;
import com.ai.rag.fulltext.FullTextSearchResult;
import com.ai.rag.RagQueryAnalysis;

import java.util.List;

/**
 * 全文检索 SPI。
 *
 * <p>默认实现可以先使用 JPA 模糊检索，后续接入 Elasticsearch/BM25 时只需要替换该接口实现。</p>
 *
 * @author data-agent
 */
public interface FullTextRetriever {

    /**
     * 按租户检索全文候选。
     *
     * @param analysis 查询分析结果
     * @param tenantId 租户编号
     * @param topK 候选数量
     * @param sourceTypes 来源类型过滤
     * @return 全文检索候选
     */
    List<FullTextSearchResult> retrieve(RagQueryAnalysis analysis, String tenantId, int topK,
            List<String> sourceTypes);
}
