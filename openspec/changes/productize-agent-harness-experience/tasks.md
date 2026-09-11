# Implementation Tasks

## Execution Rules

- 每个任务开始前读取 `problem-log.md`，发现实质问题立即新增稳定编号；解决后补根因、修改位置、验证证据和残余风险。
- 当前约束是不新增或运行自动化测试、不执行 Maven 编译/打包/后端启动；前端任务执行 `npm run build`，真实后端行为只在现有服务和用户已配置依赖可用时验证。
- 每个实现任务最多修改 5 个应用文件；超出范围必须先拆任务并更新本清单。
- 不展示 Chain-of-Thought，不把 API Key、原始工具敏感参数、Checkpoint 密文或跨用户数据写入事件和页面。

## 0. Baseline And Contract Audit

- [x] 0.1 审计普通、流式、审批暂停和恢复四条 Run 链路的 `runId/traceId`、终态事件、Trace 与持久化关系
  - Acceptance: `sdd-assumptions.md` 中 A002/A004/A005 均获得代码证据并更新状态。
  - Verify: 静态调用链覆盖 `AgentRunCoordinator`、SSE writer、Trace service、durable store 和会话持久化。
  - Files: `openspec/changes/productize-agent-harness-experience/sdd-assumptions.md`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-2, SC-3, SC-8

- [x] 0.2 建立当前页面与目标信息架构映射，确认保留、合并和改名项
  - Acceptance: 设计文档给出普通用户与管理员菜单映射，且普通用户一级导航不超过 5 项。
  - Verify: 对照 `App.jsx` 与 `navigation.jsx` 中所有实际页面 key，无孤儿页面和越权入口。
  - Files: `openspec/changes/productize-agent-harness-experience/design.md`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-1, SC-8

## 1. Stage 1 - Harness Workbench

- [x] 1.1 新增前端 Run 事件归一化器并完整消费终态、预算和事件时间字段
  - Acceptance: `run_started`、RAG、计划、工具、审批、预算、错误、暂停和终止事件都形成稳定的前端 Run Evidence；同一 Run 不重复追加终态。
  - Verify: 使用脱敏 SSE 样例做静态输入/输出检查，并执行前端构建。
  - Files: `frontend/src/agent/runEvidence.js`, `frontend/src/pages/ChatPage.jsx`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-2, SC-3, SC-8

- [x] 1.2 实现可复用的 Run Inspector，展示终态摘要、预算和安全事件时间线
  - Acceptance: Inspector 展示状态、模式、耗时、模型调用、工具调用、Token、终止原因，并按 `eventTime` 排列操作事件；最终答案仍为视觉主体。
  - Verify: 前端构建通过；桌面和移动视口无文本溢出、遮挡或布局跳动。
  - Files: `frontend/src/components/RunInspector.jsx`, `frontend/src/pages/ChatPage.jsx`, `frontend/src/styles.css`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-2, SC-3, SC-8

- [x] 1.3 将产品壳收敛为 Reference App 与 Control Plane 两个明确视角
  - Acceptance: 首屏在一个视口内呈现 Agent 身份、任务输入、最近 Run 状态和 Harness 能力摘要；管理员菜单按 Runtime、Capabilities、Governance 分组。
  - Verify: 枚举所有页面 key 与权限条件；管理员和普通用户各做一次浏览器导航检查。
  - Files: `frontend/src/App.jsx`, `frontend/src/config/navigation.jsx`, `frontend/src/pages/ChatPage.jsx`, `frontend/src/styles.css`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-1, SC-8

- [x] 1.4 提供所有者可见的安全 Run 详情读取路径，并兼容未开启 durable 的普通 Run
  - Acceptance: Run owner 可读取安全摘要；其他用户得到统一不存在/无权限响应；接口不返回 Checkpoint、密文、原始参数或隐藏推理。
  - Verify: 静态权限矩阵和 DTO 字段审查；在现有服务可用时通过 Swagger 验证 owner/other-user 两种请求。
  - Files: `src/main/java/com/ai/agent/runtime/AgentRunEvidenceService.java`, `src/main/java/com/ai/agent/runtime/dto/AgentRunEvidenceResponse.java`, `src/main/java/com/ai/agent/durable/AgentDurableRunController.java`, `src/main/java/com/ai/security/auth/SecurityConfig.java`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-2, SC-3, SC-8

- [x] 1.5 将新会话消息与 canonical Run 引用关联
  - Acceptance: 新产生的 assistant 消息保存 `runId`；历史无 Run 引用的数据库行保持兼容可读。
  - Verify: 审查 nullable SQL、实体和 Coordinator -> Recorder -> SessionManager 调用链向后兼容。
  - Files: `src/main/java/com/ai/model/ConversationMessage.java`, `src/main/java/com/ai/service/SessionManager.java`, `src/main/java/com/ai/agent/tool/AgentConversationRecorder.java`, `src/main/java/com/ai/agent/runtime/AgentRunCoordinator.java`, `sql/upgrade-agent-run-evidence.sql`
  - Covers: SC-2, SC-3

- [x] 1.6 在初始化 SQL 和历史会话页面中恢复可用 Run 证据
  - Acceptance: 新库包含 nullable `run_id`；刷新会话后，有 Run 引用的 assistant 消息加载 owner-only Run 详情，旧消息明确显示没有可恢复证据。
  - Verify: SQL 静态审查、前端构建；在现有服务可用时完成“执行 -> 刷新 -> 重开详情”路径。
  - Files: `sql/init.sql`, `frontend/src/agent/runEvidence.js`, `frontend/src/pages/ChatPage.jsx`, `frontend/src/components/RunInspector.jsx`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-2, SC-3

- [ ] 1.7 完成 Stage 1 验收并记录真实与未验证证据
  - Acceptance: SC-1、SC-2、SC-3、SC-8 有逐项结论；失败项先回到对应任务修复。
  - Verify: `npm run build`、OpenSpec strict validation、diff check、桌面/移动截图和至少一次真实 SSE 演示。
  - Files: `openspec/changes/productize-agent-harness-experience/stage-1-verify.md`, `openspec/changes/productize-agent-harness-experience/problem-log.md`, `openspec/changes/productize-agent-harness-experience/sdd-state.md`
  - Covers: SC-1, SC-2, SC-3, SC-8

## 2. Stage 2 - Task Outcome And Agent Eval

- [x] 2.1 定义任务目标、成功标准和 Outcome 状态的向后兼容领域合同
  - Acceptance: 旧请求默认 `NOT_EVALUATED`；新请求支持不可变 goal 和 criteria；Run 状态与 Outcome 状态分离。
  - Verify: DTO/枚举完整性、输入上限和 JSON 兼容静态检查。
  - Files: `src/main/java/com/ai/agent/outcome/AgentTaskContract.java`, `src/main/java/com/ai/agent/outcome/AgentSuccessCriterion.java`, `src/main/java/com/ai/agent/outcome/AgentOutcomeStatus.java`, `src/main/java/com/ai/model/AnalysisRequest.java`, `src/main/java/com/ai/model/AnalysisResponse.java`
  - Covers: SC-4

- [x] 2.2 将不可变任务合同贯穿 Run 与加密审批 Checkpoint
  - Acceptance: 任务合同在 Run 创建后不可被下游修改；暂停和恢复后仍为同一份归一化合同；旧版本 Checkpoint 仍可读取为空合同。
  - Verify: 同步、流式、暂停和恢复构造链静态审查，确认新 Checkpoint 使用 v2 且兼容 v1。
  - Files: `src/main/java/com/ai/agent/runtime/AgentRunContext.java`, `src/main/java/com/ai/agent/durable/AgentRunCheckpoint.java`, `src/main/java/com/ai/agent/durable/AgentCheckpointVersion.java`, `src/main/java/com/ai/agent/approval/AgentApprovalSuspensionService.java`, `src/main/java/com/ai/agent/durable/AgentCheckpointRestorer.java`
  - Covers: SC-4

- [x] 2.3 实现确定性 Outcome evaluator 和明确的无法评估分支
  - Acceptance: 支持结构化包含、工具选择、审批状态与稳定终态断言；缺少证据时返回 `NOT_EVALUATED`，不猜测成功。
  - Verify: 用静态样例逐项走查 pass/fail/unavailable 三类分支并记录结果。
  - Files: `src/main/java/com/ai/agent/outcome/AgentOutcomeEvaluator.java`, `src/main/java/com/ai/agent/outcome/DeterministicOutcomeEvaluator.java`, `src/main/java/com/ai/agent/outcome/AgentOutcomeEvaluation.java`, `src/main/java/com/ai/agent/outcome/AgentCriterionEvaluation.java`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-4, SC-8

- [x] 2.4 将 Outcome 评估接入 Run，并写入安全 Trace 与 owner Run 证据
  - Acceptance: 正常同步/流式 Run 的技术终态与业务 Outcome 分开返回；安全 Trace 和 owner Run API 返回 criterion-level 结果，不持久化完整目标或期望值。
  - Verify: 同步和流式调用链静态审查；DTO 敏感字段检查。
  - Files: `src/main/java/com/ai/agent/runtime/AgentRunCoordinator.java`, `src/main/java/com/ai/service/AgentExecutionTraceService.java`, `src/main/java/com/ai/model/AnalysisResponse.java`, `src/main/java/com/ai/agent/runtime/AgentRunEvidenceService.java`, `src/main/java/com/ai/agent/runtime/dto/AgentRunEvidenceResponse.java`
  - Covers: SC-4, SC-8

- [x] 2.5 为审批恢复 Run 追加 Outcome 评估与 Trace 证据
  - Acceptance: 恢复终态使用原加密 Checkpoint 合同评估；审批证据可用，暂停前工具证据缺失时显式 `NOT_EVALUATED`。
  - Verify: 审批成功、恢复失败和 v1 无合同三条静态分支审查。
  - Files: `src/main/java/com/ai/agent/durable/AgentResumeWorker.java`, `src/main/java/com/ai/service/AgentExecutionTraceService.java`, `src/main/java/com/ai/agent/runtime/AgentRunEvidenceService.java`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-4, SC-8

- [x] 2.6 在 My Agent 中加入可选任务标准输入和 Outcome 展示
  - Acceptance: 默认保持简洁提问；用户展开任务选项后可填写 goal/criteria；结果清楚区分运行完成与目标达成。
  - Verify: 前端构建和桌面/移动浏览器检查；旧请求路径不要求用户填写新字段。
  - Files: `frontend/src/components/TaskContractEditor.jsx`, `frontend/src/agent/runEvidence.js`, `frontend/src/pages/ChatPage.jsx`, `frontend/src/components/RunInspector.jsx`, `frontend/src/styles.css`
  - Covers: SC-4

- [x] 2.7 建立版本化 Agent Eval 数据模型和幂等 SQL
  - Acceptance: 数据集、样本、评测运行和样本结果均记录版本与关联 Run；不使用 Hibernate 自动建表。
  - Verify: SQL 结构、索引、租户字段、状态机和回滚兼容性审查。
  - Files: `src/main/java/com/ai/agent/eval/AgentEvalDatasetEntity.java`, `src/main/java/com/ai/agent/eval/AgentEvalSampleEntity.java`, `src/main/java/com/ai/agent/eval/AgentEvalRunEntity.java`, `src/main/java/com/ai/agent/eval/AgentEvalResultEntity.java`, `sql/upgrade-agent-eval.sql`
  - Covers: SC-5

- [x] 2.8 实现 Agent Eval 执行与六类指标聚合
  - Acceptance: 报告输出任务完成率、工具选择率、无效循环率、审批准确率、P95 耗时和平均 Token；缺字段显示 unavailable。
  - Verify: 固定样例手工演算与服务输出逐项对照，不运行自动化测试。
  - Files: `src/main/java/com/ai/agent/eval/AgentEvalService.java`, `src/main/java/com/ai/agent/eval/AgentEvalMetricCalculator.java`, `src/main/java/com/ai/agent/eval/AgentEvalController.java`, `src/main/java/com/ai/agent/eval/dto/AgentEvalReportResponse.java`, `src/main/java/com/ai/config/AsyncConfig.java`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-5, SC-8

- [x] 2.9 增加 Agent Eval 管理视图并保留样本级下钻
  - Acceptance: 管理员可查看评测版本、六类指标、不可用原因和失败样本 Run 证据；RAG 指标独立展示。
  - Verify: 前端构建、权限导航扫描和浏览器布局检查。
  - Files: `frontend/src/pages/AgentEvalPage.jsx`, `frontend/src/App.jsx`, `frontend/src/config/navigation.jsx`, `frontend/src/styles.css`
  - Covers: SC-5

- [ ] 2.10 完成 Stage 2 验收
  - Acceptance: SC-4、SC-5、SC-8 有逐项证据，至少一组固定样本可重复得到相同确定性结果。
  - Verify: OpenSpec strict validation、静态 API/SQL 审查、前端构建和用户批准后的真实评测演示。
  - Files: `openspec/changes/productize-agent-harness-experience/stage-2-verify.md`, `openspec/changes/productize-agent-harness-experience/problem-log.md`, `openspec/changes/productize-agent-harness-experience/sdd-state.md`
  - Covers: SC-4, SC-5, SC-8

## 3. Stage 3 - Context Governance And Recovery Evidence

- [x] 3.1 定义 Context Governor 的预算、优先级和决策证据合同
  - Acceptance: protected 消息、模型窗口、Run 预算、压缩开关与拒绝原因均有显式类型和配置校验。
  - Verify: 配置/YAML 绑定、默认关闭策略和异常边界静态检查。
  - Files: `src/main/java/com/ai/agent/context/AgentContextProperties.java`, `src/main/java/com/ai/agent/context/AgentContextPlan.java`, `src/main/java/com/ai/agent/context/AgentContextEvidence.java`, `src/main/resources/application.yml`, `src/main/resources/application-local.yml`
  - Covers: SC-6

- [x] 3.2 实现统一上下文准入、保守裁剪和可选摘要策略
  - Acceptance: 所有受治理模型调用共享相同准入器；无法保留 protected 消息时显式失败；关闭压缩时不静默启用模型摘要。
  - Verify: 静态调用图证明 ReAct、Chat 与 Orchestrated 模型入口均不可绕过准入器。
  - Files: `src/main/java/com/ai/agent/context/AgentContextGovernor.java`, `src/main/java/com/ai/agent/context/ConservativeContextReducer.java`, `src/main/java/com/ai/agent/context/ContextReductionStrategy.java`, `src/main/java/com/ai/mcp/McpModelService.java`, `src/main/java/com/ai/agent/react/ReActModelCaller.java`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-6, SC-8

- [x] 3.3 将上下文证据和暂停/恢复状态写入统一 Run 时间线
  - Acceptance: 实时与历史详情能区分未压缩、已压缩、等待审批、恢复领取、恢复完成和恢复失败。
  - Verify: SSE/Trace/durable 三条数据源的事件顺序与脱敏字段审查。
  - Implementation slice A Files: `src/main/java/com/ai/agent/context/AgentContextGovernor.java`, `src/main/java/com/ai/agent/runtime/event/AgentEventType.java`, `src/main/java/com/ai/agent/runtime/event/AgentSseEventWriter.java`, `src/main/java/com/ai/service/AgentExecutionTraceService.java`
  - Implementation slice B Files: `src/main/java/com/ai/agent/durable/AgentResumeWorker.java`, `src/main/java/com/ai/agent/runtime/AgentRunEvidenceService.java`, `src/main/java/com/ai/agent/runtime/dto/AgentRunEvidenceResponse.java`, `src/main/java/com/ai/service/AgentExecutionTraceService.java`, `frontend/src/agent/runEvidence.js`
  - Covers: SC-3, SC-6

- [x] 3.4 在 Run Inspector 中展示上下文压缩与恢复证据
  - Acceptance: 页面显示压缩前后 Token、保留消息数、原因与估算标记；未触发时明确显示未压缩。
  - Verify: 前端构建和长文本/窄屏布局检查。
  - Files: `frontend/src/components/RunInspector.jsx`, `frontend/src/styles.css`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-6, SC-8

- [ ] 3.5 完成 Stage 3 验收
  - Acceptance: SC-3、SC-6、SC-8 有逐项证据；至少演示一次预算拒绝或真实压缩，以及一次审批暂停/恢复。
  - Verify: 静态调用链、前端构建、Feature Flag 开关对比和用户批准后的真实运行。
  - Files: `openspec/changes/productize-agent-harness-experience/stage-3-verify.md`, `openspec/changes/productize-agent-harness-experience/problem-log.md`, `openspec/changes/productize-agent-harness-experience/sdd-state.md`
  - Covers: SC-3, SC-6, SC-8

## 4. Stage 4 - Extension Contract And Interview Demo

- [x] 4.1 定义统一 Capability Descriptor 与注册校验
  - Acceptance: Tool、Skill、sub Agent 共享身份、版本、Schema、所有者、权限、风险和生命周期字段；缺失治理字段无法注册。
  - Verify: 与现有 Tool Registry、SkillManager、Agent Profile 的字段映射审查。
  - Files: `src/main/java/com/ai/agent/capability/AgentCapabilityDescriptor.java`, `src/main/java/com/ai/agent/capability/AgentCapabilityType.java`, `src/main/java/com/ai/agent/capability/AgentCapabilityRegistry.java`, `src/main/java/com/ai/agent/capability/AgentCapabilityValidator.java`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-8

- [x] 4.2 提供权限过滤后的能力目录并复用现有工具执行治理
  - Acceptance: owner 只能看到可绑定能力；MCP/未来 provider 不得绕过 Tool Pipeline；禁用能力从后续 Run 生效。
  - Verify: 静态权限矩阵、调用链与敏感 Descriptor 字段审查。
  - Files: `src/main/java/com/ai/agent/capability/AgentCapabilityService.java`, `src/main/java/com/ai/agent/capability/AgentCapabilityController.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolRegistry.java`, `src/main/java/com/ai/service/AgentProfileService.java`, `src/main/java/com/ai/security/auth/SecurityConfig.java`
  - Covers: SC-8

- [x] 4.3 在 Agent 设置中按类型和风险展示可绑定能力
  - Acceptance: 用户能区分 Tool、Skill、sub Agent，看到风险/权限状态且不能选择不可用项；不引入低代码流程画布。
  - Verify: 前端构建、权限账号对比和移动端布局检查。
  - Files: `frontend/src/components/CapabilityPicker.jsx`, `frontend/src/pages/ChatPage.jsx`, `frontend/src/styles.css`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-1, SC-8

- [x] 4.4 编写三条可重复求职演示路径和准确的架构说明
  - Acceptance: 知识证据、工具审批、失败恢复/预算终止三条脚本均包含准备、请求、预期证据、故障注入和不能宣称的边界。
  - Verify: 按脚本逐条走查，所有接口、页面名称和配置与当前代码一致。
  - Files: `docs/interview/AGENT_HARNESS_DEMO.md`, `docs/核心逻辑详解/Agent运行时与Harness.md`, `docs/核心逻辑详解/目录索引.md`, `README.md`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-7, SC-8

- [x] 4.5 完成全量验收与知识库更新
  - Acceptance: SC-1 至 SC-8 全部具有 PASS/FAIL/UNVERIFIED 证据；FAIL 回到实现修复，UNVERIFIED 明确外部条件；问题日志无未说明阻塞项。
  - Verify: OpenSpec strict validation、git diff check、前端构建、权限/安全/数据迁移审查和三条浏览器演示。
  - Files: `openspec/changes/productize-agent-harness-experience/verify-report.md`, `openspec/changes/productize-agent-harness-experience/problem-log.md`, `openspec/changes/productize-agent-harness-experience/sdd-assumptions.md`, `openspec/changes/productize-agent-harness-experience/sdd-state.md`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-6, SC-7, SC-8

## 5. Stage 5 - Composable Agent Capabilities

- [x] 5.1 建立显式统一能力绑定数据合同
  - Acceptance: Profile 可区分 legacy `NULL` 与显式 `[]`，请求/响应无损传递稳定身份，绑定数量上限为 64。
  - Verify: 静态检查 JSON 解析失败关闭、旧字段兼容和 DTO 序列化字段。
  - Files: `src/main/java/com/ai/agent/capability/AgentCapabilityBindingSet.java`, `src/main/java/com/ai/model/AgentProfile.java`, `src/main/java/com/ai/agent/dto/AgentProfileRequest.java`, `src/main/java/com/ai/agent/dto/AgentProfileResponse.java`, `sql/init.sql`
  - Covers: SC-9

- [x] 5.2 实现绑定规范化、权限校验与幂等数据库升级
  - Acceptance: 新绑定拒绝重复、未知、越权、不可用、自引用和已知引用环；旧请求继续兼容，更新不会静默丢失非 Tool 绑定。
  - Verify: 静态走查 create/update、普通用户/管理员、disabled 与环路分支；审查幂等 SQL。
  - Files: `src/main/java/com/ai/agent/capability/AgentCapabilityService.java`, `src/main/java/com/ai/agent/capability/AgentCapabilityRegistry.java`, `src/main/java/com/ai/service/AgentProfileService.java`, `sql/upgrade-agent-capability-bindings.sql`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-9, SC-10, SC-8

- [x] 5.3A 建立正常运行与异步线程的不可变能力作用域
  - Acceptance: 每层配置 Agent 解析自己的有效绑定；作用域传播到 Tool 线程；禁用、撤权或被模式排除的能力产生安全原因。
  - Verify: 静态调用图覆盖 Configurable executor、精确 Tool 规格过滤与异步 TaskDecorator。
  - Files: `src/main/java/com/ai/agent/capability/AgentCapabilityBindingSnapshot.java`, `src/main/java/com/ai/agent/capability/AgentCapabilityScope.java`, `src/main/java/com/ai/agent/capability/AgentCapabilityService.java`, `src/main/java/com/ai/config/ContextPropagatingTaskDecorator.java`, `src/main/java/com/ai/agent/ConfigurableAgentExecutor.java`
  - Covers: SC-10, SC-11

- [x] 5.3B 在审批恢复时重建原执行 Agent 的当前能力作用域
  - Acceptance: Profile Tool 在审批后恢复时按 `executorId` 重读当前 Agent、绑定、RBAC 和运行策略；已撤销绑定或停用 Agent 失败关闭。
  - Verify: 静态调用图覆盖 Checkpoint executor、恢复 RunScope、能力快照、待审批 Tool 准入与后续 ReAct Tool 规格。
  - Files: `src/main/java/com/ai/agent/durable/AgentResumeWorker.java`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-10, SC-11, SC-8

- [x] 5.4A 建立绑定 Skill 的稳定 ID 无兜底执行边界
  - Acceptance: Runtime 只列出当前作用域绑定 Skill；未绑定或不存在的稳定 ID 在进入 Skill 实现前失败，不回退默认 Skill。
  - Verify: 静态扫描证明 `AgentSkillToolService` 先检查 AgentCapabilityScope，再调用 SkillManager 的稳定 ID 精确入口。
  - Files: `src/main/java/com/ai/skill/SkillManager.java`, `src/main/java/com/ai/agent/tool/AgentSkillToolService.java`, `src/main/java/com/ai/agent/tool/AgentTools.java`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-10, SC-11, SC-8

- [x] 5.4B 将 Skill 适配器自动接入受治理 ReAct
  - Acceptance: 有有效绑定 Skill 时自动暴露 Skill adapter；适配调用经过 Tool Pipeline并共享根 Run 的 Tool、模型、Token 和 deadline 预算；Chat 不暴露适配器。
  - Verify: 静态调用图覆盖 Skill Descriptor 运行策略、能力快照、Tool 规格/白名单、异步 Tool 线程和 McpModelService RunScope。
  - Files: `src/main/java/com/ai/agent/capability/AgentCapabilityRegistry.java`, `src/main/java/com/ai/agent/ConfigurableAgentExecutor.java`, `src/main/java/com/ai/agent/tool/AgentTools.java`, `src/main/java/com/ai/agent/tool/AgentToolInvoker.java`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-10, SC-11, SC-8

- [x] 5.5A 建立受限子 Agent adapter 与运行期委派路径守卫
  - Acceptance: adapter 只接受当前能力快照中的启用子 Agent；子 Agent 使用自己的 Profile、模型、Prompt 和能力快照；同一根 Run 最多 3 层且活动路径拒绝环路。
  - Verify: 静态调用图覆盖父能力准入、租户重读、子请求隔离、路径进入/退出和稳定拒绝原因。
  - Files: `src/main/java/com/ai/agent/capability/AgentDelegationGuard.java`, `src/main/java/com/ai/agent/tool/AgentDelegationToolService.java`, `src/main/java/com/ai/agent/tool/AgentTools.java`, `src/main/java/com/ai/agent/capability/AgentCapabilityRegistry.java`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-10, SC-11

- [x] 5.5B 隔离嵌套委派执行并保留 Tool Pipeline 治理
  - Acceptance: 委派 adapter 使用独立有界执行池，嵌套子 Agent 的普通 Tool 不会因父委派占用 Tool 池而饥饿；超时、根 Run 上下文和安全拒绝原因继续进入统一 Tool 证据。
  - Verify: 静态调用图覆盖 TaskDecorator、两个执行池、统一预算申请、超时取消和失败分类。
  - Files: `src/main/java/com/ai/config/AsyncConfig.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolExecutionPipeline.java`, `src/main/java/com/ai/agent/tool/governance/AgentToolFailureClassifier.java`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-10, SC-11, SC-8

- [x] 5.5C 打通 Auto/Chat/ReAct/Orchestrated 同步与流式路由
  - Acceptance: 显式模式不再被折叠；复杂 Auto 仅在存在有效子 Agent 时进入 Orchestrated；Configurable Agent 在同步与流式路径使用同一能力策略和委派 adapter。
  - Verify: 静态分支矩阵覆盖四模式、有无有效子 Agent、同步/流式和 Tool/Skill/sub Agent 规格差异。
  - Files: `src/main/java/com/ai/agent/runtime/AgentRunRouteResolver.java`, `src/main/java/com/ai/agent/runtime/PersonalAgentExecutionModeResolver.java`, `src/main/java/com/ai/agent/ConfigurableAgentExecutor.java`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-10, SC-11

- [x] 5.6 将三类能力绑定接入个人 Agent 设置
  - Acceptance: Tool、Skill、Sub Agent 均可选择并保存；模式控件解释实际可用范围；不可用旧绑定可移除，目录失败不能保存。
  - Verify: 前端生产构建；使用脱敏 API fixture 检查桌面与移动视口、请求 payload 和重新加载回显。
  - Files: `frontend/src/components/CapabilityPicker.jsx`, `frontend/src/pages/ChatPage.jsx`, `frontend/src/pages/admin/AgentsPage.jsx`, `frontend/src/styles.css`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-9, SC-11, SC-12, SC-8

- [x] 5.7A 同步 Stage 5 产品与演示文档
  - Acceptance: README、Harness 文档与演示路径准确描述统一三类绑定、四模式和真实运行边界，不保留“仅 Tool 可编辑”等过时结论。
  - Verify: 文档术语扫描与源码模式/绑定合同逐项核对，不把静态实现写成已通过真实 Run。
  - Files: `README.md`, `docs/核心逻辑详解/Agent运行时与Harness.md`, `docs/interview/AGENT_HARNESS_DEMO.md`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-9, SC-10, SC-11, SC-12

- [x] 5.7B 完成 Stage 5 静态验收状态
  - Acceptance: SC-9 至 SC-12 具有 PASS/FAIL/UNVERIFIED 证据；A009/A010、问题日志和 SDD 状态与当前实现一致。
  - Verify: OpenSpec strict validation、diff check、前端构建、权限/循环/预算/迁移矩阵；不以静态检查冒充后端运行验证。
  - Files: `openspec/changes/productize-agent-harness-experience/verify-report.md`, `openspec/changes/productize-agent-harness-experience/sdd-assumptions.md`, `openspec/changes/productize-agent-harness-experience/sdd-state.md`, `openspec/changes/productize-agent-harness-experience/problem-log.md`
  - Covers: SC-8, SC-9, SC-10, SC-11, SC-12
