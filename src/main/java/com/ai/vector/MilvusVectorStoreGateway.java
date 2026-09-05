package com.ai.vector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.MutationResult;
import io.milvus.param.RpcStatus;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Milvus 向量存储网关。
 *
 * @author data-agent
 */
@Component
public class MilvusVectorStoreGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(MilvusVectorStoreGateway.class);
    private static final int MILVUS_CLOSE_TIMEOUT_SECONDS = 5;
    private static final String MESSAGE_COLLECTION_NOT_LOADED = "collection not loaded";
    private static final String MESSAGE_CHANNEL_NOT_FOUND = "channel not found";

    private final String milvusHost;
    private final int milvusPort;
    private final String collectionName;
    private final String indexTypeStr;
    private final EmbeddingProfile embeddingProfile;
    private final MilvusAvailabilityChecker availabilityChecker;
    private final MilvusIndexManager indexManager;
    private final MilvusDeleteExpressionBuilder deleteExpressionBuilder;

    private EmbeddingStore<TextSegment> embeddingStore;

    private MilvusServiceClient milvusClient;
    private IndexType indexType;
    private MetricType metricType;

    public MilvusVectorStoreGateway(
            @Value("${milvus.host:localhost}") String milvusHost,
            @Value("${milvus.port:19530}") int milvusPort,
            @Value("${milvus.collection-name:data_agent_vectors}") String baseCollectionName,
            @Value("${milvus.index-type:IVF_FLAT}") String indexTypeStr,
            EmbeddingGateway embeddingGateway,
            MilvusCollectionNameResolver collectionNameResolver,
            MilvusAvailabilityChecker availabilityChecker,
            MilvusIndexManager indexManager,
            MilvusDeleteExpressionBuilder deleteExpressionBuilder) {
        this.milvusHost = milvusHost;
        this.milvusPort = milvusPort;
        this.embeddingProfile = embeddingGateway.getProfile();
        this.collectionName = collectionNameResolver.resolve(baseCollectionName, embeddingProfile);
        this.indexTypeStr = indexTypeStr;
        this.availabilityChecker = availabilityChecker;
        this.indexManager = indexManager;
        this.deleteExpressionBuilder = deleteExpressionBuilder;
    }

    @PostConstruct
    public void init() {
        if (!availabilityChecker.isAvailable(milvusHost, milvusPort)) {
            throw new IllegalStateException("Milvus 不可用: " + milvusHost + ":" + milvusPort
                    + "，当前项目强依赖向量库，请先启动 Milvus");
        }
        initMilvusStore();
    }

    public String add(Embedding embedding, TextSegment segment) {
        if (!isAvailable()) {
            throw new IllegalStateException("Milvus 客户端未初始化，无法写入向量");
        }
        try {
            return ensureEmbeddingStore().add(embedding, segment);
        } catch (RuntimeException e) {
            if (!isRecoverableMilvusState(e)) {
                throw e;
            }
            LOGGER.warn("Milvus 写入遇到可恢复状态异常，刷新 collection 后重试一次: {}", e.getMessage());
            refreshCollectionState();
            return ensureEmbeddingStore().add(embedding, segment);
        }
    }

    /**
     * 按输入顺序批量写入向量及对应片段，并返回同序 Milvus 主键。
     *
     * @param embeddings 已完成 Profile 校验的向量
     * @param segments 与向量一一对应的原始片段
     * @return 与输入顺序一致的主键
     */
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> segments) {
        requireMatchingCount(embeddings, segments);
        if (embeddings.isEmpty()) {
            return List.of();
        }
        if (!isAvailable()) {
            throw new IllegalStateException("Milvus 客户端未初始化，无法批量写入向量");
        }
        List<String> primaryKeys;
        try {
            primaryKeys = ensureEmbeddingStore().addAll(embeddings, segments);
        } catch (RuntimeException e) {
            if (!isRecoverableMilvusState(e)) {
                throw e;
            }
            LOGGER.warn("Milvus 批量写入遇到可恢复状态异常，刷新 collection 后重试一次: exception={}",
                    e.getClass().getName());
            refreshCollectionState();
            primaryKeys = ensureEmbeddingStore().addAll(embeddings, segments);
        }
        validatePrimaryKeys(primaryKeys, segments.size());
        return List.copyOf(primaryKeys);
    }

    public List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int topK, double minScore, Filter filter) {
        if (!isAvailable()) {
            LOGGER.debug("Milvus 客户端未初始化，跳过向量检索");
            return List.of();
        }
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(minScore);
        if (filter != null) {
            requestBuilder.filter(filter);
        }
        try {
            return ensureEmbeddingStore().search(requestBuilder.build()).matches();
        } catch (RuntimeException e) {
            if (!isRecoverableMilvusState(e)) {
                throw e;
            }
            LOGGER.warn("Milvus 检索遇到可恢复状态异常，刷新 collection 后重试一次: {}", e.getMessage());
            refreshCollectionState();
            return ensureEmbeddingStore().search(requestBuilder.build()).matches();
        }
    }

    public boolean deleteBySource(String type, String sourceId, String tenantId) {
        if (!isAvailable()) {
            LOGGER.debug("Milvus 客户端未初始化，跳过向量删除");
            return false;
        }
        try {
            String expr = buildDeleteExpression(type, sourceId, tenantId);
            R<MutationResult> resp = delete(expr);
            if (resp.getStatus() != R.Status.Success.getCode()) {
                if (isRecoverableMilvusState(resp.getMessage())) {
                    LOGGER.warn("Milvus 删除遇到可恢复状态异常，刷新 collection 后重试一次: {}", resp.getMessage());
                    refreshCollectionState();
                    resp = delete(expr);
                }
            }
            if (resp.getStatus() != R.Status.Success.getCode()) {
                LOGGER.warn("Milvus 删除失败: status={}, message={}", resp.getStatus(), resp.getMessage());
                return false;
            }
            LOGGER.debug("Milvus 删除已提交，type={}, sourceId={}, tenant={}", type, sourceId, tenantId);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Milvus 删除异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断 Milvus 客户端是否已经初始化。
     *
     * @return 是否可用
     */
    public boolean isAvailable() {
        return milvusClient != null;
    }

    public String getCollectionName() {
        return collectionName;
    }

    @PreDestroy
    public void shutdown() {
        closeMilvusClientQuietly();
    }

    private void initMilvusStore() {
        try {
            this.indexType = IndexType.valueOf(indexTypeStr);
            this.metricType = MetricType.valueOf(embeddingProfile.metric());
            this.milvusClient = new MilvusServiceClient(
                    ConnectParam.newBuilder()
                            .withHost(milvusHost)
                            .withPort(milvusPort)
                            .build());

            boolean collectionExists = indexManager.ensureCollectionAndIndex(milvusClient, collectionName,
                    indexType, metricType);
            if (collectionExists) {
                requestCollectionLoad();
            }

            LOGGER.info("Milvus 客户端已初始化 [{}:{}] collection={} dim={} index={} metric={}",
                    milvusHost, milvusPort, collectionName, embeddingProfile.dimension(), indexTypeStr,
                    embeddingProfile.metric());
        } catch (Exception e) {
            closeMilvusClientQuietly();
            throw new IllegalStateException("Milvus 向量存储初始化失败: " + e.getMessage(), e);
        }
    }

    private void closeMilvusClientQuietly() {
        if (milvusClient == null) {
            return;
        }
        try {
            milvusClient.close(MILVUS_CLOSE_TIMEOUT_SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("关闭 MilvusServiceClient 时线程被中断");
        } catch (Exception e) {
            LOGGER.warn("关闭 MilvusServiceClient 失败: {}", e.getMessage());
        } finally {
            milvusClient = null;
            embeddingStore = null;
        }
    }

    private String buildDeleteExpression(String type, String sourceId, String tenantId) {
        return deleteExpressionBuilder.buildBySource(type, sourceId, tenantId);
    }

    private R<MutationResult> delete(String expr) {
        return milvusClient.delete(
                DeleteParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withExpr(expr)
                        .build());
    }

    private void refreshCollectionState() {
        closeMilvusClientQuietly();
        initMilvusStore();
    }

    private synchronized EmbeddingStore<TextSegment> ensureEmbeddingStore() {
        if (embeddingStore != null) {
            return embeddingStore;
        }
        if (milvusClient == null) {
            throw new IllegalStateException("Milvus 客户端未初始化，无法创建向量存储");
        }
        this.embeddingStore = MilvusEmbeddingStore.builder()
                .host(milvusHost)
                .port(milvusPort)
                .collectionName(collectionName)
                .dimension(embeddingProfile.dimension())
                .indexType(indexType)
                .metricType(metricType)
                .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .retrieveEmbeddingsOnSearch(true)
                .build();
        requestCollectionLoad();
        LOGGER.info("Milvus embedding store 已延迟初始化 collection={}", collectionName);
        return embeddingStore;
    }

    private void requestCollectionLoad() {
        R<RpcStatus> resp = milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withSyncLoad(false)
                        .build());
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Milvus collection 加载请求失败: status=" + resp.getStatus()
                    + ", message=" + resp.getMessage());
        }
    }

    private boolean isRecoverableMilvusState(Throwable throwable) {
        return throwable != null && isRecoverableMilvusState(throwable.getMessage());
    }

    private boolean isRecoverableMilvusState(String message) {
        if (message == null) {
            return false;
        }
        return message.contains(MESSAGE_COLLECTION_NOT_LOADED) || message.contains(MESSAGE_CHANNEL_NOT_FOUND);
    }

    private void requireMatchingCount(List<Embedding> embeddings, List<TextSegment> segments) {
        if (embeddings == null || segments == null || embeddings.size() != segments.size()) {
            throw new IllegalArgumentException("Milvus 批量写入数量不一致: embeddings="
                    + sizeOf(embeddings) + ", segments=" + sizeOf(segments));
        }
    }

    private void validatePrimaryKeys(List<String> primaryKeys, int expectedCount) {
        if (primaryKeys == null || primaryKeys.size() != expectedCount) {
            throw new IllegalStateException("Milvus 批量写入主键数量不一致: expected=" + expectedCount
                    + ", actual=" + sizeOf(primaryKeys));
        }
        for (int index = 0; index < primaryKeys.size(); index++) {
            if (primaryKeys.get(index) == null || primaryKeys.get(index).isBlank()) {
                throw new IllegalStateException("Milvus 批量写入返回空主键: index=" + index);
            }
        }
    }

    private int sizeOf(List<?> values) {
        return values == null ? -1 : values.size();
    }
}
