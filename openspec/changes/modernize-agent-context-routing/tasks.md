# Tasks

## C004：记忆写入准入

- [x] Task 24：合并显式/隐式语义提取，移除关键词直接入库及整句指纹兜底。
  - Files: `SemanticMemoryExtractor.java`, `MemoryWorthinessEvaluator.java`, `ConversationMemoryCaptureService.java`, `SemanticMemoryExtractionJob.java`, `SemanticMemoryService.java`
  - Acceptance: 仅跨会话的用户陈述/真实保存请求通过同一校验；问题、临时任务、引用、否定与假设不写；稳定键类型匹配；开关/故障均不绕过准入。
  - Verify: JDK 17 编译，源码调用点审查，隔离账号真实正反例请求和记忆列表对照；不新增测试类。
  - Covers: C004 写入边界及原 SC2 来源治理。
- [x] Task 25：真实回归、数据收尾及文档同步。
  - Files: `memory-admission-verification.md`, `docs/核心逻辑详解/用户语义记忆.md`, `docs/核心逻辑详解/目录索引.md`, `docs/interview/当前进度与验收交接.md`, `CLAUDE.md`
  - Acceptance: 明确真实调用和合同校验的证据边界，不宣称模型语义判断永不出错；保存耗时与未验事项如实记录。
  - Verify: 原故障不新增长期记忆，正例保存及相同属性修正生效，隔离数据清理，差异检查。
  - Covers: C004 验收闭环。

## 2026-09-23 在线验收与编译修复

- [x] Task 22：修复真实编译发现的清理残留并重新编译。
  - Files: `MemoryContextPromptFormatter.java`, `AgentExecutionTraceService.java`
  - Acceptance: 不恢复已删除的宽泛工具兜底，画像集合安全判空；JDK 17 编译通过。
  - Verify: 用户“开始验证项目 / 改一下”授权编译；首次复现四条错误，最小修复后重编译，不新增测试类。
- [x] Task 23：启动最新后端，使用隔离验收数据验证 GPT Schema 路由与主要上下文链路。
  - Acceptance: 区分实际成功、降级、失败、未覆盖；保留新版本证据，不复用旧成绩。
  - Verify: 真实请求与 Trace；不操作用户已有会话/知识，不执行真实业务写工具。
  - Result: 已完成首轮运行验收；编译/构建和主要执行链路通过，发现长期记忆写入误判，整体未通过。见 `runtime-verification-v7.md`，任务完成不等于所有功能验收通过。

## 2026-09-23 后续：绑定现有 GPT

- [x] Task 21：按用户“轻量模型就用我的当前配置 GPT / 你来配置吧”的授权，将 local 规划模型显式绑定现有启用记录。
  - Acceptance: 复用 `gpt-5.6-terra` 的数据库 modelId，保留原模型连接参数和公共默认；不新增模型、不修改数据库。
  - Verify: 只读核实 modelId、model_name、enabled 和共享租户权限；静态核对 profile 默认 local、Router 显式 ID 优先级及 diff。未重启、构建或在线调用。

## 2026-09-23 C003：原生结构化路由

- [x] Task 18：在统一模型调用路径支持请求级 JSON Schema，删除重复 JSON 客户端缓存。
  - Acceptance: 同步默认/自定义客户端均发送请求格式；原有配额、重试、记账及 Run 治理复用；普通调用保持原行为。
  - Verify: LangChain4j 1.19.0 官方源码签名与调用链静态核对。
- [x] Task 19：Router 七字段原生合同、显式兼容模式及 v7。
  - Acceptance: 默认 JSON_SCHEMA，可显式 JSON_OBJECT；不自动追加降级调用；保留严格解析和授权校验。
  - Verify: 枚举/字段与 Decision 对齐、配置绑定和失败路径静态审查。
- [x] Task 20：记录官方调研、轻量模型配置方法及运行限制，同步知识库。
  - Acceptance: 不把协议改进等同于意图准确率提升，不声称已启用独立轻量模型或完成在线验收。
  - Verify: diff 检查、引用检查，不新增测试类或主动构建。

## 2026-09-23 C002：合同简化与上下文统一

- [x] Task 14：类型化语义规划结果，保留严格拒绝策略。
  - Acceptance: 模型理解职责不变；直接绑定 Decision；无手工字段映射；未知/缺失/null/强制转换/重复候选均拒绝。
  - Verify: 静态检查 Jackson 配置、记录类型和所有调用点；不新增测试类。
  - Files: `SemanticRequestRouter.java`, `ModelSemanticRequestRouter.java`
  - Covers: SC3
- [x] Task 15：按来源装配记忆，统一长期记忆读取。
  - Acceptance: 历史和工作状态按当前 Session；画像独立；semanticMemory 只触发长期检索，不混入全用户近期摘要；单类故障不清空其他来源。
  - Verify: 静态追踪 REQUIRED/OPTIONAL/OFF、身份与异常路径。
  - Files: `MemoryManager.java`, `MemoryContext.java`, `MemoryContextPromptFormatter.java`, `ConfiguredAgentExecutionService.java`, `RequestExecutionPlan.java`
  - Covers: SC1, SC2, SC4, SC5
- [x] Task 16：知识工具统一到混合 RAG，删除旧检索旁路。
  - Acceptance: searchKnowledge 与预加载共用 tenantId+userId 过滤的 RagRetrievalService；保留引用；无问候词误跳过；searchMemory 共用 Task 15 的长期检索入口。
  - Verify: 静态追踪双入口至检索过滤器，检查空结果/故障分支。
  - Files: `AgentKnowledgeToolService.java`, `RagRetrievalService.java`
  - Covers: SC4, SC5
- [x] Task 15a：删除装配解耦后无调用的近期摘要召回方法。
  - Acceptance: 移除 ShortTermMemory.recall、专用访问计数辅助函数及唯一配套查询；摘要写入/查看/维护功能保留。
  - Verify: 搜索全部生产引用，确认这些方法已无消费者。
  - Files: `ShortTermMemory.java`, `MemoryEntryRepository.java`, `docs/核心逻辑详解/用户语义记忆.md`
  - Covers: SC2
- [x] Task 17：文档及静态验收收口。
  - Acceptance: 区分传输格式与模型理解，更新 v6 记忆装配和检索路径；保留运行未验说明。
  - Verify: 差异检查、生产调用点搜索、人工验收清单；不主动运行测试/编译/构建。
  - Files: `CLAUDE.md`, `docs/核心逻辑详解/Agent运行时与Harness.md`, `docs/核心逻辑详解/目录索引.md`, `docs/interview/当前进度与验收交接.md`, `context-retrieval-verification.md`
  - Covers: SC3, SC5, SC6, SC7（运行待验）

Task 14 与 Task 15 相互独立；Task 16 使用 Task 15 的记忆入口；最后完成 Task 17。流程 artifacts（design/tasks/state/assumptions）同步维护，不归入业务源码范围。

C002 实现及指定静态检查已完成，模型和编译运行未验。详见 `context-retrieval-verification.md`，不引用旧版运行成绩。

## 2026-09-23 用户批准的关键词路由收敛

- [x] Task 10：构建已授权能力摘要与严格 Router 合同。
  - Acceptance: 基于 Registry 元数据选择工具；输入/输出有界，未知名称、非法 JSON 合同失败关闭。
  - Verify: 静态检查字段类型、目录交集和上限；不新增测试类。
  - Files: `SemanticRequestRouter.java`, `ModelSemanticRequestRouter.java`, `AgentToolCandidateSelector.java`
  - Covers: SC3, SC5
- [x] Task 11：移除关键词规划，落实确定性会话和工具驱动模式。
  - Acceptance: 仅精确能力介绍本地响应；OPTIONAL/模式/候选一致；保留固定模式和保守降级。
  - Verify: 搜索旧词表、调用点、模式边界并人工推演。
  - Files: `PersonalAgentRequestPlanner.java`, `RequestExecutionPlan.java`, `AgentCapabilityService.java`
  - Covers: SC1, SC2, SC3, SC4, SC5
- [x] Task 12：执行侧移除宽泛 fallback，展示必要的能力与查询提示。
  - Acceptance: 实际模型规格仍与能力交集；OFF 检索不暴露；技能/委派看到绑定描述；路由失败有明确能力限制。
  - Verify: 检查同步/流式共用路径、审批 Checkpoint 的工具白名单。
  - Files: `ConfigurableAgentExecutor.java`, `AgentRunCoordinator.java`, `RequestPlanningEvidence.java`, `AgentCapabilityBindingSnapshot.java`, `AgentResumeWorker.java`
  - Covers: SC4, SC5, SC6
- [x] Task 13：配置、说明与静态验收收口。
  - Acceptance: 配置注释与新路由一致，保留人工 Eval 用例和实测限制，不复用旧 51/51 成绩。
  - Verify: `git diff --check`、生产源码引用检查；不主动测试/编译/构建。
  - Files: `application.yml`, `CLAUDE.md`, `docs/核心逻辑详解/Agent运行时与Harness.md`, `docs/interview/当前进度与验收交接.md`, `docs/核心逻辑详解/目录索引.md`, `routing-revision-verification.md`
  - Covers: SC3, SC6, SC7（按用户后续约束以静态检查及人工 Eval 接替已删除单测）

## 上轮历史任务

本轮 Task 10–13 的完成只表示代码、文档及指定静态审查已完成，不表示真实模型或构建验收通过。详情见 `routing-revision-verification.md`。

> 说明：下列测试名称记录本次改造时采用的历史验证方式。验证完成后，用户按项目既定约束要求删除全部 `src/test` 测试源码与测试专属依赖，仓库不再保留这些测试文件。

- [x] Task 1: 建立 Context Plan 类型与黄金集测试（测试/实现）
  - Acceptance: 四类来源支持三态；至少 30 个固定规划样本可加载并执行。
  - Verify: `ContextRequirementTest`、`RequestPlanningGoldenDatasetTest`。
  - Files: `ContextRequirement.java`, `RequestContextPlan.java`, `request-planning-golden.json`, `RequestPlanningGoldenDatasetTest.java`, `RequestExecutionPlan.java`
  - Covers: SC1, SC2, SC7

- [x] Task 2: 会话状态确定性接入（测试/实现）
  - Acceptance: 有历史默认 REQUIRED，空会话 OFF；执行层按计划加载。
  - Verify: `PersonalAgentRequestPlannerTest`、`ConfiguredAgentExecutionServiceTest`。
  - Files: `PersonalAgentRequestPlanner.java`, `ConfiguredAgentExecutionService.java`, `MemoryManager.java`, `PersonalAgentRequestPlannerTest.java`, `ConfiguredAgentExecutionServiceTest.java`
  - Covers: SC1, SC2

- [x] Task 3: 实现安全的结构化语义 Router（测试/实现）
  - Acceptance: 仅模糊请求调用；非法枚举、低置信度、异常均保守降级。
  - Verify: `SemanticRequestRouterTest`、`HybridRequestPlannerTest`。
  - Files: `SemanticRequestRouter.java`, `ModelSemanticRequestRouter.java`, `AgentPlanningProperties.java`, `ModelSemanticRequestRouterTest.java`, `HybridRequestPlannerTest.java`
  - Covers: SC3

- [x] Task 4: 混合规划与 standalone query（测试/实现）
  - Acceptance: 路由结果生成三态计划和独立检索查询，原问题不变。
  - Verify: 规划器与检索调用参数测试。
  - Files: `PersonalAgentRequestPlanner.java`, `RequestExecutionPlan.java`, `ConfigurableAgentExecutor.java`, `PersonalAgentRequestPlannerTest.java`, `ConfigurableAgentExecutorTest.java`
  - Covers: SC3, SC4

- [x] Task 5: OPTIONAL 检索接入受治理工具（测试/实现）
  - Acceptance: OPTIONAL 知识/记忆进入 ReAct 精确候选；无绑定或无权限不扩大能力。
  - Verify: `AgentToolCandidateSelectorTest`、`ConfigurableAgentExecutorTest`。
  - Files: `AgentToolCandidateSelector.java`, `PersonalAgentRequestPlanner.java`, `ConfigurableAgentExecutor.java`, `AgentToolCandidateSelectorTest.java`, `ConfigurableAgentExecutorTest.java`
  - Covers: SC5

- [x] Task 6: Trace 和前端展示结构化计划（实现/测试）
  - Acceptance: 新 Run 展示四类三态来源、Router 来源和查询改写；旧 Trace 仍可读。
  - Verify: 后端证据测试和前端构建。
  - Files: `AgentRunCoordinator.java`, `AgentRunEvidenceService.java`, `AgentExecutionTraceService.java`, `TracePage.jsx`, `runEvidence.js`
  - Covers: SC2, SC4

- [x] Task 7: 扩展 Agent Eval 数据合同（测试/实现）
  - Acceptance: 样本和结果可持久化期望/观察路由与上下文标签。
  - Verify: 实体、SQL 和 Service 测试。
  - Files: `AgentEvalSampleEntity.java`, `AgentEvalResultEntity.java`, `AgentEvalService.java`, `upgrade-agent-context-routing.sql`, `init.sql`
  - Covers: SC6

- [x] Task 8: 增加路由与上下文指标（测试/实现）
  - Acceptance: 精确匹配率可计算；缺证据为 unavailable。
  - Verify: `AgentEvalMetricCalculatorTest`。
  - Files: `AgentEvalMetricCalculator.java`, `AgentEvalReportResponse.java`, `AgentEvalMetricCalculatorTest.java`, `AgentEvalService.java`
  - Covers: SC6

- [x] Task 9: 配置、文档与全量验证（实现/验证）
  - Acceptance: 固定配置可直接启动；核心文档与真实代码一致；全量后端测试和前端构建通过。
  - Verify: Maven 全量测试、npm build、代码搜索和变更审查。
  - Files: `application.yml`, `application-local.yml`, `application-prod.yml`, `Agent运行时与Harness.md`, `当前进度与验收交接.md`
  - Covers: SC7
