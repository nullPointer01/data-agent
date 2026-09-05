package com.ai.vector;

/**
 * 区分 Embedding 输入在检索链路中的用途。
 *
 * @author data-agent
 */
public enum EmbeddingPurpose {

    /** 启动兼容性探测，不应用业务输入前缀。 */
    PROBE,

    /** 检索查询，应用 query 前缀。 */
    QUERY,

    /** 索引文档，应用 document 前缀。 */
    DOCUMENT
}
