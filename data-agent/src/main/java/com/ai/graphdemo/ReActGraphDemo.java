package com.ai.graphdemo;

import java.util.Set;
import java.util.function.Function;

/**
 * 用最小图引擎把"ReAct + 危险动作人工审批"跑成一张状态图的演示。
 *
 * <p>它把主项目里埋在 {@code ReActLoopRunner} 的 while/if 显式化为节点和边，并额外加了
 * 一个 human-in-the-loop 审批中断点——展示 LangGraph 风格相比裸循环多出来的能力：
 * 模型自主路由（条件边）+ 危险动作审批（interrupt）+ 共享状态。</p>
 *
 * <p>用 mock agent（按 step 决策）替代真实 LLM，使 demo 无需 API key、可复现，聚焦图机制本身。
 * 真实场景中 agent 节点内部就是一次带工具的模型调用。</p>
 *
 * <pre>
 *   START → retrieve → agent ──(条件边)──┬─ 调安全工具 → safeTool ─┐
 *                         ▲              ├─ 调危险工具 → ⏸dangerTool┤(成环回 agent)
 *                         └──────────────┘─ 无工具    → finalize → END
 * </pre>
 *
 * @author data-agent
 */
public final class ReActGraphDemo {

    /** 危险工具集合：调用前必须人工审批。 */
    private static final Set<String> DANGEROUS_TOOLS = Set.of("updatePrice", "deleteData", "transfer");

    private ReActGraphDemo() {
    }

    /**
     * 构建演示用状态图。
     *
     * @return 组装好的状态图
     */
    public static StateGraph buildGraph() {
        Node retrieve = state -> {
            state.note("📚 [retrieve] 加载检索/记忆上下文");
            return state;
        };

        // mock agent：按轮次决定调什么工具或给最终答案（真实场景这里是一次带工具的 LLM 调用）
        Node agent = state -> {
            state.step++;
            if (state.step == 1) {
                state.pendingTool = "queryHotelOccupancy";
                state.note("🧠 [agent] 第" + state.step + "步：决定调用安全工具 queryHotelOccupancy");
            } else if (state.step == 2) {
                state.pendingTool = "updatePrice";
                state.note("🧠 [agent] 第" + state.step + "步：决定调用危险工具 updatePrice（房价下调10%）");
            } else {
                state.pendingTool = null;
                state.answer = "成都出租率 72.5%；房价已在人工审批后下调 10%。";
                state.note("🧠 [agent] 第" + state.step + "步：信息足够，给出最终答案");
            }
            return state;
        };

        Node safeTool = state -> {
            state.note("🔧 [safeTool] 执行 " + state.pendingTool + " → 返回结果");
            state.pendingTool = null;
            return state;
        };

        Node dangerTool = state -> {
            state.note("🔧 [dangerTool] 执行（已批准）" + state.pendingTool + " → 房价已下调");
            state.pendingTool = null;
            return state;
        };

        Node finalize = state -> {
            state.note("📝 [finalize] 整理最终答案");
            return state;
        };

        // 条件边：agent 执行完，由 State 决定下一个节点（LLM-as-router 的落点）
        Function<GraphState, String> routeAfterAgent = state -> {
            if (state.pendingTool == null) {
                return "finalize";
            }
            return DANGEROUS_TOOLS.contains(state.pendingTool) ? "dangerTool" : "safeTool";
        };

        return new StateGraph()
                .addNode("retrieve", retrieve)
                .addNode("agent", agent)
                .addNode("safeTool", safeTool)
                .addNode("dangerTool", dangerTool)
                .addNode("finalize", finalize)
                .setEntry("retrieve")
                .addEdge("retrieve", "agent")
                .addConditionalEdge("agent", routeAfterAgent)
                .addEdge("safeTool", "agent")     // 安全工具：成环回 agent
                .addEdge("dangerTool", "agent")   // 危险工具执行后也回 agent
                .addEdge("finalize", StateGraph.END)
                .interruptBefore("dangerTool");   // 危险动作前中断等审批
    }

    /**
     * 运行演示。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        StateGraph graph = buildGraph();
        GraphState state = new GraphState();
        state.query = "查一下成都酒店出租率，然后把房价下调 10%";

        System.out.println("=== 用户请求: " + state.query + " ===\n");

        StateGraph.Result result = graph.run(state);
        // 命中审批中断点就模拟人工批准后恢复，直到流程结束
        while (result.paused()) {
            System.out.println("\n>>> 🔐 人工审批请求: " + state.pendingApproval + " —— 模拟批准 ✓\n");
            state.approved = true;
            result = graph.resume(state, result.pausedAt());
        }

        // 流程结束后一次性打印完整轨迹
        state.log.forEach(System.out::println);
        System.out.println("\n=== 最终答案: " + state.answer + " ===");
    }
}
