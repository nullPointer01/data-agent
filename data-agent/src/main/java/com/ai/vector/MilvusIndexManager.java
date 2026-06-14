package com.ai.vector;

import io.milvus.client.MilvusServiceClient;
import io.milvus.exception.ServerException;
import io.milvus.grpc.DescribeIndexResponse;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.index.DescribeIndexParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 确保 Milvus collection 拥有可用的向量索引。
 *
 * @author data-agent
 */
@Component
public class MilvusIndexManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MilvusIndexManager.class);
    private static final String VECTOR_FIELD_NAME = "vector";
    private static final String INDEX_EXTRA_PARAM_IVF_FLAT = "{\"nlist\":1024}";
    private static final String INDEX_EXTRA_PARAM_IVF_PQ = "{\"nlist\":1024,\"m\":8}";
    private static final String INDEX_EXTRA_PARAM_HNSW = "{\"M\":8,\"efConstruction\":64}";
    private static final String INDEX_EXTRA_PARAM_DEFAULT = "{}";

    /**
     * Ensures an existing collection has an index, or lets LangChain4j create a missing collection.
     *
     * @param client Milvus service client
     * @param collectionName collection name
     * @param indexType index type
     * @param metricType metric type
     */
    public boolean ensureCollectionAndIndex(MilvusServiceClient client, String collectionName,
            IndexType indexType, MetricType metricType) {
        if (!collectionExists(client, collectionName)) {
            LOGGER.info("Milvus collection '{}' does not exist, LangChain4j will create it on first build",
                    collectionName);
            return false;
        }
        if (vectorIndexExists(client, collectionName)) {
            LOGGER.info("Milvus collection '{}' and vector index already exist, reusing", collectionName);
            return true;
        }
        createIndex(client, collectionName, indexType, metricType);
        return true;
    }

    private void createIndex(MilvusServiceClient client, String collectionName,
            IndexType indexType, MetricType metricType) {
        String extraParam = buildIndexExtraParam(indexType);
        R<RpcStatus> createResp = client.createIndex(
                CreateIndexParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFieldName(VECTOR_FIELD_NAME)
                        .withIndexType(indexType)
                        .withMetricType(metricType)
                        .withExtraParam(extraParam)
                        .withSyncMode(true)
                        .build());

        if (createResp.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Failed to auto-create index on Milvus collection '"
                    + collectionName + "': status=" + createResp.getStatus()
                    + ", message=" + createResp.getMessage());
        }
        LOGGER.info("Auto-created vector index on collection '{}' (type={}, metric={}, params={})",
                collectionName, indexType, metricType, extraParam);
    }

    private boolean collectionExists(MilvusServiceClient client, String collectionName) {
        try {
            R<Boolean> resp = client.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build());
            if (resp.getStatus() != R.Status.Success.getCode()) {
                LOGGER.warn("hasCollection check returned non-success (status={}, msg={}), assuming missing",
                        resp.getStatus(), resp.getMessage());
                return false;
            }
            return Boolean.TRUE.equals(resp.getData());
        } catch (Exception e) {
            LOGGER.warn("hasCollection threw, assuming collection missing: {}", e.getMessage());
            return false;
        }
    }

    private boolean vectorIndexExists(MilvusServiceClient client, String collectionName) {
        try {
            R<DescribeIndexResponse> resp = client.describeIndex(
                    DescribeIndexParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build());
            return resp.getStatus() == R.Status.Success.getCode()
                    && resp.getData() != null
                    && resp.getData().getIndexDescriptionsCount() > 0;
        } catch (ServerException e) {
            LOGGER.debug("describeIndex reports no index: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.warn("describeIndex unexpected error, treating index as missing: {}", e.getMessage());
            return false;
        }
    }

    private String buildIndexExtraParam(IndexType indexType) {
        return switch (indexType) {
            case IVF_FLAT, IVF_SQ8 -> INDEX_EXTRA_PARAM_IVF_FLAT;
            case IVF_PQ -> INDEX_EXTRA_PARAM_IVF_PQ;
            case HNSW -> INDEX_EXTRA_PARAM_HNSW;
            default -> INDEX_EXTRA_PARAM_DEFAULT;
        };
    }
}
