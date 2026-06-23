package com.ai.agent.tool;

import dev.langchain4j.agent.tool.DefaultToolExecutor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册与执行中心：从 {@link AgentTools} 的 {@code @Tool} 注解自动生成工具规格并按参数名绑定执行。
 *
 * <p>工具的定义（Schema）与执行（参数绑定、反射调用）均由 LangChain4j 注解体系驱动，
 * 新增工具只需在 {@link AgentTools} 上添加一个带 {@code @Tool}/{@code @P} 注解的方法。</p>
 *
 * @author data-agent
 */
@Component
public class AgentToolInvoker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentToolInvoker.class);
    private static final String UNKNOWN_TOOL_PREFIX = "未知工具: ";
    private static final String TOOL_ERROR_PREFIX = "工具执行失败: ";

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;

    public AgentToolInvoker(AgentTools agentTools) {
        List<ToolSpecification> specifications = new ArrayList<>();
        Map<String, ToolExecutor> executors = new LinkedHashMap<>();
        // 按方法名排序保证规格顺序确定性（getDeclaredMethods 顺序不稳定）
        List<Method> toolMethods = Arrays.stream(AgentTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .sorted(Comparator.comparing(Method::getName))
                .toList();
        for (Method method : toolMethods) {
            ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
            specifications.add(specification);
            executors.put(specification.name(), new DefaultToolExecutor(agentTools, method));
        }
        this.toolSpecifications = List.copyOf(specifications);
        this.toolExecutors = Map.copyOf(executors);
        LOGGER.info("Registered {} agent tools from @Tool annotations", toolSpecifications.size());
    }

    /**
     * 执行一次原生工具调用请求，参数按名称自动绑定。
     *
     * @param request 原生工具执行请求
     * @return 工具结果文本
     */
    public String invoke(ToolExecutionRequest request) {
        LOGGER.info("Tool call: {}({})", request.name(), request.arguments());
        ToolExecutor executor = toolExecutors.get(request.name());
        if (executor == null) {
            return UNKNOWN_TOOL_PREFIX + request.name();
        }
        try {
            return executor.execute(request, null);
        } catch (Exception e) {
            LOGGER.warn("Tool execution failed: {} - {}", request.name(), e.getMessage());
            return TOOL_ERROR_PREFIX + e.getMessage();
        }
    }

    /**
     * 返回全部工具规格列表。
     *
     * @return 工具规格
     */
    public List<ToolSpecification> buildToolSpecifications() {
        return toolSpecifications;
    }

    /**
     * 按工具名过滤，返回指定工具的规格列表。
     *
     * @param toolNames 要保留的工具方法名，为空时返回全部
     * @return 过滤后的工具规格
     */
    public List<ToolSpecification> buildToolSpecifications(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return toolSpecifications;
        }
        return toolSpecifications.stream()
                .filter(spec -> toolNames.contains(spec.name()))
                .toList();
    }

    /**
     * 返回所有已注册工具的名称和描述，供前端展示可选工具。
     *
     * @return 工具信息列表
     */
    public List<ToolInfo> getAllToolInfo() {
        return toolSpecifications.stream()
                .map(spec -> new ToolInfo(spec.name(), spec.description()))
                .toList();
    }

    /**
     * 工具摘要信息。
     *
     * @param name 工具名
     * @param description 工具描述
     */
    public record ToolInfo(String name, String description) {
    }
}
