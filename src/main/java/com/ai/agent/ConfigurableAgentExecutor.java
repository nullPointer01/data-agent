package com.ai.agent;

import com.ai.agent.capability.AgentCapabilityBindingSnapshot;
import com.ai.agent.capability.AgentCapabilityScope;
import com.ai.agent.capability.AgentCapabilityService;
import com.ai.agent.react.ReActLoopRunner;
import com.ai.agent.react.ReActExecutionResult;
import com.ai.agent.react.ReActStreamEventWriter;
import com.ai.agent.runtime.AgentExecutionMode;
import com.ai.agent.runtime.AgentRunContext;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.planning.RequestExecutionPlan;
import com.ai.agent.tool.AgentToolInvoker;
import com.ai.agent.tool.AgentTools;
import com.ai.agent.tool.governance.AgentToolInvocationContext;
import com.ai.agent.tool.governance.AgentToolInvocationContextFactory;
import com.ai.memory.MemoryContextPromptFormatter;
import com.ai.memory.dto.MemoryContext;
import com.ai.mcp.McpModelService;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.rag.RagRetrievalService;
import com.ai.rag.dto.RagContextResponse;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Consumer;

/**
 * 基于 AgentProfile 配置驱动的通用 Agent 执行器。
 *
 * <p>根据统一 Runtime 已解析的 {@code executionMode} 选择执行策略：
 * <ul>
 *   <li>{@code chat} — 直接回答：system prompt + 个人知识 + 记忆 + 用户问题</li>
 *   <li>{@code react} — 仅暴露当前能力快照允许的 Tool/Skill</li>
 *   <li>{@code orchestrated} — 在 ReAct 基础上允许受控子 Agent 委派</li>
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
    private final RagRetrievalService ragRetrievalService;
    private final AgentCapabilityService capabilityService;

    public ConfigurableAgentExecutor(McpModelService modelService,
            AgentPromptComposer promptComposer,
            AgentToolInvoker toolInvoker,
            ReActLoopRunner loopRunner,
            ReActStreamEventWriter streamEventWriter,
            MemoryContextPromptFormatter memoryFormatter,
            AgentToolInvocationContextFactory toolInvocationContextFactory,
            RagRetrievalService ragRetrievalService,
            AgentCapabilityService capabilityService) {
        this.modelService = modelService;
        this.promptComposer = promptComposer;
        this.toolInvoker = toolInvoker;
        this.loopRunner = loopRunner;
        this.streamEventWriter = streamEventWriter;
        this.memoryFormatter = memoryFormatter;
        this.toolInvocationContextFactory = toolInvocationContextFactory;
        this.ragRetrievalService = ragRetrievalService;
        this.capabilityService = capabilityService;
    }

    /**
     * 按请求规划层固化的资源需求执行。
     */
    public AnalysisResponse execute(AgentProfile profile, AnalysisRequest request,
            String fileContent, MemoryContext memoryContext, AgentExecutionMode mode,
            RequestExecutionPlan requestPlan) {
        requireActiveRun(profile);
        RequestExecutionPlan effectivePlan = requireResolvedPlan(profile, request, mode, requestPlan);
        MemoryContext effectiveMemory = memoryContext == null ? MemoryContext.empty() : memoryContext;
        AgentCapabilityBindingSnapshot snapshot = capabilityService.resolveRuntimeBindings(profile, mode);
        logCapabilitySnapshot(profile, snapshot);
        AnalysisResponse response = AgentCapabilityScope.call(snapshot,
                () -> executeInCapabilityScope(
                        profile, request, fileContent, effectiveMemory, mode, effectivePlan));
        return attachPlanMetadata(response, effectivePlan);
    }

    private AnalysisResponse executeInCapabilityScope(AgentProfile profile, AnalysisRequest request,
            String fileContent, MemoryContext memoryContext, AgentExecutionMode mode,
            RequestExecutionPlan requestPlan) {
        if (!requestPlan.modelRequired()) {
            AnalysisResponse response = AnalysisResponse.ok(requestPlan.localResponse());
            response.setModelUsed("local-harness");
            return response;
        }
        RagContextResponse ragContext = requestPlan.ragRequired()
                ? retrieveRagContext(request) : RagContextResponse.empty();
        return switch (mode) {
            case CHAT -> executeChat(profile, request, fileContent, memoryContext, ragContext);
            case REACT -> executeReAct(
                    profile, request, fileContent, memoryContext, ragContext, requestPlan);
            case ORCHESTRATED -> executeReAct(
                    profile, request, fileContent, memoryContext, ragContext, requestPlan);
        };
    }

    /**
     * 按请求规划层固化的资源需求流式执行。
     */
    public AnalysisResponse executeStreaming(AgentProfile profile, AnalysisRequest request,
            String fileContent, MemoryContext memoryContext, Consumer<String> eventEmitter,
            AgentExecutionMode mode, RequestExecutionPlan requestPlan) {
        requireActiveRun(profile);
        RequestExecutionPlan effectivePlan = requireResolvedPlan(profile, request, mode, requestPlan);
        if (eventEmitter == null) {
            throw new IllegalArgumentException("流式 Agent 事件接收端不能为空");
        }
        MemoryContext effectiveMemory = memoryContext == null ? MemoryContext.empty() : memoryContext;
        AgentCapabilityBindingSnapshot snapshot = capabilityService.resolveRuntimeBindings(profile, mode);
        logCapabilitySnapshot(profile, snapshot);
        AnalysisResponse response = AgentCapabilityScope.call(snapshot,
                () -> executeStreamingInCapabilityScope(
                        profile, request, fileContent, effectiveMemory, eventEmitter, mode, effectivePlan));
        return attachPlanMetadata(response, effectivePlan);
    }

    private AnalysisResponse executeStreamingInCapabilityScope(AgentProfile profile,
            AnalysisRequest request,
            String fileContent,
            MemoryContext memoryContext,
            Consumer<String> eventEmitter,
            AgentExecutionMode mode,
            RequestExecutionPlan requestPlan) {
        if (!requestPlan.modelRequired()) {
            AnalysisResponse response = AnalysisResponse.ok(requestPlan.localResponse());
            response.setModelUsed("local-harness");
            streamEventWriter.emitToken(eventEmitter, response.getResult());
            return response;
        }
        RagContextResponse ragContext = requestPlan.ragRequired()
                ? retrieveRagContext(request) : RagContextResponse.empty();
        if (requestPlan.ragRequired()) {
            streamEventWriter.emitRagContext(eventEmitter, ragContext);
        }
        if (mode == AgentExecutionMode.CHAT) {
            AnalysisResponse response = executeChat(profile, request, fileContent, memoryContext, ragContext);
            streamEventWriter.emitToken(eventEmitter, response.getResult());
            return response;
        }
        return executeReActStreaming(
                profile, request, fileContent, memoryContext, ragContext, eventEmitter, requestPlan);
    }

    private AnalysisResponse executeChat(AgentProfile profile, AnalysisRequest request,
            String fileContent, MemoryContext memoryContext, RagContextResponse ragContext) {
        String dataContent = mergeRagContext(fileContent, ragContext);
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
            String fileContent, MemoryContext memoryContext, RagContextResponse ragContext,
            RequestExecutionPlan requestPlan) {
        List<ChatMessage> messages = buildMessages(
                profile, request, fileContent, memoryContext, ragContext, requestPlan);
        List<ToolSpecification> toolSpecs = scopedToolSpecifications(requestPlan);
        AgentToolInvocationContext toolContext = toolInvocationContextFactory.profile(profile.getAgentId(), toolSpecs);
        String modelId = StringUtils.hasText(request.getModelId()) ? request.getModelId()
                : profile.getModelId();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "";
        LOGGER.info("ConfigurableAgent react mode | agent={} | model={} | tools={}",
                profile.getName(), modelId, toolSpecs.size());

        List<AnalysisResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
        ReActExecutionResult result = loopRunner.run(messages, toolSpecs, modelId, sessionId,
                request.getQuestion(), thinkingSteps, toolContext, requestPlan.initialToolChoice());
        AnalysisResponse response = result.success()
                ? AnalysisResponse.ok(result.answer())
                : AnalysisResponse.fail(result.answer());
        response.setApprovalId(result.approvalId());
        response.setThinkingSteps(thinkingSteps);
        return response;
    }

    private AnalysisResponse executeReActStreaming(AgentProfile profile, AnalysisRequest request,
            String fileContent, MemoryContext memoryContext, RagContextResponse ragContext,
            Consumer<String> eventEmitter, RequestExecutionPlan requestPlan) {
        List<ChatMessage> messages = buildMessages(
                profile, request, fileContent, memoryContext, ragContext, requestPlan);
        List<ToolSpecification> toolSpecs = scopedToolSpecifications(requestPlan);
        AgentToolInvocationContext toolContext = toolInvocationContextFactory.profile(profile.getAgentId(), toolSpecs);
        String modelId = StringUtils.hasText(request.getModelId()) ? request.getModelId()
                : profile.getModelId();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "";
        LOGGER.info("ConfigurableAgent react-streaming | agent={} | model={} | tools={}",
                profile.getName(), modelId, toolSpecs.size());
        ReActExecutionResult result = loopRunner.runStreaming(messages, toolSpecs, modelId, sessionId,
                request.getQuestion(), eventEmitter, toolContext, requestPlan.initialToolChoice());
        AnalysisResponse response = result.success()
                ? AnalysisResponse.ok(result.answer())
                : AnalysisResponse.fail(result.answer());
        response.setSkillUsed("agent:" + profile.getName());
        return response;
    }

    private List<ToolSpecification> scopedToolSpecifications(RequestExecutionPlan requestPlan) {
        AgentCapabilityBindingSnapshot snapshot = AgentCapabilityScope.current()
                .orElseThrow(() -> new IllegalStateException("Agent 能力作用域未建立"));
        Set<String> allowedTools = new LinkedHashSet<>(snapshot.toolNames());
        if (!requestPlan.toolSelectionFallback()) {
            allowedTools.retainAll(requestPlan.candidateTools());
        }
        boolean skillAdaptersSelected = requestPlan.toolSelectionFallback()
                || requestPlan.candidateTools().contains(AgentTools.LIST_AVAILABLE_SKILLS_TOOL)
                || requestPlan.candidateTools().contains(AgentTools.USE_SKILL_TOOL);
        if (skillAdaptersSelected && !snapshot.skillIds().isEmpty()) {
            allowedTools.add(AgentTools.LIST_AVAILABLE_SKILLS_TOOL);
            allowedTools.add(AgentTools.USE_SKILL_TOOL);
        }
        boolean delegationSelected = requestPlan.toolSelectionFallback()
                || requestPlan.candidateTools().contains(AgentTools.DELEGATE_TO_AGENT_TOOL);
        if (delegationSelected && !snapshot.subAgentIds().isEmpty()) {
            allowedTools.add(AgentTools.DELEGATE_TO_AGENT_TOOL);
        }
        return toolInvoker.buildExactToolSpecifications(allowedTools);
    }

    private void logCapabilitySnapshot(AgentProfile profile, AgentCapabilityBindingSnapshot snapshot) {
        LOGGER.info("ConfigurableAgent capability scope | agent={} | mode={} | "
                        + "tools={} | skills={} | subAgents={} | excluded={}",
                profile.getName(), snapshot.mode(),
                snapshot.toolNames().size(), snapshot.skillIds().size(), snapshot.subAgentIds().size(),
                snapshot.exclusions().size());
    }

    private List<ChatMessage> buildMessages(AgentProfile profile, AnalysisRequest request,
            String fileContent, MemoryContext memoryContext, RagContextResponse ragContext,
            RequestExecutionPlan requestPlan) {
        List<ChatMessage> messages = new ArrayList<>();
        // react 模式：用户配置的角色 + 框架基座（工具调用纪律、防幻觉），框架规则与默认 Agent 共享，
        // 用户无需自己在 prompt 里写"直接发起原生工具调用"这类规则。
        String systemPrompt = AgentSystemPrompts.compose(profile.getSystemPrompt())
                + boundSkillInstructions(requestPlan)
                + boundSubAgentInstructions(requestPlan);
        messages.add(new SystemMessage(systemPrompt));

        StringBuilder userContent = new StringBuilder(request.getQuestion());
        if (ragContext != null && ragContext.hasContext()) {
            userContent.append("\n\n## 个人知识库证据\n")
                    .append("请优先依据以下资料回答，并在结论中保留 [R1] 形式的引用编号。\n")
                    .append(ragContext.getContext());
        }
        if (StringUtils.hasText(fileContent)) {
            userContent.append("\n\n## 参考数据\n").append(fileContent);
        }
        if (memoryFormatter.hasMemory(memoryContext)) {
            userContent.append("\n\n").append(memoryFormatter.toSection(memoryContext));
        }
        messages.add(new UserMessage(userContent.toString()));
        return messages;
    }

    private String boundSkillInstructions(RequestExecutionPlan requestPlan) {
        AgentCapabilityBindingSnapshot snapshot = AgentCapabilityScope.current()
                .orElseThrow(() -> new IllegalStateException("Agent 能力作用域未建立"));
        boolean selected = requestPlan.toolSelectionFallback()
                || requestPlan.candidateTools().contains(AgentTools.LIST_AVAILABLE_SKILLS_TOOL)
                || requestPlan.candidateTools().contains(AgentTools.USE_SKILL_TOOL);
        if (!selected || snapshot.skillIds().isEmpty()) {
            return "";
        }
        String stableIds = String.join(", ", snapshot.skillIds());
        return "\n\n## 已绑定 Skills\n"
                + "当前 Agent 可使用的 Skill 稳定 ID: " + stableIds + "。\n"
                + "需要 Skill 时，先调用 listAvailableSkills 确认能力，再调用 useSkill；"
                + "skillId 必须使用列表中的精确稳定 ID，不得猜测或改写。";
    }

    private String boundSubAgentInstructions(RequestExecutionPlan requestPlan) {
        AgentCapabilityBindingSnapshot snapshot = AgentCapabilityScope.current()
                .orElseThrow(() -> new IllegalStateException("Agent 能力作用域未建立"));
        boolean selected = requestPlan.toolSelectionFallback()
                || requestPlan.candidateTools().contains(AgentTools.DELEGATE_TO_AGENT_TOOL);
        if (!selected || snapshot.subAgentIds().isEmpty()) {
            return "";
        }
        String stableIds = String.join(", ", snapshot.subAgentIds());
        return "\n\n## 已绑定子 Agents\n"
                + "当前 Agent 可委派的子 Agent 稳定 ID: " + stableIds + "。\n"
                + "仅当任务可明确拆给某个子 Agent 时调用 delegateToAgent；"
                + "agentId 必须使用上述精确稳定 ID，并提供边界清楚的 task。";
    }

    private RagContextResponse retrieveRagContext(AnalysisRequest request) {
        if (request == null || !StringUtils.hasText(request.getQuestion())) {
            return RagContextResponse.empty();
        }
        try {
            return ragRetrievalService.retrieve(request.getQuestion());
        } catch (Exception e) {
            LOGGER.warn("个人 Agent 知识检索失败，降级为无知识上下文: {}", e.getMessage());
            return RagContextResponse.unavailable();
        }
    }

    private String mergeRagContext(String fileContent, RagContextResponse ragContext) {
        if (ragContext == null || !ragContext.hasContext()) {
            return fileContent;
        }
        String knowledgeSection = "## 个人知识库证据\n"
                + "请优先依据以下资料回答，并在结论中保留 [R1] 形式的引用编号。\n"
                + ragContext.getContext();
        if (!StringUtils.hasText(fileContent)) {
            return knowledgeSection;
        }
        return fileContent + "\n\n" + knowledgeSection;
    }

    private RequestExecutionPlan requireResolvedPlan(AgentProfile profile,
            AnalysisRequest request,
            AgentExecutionMode mode,
            RequestExecutionPlan requestPlan) {
        if (profile == null || request == null || mode == null || requestPlan == null) {
            throw new IllegalArgumentException("配置化 Agent 必须使用完整的已解析执行参数");
        }
        if (requestPlan.mode() != mode) {
            throw new IllegalArgumentException("请求计划与执行模式不一致");
        }
        return requestPlan;
    }

    private AgentRunContext requireActiveRun(AgentProfile profile) {
        AgentRunContext runContext = AgentRunScope.requireCurrent();
        if (profile != null && !Objects.equals(runContext.tenantId(), profile.getTenantId())) {
            throw new SecurityException("AgentProfile 与当前 Agent Run 租户不一致");
        }
        return runContext;
    }

    private AnalysisResponse attachPlanMetadata(AnalysisResponse response, RequestExecutionPlan requestPlan) {
        if (response == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (response.getExecutionMetadata() != null) {
            metadata.putAll(response.getExecutionMetadata());
        }
        metadata.put("requestPlan", requestPlan.toEvidence());
        response.setExecutionMetadata(metadata);
        return response;
    }
}
