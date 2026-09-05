package com.ai.agent;

import com.ai.agent.react.ReActLoopRunner;
import com.ai.agent.react.ReActExecutionResult;
import com.ai.agent.react.ReActStreamEventWriter;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import com.ai.agent.tool.governance.AgentToolInvocationContextFactory;
import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.memory.dto.MemoryContext;
import com.ai.mcp.McpModelService;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 基于 AgentProfile 配置驱动的通用 Agent 执行器。
 *
 * <p>根据 {@code executionMode} 选择执行策略：
 * <ul>
 *   <li>{@code chat} — 纯对话：system prompt + 记忆 + 用户问题 → 模型直答</li>
 *   <li>{@code react} — 工具循环：按 profile.tools 过滤工具规格 → ReAct 循环</li>
 * </ul>
 *
 * @author data-agent
 */
@Component
public class ConfigurableAgentExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurableAgentExecutor.class);

    private final McpModelService modelService;
    private final AgentPromptComposer promptComposer;
    private final AgentToolInvoker toolInvoker;
    private final ReActLoopRunner loopRunner;
    private final ReActStreamEventWriter streamEventWriter;
    private final MemoryContextPromptFormatter memoryFormatter;
    private final AgentToolInvocationContextFactory toolInvocationContextFactory;

    public ConfigurableAgentExecutor(McpModelService modelService,
            AgentPromptComposer promptComposer,
            AgentToolInvoker toolInvoker,
            ReActLoopRunner loopRunner,
            ReActStreamEventWriter streamEventWriter,
            MemoryContextPromptFormatter memoryFormatter,
            AgentToolInvocationContextFactory toolInvocationContextFactory) {
        this.modelService = modelService;
        this.promptComposer = promptComposer;
        this.toolInvoker = toolInvoker;
        this.loopRunner = loopRunner;
        this.streamEventWriter = streamEventWriter;
        this.memoryFormatter = memoryFormatter;
        this.toolInvocationContextFactory = toolInvocationContextFactory;
    }

    /**
     * 同步执行：根据 profile 的 executionMode 分发到 chat 或 react 路径。
     *
     * @param profile Agent 配置
     * @param request 分析请求
     * @param fileContent 文件内容，可为空
     * @param memoryContext 记忆上下文，可为空
     * @return 分析结果
     */
    public AnalysisResponse execute(AgentProfile profile, AnalysisRequest request,
            String fileContent, MemoryContext memoryContext) {
        if (profile.isChatMode()) {
            return executeChat(profile, request, fileContent, memoryContext);
        }
        return executeReAct(profile, request, fileContent, memoryContext);
    }

    /**
     * 流式执行：根据 profile 的 executionMode 分发到 chat 流式或 react 流式。
     *
     * @param profile Agent 配置
     * @param request 分析请求
     * @param fileContent 文件内容，可为空
     * @param memoryContext 记忆上下文，可为空
     * @param eventEmitter SSE 事件消费者
     * @return 完整分析响应
     */
    public AnalysisResponse executeStreaming(AgentProfile profile, AnalysisRequest request,
            String fileContent, MemoryContext memoryContext, Consumer<String> eventEmitter) {
        if (profile.isChatMode()) {
            AnalysisResponse response = executeChat(profile, request, fileContent, memoryContext);
            streamEventWriter.emitToken(eventEmitter, response.getResult());
            return response;
        }
        return executeReActStreaming(profile, request, fileContent, memoryContext, eventEmitter);
    }

    private AnalysisResponse executeChat(AgentProfile profile, AnalysisRequest request,
            String fileContent, MemoryContext memoryContext) {
        String dataContent = fileContent;
        if (memoryFormatter.hasMemory(memoryContext)) {
            dataContent = promptComposer.mergeMemoryContext(dataContent, memoryContext);
        }
        String prompt = promptComposer.buildPrompt(profile, request.getQuestion(), dataContent);
        String modelId = StringUtils.hasText(request.getModelId()) ? request.getModelId()
                : profile.getModelId();
        LOGGER.info("ConfigurableAgent chat mode | agent={} | model={}", profile.getName(), modelId);
        String result = modelService.callModel(prompt, modelId);
        return AnalysisResponse.ok(result);
    }

    private AnalysisResponse executeReAct(AgentProfile profile, AnalysisRequest request,
            String fileContent, MemoryContext memoryContext) {
        List<ChatMessage> messages = buildMessages(profile, request, fileContent, memoryContext);
        List<ToolSpecification> toolSpecs = toolInvoker.buildToolSpecifications(profile.getToolList());
        AgentToolInvocationContext toolContext = toolInvocationContextFactory.profile(profile.getAgentId(), toolSpecs);
        String modelId = StringUtils.hasText(request.getModelId()) ? request.getModelId()
                : profile.getModelId();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "";
        LOGGER.info("ConfigurableAgent react mode | agent={} | model={} | tools={}",
                profile.getName(), modelId, toolSpecs.size());

        List<AnalysisResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
        ReActExecutionResult result = loopRunner.run(messages, toolSpecs, modelId, sessionId,
                request.getQuestion(), thinkingSteps, toolContext);
        AnalysisResponse response = AnalysisResponse.ok(result.answer());
        response.setThinkingSteps(thinkingSteps);
        return response;
    }

    private AnalysisResponse executeReActStreaming(AgentProfile profile, AnalysisRequest request,
            String fileContent, MemoryContext memoryContext, Consumer<String> eventEmitter) {
        List<ChatMessage> messages = buildMessages(profile, request, fileContent, memoryContext);
        List<ToolSpecification> toolSpecs = toolInvoker.buildToolSpecifications(profile.getToolList());
        AgentToolInvocationContext toolContext = toolInvocationContextFactory.profile(profile.getAgentId(), toolSpecs);
        String modelId = StringUtils.hasText(request.getModelId()) ? request.getModelId()
                : profile.getModelId();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "";
        LOGGER.info("ConfigurableAgent react-streaming | agent={} | model={} | tools={}",
                profile.getName(), modelId, toolSpecs.size());
        ReActExecutionResult result = loopRunner.runStreaming(messages, toolSpecs, modelId, sessionId,
                request.getQuestion(), eventEmitter, toolContext);
        AnalysisResponse response = result.success()
                ? AnalysisResponse.ok(result.answer())
                : AnalysisResponse.fail(result.answer());
        response.setSkillUsed("agent:" + profile.getName());
        return response;
    }

    private List<ChatMessage> buildMessages(AgentProfile profile, AnalysisRequest request,
            String fileContent, MemoryContext memoryContext) {
        List<ChatMessage> messages = new ArrayList<>();
        // react 模式：用户配置的角色 + 框架基座（工具调用纪律、防幻觉），框架规则与默认 Agent 共享，
        // 用户无需自己在 prompt 里写"直接发起原生工具调用"这类规则。
        String systemPrompt = AgentSystemPrompts.compose(profile.getSystemPrompt());
        messages.add(new SystemMessage(systemPrompt));

        StringBuilder userContent = new StringBuilder(request.getQuestion());
        if (StringUtils.hasText(fileContent)) {
            userContent.append("\n\n## 参考数据\n").append(fileContent);
        }
        if (memoryFormatter.hasMemory(memoryContext)) {
            userContent.append("\n\n").append(memoryFormatter.toSection(memoryContext));
        }
        messages.add(new UserMessage(userContent.toString()));
        return messages;
    }
}
