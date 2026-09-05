package com.ai.agent.react;

import com.ai.model.AnalysisRequest;
import com.ai.model.ConversationSession;
import org.springframework.stereotype.Component;
import com.ai.agent.TaskClassification;
import com.ai.agent.TaskComplexityClassifier;

/**
 * 判断请求是否可以跳过完整 ReAct 循环，走快速直答路径。
 *
 * <p>策略保持保守：只有简单问候和纯概念解释进入快速路径。
 * 包含业务分析、文件、数据源或有历史上下文的请求都保留在推理循环中。</p>
 *
 * @author data-agent
 */
@Component
public class ReActFastPathDecider {

    private final TaskComplexityClassifier classifier;

    /**
     * 构造快速路径判定器。
     *
     * @param classifier 任务复杂度分类器
     */
    public ReActFastPathDecider(TaskComplexityClassifier classifier) {
        this.classifier = classifier;
    }

    /**
     * 判断请求是否应使用快速路径直答。
     *
     * @param request 分析请求
     * @param fileContent 可选文件内容
     * @param session 当前会话
     * @return 应走快速路径返回 true
     */
    public boolean shouldUseFastPath(AnalysisRequest request, String fileContent, ConversationSession session) {
        TaskClassification classification = classifier.classify(request, fileContent, session);
        return classification.fastPathAllowed();
    }
}
