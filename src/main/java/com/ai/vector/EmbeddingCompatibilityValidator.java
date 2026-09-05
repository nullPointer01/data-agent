package com.ai.vector;

import dev.langchain4j.data.embedding.Embedding;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 校验模型实际输出与当前 Embedding Profile 是否兼容。
 *
 * @author data-agent
 */
@Component
public class EmbeddingCompatibilityValidator {

    /**
     * 校验向量是否存在以及维度是否符合索引契约。
     *
     * @param profile 当前模型和索引契约
     * @param embedding 模型实际输出
     * @param operation 触发向量化的阶段
     */
    public void validate(EmbeddingProfile profile, Embedding embedding, String operation) {
        if (embedding == null || embedding.vector() == null) {
            throw incompatible(profile, "null", operation);
        }
        int actualDimension = embedding.dimension();
        if (actualDimension != profile.dimension()) {
            throw incompatible(profile, String.valueOf(actualDimension), operation);
        }
    }

    /**
     * 在批量结果离开 Gateway 前校验响应数量和每个向量的兼容性。
     *
     * @param profile 当前模型和索引契约
     * @param embeddings 模型批量输出
     * @param expectedCount 输入片段数量
     * @param operation 触发向量化的阶段
     */
    public void validateBatch(EmbeddingProfile profile, List<Embedding> embeddings,
            int expectedCount, String operation) {
        int actualCount = embeddings == null ? -1 : embeddings.size();
        if (actualCount != expectedCount) {
            throw new IllegalStateException("Embedding 批量响应数量不兼容: modelId=" + profile.modelId()
                    + ", operation=" + operation
                    + ", expectedCount=" + expectedCount
                    + ", actualCount=" + (embeddings == null ? "null" : actualCount));
        }
        for (int index = 0; index < embeddings.size(); index++) {
            validate(profile, embeddings.get(index), operation + "[" + index + "]");
        }
    }

    private IllegalStateException incompatible(EmbeddingProfile profile, String actualDimension,
            String operation) {
        return new IllegalStateException("Embedding 维度不兼容: provider=" + profile.provider()
                + ", modelId=" + profile.modelId()
                + ", expectedDimension=" + profile.dimension()
                + ", actualDimension=" + actualDimension
                + ", operation=" + operation);
    }
}
