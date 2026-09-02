package com.ai.graphdemo;

import java.util.ArrayList;
import java.util.List;

/**
 * 贯穿状态图全程的共享状态（LangGraph 的 State）。
 *
 * <p>每个节点读写同一个 State，从而解决"各编排层不共享状态"的问题。
 * 演示用，字段直接公开以保持可读性。</p>
 *
 * @author data-agent
 */
public class GraphState {

    /** 用户原始请求。 */
    public String query;

    /** 执行轨迹（相当于 messages，演示用于打印 graph 走过的路径）。 */
    public final List<String> log = new ArrayList<>();

    /** 当前推理轮次，驱动 mock agent 决策，保证可复现。 */
    public int step = 0;

    /** agent 本轮决定要调用的工具名；为 null 表示要给最终答案。 */
    public String pendingTool;

    /** 待人工审批的危险节点名；非空表示当前卡在审批中断点。 */
    public String pendingApproval;

    /** 审批是否已通过（恢复运行时由调用方置位，越过中断点）。 */
    public boolean approved;

    /** 最终答案。 */
    public String answer;

    /**
     * 追加一条执行轨迹。
     *
     * @param message 轨迹文本
     */
    public void note(String message) {
        log.add(message);
    }
}
