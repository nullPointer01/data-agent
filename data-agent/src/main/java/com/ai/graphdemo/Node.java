package com.ai.graphdemo;

/**
 * 状态图的一个节点：吃 State、做一步工作、返回（修改后的）State。
 *
 * <p>节点可以是 LLM 推理、调工具、检索等任意一步。在 LangGraph 里它是图的基本执行单元。</p>
 *
 * @author data-agent
 */
@FunctionalInterface
public interface Node {

    /**
     * 执行该节点。
     *
     * @param state 进入节点时的共享状态
     * @return 处理后的共享状态
     */
    GraphState apply(GraphState state);
}
