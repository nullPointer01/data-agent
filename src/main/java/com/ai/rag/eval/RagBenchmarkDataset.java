package com.ai.rag.eval;

import java.util.List;

/**
 * 可重复运行的 RAG 黄金数据集。
 *
 * @param datasetId 数据集编号
 * @param corpusVersion 对应知识语料版本
 * @param cases 标注用例
 * @author data-agent
 */
public record RagBenchmarkDataset(
        String datasetId,
        String corpusVersion,
        List<RagBenchmarkCase> cases) {
}
