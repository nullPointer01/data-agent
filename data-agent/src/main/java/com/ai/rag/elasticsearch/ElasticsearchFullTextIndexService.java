package com.ai.rag.elasticsearch;
import com.ai.rag.elasticsearch.ElasticsearchFullTextClient;
import com.ai.rag.fulltext.FullTextIndexService;
import com.ai.rag.fulltext.FullTextDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基于 Elasticsearch 的全文索引写入服务。
 *
 * @author data-agent
 */
@Service
@ConditionalOnProperty(name = "app.rag.full-text-provider", havingValue = "elasticsearch")
public class ElasticsearchFullTextIndexService implements FullTextIndexService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchFullTextIndexService.class);

    private final ElasticsearchFullTextClient client;

    public ElasticsearchFullTextIndexService(ElasticsearchFullTextClient client) {
        this.client = client;
    }

    @Override
    public void index(List<FullTextDocument> documents) {
        try {
            client.bulkIndex(documents);
        } catch (Exception e) {
            LOGGER.warn("Elasticsearch full-text index failed: {}", e.getMessage());
        }
    }

    @Override
    public void deleteBySource(String sourceType, String sourceId, String tenantId) {
        try {
            client.deleteBySource(sourceType, sourceId, tenantId);
        } catch (Exception e) {
            LOGGER.warn("Elasticsearch full-text delete failed: type={}, sourceId={}", sourceType, sourceId, e);
        }
    }
}
