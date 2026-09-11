package com.ai.rag.retrieval;
import com.ai.rag.retrieval.RetrievalResult;
import com.ai.rag.retrieval.HybridRetrievalResult;
import com.ai.rag.retrieval.DefaultHybridRetriever;
import com.ai.rag.RagQueryAnalysis;

import java.util.List;

/**
 * RAG 混合检索 SPI。
 *
 * <p>默认实现 {@link DefaultHybridRetriever} 通过向量检索与全文检索双通道召回，再经 RRF 融合。
 * 可替换为其他混合检索策略。</p>
 *
 * @author data-agent
 */
public interface HybridRetriever {

    /**
     * 执行混合检索。
     *
     * @param analysis 查询分析结果
     * @param tenantId 租户编号
     * @param userId 用户编号
     * @param candidateTopK 候选数量
     * @param minScore 向量最小分数
     * @param sourceTypes 来源类型过滤
     * @return 统一检索结果
     */
    List<RetrievalResult> retrieve(RagQueryAnalysis analysis, String tenantId, String userId, int candidateTopK,
            double minScore, List<String> sourceTypes);

    /**
     * 执行混合检索并返回通道级耗时。
     *
     * @param analysis 查询分析结果
     * @param tenantId 租户编号
     * @param userId 用户编号
     * @param candidateTopK 候选数量
     * @param minScore 向量最小分数
     * @param sourceTypes 来源类型过滤
     * @return 混合检索结果和通道耗时
     */
    HybridRetrievalResult retrieveWithTrace(RagQueryAnalysis analysis, String tenantId, String userId,
            int candidateTopK,
            double minScore, List<String> sourceTypes);
}
