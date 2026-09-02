package com.ai.graphdemo;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 极简状态图引擎 —— LangGraph 风格的核心，约百行说清"图"到底是什么。
 *
 * <p>它只由三样东西组成：</p>
 * <ul>
 *   <li><b>节点表</b> {@code name -> Node}：每个步骤</li>
 *   <li><b>边表</b> {@code name -> 路由函数}：固定边总返回同一目标；条件边由 State 决定下一个去哪
 *       （这就是 LLM-as-router 的落点）</li>
 *   <li><b>运行循环</b>：从入口开始，跑当前节点 → 按边跳转 → 直到 END</li>
 * </ul>
 *
 * <p>额外支持 <b>interrupt</b>：把某节点标为"执行前需人工审批"，运行到它前面会暂停并 checkpoint
 * 当前位置，等审批通过后再 {@link #resume} 继续——这就是 human-in-the-loop（"带刹车的自主"）。</p>
 *
 * @author data-agent
 */
public class StateGraph {

    /** 终止节点名。 */
    public static final String END = "END";

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, Function<GraphState, String>> edges = new LinkedHashMap<>();
    private final Set<String> interruptBefore = new HashSet<>();
    private String entry;

    /**
     * 注册一个节点。
     *
     * @param name 节点名
     * @param node 节点实现
     * @return this，便于链式构建
     */
    public StateGraph addNode(String name, Node node) {
        nodes.put(name, node);
        return this;
    }

    /**
     * 固定边：{@code from} 执行完总是跳到 {@code to}。
     *
     * @param from 起点节点名
     * @param to 终点节点名
     * @return this
     */
    public StateGraph addEdge(String from, String to) {
        edges.put(from, state -> to);
        return this;
    }

    /**
     * 条件边：{@code from} 执行完，由 {@code router} 根据当前 State 决定下一个节点。
     *
     * @param from 起点节点名
     * @param router 路由函数，返回下一个节点名
     * @return this
     */
    public StateGraph addConditionalEdge(String from, Function<GraphState, String> router) {
        edges.put(from, router);
        return this;
    }

    /**
     * 把某节点标记为"执行前需人工审批"的中断点。
     *
     * @param node 节点名
     * @return this
     */
    public StateGraph interruptBefore(String node) {
        interruptBefore.add(node);
        return this;
    }

    /**
     * 设置入口节点。
     *
     * @param name 入口节点名
     * @return this
     */
    public StateGraph setEntry(String name) {
        this.entry = name;
        return this;
    }

    /**
     * 从入口运行，直到到达 END 或在某个中断点前暂停。
     *
     * @param state 初始状态
     * @return 运行结果（DONE 或 PAUSED + 暂停所在节点）
     */
    public Result run(GraphState state) {
        return resume(state, entry);
    }

    /**
     * 从指定节点继续运行（审批通过后用它恢复执行）。
     *
     * @param state 当前状态
     * @param current 从哪个节点继续
     * @return 运行结果
     */
    public Result resume(GraphState state, String current) {
        while (!END.equals(current)) {
            // 危险节点：执行前暂停等审批（checkpoint = 当前节点名 + state）
            if (interruptBefore.contains(current) && !state.approved) {
                state.pendingApproval = current;
                state.note("⏸ 中断：节点 '" + current + "' 是危险动作，暂停等待人工审批");
                return Result.paused(current);
            }
            // 越过中断点后复位审批标志，供后续可能的危险动作再次拦截
            state.approved = false;
            state.pendingApproval = null;

            Node node = nodes.get(current);
            if (node == null) {
                throw new IllegalStateException("未注册的节点: " + current);
            }
            state = node.apply(state);

            Function<GraphState, String> edge = edges.get(current);
            current = edge == null ? END : edge.apply(state);
        }
        state.note("✅ 到达 END");
        return Result.done();
    }

    /**
     * 运行结果。
     *
     * @param paused 是否在中断点暂停
     * @param pausedAt 暂停所在节点名（未暂停为 null）
     */
    public record Result(boolean paused, String pausedAt) {

        static Result done() {
            return new Result(false, null);
        }

        static Result paused(String at) {
            return new Result(true, at);
        }
    }
}
