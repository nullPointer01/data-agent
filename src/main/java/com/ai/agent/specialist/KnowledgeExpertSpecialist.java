package com.ai.agent.specialist;

import com.ai.knowledge.dto.KnowledgeSearchResponse;
import com.ai.knowledge.dto.KnowledgeSearchResult;
import com.ai.mcp.McpModelService;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisResponse;
import com.ai.service.knowledge.KnowledgeService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import com.ai.agent.AgentExecutionRequest;
import com.ai.agent.AgentPromptComposer;
import com.ai.agent.AgentType;

/**
 * 知识检索专家，从知识库中检索相关内容并生成带引用的回答。
 *
 * <p>执行时先调用 {@link KnowledgeService} 搜索知识库，
 * 将检索结果格式化为带引用编号的参考文本，调用模型生成回答，
 * 最后从回答中提取引用标注并记录到执行元数据。</p>
 *
 * @author data-agent
 */
@Component
public class KnowledgeExpertSpecialist implements AgentSpecialist {

    private static final int DEFAULT_TOP_K = 5;
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(R\\d+)]");

    private final KnowledgeService knowledgeService;
    private final McpModelService modelService;
    private final AgentPromptComposer promptComposer;

    /**
     * 构造知识检索专家。
     *
     * @param knowledgeService 知识服务
     * @param modelService 模型调用服务
     * @param promptComposer Prompt 组装器
     */
    public KnowledgeExpertSpecialist(KnowledgeService knowledgeService,
            McpModelService modelService,
            AgentPromptComposer promptComposer) {
        this.knowledgeService = knowledgeService;
        this.modelService = modelService;
        this.promptComposer = promptComposer;
    }

    @Override
    public AgentType type() {
        return AgentType.KNOWLEDGE;
    }

    /**
     * 执行知识检索并生成带引用的回答。
     *
     * <p>处理流程：
     * <ol>
     *   <li>搜索知识库，获取 Top-K 结果</li>
     *   <li>无结果或搜索失败时返回错误</li>
     *   <li>格式化引用文本，如 "[R1 | knowledge | knowledge-1"</li>
     *   <li>调用模型生成基于知识的回答</li>
     *   <li>提取回答中的引用标注 [R1]、[R2] 等</li>
     *   <li>在执行元数据中记录引用列表</li>
     * </ol>
     * </p>
     *
     * @param request 执行请求
     * @return 带引用的知识回答
     */
    @Override
    public AnalysisResponse execute(AgentExecutionRequest request) {
        String question = request.request().getQuestion();

        // 搜索知识库
        KnowledgeSearchResponse searchResponse = knowledgeService.searchKnowledge(question, DEFAULT_TOP_K);

        // 检查搜索结果
        if (!searchResponse.success()) {
            return AnalysisResponse.fail(searchResponse.message() != null
                    ? searchResponse.message() : "知识检索失败");
        }

        List<KnowledgeSearchResult> results = searchResponse.results();
        if (results == null || results.isEmpty()) {
            return AnalysisResponse.fail(searchResponse.message() != null
                    ? searchResponse.message() : "未找到相关知识");
        }

        // 格式化引用文本
        StringBuilder referencesBuilder = new StringBuilder();
        for (KnowledgeSearchResult result : results) {
            referencesBuilder.append("[")
                    .append(result.referenceId())
                    .append(" | ")
                    .append(result.sourceType())
                    .append(" | ")
                    .append(result.sourceId())
                    .append("]\n")
                    .append(result.content())
                    .append("\n\n");
        }

        // 构建包含知识上下文的 Prompt
        String knowledgeContext = referencesBuilder.toString().trim();
        String prompt = "请基于以下知识回答用户问题，并在回答中标注引用来源。\n\n"
                + "## 知识参考\n" + knowledgeContext
                + "\n\n## 用户问题\n" + question;

        // 调用模型
        String modelResult = modelService.callModel(prompt, null);

        // 提取引用
        List<String> citations = extractCitations(modelResult);

        // 构建响应
        AnalysisResponse response = AnalysisResponse.ok(modelResult);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("citations", citations);
        response.setExecutionMetadata(metadata);
        return response;
    }

    /**
     * 从模型回答中提取引用标注。
     *
     * @param text 模型回答文本
     * @return 引用编号列表，如 ["R1", "R2"]
     */
    private List<String> extractCitations(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<String> citations = new ArrayList<>();
        Matcher matcher = CITATION_PATTERN.matcher(text);
        while (matcher.find()) {
            String citation = matcher.group(1);
            if (!citations.contains(citation)) {
                citations.add(citation);
            }
        }
        return citations;
    }
}
