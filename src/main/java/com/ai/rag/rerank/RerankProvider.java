package com.ai.rag.rerank;

import java.util.List;

/**
 * RAG 精排模型的统一 Provider 契约。
 *
 * @author data-agent
 */
public interface RerankProvider {

    /**
     * 对候选片段计算相关性分数。
     *
     * @param request 精排请求
     * @return 与原候选索引关联的模型分数
     */
    RerankResponse rerank(RerankRequest request);

    /**
     * 返回稳定的 Provider 名称，用于 Trace 和评测报告。
     *
     * @return Provider 名称
     */
    String providerName();

    /**
     * 返回当前模型身份。
     *
     * @return 模型名称或规则版本
     */
    String modelName();

    /**
     * 一次精排请求。
     *
     * @param query 用户查询
     * @param keywords 查询关键词
     * @param candidates 保留原始位置的候选
     */
    record RerankRequest(String query, List<String> keywords, List<RerankCandidate> candidates) {
    }

    /**
     * 交给 Provider 的候选片段。
     *
     * @param index 原候选索引
     * @param content Chunk 正文
     * @param retrievalScore RRF 召回分数
     * @param sourceType 来源类型
     */
    record RerankCandidate(int index, String content, double retrievalScore, String sourceType) {
    }

    /**
     * Provider 返回结果。
     *
     * @param scores 候选索引与相关性分数
     */
    record RerankResponse(List<RerankScore> scores) {
    }

    /**
     * 单个候选的相关性分数。
     *
     * @param index 原候选索引
     * @param score 相关性分数
     */
    record RerankScore(int index, double score) {
    }

    /**
     * 带稳定错误分类的 Provider 异常，供 fail-open 和 Trace 使用。
     */
    final class RerankProviderException extends RuntimeException {

        private final String category;
        private final boolean retriable;

        /**
         * 创建 Provider 异常。
         *
         * @param category 低基数错误类别
         * @param message 安全错误信息
         * @param retriable 是否允许有限重试
         */
        public RerankProviderException(String category, String message, boolean retriable) {
            super(message);
            this.category = category;
            this.retriable = retriable;
        }

        /**
         * 创建带根因的 Provider 异常。
         *
         * @param category 低基数错误类别
         * @param message 安全错误信息
         * @param retriable 是否允许有限重试
         * @param cause 根因
         */
        public RerankProviderException(String category, String message, boolean retriable, Throwable cause) {
            super(message, cause);
            this.category = category;
            this.retriable = retriable;
        }

        public String category() {
            return category;
        }

        public boolean retriable() {
            return retriable;
        }
    }
}
