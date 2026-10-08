package com.ai.rag;

import com.ai.mcp.McpModelService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.DefaultQueryTransformer;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** 官方查询变换适配：明确问题直通，有历史才补全，扩展显式开启且有界。 */
@Component
public class RagQueryTransformer implements QueryTransformer {
    private final RagProperties properties;
    private final McpModelService modelService;
    private final QueryTransformer direct = new DefaultQueryTransformer();

    public RagQueryTransformer(RagProperties properties, McpModelService modelService) {
        this.properties = properties;
        this.modelService = modelService;
    }

    @Override
    public Collection<Query> transform(Query query) {
        var config = properties.getQuery();
        String text = requireQuery(query.text(), config.getMaxQueryChars());
        Metadata metadata = query.metadata() == null ? Metadata.from(UserMessage.from(text), null, List.of())
                : query.metadata();
        Query input = Query.from(text, metadata);
        Collection<Query> primary = direct.transform(input);
        if (config.isCompressionEnabled() && metadata.chatMemory() != null && !metadata.chatMemory().isEmpty()) {
            primary = CompressingQueryTransformer.builder()
                    .chatModel(modelService.chatModel(config.getModelId())).build().transform(input);
        }
        Query standalone = primary.iterator().next();
        String standaloneText = requireQuery(standalone.text(), config.getMaxQueryChars());
        LinkedHashSet<String> texts = new LinkedHashSet<>();
        texts.add(standaloneText);
        if (config.isExpansionEnabled()) {
            var expanded = ExpandingQueryTransformer.builder()
                    .chatModel(modelService.chatModel(config.getModelId()))
                    .n(config.getMaxQueries() - 1).build().transform(Query.from(standaloneText, metadata));
            for (Query variant : expanded) {
                if (variant.text() != null && !variant.text().isBlank()) {
                    texts.add(requireQuery(variant.text(), config.getMaxQueryChars()));
                }
                if (texts.size() == config.getMaxQueries()) break;
            }
        }
        // 保留主查询，变体去重后受服务端数量限制；变体不携带或决定业务身份。
        return texts.stream().map(value -> Query.from(value, metadata)).toList();
    }

    public RagQueryAnalysis analyze(String query) {
        return analyze(Query.from(query));
    }

    /** 旧响应合同只投影主查询与变体，删除关键词词表和领域分类。 */
    public RagQueryAnalysis analyze(Query query) {
        List<String> variants = transform(query).stream().map(Query::text).toList();
        return new RagQueryAnalysis(query.text(), variants.get(0), List.of(), "GENERAL", variants);
    }

    private static String requireQuery(String query, int maxChars) {
        if (query == null || query.isBlank() || query.length() > maxChars) {
            throw new IllegalArgumentException("检索问题不能为空且不得超过 " + maxChars + " 字符");
        }
        return query.strip();
    }
}
