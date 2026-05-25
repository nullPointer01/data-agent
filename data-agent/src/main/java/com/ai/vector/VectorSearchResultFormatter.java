package com.ai.vector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将向量检索命中结果格式化成模型可引用的文本。
 *
 * @author data-agent
 */
@Component
public class VectorSearchResultFormatter {

    private static final String REFERENCE_PREFIX = "R";

    /**
     * 格式化单条检索结果，保留来源、章节和字符范围。
     *
     * @param match 检索命中
     * @param index 从 1 开始的结果序号
     * @return 可直接注入提示词的结果文本
     */
    public String format(EmbeddingMatch<TextSegment> match, int index) {
        TextSegment segment = match.embedded();
        String type = segment.metadata().getString(VectorMetadataKeys.TYPE);
        String sourceId = resolveSourceId(segment);
        String location = formatLocation(segment);
        return "[%s%d | %s | %s%s | 相关度:%s] %s".formatted(
                REFERENCE_PREFIX, index, type, sourceId, location, String.format("%.2f", match.score()),
                segment.text());
    }

    private String resolveSourceId(TextSegment segment) {
        String sourceId = segment.metadata().getString(VectorMetadataKeys.SOURCE_ID);
        if (sourceId == null || sourceId.isBlank()) {
            return segment.metadata().getString(VectorMetadataKeys.ID);
        }
        return sourceId;
    }

    private String formatLocation(TextSegment segment) {
        String sectionPath = segment.metadata().getString(VectorMetadataKeys.SECTION_PATH);
        int charStart = parseInt(segment.metadata().getString(VectorMetadataKeys.CHAR_START));
        int charEnd = parseInt(segment.metadata().getString(VectorMetadataKeys.CHAR_END));
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(sectionPath)) {
            builder.append(" | ").append(sectionPath);
        }
        if (charEnd > charStart) {
            builder.append(" | 字符 ").append(charStart).append("-").append(charEnd);
        }
        appendStructureFlag(builder, segment, VectorMetadataKeys.CONTAINS_TABLE, "表格");
        appendStructureFlag(builder, segment, VectorMetadataKeys.CONTAINS_CODE, "代码");
        appendStructureFlag(builder, segment, VectorMetadataKeys.CONTAINS_LIST, "列表");
        return builder.toString();
    }

    private void appendStructureFlag(StringBuilder builder, TextSegment segment, String key, String label) {
        if (Boolean.parseBoolean(segment.metadata().getString(key))) {
            builder.append(" | ").append(label);
        }
    }

    private int parseInt(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
