## 1. Governed Tool Catalog And Configuration

- [x] 1.1 定义工具风险、治理注解和不可变 Descriptor。（实现任务）
  - Acceptance: 风险等级、只读/幂等/可重试、权限、timeout、最大尝试数和结果长度均为类型化字段；非法组合在构造阶段拒绝。
  - Verify: 静态推演缺失元数据、非正限制和非幂等重试三类失败路径。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolRiskLevel.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolPolicy.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolDescriptor.java`
  - Covers: SC-3, SC-8

- [x] 1.2 增加 Tool Governance 服务端配置和专用有界执行器。（实现任务）
  - Acceptance: 参数/结果上限、默认 timeout、最大尝试数、退避、Journal 容量和线程池均有正数校验与保护性默认值；执行器传播 Security/MDC/Run Scope。
  - Verify: 静态核对配置绑定、默认值、非法值校验、队列边界和 TaskDecorator。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolGovernanceProperties.java`, `src/main/java/com/ai/config/AsyncConfig.java`, `src/main/resources/application.yml`
  - Covers: SC-3, SC-4, SC-6

- [x] 1.3 建立唯一 AgentToolRegistry 并为全部现有工具补齐治理元数据。（实现任务）
  - Acceptance: Registry 同时持有 ToolSpecification、ToolExecutor 和 Descriptor；重复/缺失/非法策略启动失败；全部现有工具名称与参数 Schema 不变。
  - Verify: 比对变更前后工具名清单，扫描所有 `@Tool` 确认 100% 有治理注解且注册入口唯一。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolRegistry.java`, `src/main/java/com/ai/agent/tool/AgentTools.java`, `src/main/java/com/ai/agent/tool/AgentToolInvoker.java`
  - Covers: SC-1, SC-3, SC-8

## 2. Run Authorization And Invocation Boundaries

- [x] 2.1 实现基于现有 RBAC 的不可变工具授权快照。（实现任务）
  - Acceptance: 每 Run 只解析一次启用用户、租户、角色、权限和服务端工具集合；支持 `*:*`；不保存 JWT、密码或实体引用。
  - Verify: 人工推演普通用户、管理员、禁用用户、跨租户用户和缺失用户五类结果。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolAuthorizationSnapshot.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolAuthorizationService.java`, `src/main/java/com/ai/security/SysUserRepository.java`, `src/main/java/com/ai/security/rbac/RolePermissionService.java`
  - Covers: SC-2, SC-7

- [x] 2.2 将授权快照和有界工具 Journal 固化到 AgentRunContext。（实现任务）
  - Acceptance: Coordinator 创建 Run 时生成权限快照和 Journal；并发追加线程安全、有容量上限和 overflow 计数；Context 仍不可替换核心字段。
  - Verify: 静态检查 Run 创建、并发共享、终态读取和 Registry 清理路径。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolExecutionRecord.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolExecutionJournal.java`, `src/main/java/com/ai/agent/runtime/AgentRunContext.java`, `src/main/java/com/ai/agent/runtime/AgentRunCoordinator.java`
  - Covers: SC-2, SC-7

- [x] 2.3 定义显式 AgentToolInvocationContext 和服务端白名单构造器。（实现任务）
  - Acceptance: 上下文固定执行者与精确工具集合；Profile 空列表保留“服务端全部”的旧语义；调用方不能传入未注册工具扩大集合。
  - Verify: 推演配置 Profile、默认 ReAct、专家和 Orchestrator 预检的集合交集。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolInvocationContext.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolInvocationContextFactory.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolRegistry.java`
  - Covers: SC-2, SC-8

## 3. Validation, Result Contract And Safety

- [x] 3.1 实现基于 ToolSpecification 的结构化参数校验。（实现任务）
  - Acceptance: 拒绝畸形/超长 JSON、缺少必填字段、未知字段和不兼容标量类型；拒绝发生在 ToolExecutor 前且不记录原始参数值。
  - Verify: 按每类参数错误人工构造 JSON 并静态推演零实现调用路径。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolArgumentValidator.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolArgumentValidation.java`
  - Covers: SC-1, SC-5, SC-6

- [x] 3.2 定义稳定工具状态、结构化结果和模型观察格式。（实现任务）
  - Acceptance: 结果包含 toolCallId、状态/错误码、retriable、attempts、duration、安全 payload 和处理标记；模型观察只由该结果生成。
  - Verify: 静态核对成功、未知、参数、未授权、策略、超时、执行失败和 Run 终止状态完备性。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolExecutionStatus.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolExecutionResult.java`
  - Covers: SC-4, SC-6

- [x] 3.3 实现统一结果脱敏、截断和安全参数摘要。（实现任务）
  - Acceptance: 屏蔽 Bearer/API Key/password/secret/token/连接串凭据；使用 Descriptor 与服务端较小限制；参数只保留字段名、长度和摘要。
  - Verify: 敏感模式扫描与代表性字符串人工推演，确认模型/Event/Trace 共用同一安全版本。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolOutputSanitizer.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolSafePayload.java`
  - Additional hardening: 移除现有 Tool Service 与 ReAct 日志中的原始参数，并让已捕获异常返回治理管道分类。
  - Covers: SC-6, SC-7

- [x] 3.4 实现工具异常分类和有限重试判定。（实现任务）
  - Acceptance: 仅明确瞬时连接、timeout、429、5xx 可标记 retriable；权限/参数/策略/4xx/业务空结果不可重试；未知异常默认不可重试。
  - Verify: 建立异常分类矩阵并检查默认分支 fail-closed。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolFailure.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolFailureClassifier.java`
  - Covers: SC-5, SC-6

## 4. Governed Execution Pipeline

- [x] 4.1 实现默认拒绝的单次工具执行管道并收敛 AgentToolInvoker。（实现任务）
  - Acceptance: 固定执行 resolve -> validate -> authorize -> budget -> execute -> sanitize；Invoker 不再直接持有全局反射 Map，缺少显式 InvocationContext 的 Agent 调用不能执行。
  - Verify: 搜索所有 `ToolExecutor.execute` 和 `AgentToolInvoker.invoke`，确认实现调用所有权唯一且拒绝路径不消耗预算。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolPolicyEngine.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolExecutionPipeline.java`, `src/main/java/com/ai/agent/tool/AgentToolInvoker.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolRegistry.java`
  - Covers: SC-1, SC-2, SC-4, SC-6

- [x] 4.2 接入工具级 timeout、Future 中断和受限指数退避重试。（实现任务）
  - Acceptance: 等待上限取工具 timeout 与 Run 剩余 deadline 较小值；每个真实尝试重新准入并消耗一次工具预算；重试条件满足安全四联条件且不超过双重上限。
  - Verify: 人工推演成功、第一次瞬时失败后成功、非幂等失败、工具超时、Run 取消和预算耗尽。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolExecutionPipeline.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolRetryPolicy.java`, `src/main/java/com/ai/agent/runtime/AgentRunControl.java`, `src/main/java/com/ai/config/AsyncConfig.java`
  - Covers: SC-4, SC-5

- [x] 4.3 接入 Tool Journal、类型化事件、结构化日志和低基数指标。（实现任务）
  - Acceptance: 每逻辑调用仅产生一个兼容 `tool_call` 事件；Journal 记录安全元数据；指标不使用 runId/userId/异常文本标签；拒绝和重试均可区分。
  - Verify: 静态检查事件次数、Journal 容量、Trace 前数据和 Micrometer tag 集合。
  - Files: `src/main/java/com/ai/agent/tool/governance/AgentToolTelemetry.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolExecutionPipeline.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolExecutionJournal.java`, `src/main/java/com/ai/logging/StructuredLogger.java`, `src/main/java/com/ai/agent/runtime/event/AgentSseEventWriter.java`
  - Covers: SC-6, SC-7

## 5. ReAct And Orchestrator Integration

- [x] 5.1 将显式工具调用上下文贯穿默认 ReAct、配置 Agent 和循环执行器。（实现任务）
  - Acceptance: 默认与 Profile ReAct 均从服务端 Registry 构造允许集合；工具集合与发送给模型的 ToolSpecification 一致；同步/流式共用合同。
  - Verify: 静态追踪两个入口到 ReActLoopRunner，确认没有从 AnalysisRequest 读取白名单。
  - Files: `src/main/java/com/ai/agent/react/ReActAgent.java`, `src/main/java/com/ai/agent/ConfigurableAgentExecutor.java`, `src/main/java/com/ai/agent/react/ReActLoopRunner.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolInvocationContextFactory.java`
  - Covers: SC-1, SC-2, SC-8

- [x] 5.2 让 ReAct Step 处理结构化工具结果并移除字符串控制流。（实现任务）
  - Acceptance: 同步/流式 Step 把同一 InvocationContext 交给 Invoker；ToolExecutionResultMessage 使用安全观察；恢复建议按状态码判断；不再独立重复发送 tool_call。
  - Verify: 搜索未知工具/失败中文前缀的控制流和 `emitToolCall` 调用，确认只保留展示兼容而非决策依赖。
  - Files: `src/main/java/com/ai/agent/react/ReActStepHandler.java`, `src/main/java/com/ai/agent/react/ReActSynchronousStepProcessor.java`, `src/main/java/com/ai/agent/react/ReActStreamingStepProcessor.java`, `src/main/java/com/ai/agent/react/ErrorRecoveryAdvisor.java`, `src/main/java/com/ai/agent/react/ReActStepOutcome.java`
  - Covers: SC-1, SC-6, SC-7, SC-8

- [x] 5.3 将 Orchestrator 并行预检限制为显式只读工具集合。（实现任务）
  - Acceptance: 三类预检工具拥有固定 InvocationContext；任何其他工具名在执行期拒绝；并行线程继承同一 Run 权限和预算。
  - Verify: 静态推演预检合法工具、伪造工具和排队期间 Run 取消场景。
  - Files: `src/main/java/com/ai/agent/orchestrator/ParallelPlanExecutor.java`, `src/main/java/com/ai/agent/orchestrator/ParallelTaskExecutor.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolInvocationContextFactory.java`
  - Covers: SC-1, SC-2, SC-4

## 6. Trace, Documentation And Static Acceptance

- [x] 6.1 将有界 Tool Journal 汇总进统一 Run Trace。（实现任务）
  - Acceptance: Trace 按 runId/toolCallId 记录安全工具摘要、准入状态、尝试数、耗时和处理标记；不新增表；原始参数和完整结果不落库。
  - Verify: 核对 `recordRun` JSON 元数据和敏感字段扫描，确认旧 Trace API 字段兼容。
  - Files: `src/main/java/com/ai/service/AgentExecutionTraceService.java`, `src/main/java/com/ai/agent/runtime/AgentRunContext.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolExecutionJournal.java`, `src/main/java/com/ai/agent/dto/AgentExecutionTraceResponse.java`
  - Covers: SC-6, SC-7, SC-8

- [x] 6.2 更新 Tool Governance 架构、配置、错误语义和演化边界文档。（实现任务）
  - Acceptance: 文档解释模型建议与服务端授权分离、逻辑调用与尝试、timeout 协作式限制、幂等边界和第三阶段审批；不宣称 exactly-once。
  - Verify: 对照 proposal/design/spec 逐项核对术语、默认值声明与 Anti-Scope。
  - Files: `CLAUDE.md`, `README.md`, `docs/核心逻辑详解/Agent运行时与Harness.md`, `docs/核心逻辑详解/Agent工具治理.md`, `docs/演化/常见陷阱.md`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-6, SC-7, SC-8

- [x] 6.3 执行无编译、无自动化测试的静态验收并记录结果。（验证任务）
  - Acceptance: OpenSpec strict validation、工具注册完整性、全调用点、权限矩阵、参数拒绝、重试边界、预算、事件/Trace、敏感信息和 diff integrity 检查完成；显式列出未运行项。
  - Verify: 生成 `verify-report.md`，逐项记录 SC-1..SC-8 证据、人工场景和真实 timeout/并发/外部副作用剩余风险。
  - Files: `openspec/changes/govern-agent-tool-execution/verify-report.md`, `openspec/changes/govern-agent-tool-execution/tasks.md`, `openspec/changes/govern-agent-tool-execution/sdd-state.md`, `openspec/changes/govern-agent-tool-execution/sdd-assumptions.md`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-6, SC-7, SC-8

## Test Strategy Override

用户已明确要求默认不创建或运行自动化测试，也不执行 Maven 编译或前端构建。该项目不是 goods-ds，但用户指令优先于通用 SDD 测试模板，因此本 change 不创建 `src/test` 任务、不设置增量覆盖率目标。实现验证采用 OpenSpec strict validation、静态调用链扫描、授权/失败/重试人工场景推演、敏感信息扫描和 diff integrity；只有用户后续明确改变约束时，才补充 JDK 17 对应测试或编译。
