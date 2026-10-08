# 设计
1. AgentChatService 统一构建 AiServices；工具注册为 Map<ToolSpecification, ToolExecutor>，不启用并发工具。
2. 每 Run 独立 GovernedToolSession：完整模型响应交给框架前整组纯准入；有审批则持久化并抛出类型化暂停信号。
3. 适配器每轮检查根 Run 迭代预算和首轮 ToolChoice；流式错误显式传给 onError，回调恢复 TaskContextSnapshot。
4. AgentToolInvoker -> AgentToolExecutionPipeline -> DefaultToolExecutor 保持不变。错误建议只追加工具观察，不伪造用户指令。Run 总预算约束收敛。
5. 1.20.2 AiServices 没有公开 transcript resume 入口。按批准预案，DurableToolContinuation 只续跑审批后的原生消息，共用 Session 模型/工具适配器；不依赖框架内部 API。
6. 移除旧循环、文本意图纠偏和反思控制器；保留错误建议、工作记忆和 SSE 协议。
批准来源为会话计划，不声称新建文档单独经过审核。
