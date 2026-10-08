# Supervisor 与原编排对照

> 2026-09-30；实现版本：LangChain4j Core 1.20.2 / Agentic 1.20.2-beta30（实验性模块）。
> 本文讲已经接入的实际代码，以及仍未完成的边界，不把框架能力当成项目能力。

## 这次改了什么

1. 新增 `AgentOrchestrationProperties`：`app.agent.orchestration.engine` 在 `TOOL_CALLING` 和 `SUPERVISOR` 之间切换。公共配置/生产沿用前者，local 显式开启后者，改后重启后端。
2. 新增 `AgenticSupervisorService`：真正调用官方 `AgenticServices.supervisorBuilder()`。模型决定下一位执行者、任务内容和何时结束；Java 没有手写一套新的决策循环。
3. 把授权专家包装成 `sequenceBuilder(Worker.class)` 节点：节点只有 `task` 参数，专家 ID 由 Java 闭包固定。节点调用原 `AgentToolInvoker`，不会绕过工具管道去直接执行专家。
4. 保留 `local_assistant` 节点：处理主助手原本拥有的普通工具/Skill 和上下文任务，但不暴露 `delegateToAgent`。避免 Supervisor 和主助手同时负责委派。
5. 协作完成后，收集全部节点结果，用现有 `AgentChatService.stream` 汇总为最终回答。决策和专家执行是同步的，过程中有进度事件，最终汇总才是 token 流。

只对本次计划为 ORCHESTRATED 且实际候选允许 `delegateToAgent` 的请求启用 Supervisor。CHAT、REACT、没有专家绑定或没有委派候选的请求不会强行进入它。子专家内部沿用已有执行路径，不递归新建 Supervisor。

## 两条调用链

```text
TOOL_CALLING
ConfigurableAgentExecutor
  → AgentChatToolService → AiServices：主助手模型选择工具或专家
  → GovernedToolSession → Tool Pipeline → delegateToAgent
  → AgenticDelegationWorkflow：规划 → 专家执行
  → 原生工具结果交回主助手 → 主助手决定继续或回答

SUPERVISOR
ConfigurableAgentExecutor
  → AgenticSupervisorService → 官方 Supervisor：模型选择节点
  → expert_N：Tool Pipeline → delegateToAgent → 同一个专家顺序工作流
    或 local_assistant：主助手自己的 AiServices 工具循环
  → 结果交回 Supervisor → 再选择节点，直到 done
  → 所有执行证据交给 AiServices → 流式最终汇总
```

注意：现在保留的 TOOL_CALLING 路径也已使用 AiServices，不是“完全手写 vs 完全框架”。我们对比的是“项目组织工具委派 + 框架工具循环”和“官方 Supervisor 组织多 Agent”。此前手写的正常 ReAct 循环已经删除，不为对照而重新复制一套。

专家内部的顺序工作流仍是固定的“规划 → 执行”；外层 Supervisor 动态决定调用哪个专家、调用几次、是否结束。固定步骤和动态编排可以组合。

## 为什么框架接上后还需要 Java 代码

| 问题 | 原路径 | Supervisor 路径 |
|---|---|---|
| 谁选专家 | 主助手通过 tool_calls 选择 delegateToAgent | 专门的 Supervisor 模型决策，再由框架调度节点 |
| 普通工具 | 主助手直接选择 | local_assistant 或专家在各自能力范围内选择 |
| 编排扩展 | 自行维护委派适配和组合关系 | 复用框架节点、作用域、组合 API；依赖 beta API 的行为 |
| 调用成本 | 工具决策与回答可在同一助手循环中完成 | 多了管理者决策，本实现还增加一次最终汇总；不保证更省或更快 |
| 状态调试 | 原生用户/AI/工具消息较直观 | 多了 AgenticScope 和框架代理，异常、参数与生命周期需要适配 |
| 安全与预算 | Harness 强制检查 | 仍由同一 Harness 检查，框架不能自动理解业务权限 |
| 审批恢复 | 主助手直接审批工具已有持久化续跑 | 整张图的恢复未实现；审批前明确拒绝，不写不完整现场 |

因此，接框架减少的是通用调度代码，不会让身份、事务、副作用和成本治理自动消失。应按任务复杂度决定是否需要 Supervisor，而不是把所有聊天都套一层管理者。

## 这次处理的框架边界

- 框架默认达到 `maxAgentsInvocations` 时可能直接返回最后结果。本实现让根 Run 的迭代预算终止循环，每次 Supervisor 决策先调用 `beforeIteration()`，超限有明确的 `ITERATION_LIMIT`，不能被当作完成。
- 规划模型通过 `McpModelService.structuredChatModel` 接入，沿用本次回答模型配置，不另建凭据或模型记录。Agentic 的动态 `arguments` 是 Map，此路径显式使用 JSON Object，再由框架反序列化；不是 Router 使用的严格原生 JSON Schema。
- `AgenticScope` 保留已写变量。本实现要求每次决策重新提供唯一非空 `task`，禁止模型通过额外参数覆写身份或复用上一步的 task。
- 专家 ID、根 Run、租户、调用者不由模型填写；目录仅来自当前能力快照。执行时原委派服务还会重查专家租户和启用状态。
- 没有给节点配置自动重试；专家失败即停止，不在已有动作之后自动切换另一个引擎重跑。工具自身的有限幂等重试仍由原 Pipeline 决定。
- `SupervisorExecutionScope` 随 `TaskContextSnapshot` 跨线程传播并恢复。审批暂停前检查此边界，避免把整个 Run 误保存成一个叶节点的恢复现场。需要审批的学习任务，请使用 TOOL_CALLING，并让主助手直接调用审批工具。
- 框架的 Supervisor INFO 日志会输出完整参数，公共配置将这个特定 logger 调为 WARN；项目保留安全的进度与指标。
- 单次 Scope 结束后清理；不把 AgenticScope 当作长期记忆，也不另建一套聊天数据库。
- Supervisor 自己的临时 `MessageWindowChatMemory` 预填真实历史角色，当前问题单独传入。历史仍能按完整轮次裁剪，不把整段会话拼成一个无法拆分的巨大 user 消息。

## 怎么操作、输入什么

先用不写外部数据的小任务看懂编排，暂时不叠加 RAG、数据库与审批。

1. 准备两个启用的专家（名称是示例，并非本次自动创建）：
   - “需求分析专家”：用途写清“拆分用户需求，列出功能、输入输出与验收条件”；提示词要求只分析需求。模型使用已有 GPT，无需绑定写工具。
   - “风险评审专家”：用途写清“检查方案中的遗漏、异常流程和实现风险”；提示词要求输出风险与修正建议。模型同样使用已有配置。
2. 在主 Agent 的能力绑定中勾选这两个专家。使用可进行专家协作的执行模式，保存后重启本地后端加载 `engine: SUPERVISOR`。
3. 手动新建一个会话，输入：

   > 我要做一个请假审批功能。请先让需求分析专家列出功能和验收条件，再让风险评审专家检查遗漏与异常流程，最后给我一份整合后的实施建议。

4. 确认本次请求计划为 ORCHESTRATED，候选有 delegateToAgent。页面应有“规划专家协作”和“执行协作任务”，工具记录可见实际委派，最后流式显示汇总。如果仍走 CHAT/REACT，先检查绑定、模式与请求计划，不要用关键词硬编码来强迫进入 Supervisor。
5. 查看该次执行 Trace 的 `sharedContext.orchestration`：`engine` 应为 SUPERVISOR，`executors` 是实际顺序；`planningAndExecutionModelCalls` 包含管理者与执行节点调用，`synthesisModelCalls` 是汇总调用，`tokens` / `durationMs` 是此阶段增量。全请求成本以 Run 总指标为准，里面还包括前置请求规划等步骤。失败时重点看 Run 终止原因和 Tool Journal，不保证存在成功响应的阶段指标。
6. 将 `application-local.yml` 中同一配置改为 `TOOL_CALLING`，重启；手动新建会话，保持模型、专家配置和输入一致，重复任务。Trace 的 `orchestration.engine` 应为 TOOL_CALLING。
7. 比较实际选择顺序、任务是否遗漏、错误是否可解释、Run 总模型调用数、Token 和耗时。关闭断点后再比耗时，每种方式重复几次；一次结果不足以判断性能优劣。

本轮没有自动创建专家、修改业务数据库或调用收费端点。真实 GPT 的 JSON Object 兼容性、选人质量与成本仍待上述验收。

## 怎么 Debug

按以下四个位置走一遍即可，不必从头逐行读框架源码：

1. `ConfigurableAgentExecutor.executeReAct/executeReActStreaming`：看本次工具规格与 `supervisor.supports`，理解进入哪个分支。
2. `AgenticSupervisorService.decisionModel().doChat`：请求里有框架生成的专家卡片；响应形如 `{"agentName":"expert_0","arguments":{"task":"分析请假需求"}}`。这是模型决定下一位执行者的地方。
3. `AgenticSupervisorService.worker` 的 action：观察模型只提供 task，Java 捕获的真实专家 ID 如何转成受治理的 delegateToAgent 调用。再进入原有 `AgenticDelegationWorkflow` 看规划和执行。
4. `AgenticSupervisorService.executeScoped` 中 `supervisor.invoke` 后：查看全部 evidence，以及最后一次 `chat.stream`。明确管理者决策、专家回答、最终汇总是不同调用。

学完后尝试自己解释：为什么权限不交给 Supervisor？为什么 Supervisor 没有输出 token 时前端仍能显示进度？为什么图恢复不能直接复用叶节点 Checkpoint？能够结合断点回答这三问，比记住 builder 方法名更有用。

官方参考：[Agentic 教程](https://docs.langchain4j.dev/tutorials/agents/)。实现以本地下载的 1.20.2-beta30 源码为准，避免把文档主分支的新 API 误当作当前依赖已有能力。
