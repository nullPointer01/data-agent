# SDD Assumptions

## Confirmed

- [A001] 当前系统保留 Agent 概念，AgentProfile 负责绑定模型、system prompt、Tools、Skills 和数据源；Harness 负责一次执行的生命周期与治理。
  - 来源: 用户讨论与现有 `AgentProfile`
  - 置信度: 高
  - 验证: proposal Objective、agent-run-harness spec

- [A002] 第一阶段统一 Chat、ReAct、Orchestrated 三种运行模式，不新增第四种 Agent 类型。
  - 来源: 用户确认的目标架构
  - 置信度: 高
  - 验证: SC-2、Anti-Scope

- [A003] 第一阶段优先建设统一 Run、预算、deadline/取消、事件和 Trace，不引入持久化 Checkpoint/Resume。
  - 来源: 探索阶段对齐结果
  - 置信度: 高
  - 验证: proposal What Changes 与 Anti-Scope

- [A004] 用户明确要求默认不创建或运行自动化测试，也不执行 Maven 编译。
  - 来源: 用户历史明确指令
  - 置信度: 高
  - 验证: tasks Test Strategy Override

- [A005] 项目 Java 基线为 JDK 17，Spring Boot 为 3.2，LangChain4j 为 1.19。
  - 来源: 当前 `pom.xml` 与 `CLAUDE.md`
  - 置信度: 高
  - 验证: proposal Impact

- [A006] 第一阶段不得要求数据库迁移，现有分析 API 与 AgentProfile 数据应保持兼容。
  - 来源: 演进风险与用户当前项目状态
  - 置信度: 高
  - 验证: SC-7

- [A009] 当前仓库前端能够忽略 SSE 新增字段，同时继续消费原有事件类型和字段。
  - 来源: `frontend/src/pages/ChatPage.jsx` 静态检查
  - 置信度: 高（仅针对当前仓库客户端）
  - 验证: 保留 `token/tool_call/orchestration/error/done` 分支，并新增 `run_started` 处理

## Unverified

- [A007] `app.agent.runtime.*` 的默认 timeout、轮数、模型/工具调用和 Token 上限能覆盖当前常用请求。
  - 来源: 这些数值目前只能作为保护性起点
  - 置信度: 中低
  - 验证: 上线后使用 Trace 的 P50/P95/P99 分布调优，不宣称当前值最优

- [A008] LangChain4j 1.19 对项目所有兼容模型都能稳定返回真实 TokenUsage。
  - 来源: 不同 OpenAI-compatible 厂商实现存在差异
  - 置信度: 中
  - 验证: 缺失时使用现有 TokenMonitor 估算，并在 snapshot 标记 estimated

- [A010] 节点内 Registry 足以支撑当前单机/面试项目阶段的显式取消。
  - 来源: 当前没有多实例协调基础设施要求
  - 置信度: 中高
  - 验证: 多实例部署前单独设计共享 Registry 与任务所有权

## Invalidated

- [A011] “只把 ReAct 的 8 改成配置项就完成了 Harness 治理”。
  - 原因: 同步/流式路由、Orchestrator 子任务、模型/工具预算、取消、事件和 Trace 仍会分散且不一致

- [A012] “取消等同于 `Future.cancel(true)`”。
  - 原因: 外部 HTTP 调用未必响应线程中断，必须用可观察的协作式状态在调用边界停止后续工作
