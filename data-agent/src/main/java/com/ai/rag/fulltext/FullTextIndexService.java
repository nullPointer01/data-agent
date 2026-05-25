package com.ai.rag.fulltext;
import com.ai.rag.fulltext.FullTextDocument;

import java.util.List;

/**
 * 全文索引写入服务。
 *
 * @author data-agent
 */
public interface FullTextIndexService {

    /**
     * 写入同一来源下的分块文档。
     *
     * @param documents 分块文档
     */
    void index(List<FullTextDocument> documents);

    /**
     * 删除同一来源下的全文索引。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源编号
     * @param tenantId 租户编号
     */
    void deleteBySource(String sourceType, String sourceId, String tenantId);
}
