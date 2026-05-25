package com.ai.rag.fulltext;
import com.ai.rag.fulltext.FullTextDocument;
import com.ai.rag.fulltext.FullTextIndexService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 未启用外部全文索引时的空实现。
 *
 * @author data-agent
 */
@Component
@ConditionalOnProperty(name = "app.rag.full-text-provider", havingValue = "jpa", matchIfMissing = true)
public class NoopFullTextIndexService implements FullTextIndexService {

    @Override
    public void index(List<FullTextDocument> documents) {
        // 本地 JPA 兜底检索直接读取业务表，不需要额外写入全文索引。
    }

    @Override
    public void deleteBySource(String sourceType, String sourceId, String tenantId) {
        // 本地 JPA 兜底检索直接读取业务表，不需要额外清理全文索引。
    }
}
