package com.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class VectorMemoryService {

    private static final Logger log = LoggerFactory.getLogger(VectorMemoryService.class);
    private static final double DEFAULT_MIN_SCORE = 0.5;
    private static final String PERSIST_DIR = "data/vector-store";
    private static final String PERSIST_FILE = "embeddings.json";

    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private int milvusPort;

    @Value("${milvus.collection-name:data_agent_vectors}")
    private String collectionName;

    @Value("${milvus.dimension:384}")
    private int dimension;

    @Value("${milvus.index-type:IVF_FLAT}")
    private String indexTypeStr;

    @Value("${milvus.metric-type:COSINE}")
    private String metricTypeStr;

    private EmbeddingStore<TextSegment> embeddingStore;
    private EmbeddingModel embeddingModel;
    private boolean usingMilvus = false;

    private final AtomicInteger indexedCount = new AtomicInteger(0);
    private final Map<String, IndexEntry> indexRegistry = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    @PostConstruct
    public void init() {
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        if (isMilvusAvailable()) {
            try {
                IndexType indexType = IndexType.valueOf(indexTypeStr);
                MetricType metricType = MetricType.valueOf(metricTypeStr);

                this.embeddingStore = MilvusEmbeddingStore.builder()
                        .host(milvusHost)
                        .port(milvusPort)
                        .collectionName(collectionName)
                        .dimension(dimension)
                        .indexType(indexType)
                        .metricType(metricType)
                        .consistencyLevel(io.milvus.common.clientenum.ConsistencyLevelEnum.EVENTUALLY)
                        .retrieveEmbeddingsOnSearch(true)
                        .build();

                this.usingMilvus = true;
                log.info("VectorMemoryService initialized with MilvusEmbeddingStore [{}:{}] collection={} dim={} index={} metric={}",
                        milvusHost, milvusPort, collectionName, dimension, indexTypeStr, metricTypeStr);
            } catch (Exception e) {
                log.warn("Failed to create MilvusEmbeddingStore, falling back to InMemoryEmbeddingStore: {}",
                        e.getMessage());
                initInMemoryStore();
            }
        } else {
            log.warn("Milvus not available at {}:{}, using InMemoryEmbeddingStore", milvusHost, milvusPort);
            initInMemoryStore();
        }
    }

    private void initInMemoryStore() {
        this.usingMilvus = false;
        Path persistPath = Paths.get(PERSIST_DIR, PERSIST_FILE);
        if (Files.exists(persistPath)) {
            try {
                String json = Files.readString(persistPath);
                this.embeddingStore = InMemoryEmbeddingStore.fromJson(json);
                loadRegistry();
                log.info("VectorMemoryService restored from {} ({} entries)", persistPath, indexedCount.get());
                return;
            } catch (Exception e) {
                log.warn("Failed to restore InMemoryEmbeddingStore from disk, starting fresh: {}", e.getMessage());
            }
        }
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        log.info("VectorMemoryService initialized with InMemoryEmbeddingStore (Milvus unavailable)");
    }

    @PreDestroy
    public void shutdown() {
        if (!usingMilvus) {
            persistToDisk();
        }
    }

    @Scheduled(fixedRate = 60_000)
    public void autoPersist() {
        if (!usingMilvus && dirty) {
            persistToDisk();
            dirty = false;
        }
    }

    private void persistToDisk() {
        try {
            Path dir = Paths.get(PERSIST_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            if (embeddingStore instanceof InMemoryEmbeddingStore<TextSegment> inMemory) {
                String json = inMemory.serializeToJson();
                Files.writeString(Paths.get(PERSIST_DIR, PERSIST_FILE), json);
                saveRegistry();
                log.debug("InMemoryEmbeddingStore persisted to disk ({} entries)", indexedCount.get());
            }
        } catch (IOException e) {
            log.warn("Failed to persist InMemoryEmbeddingStore to disk: {}", e.getMessage());
        }
    }

    private void loadRegistry() {
        Path registryPath = Paths.get(PERSIST_DIR, "registry.json");
        if (Files.exists(registryPath)) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Map<String, String>> raw = mapper.readValue(
                        Files.readString(registryPath),
                        mapper.getTypeFactory().constructMapType(Map.class, String.class, Map.class));
                raw.forEach((key, val) -> indexRegistry.put(key, new IndexEntry(val.get("type"), val.get("id"), val.get("text"))));
                indexedCount.set(indexRegistry.size());
            } catch (Exception e) {
                log.warn("Failed to load registry: {}", e.getMessage());
            }
        }
    }

    private void saveRegistry() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Files.writeString(Paths.get(PERSIST_DIR, "registry.json"), mapper.writeValueAsString(indexRegistry));
        } catch (Exception e) {
            log.warn("Failed to save registry: {}", e.getMessage());
        }
    }

    private boolean isMilvusAvailable() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(milvusHost, milvusPort), 3000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isUsingMilvus() {
        return usingMilvus;
    }

    public void indexConversation(String sessionId, String userMessage, String assistantReply) {
        String summary = "用户: " + userMessage + "\n助手: " + assistantReply;
        String entryId = "conversation:" + sessionId + ":" + System.currentTimeMillis();
        index(summary, "conversation", entryId);
        log.debug("Indexed conversation for session: {}", sessionId);
    }

    public void indexFile(String fileId, String filename, String content) {
        if (content == null || content.isEmpty()) return;
        removeFromStore("file", fileId);

        int chunkSize = 500;
        int overlap = 100;
        int pos = 0;
        int chunkIndex = 0;
        while (pos < content.length()) {
            int end = Math.min(pos + chunkSize, content.length());
            String chunk = content.substring(pos, end);
            String text = "文件[" + filename + "] " + chunk;
            index(text, "file", fileId + "_chunk_" + chunkIndex);
            pos += chunkSize - overlap;
            chunkIndex++;
        }
        log.debug("Indexed file: {} ({} chunks)", filename, chunkIndex);
    }

    public void indexSkill(String skillName, String description, String promptTemplate) {
        String text = "技能[" + skillName + "]: " + description + " | 模板: " + promptTemplate;
        index(text, "skill", skillName);
        log.debug("Indexed skill: {}", skillName);
    }

    public void indexKnowledge(String content, String source) {
        if (content == null || content.isEmpty()) return;
        removeFromStore("knowledge", source);

        int chunkSize = 500;
        int overlap = 100;
        int pos = 0;
        int chunkIndex = 0;
        while (pos < content.length()) {
            int end = Math.min(pos + chunkSize, content.length());
            String chunk = content.substring(pos, end);
            index(chunk, "knowledge", source + "_chunk_" + chunkIndex);
            pos += chunkSize - overlap;
            chunkIndex++;
        }
        log.debug("Indexed knowledge from: {} ({} chunks)", source, chunkIndex);
    }

    private void index(String text, String type, String id) {
        try {
            TextSegment segment = TextSegment.from(text,
                    new Metadata()
                            .put("type", type)
                            .put("id", id));
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);

            String registryKey = type + ":" + id;
            if (!indexRegistry.containsKey(registryKey)) {
                indexedCount.incrementAndGet();
            }
            indexRegistry.put(registryKey, new IndexEntry(type, id, text.length() > 100 ? text.substring(0, 100) : text));
            dirty = true;
        } catch (Exception e) {
            log.warn("Failed to index text [{}]: {}", type, e.getMessage());
        }
    }

    public String searchRelevant(String query, int topK) {
        return searchRelevant(query, topK, DEFAULT_MIN_SCORE);
    }

    public String searchRelevant(String query, int topK, double minScore) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(queryEmbedding, topK, minScore);
            if (matches.isEmpty()) {
                log.debug("No relevant results found for query: {}", query.substring(0, Math.min(50, query.length())));
                return "";
            }
            return matches.stream()
                    .map(match -> {
                        double score = match.score();
                        TextSegment segment = match.embedded();
                        String type = segment.metadata().getString("type");
                        return "[" + type + " | 相关度:" + String.format("%.2f", score) + "] " + segment.text();
                    })
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.warn("Search failed for query: {}", e.getMessage());
            return "";
        }
    }

    public List<EmbeddingMatch<TextSegment>> searchMatches(String query, int topK, double minScore) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            return embeddingStore.findRelevant(queryEmbedding, topK, minScore);
        } catch (Exception e) {
            log.warn("Search matches failed: {}", e.getMessage());
            return List.of();
        }
    }

    public synchronized void removeFromStore(String type, String id) {
        List<String> keysToRemove = indexRegistry.keySet().stream()
                .filter(key -> {
                    IndexEntry entry = indexRegistry.get(key);
                    if (entry == null) return false;
                    return entry.type.equals(type) && entry.id.startsWith(id);
                })
                .toList();

        if (keysToRemove.isEmpty()) return;

        if (!usingMilvus) {
            InMemoryEmbeddingStore<TextSegment> newStore = new InMemoryEmbeddingStore<>();
            Embedding dummyQuery = embeddingModel.embed("dummy").content();
            List<EmbeddingMatch<TextSegment>> allMatches = embeddingStore.findRelevant(dummyQuery, 100000, 0.0);

            for (EmbeddingMatch<TextSegment> match : allMatches) {
                TextSegment segment = match.embedded();
                String segType = segment.metadata().getString("type");
                String segId = segment.metadata().getString("id");
                if (segType != null && segType.equals(type) && segId != null && segId.startsWith(id)) {
                    continue;
                }
                Embedding embedding = match.embedding();
                if (embedding != null) {
                    newStore.add(embedding, segment);
                } else {
                    newStore.add(embeddingModel.embed(segment).content(), segment);
                }
            }
            this.embeddingStore = newStore;
        }

        int removedCount = keysToRemove.size();
        keysToRemove.forEach(indexRegistry::remove);
        indexedCount.addAndGet(-removedCount);
        dirty = true;
        log.info("Removed {} entries from store - type: {}, id prefix: {}", removedCount, type, id);
    }

    public int getIndexedCount() {
        return indexedCount.get();
    }

    public Map<String, Long> getIndexedCountByType() {
        return indexRegistry.values().stream()
                .collect(Collectors.groupingBy(e -> e.type, Collectors.counting()));
    }

    private record IndexEntry(String type, String id, String text) {
    }
}
