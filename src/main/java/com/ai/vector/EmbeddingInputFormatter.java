package com.ai.vector;

import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 在 Embedding Gateway 边界按用途统一格式化模型输入。
 *
 * @author data-agent
 */
@Component
public class EmbeddingInputFormatter {

    private final String queryPrefix;
    private final String documentPrefix;

    /**
     * 从统一配置中读取 query/document 输入契约。
     *
     * @param properties Embedding 配置
     */
    public EmbeddingInputFormatter(EmbeddingProperties properties) {
        EmbeddingProperties.Api api = Objects.requireNonNull(properties.getApi(),
                "Embedding 配置不能为空: app.embedding.api");
        this.queryPrefix = valueOrEmpty(api.getQueryPrefix());
        this.documentPrefix = valueOrEmpty(api.getDocumentPrefix());
    }

    /**
     * 按用途格式化纯文本输入。
     *
     * @param text 原始输入
     * @param purpose 输入用途
     * @return 仅添加对应用途前缀的文本
     */
    public String format(String text, EmbeddingPurpose purpose) {
        Objects.requireNonNull(text, "Embedding 输入文本不能为空");
        Objects.requireNonNull(purpose, "Embedding 输入用途不能为空");
        return switch (purpose) {
            case QUERY -> queryPrefix + text;
            case DOCUMENT -> documentPrefix + text;
            case PROBE -> text;
        };
    }

    /**
     * 按用途格式化文档片段，并保留片段的完整元数据。
     *
     * @param segment 原始文档片段
     * @param purpose 输入用途
     * @return 文本已格式化、元数据未改变的新片段
     */
    public TextSegment format(TextSegment segment, EmbeddingPurpose purpose) {
        Objects.requireNonNull(segment, "Embedding 文档片段不能为空");
        return TextSegment.from(format(segment.text(), purpose), segment.metadata());
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
