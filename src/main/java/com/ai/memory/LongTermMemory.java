package com.ai.memory;

import com.ai.indexing.IndexTask;
import com.ai.indexing.IndexTaskStore;
import com.ai.memory.dto.MemoryCaptureRequest;
import com.ai.repository.MemoryEntryRepository;
import com.ai.service.VectorMemoryService;
import com.ai.vector.VectorDocumentTypes;
import com.ai.vector.VectorMetadataKeys;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 长期记忆元数据存储和 Milvus 向量索引入口。
 *
 * @author data-agent
 */
@Service
public class LongTermMemory {

    private static final double DEFAULT_MIN_SCORE = 0.45D;
    private static final int PROFILE_EVIDENCE_LIMIT = 200;

    private final MemoryEntryRepository memoryEntryRepository;
    private final MemoryEntryFactory memoryEntryFactory;
    private final VectorMemoryService vectorMemoryService;
    private final MemoryQuotaService memoryQuotaService;
    private final MemoryProperties memoryProperties;
    private final IndexTaskStore indexTasks;
    private final MemoryWriteGuard writeGuard;

    public LongTermMemory(MemoryEntryRepository memoryEntryRepository,
            MemoryEntryFactory memoryEntryFactory,
            VectorMemoryService vectorMemoryService,
            MemoryQuotaService memoryQuotaService,
            MemoryProperties memoryProperties, IndexTaskStore indexTasks, MemoryWriteGuard writeGuard) {
        this.memoryEntryRepository = memoryEntryRepository;
        this.memoryEntryFactory = memoryEntryFactory;
        this.vectorMemoryService = vectorMemoryService;
        this.memoryQuotaService = memoryQuotaService;
        this.memoryProperties = memoryProperties;
        this.indexTasks = indexTasks;
        this.writeGuard = writeGuard;
    }

    /**
     * 保存长期记忆并在同一事务登记索引任务，外部向量服务由后台处理。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param request 写入请求
     * @return 持久化后的记忆元数据
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryEntry capture(String tenantId, String userId, MemoryCaptureRequest request) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("tenantId and userId are required");
        }
        MemoryCaptureRequest normalized = request.normalize();
        writeGuard.advance(tenantId, userId);
        MemoryEntry entry = memoryEntryFactory.create(tenantId, userId, normalized);
        entry.setTier(MemoryTier.LONG_TERM);
        MemoryEntry saved = memoryEntryRepository.save(entry);
        String content = normalized.effectiveContent();
        if (StringUtils.hasText(content)) {
            scheduleIndex(saved);
            saved.setVectorId(VectorDocumentTypes.MEMORY + ":" + saved.getMemoryId());
            return memoryEntryRepository.save(saved);
        }
        return saved;
    }

    /**
     * 根据稳定语义键新增或覆盖长期语义记忆。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param request 语义记忆请求
     * @return 写入后的记忆
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryEntry upsertSemantic(String tenantId, String userId, MemoryCaptureRequest request) {
        writeGuard.lock(tenantId, userId);
        MemoryCaptureRequest normalized = request.normalize();
        if (!StringUtils.hasText(normalized.semanticKey())) {
            throw new IllegalArgumentException("semanticKey is required");
        }
        MemoryEntry incoming = memoryEntryFactory.create(tenantId, userId, normalized);
        MemoryEntry existing = memoryEntryRepository.findByTenantIdAndUserIdAndTypeAndSemanticKey(
                tenantId, userId, normalized.type(), normalized.semanticKey()).orElse(null);
        if (existing == null) {
            memoryQuotaService.pruneBeforeCapture(tenantId, userId);
        }
        MemoryEntry target = existing == null ? incoming : applySemanticUpdate(existing, incoming);
        target.setTier(MemoryTier.LONG_TERM);
        target.setExpiresAt(null);
        MemoryEntry saved = memoryEntryRepository.save(target);
        String content = resolveContent(saved);
        if (StringUtils.hasText(content)) {
            scheduleIndex(saved);
            saved.setVectorId(VectorDocumentTypes.MEMORY + ":" + saved.getMemoryId());
            return memoryEntryRepository.save(saved);
        }
        return saved;
    }

    /**
     * 保存已经存在的记忆实体为长期记忆。
     *
     * @param entry 记忆实体
     * @return 保存后的长期记忆
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryEntry savePromoted(MemoryEntry entry) {
        writeGuard.lock(entry.getTenantId(), entry.getUserId());
        // 晋升候选可能已经被用户删除，不能用陈旧实体重新插入。
        entry = memoryEntryRepository.findByMemoryIdAndTenantIdAndUserId(
                entry.getMemoryId(), entry.getTenantId(), entry.getUserId())
                .orElseThrow(() -> new IllegalStateException("记忆晋升来源已失效"));
        entry.setTier(MemoryTier.LONG_TERM);
        entry.setExpiresAt(null);
        MemoryEntry saved = memoryEntryRepository.save(entry);
        String content = resolveContent(saved);
        if (StringUtils.hasText(content)) {
            scheduleIndex(saved);
            saved.setVectorId(VectorDocumentTypes.MEMORY + ":" + saved.getMemoryId());
            return memoryEntryRepository.save(saved);
        }
        return saved;
    }

    /**
     * 检索相关长期记忆上下文。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param query 查询文本
     * @param topK 返回数量
     * @return 格式化后的记忆上下文
     */
    public String recall(String tenantId, String userId, String query, int topK) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId) || !StringUtils.hasText(query)) {
            return "";
        }
        List<EmbeddingMatch<TextSegment>> matches = vectorMemoryService.searchMatches(
                query, topK, DEFAULT_MIN_SCORE, tenantId, userId, List.of(VectorDocumentTypes.MEMORY));
        if (matches.isEmpty()) {
            return "";
        }
        // 向量索引是异步投影：主库决定记忆是否仍存在、有效且属于当前用户。
        List<String> ids = matches.stream().map(this::sourceId)
                .filter(StringUtils::hasText).distinct().toList();
        if (ids.isEmpty()) {
            return "";
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, MemoryEntry> current = memoryEntryRepository
                .findActiveLongTermByIds(ids, tenantId, userId, now).stream()
                .collect(Collectors.toMap(MemoryEntry::getMemoryId, entry -> entry));
        Map<String, String> evidence = new LinkedHashMap<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            String id = sourceId(match);
            MemoryEntry entry = current.get(id);
            if (entry == null) {
                continue;
            }
            String content = resolveContent(entry);
            String indexed = match.embedded().text();
            String fragment = indexed.startsWith("记忆: ") ? indexed.substring("记忆: ".length()) : indexed;
            // 修改后尚未重建的旧片段不能继续作为当前记忆证据。
            if (StringUtils.hasText(content) && StringUtils.hasText(fragment) && content.contains(fragment)) {
                evidence.putIfAbsent(id, "[memoryId=" + id + " | 相关度:"
                        + String.format(Locale.ROOT, "%.2f", match.score()) + "] 记忆: " + fragment);
            }
        }
        return String.join("\n\n", evidence.values());
    }

    private String sourceId(EmbeddingMatch<TextSegment> match) {
        return match.embedded().metadata().getString(VectorMetadataKeys.SOURCE_ID);
    }

    private void scheduleIndex(MemoryEntry entry) {
        indexTasks.enqueue(IndexTask.Kind.MEMORY, entry.getTenantId(), entry.getUserId(), entry.getMemoryId());
    }

    /**
     * 查询用于刷新用户画像的长期记忆证据。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 画像证据记忆
     */
    @Transactional
    public List<MemoryEntry> listProfileEvidence(String tenantId, String userId) {
        // 当前读避免外层事务的旧快照在重置后重新生成旧画像；调用方先持有用户写锁。
        return memoryEntryRepository.findByTenantIdAndUserIdAndTierAndTypeInOrderByUpdatedAtDesc(
                tenantId,
                userId,
                MemoryTier.LONG_TERM,
                List.of(MemoryType.PREFERENCE, MemoryType.ENTITY),
                PageRequest.of(0, PROFILE_EVIDENCE_LIMIT));
    }

    private MemoryEntry applySemanticUpdate(MemoryEntry target, MemoryEntry incoming) {
        target.setSessionId(incoming.getSessionId());
        target.setSource(incoming.getSource());
        target.setContent(incoming.getContent());
        target.setCompressedContent(incoming.getCompressedContent());
        target.setSourceContentLength(incoming.getSourceContentLength());
        target.setStoredContentLength(incoming.getStoredContentLength());
        target.setMetadataJson(incoming.getMetadataJson());
        target.setSemanticKey(incoming.getSemanticKey());
        target.setConfidence(incoming.getConfidence());
        target.setKeyEntitiesJson(incoming.getKeyEntitiesJson());
        target.setTopicTagsJson(incoming.getTopicTagsJson());
        target.setRelevanceScore(incoming.getRelevanceScore());
        target.setDecayWeight(incoming.getDecayWeight());
        target.setLastAccessedAt(LocalDateTime.now());
        return target;
    }

    /**
     * 对长期记忆做低频慢衰减。
     *
     * @param now 当前时间
     * @param limit 最大处理数量
     * @return 衰减维护结果
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryDecayResult applyDecay(LocalDateTime now, int limit) {
        List<MemoryEntry> entries = memoryEntryRepository.findByTierOrderByUpdatedAtAsc(
                MemoryTier.LONG_TERM, PageRequest.of(0, normalizeLimit(limit)));
        int updatedCount = 0;
        for (MemoryEntry entry : entries) {
            LocalDateTime lastAccessedAt = entry.getLastAccessedAt() == null ? entry.getUpdatedAt()
                    : entry.getLastAccessedAt();
            LocalDateTime baseline = lastAccessedAt == null ? now : lastAccessedAt;
            long daysSinceAccess = ChronoUnit.DAYS.between(baseline, now);
            if (daysSinceAccess < memoryProperties.getLongTermDecayAfterDays()) {
                continue;
            }
            entry.setDecayWeight(roundWeight(entry.getDecayWeight() * memoryProperties.getLongTermDecayRate()));
            // 扫描后可能发生画像重置；维护只能更新原记录，不能把已删除的依据重新插入。
            updatedCount += memoryEntryRepository.updateLongTermDecayIfUnchanged(entry);
        }
        return new MemoryDecayResult(updatedCount, 0);
    }

    private String resolveContent(MemoryEntry entry) {
        if (StringUtils.hasText(entry.getCompressedContent())) {
            return entry.getCompressedContent();
        }
        return entry.getContent();
    }

    private double roundWeight(double value) {
        return Math.round(Math.max(0D, value) * 10000D) / 10000D;
    }

    private int normalizeLimit(int limit) {
        int fallback = Math.max(1, memoryProperties.getRetentionBatchSize());
        return limit > 0 ? limit : fallback;
    }
}
