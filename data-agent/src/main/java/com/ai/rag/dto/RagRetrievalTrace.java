package com.ai.rag.dto;

/**
 * RAG 检索链路观测信息。
 *
 * @param enabled RAG 是否启用
 * @param fullTextProvider 全文检索提供方
 * @param vectorProvider 向量检索提供方
 * @param candidateTopK 候选召回数量
 * @param topK 最终返回数量
 * @param maxContextChars 上下文字符预算
 * @param minScore 向量最小相关度
 * @param candidateCount 候选总数
 * @param finalCount 最终上下文数量
 * @param vectorCandidateCount 向量通道命中数量
 * @param fullTextCandidateCount 全文通道命中数量
 * @param vectorRawCount 向量通道原始候选数量
 * @param fullTextRawCount 全文通道原始候选数量
 * @param hybridCandidateCount 同时命中向量和全文的数量
 * @param retrievalTimeMs 召回耗时
 * @param vectorRetrievalTimeMs 向量检索耗时
 * @param fullTextRetrievalTimeMs 全文检索耗时
 * @param fusionTimeMs RRF 融合耗时
 * @param rerankTimeMs 重排耗时
 * @param parentContextTimeMs 父级上下文解析耗时
 * @param compressionTimeMs 上下文压缩耗时
 * @param totalTimeMs 总耗时
 * @param contextChars 最终上下文字符数
 * @param compressed 上下文是否被压缩
 * @author data-agent
 */
public record RagRetrievalTrace(
        boolean enabled,
        String fullTextProvider,
        String vectorProvider,
        int candidateTopK,
        int topK,
        int maxContextChars,
        double minScore,
        int candidateCount,
        int finalCount,
        int vectorCandidateCount,
        int fullTextCandidateCount,
        int vectorRawCount,
        int fullTextRawCount,
        int hybridCandidateCount,
        long retrievalTimeMs,
        long vectorRetrievalTimeMs,
        long fullTextRetrievalTimeMs,
        long fusionTimeMs,
        long rerankTimeMs,
        long parentContextTimeMs,
        long compressionTimeMs,
        long totalTimeMs,
        int contextChars,
        boolean compressed) {

    /**
     * 返回空链路信息。
     *
     * @return 空链路信息
     */
    public static RagRetrievalTrace empty() {
        return new RagRetrievalTrace(false, "", "", 0, 0, 0, 0D, 0, 0, 0, 0, 0, 0, 0,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0, false);
    }
}
