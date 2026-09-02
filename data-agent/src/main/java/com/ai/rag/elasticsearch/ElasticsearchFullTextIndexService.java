package com.ai.rag.elasticsearch;
import com.ai.rag.elasticsearch.ElasticsearchFullTextClient;
import com.ai.rag.fulltext.FullTextIndexService;
import com.ai.rag.fulltext.FullTextDocument;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基于 Elasticsearch 的全文索引写入服务。
 *
 * @author data-agent
 */
@Service
public class ElasticsearchFullTextIndexService implements FullTextIndexService {

    private final ElasticsearchFullTextClient client;

    public ElasticsearchFullTextIndexService(ElasticsearchFullTextClient client) {
        this.client = client;
    }

    @Override
    public void index(List<FullTextDocument> documents) {
        client.bulkIndex(documents);
    }

    @Override
    public void deleteBySource(String sourceType, String sourceId, String tenantId) {
        client.deleteBySource(sourceType, sourceId, tenantId);
    }
}
