package com.ai.vector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.GetLoadStateResponse;
import io.milvus.grpc.LoadState;
import io.milvus.grpc.MutationResult;
import io.milvus.param.RpcStatus;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.GetLoadStateParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Milvus 向量存储网关。
 *
 * @author data-agent
 */
@Component
public class MilvusVectorStoreGateway implements EmbeddingStore<TextSegment> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MilvusVectorStoreGateway.class);
    private static final int MILVUS_CLOSE_TIMEOUT_SECONDS = 5;
    private static final long COLLECTION_LOAD_WAIT_INTERVAL_MS = 500L;
    private static final long COLLECTION_LOAD_TIMEOUT_MS = 60_000L;
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

    @Override
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

    /** 向量记录必须有业务元数据；拒绝无来源/身份的框架写入重载。 */
    @Override
    public String add(Embedding embedding) {
        throw new UnsupportedOperationException("向量入库必须提供 TextSegment 和业务元数据");
    }

    @Override
    public void add(String id, Embedding embedding) {
        throw new UnsupportedOperationException("向量入库必须提供 TextSegment 和业务元数据");
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw new UnsupportedOperationException("向量入库必须提供 TextSegment 和业务元数据");
    }

    /** 官方 Retriever 通过 EmbeddingStore 接口检索，仍保留原有加载恢复与单次重试。 */
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        return new EmbeddingSearchResult<>(search(request.queryEmbedding(), request.maxResults(),
                request.minScore(), request.filter()));
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
                requestCollectionLoad(false);
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
        EmbeddingStore<TextSegment> initializedStore = MilvusEmbeddingStore.builder()
                .milvusClient(milvusClient)
                .collectionName(collectionName)
                .dimension(embeddingProfile.dimension())
                .indexType(indexType)
                .metricType(metricType)
                .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .retrieveEmbeddingsOnSearch(true)
                .build();
        requestCollectionLoad(true);
        this.embeddingStore = initializedStore;
        LOGGER.info("Milvus embedding store 已延迟初始化 collection={}", collectionName);
        return initializedStore;
    }

    /**
     * 请求 Milvus 加载 Collection。启动阶段只预热，真正执行向量操作前必须有界等待加载完成。
     *
     * @param awaitLoaded 是否等待 Collection 进入 Loaded 状态
     */
    private void requestCollectionLoad(boolean awaitLoaded) {
        R<RpcStatus> resp = milvusClient.loadCollection(
                buildLoadCollectionParam(collectionName));
        if (resp.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Milvus collection 加载请求失败: status=" + resp.getStatus()
                    + ", message=" + resp.getMessage());
        }
        if (awaitLoaded) {
            awaitCollectionLoaded(milvusClient, collectionName,
                    COLLECTION_LOAD_WAIT_INTERVAL_MS, COLLECTION_LOAD_TIMEOUT_MS);
        }
    }

    /**
     * 构建非阻塞 Collection 加载参数。加载完成由本项目显式查询状态，避免 SDK 同步等待静默超时。
     *
     * @param targetCollection 物理 Collection 名称
     * @return Milvus 加载参数
     */
    static LoadCollectionParam buildLoadCollectionParam(String targetCollection) {
        return LoadCollectionParam.newBuilder()
                .withCollectionName(targetCollection)
                .withSyncLoad(false)
                .build();
    }

    /**
     * 有界等待 Collection 真正进入 Loaded 状态。
     *
     * @param client Milvus 客户端
     * @param targetCollection 物理 Collection 名称
     * @param pollIntervalMs 状态查询间隔，最小按 1 毫秒处理
     * @param timeoutMs 最大等待时间
     */
    static void awaitCollectionLoaded(MilvusServiceClient client, String targetCollection,
            long pollIntervalMs, long timeoutMs) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMs));
        LoadState lastState = LoadState.LoadStateNotExist;
        while (true) {
            R<GetLoadStateResponse> response = client.getLoadState(
                    GetLoadStateParam.newBuilder()
                            .withCollectionName(targetCollection)
                            .build());
            if (response.getStatus() != R.Status.Success.getCode() || response.getData() == null) {
                throw new IllegalStateException("Milvus collection 加载状态查询失败: collection="
                        + targetCollection + ", status=" + response.getStatus() + ", message=" + response.getMessage());
            }
            lastState = response.getData().getState();
            if (LoadState.LoadStateLoaded.equals(lastState)) {
                return;
            }
            if (System.nanoTime() >= deadlineNanos) {
                throw new IllegalStateException("Milvus collection 加载超时: collection="
                        + targetCollection + ", state=" + lastState + ", timeoutMs=" + timeoutMs);
            }
            sleepBeforeNextLoadStateCheck(targetCollection, Math.max(1L, pollIntervalMs));
        }
    }

    private static void sleepBeforeNextLoadStateCheck(String targetCollection, long pollIntervalMs) {
        try {
            Thread.sleep(pollIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Milvus collection 加载时线程被中断: collection="
                    + targetCollection, e);
        }
    }

    /**
     * 检查异常链中是否包含可通过重新加载 Collection 恢复的状态错误。
     *
     * @param throwable Milvus 调用异常
     * @return 是否允许刷新状态后重试一次
     */
    static boolean isRecoverableMilvusState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (isRecoverableMilvusState(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isRecoverableMilvusState(String message) {
        if (message == null) {
            return false;
        }
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        return normalizedMessage.contains(MESSAGE_COLLECTION_NOT_LOADED)
                || normalizedMessage.contains(MESSAGE_CHANNEL_NOT_FOUND);
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
