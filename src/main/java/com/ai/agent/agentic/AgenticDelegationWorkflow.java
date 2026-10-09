package com.ai.agent.agentic;

import com.ai.agent.AgentExecutionContext;
import com.ai.agent.ConfigurableAgentExecutor;
import com.ai.agent.runtime.AgentRunScope;
import com.ai.agent.runtime.PersonalAgentExecutionModeResolver;
import com.ai.agent.runtime.planning.RequestExecutionPlan;
import com.ai.memory.dto.MemoryContext;
import com.ai.logging.StructuredLogger;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisResponse;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.service.V;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * 专家委派的框架顺序工作流：规划 → 执行。父 Agent 仍通过 Tool Calling 选择绑定专家。
 * AgenticScope 只传递本次节点状态，根 Run 继续负责身份、预算、取消和审批。
 * 使用普通 Java 节点适配已有受治理服务；不会额外创建一套无权限校验的模型客户端。
 */
@Component
public class AgenticDelegationWorkflow {

    private final Delegation workflow;

    public AgenticDelegationWorkflow(ObjectProvider<PersonalAgentExecutionModeResolver> planner,
                                     ObjectProvider<ConfigurableAgentExecutor> executor, StructuredLogger logger) {
        workflow = AgenticServices.sequenceBuilder(Delegation.class)
                .listener(new AgentLifecycleListener(logger, "DELEGATION", Map.of()))
                .subAgents(new PlanningStep(planner), new ExecutionStep(executor))
                .outputKey("response")
                .build();
    }

    /**
     * 只由完成授权和防环路检查的委派入口调用。
     * 无 @MemoryId，因此框架每次创建临时 AgenticScope，结束后清理，不跨用户复用状态。
     */
    public AnalysisResponse execute(AgentProfile profile, AgentExecutionContext context) {
        AgentRunScope.requireCurrent().control().ensureActive();
        try {
            return workflow.execute(profile, context);
        } catch (AgentInvocationException failure) {
            // 框架反射会包装节点异常；恢复原有预算/取消/业务拒绝类型，不将失败变成成功文本。
            Throwable cause = failure;
            while ((cause instanceof AgentInvocationException || cause instanceof InvocationTargetException)
                    && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw failure;
        }
    }

    /**
     * @V 把输入写入本次 AgenticScope；这是工作流接口，不是直接发给模型的提示词。
     */
    public interface Delegation {
        @Agent("先规划已授权专家的任务，再执行该计划")
        AnalysisResponse execute(@V("profile") AgentProfile profile,
                                 @V("context") AgentExecutionContext context);
    }

    /**
     * 第一步产出 plan，仍使用现有语义 Router 与根 Run 的模型预算。
     */
    public record PlanningStep(ObjectProvider<PersonalAgentExecutionModeResolver> planner) {
        @Agent(value = "规划专家任务所需的上下文和获准能力", outputKey = "plan")
        public RequestExecutionPlan plan(@V("profile") AgentProfile profile,
                                         @V("context") AgentExecutionContext context) {
            AgentRunScope.requireCurrent().control().ensureActive();
            return planner.getObject().plan(profile, context);
        }
    }

    /**
     * 第二步的 @V("plan") 自动绑定第一步 outputKey；框架负责先后依赖与失败短路。
     */
    public record ExecutionStep(ObjectProvider<ConfigurableAgentExecutor> executor) {
        @Agent(value = "按已校验计划执行专家任务", outputKey = "response")
        public AnalysisResponse execute(@V("profile") AgentProfile profile,
                                        @V("context") AgentExecutionContext context, @V("plan") RequestExecutionPlan plan) {
            AgentRunScope.requireCurrent().control().ensureActive();
            return executor.getObject().execute(profile, context.getRequest(), context.getFileContent(),
                    MemoryContext.empty(), plan.mode(), plan);
        }
    }
}
