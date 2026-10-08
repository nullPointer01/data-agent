package com.ai.rag.indexing;

import com.ai.rag.fulltext.FullTextDocument;
import com.ai.rag.fulltext.FullTextIndexService;
import com.ai.service.VectorMemoryService;
import com.ai.vector.TextChunker;
import com.ai.vector.VectorChunk;
import com.ai.vector.VectorDocumentTypes;
import org.springframework.stereotype.Service;

import java.util.List;

/** 一次分块同时驱动向量与全文；失败传播给持久任务重放，不宣称跨存储事务。 */
@Service
public class RagDocumentIndexer {
    private final TextChunker chunker;
    private final VectorMemoryService vectors;
    private final FullTextIndexService fullText;

    public RagDocumentIndexer(TextChunker chunker, VectorMemoryService vectors, FullTextIndexService fullText) {
        this.chunker = chunker;
        this.vectors = vectors;
        this.fullText = fullText;
    }

    /** 读取最新正文后替换同一来源全部分块；单边失败由原持久任务重放补齐。 */
    public void replace(String type, String sourceId, String title, String content, String tenant, String user) {
        if ((!VectorDocumentTypes.FILE.equals(type) && !VectorDocumentTypes.KNOWLEDGE.equals(type))
                || sourceId == null || sourceId.isBlank() || tenant == null || tenant.isBlank()
                || user == null || user.isBlank()) {
            throw new IllegalArgumentException("双索引需要合法来源类型和完整身份");
        }
        String body = content == null ? "" : content;
        List<VectorChunk> chunks = chunker.split(sourceId, body);
        // 先检查 ES 契约并清理旧全文，防止换分块后留下失效旧块。
        fullText.deleteBySource(type, sourceId, tenant);
        vectors.replaceDocumentChunks(type, sourceId, title, chunks, tenant, user);
        fullText.index(chunks.stream().map(chunk -> new FullTextDocument(type, sourceId, chunk.id(), tenant, user,
                title, chunk.text(), chunk.metadata(), parentContext(chunk, body))).toList());
    }

    /** 来源删除也清理两边索引；来源 ID 是业务表唯一主键，租户过滤不可省略。 */
    public void remove(String type, String sourceId, String tenant) {
        fullText.deleteBySource(type, sourceId, tenant);
        vectors.removeFromStore(type, sourceId, tenant);
    }

    private static String parentContext(VectorChunk chunk, String source) {
        if (chunk.metadata().hasParent()) return "";
        return source.substring(chunk.parentCharStart(), chunk.parentCharEnd()).trim();
    }
}
