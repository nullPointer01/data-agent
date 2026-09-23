# SDD State

```yaml
schemaVersion: 1
change: modernize-agent-context-routing
phase: { name: sdd-verify, status: COMPLETED }
outcome: PASS_WITH_NOTES
returnPhase: null
nextPhase: null
verificationScope: '仅 C004 记忆准入修复：10 条真实对话、19 项合同/准入检查、JDK 17 编译及 diff 检查通过；模型语义误判仍非零保证，前轮其他遗留及未验路径保持未结，不代表项目整体通过；未新增测试类'
verificationArtifact: memory-admission-verification.md
sourceEvidence:
  baseRevision: 94069f8507276bf29d5556a3a41245ffe20c80ad
  preexistingWorktreeFiles: [docker-compose.yml, docs/interview/INTERVIEW_TECH_STORIES.md, docs/interview/当前进度与验收交接.md, docs/核心逻辑详解/Agent运行时与Harness.md, frontend/src/pages/AgentEvalPage.jsx, frontend/src/pages/TracePage.jsx, openspec/changes/modernize-agent-context-routing/design.md, openspec/changes/modernize-agent-context-routing/proposal.md, openspec/changes/modernize-agent-context-routing/sdd-assumptions.md, openspec/changes/modernize-agent-context-routing/sdd-state.md, openspec/changes/modernize-agent-context-routing/tasks.md, openspec/changes/modernize-agent-context-routing/verify-report.md, pom.xml, sql/init.sql, sql/upgrade-agent-context-routing.sql, sql/upgrade-agent-eval.sql, src/main/java/com/ai/agent/ConfigurableAgentExecutor.java, src/main/java/com/ai/agent/eval/AgentEvalMetricCalculator.java, src/main/java/com/ai/agent/eval/AgentEvalResultEntity.java, src/main/java/com/ai/agent/eval/AgentEvalSampleEntity.java, src/main/java/com/ai/agent/eval/AgentEvalService.java, src/main/java/com/ai/agent/eval/dto/AgentEvalReportResponse.java, src/main/java/com/ai/agent/runtime/AgentRunCoordinator.java, src/main/java/com/ai/agent/runtime/AgentRunEvidenceService.java, src/main/java/com/ai/agent/runtime/ConfiguredAgentExecutionService.java, src/main/java/com/ai/agent/runtime/planning/AgentPlanningProperties.java, src/main/java/com/ai/agent/runtime/planning/AgentToolCandidateSelector.java, src/main/java/com/ai/agent/runtime/planning/ContextRequirement.java, src/main/java/com/ai/agent/runtime/planning/ModelSemanticRequestRouter.java, src/main/java/com/ai/agent/runtime/planning/PersonalAgentRequestPlanner.java, src/main/java/com/ai/agent/runtime/planning/RequestContextPlan.java, src/main/java/com/ai/agent/runtime/planning/RequestExecutionPlan.java, src/main/java/com/ai/agent/runtime/planning/RequestPlanningEvidence.java, src/main/java/com/ai/agent/runtime/planning/SemanticRequestRouter.java, src/main/java/com/ai/memory/MemoryContextPromptFormatter.java, src/main/java/com/ai/memory/MemoryManager.java, src/main/java/com/ai/memory/dto/MemoryContext.java, src/main/java/com/ai/model/ConversationSession.java, src/main/java/com/ai/vector/MilvusVectorStoreGateway.java, src/main/resources/application.yml, src/main/resources/static/assets/index-CQB4o72U.js, src/main/resources/static/assets/index-DS6v5Wps.js, src/main/resources/static/index.html, src/test/java/com/ai/memory/LongTermMemoryTest.java, src/test/java/com/ai/memory/SemanticMemoryExtractorTest.java, src/test/java/com/ai/memory/SemanticMemoryServiceTest.java, src/test/java/com/ai/memory/UserMemoryProfileExtractorTest.java, src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker]
  workingTreeBaseline: /tmp/data-agent-routing.soMPo8
  c002WorkingTreeBaseline: /tmp/data-agent-context-c002.sMY4h4
  c003WorkingTreeBaseline: /tmp/data-agent-schema-c003.iWgj1t
  runtimeVerificationBaseline: /tmp/data-agent-verify-v7.2h5Qqm
  c004WorkingTreeBaseline: /tmp/data-agent-memory-c004.4eO4yp
  taskLedger:
    - task: Task 24
      files: [src/main/java/com/ai/memory/SemanticMemoryExtractor.java, src/main/java/com/ai/memory/MemoryWorthinessEvaluator.java, src/main/java/com/ai/memory/ConversationMemoryCaptureService.java, src/main/java/com/ai/memory/SemanticMemoryExtractionJob.java, src/main/java/com/ai/memory/SemanticMemoryService.java]
      evidence: '相对 c004WorkingTreeBaseline：统一 speechAct/scope 语义提取与严格准入，删除关键词直存及整句指纹兜底；同步前缀仅调度；semantic-v2；546 源码编译通过。'
    - task: Task 25
      files: [openspec/changes/modernize-agent-context-routing/memory-admission-verification.md, CLAUDE.md, docs/核心逻辑详解/用户语义记忆.md, docs/核心逻辑详解/目录索引.md, docs/interview/当前进度与验收交接.md]
      evidence: '10/10 真实对话及 19 项合同/准入检查通过；清理本轮 12 条记忆和 10 个会话，停用精确 QA 账号，停止本轮后端；历史用户数据未动。未新增测试类或扩大为整体通过。'
    - task: Task 22
      files: [src/main/java/com/ai/memory/MemoryContextPromptFormatter.java, src/main/java/com/ai/service/AgentExecutionTraceService.java]
      evidence: '相对 runtimeVerificationBaseline：四处画像集合判空改用 CollectionUtils；新 Trace 的旧工具兜底标志明确为 false，保留历史读取。首次 JDK 17 编译复现四条错误，修复后 546 个源码编译成功。'
    - task: Task 23
      files: [openspec/changes/modernize-agent-context-routing/runtime-verification-v7.md]
      evidence: 'local 真实 GPT Schema、SSE、多轮、REQUIRED/OPTIONAL 知识、calculate、跨会话记忆与双用户隔离均已运行，前端构建成功；发现记忆写入误判，未把通路成功当作整体正确。隔离资料/主账号会话记忆清理，两账号停用，验收后端停止。'
    - task: Task 21
      files: [src/main/resources/application-local.yml, CLAUDE.md, docs/核心逻辑详解/Agent运行时与Harness.md, docs/核心逻辑详解/目录索引.md, docs/interview/当前进度与验收交接.md, openspec/changes/modernize-agent-context-routing/structured-routing-verification.md]
      evidence: '用户明确要求复用现有 GPT 并代为配置。local 文件本轮修改前干净，相对 HEAD 仅新增 planning.model-id 及注释；只读数据库确认 GPT 记录启用且属共享 default 租户。静态配置/调用链及 diff 检查完成，未重启或在线验收。'
    - task: Task 18
      files: [src/main/java/com/ai/mcp/McpModelService.java, src/main/java/com/ai/mcp/ModelClientFactory.java, src/main/java/com/ai/mcp/ModelClientRegistry.java, src/main/java/com/ai/config/LangChain4jConfig.java, src/main/java/com/ai/service/ModelConnectionProbeService.java]
      evidence: '相对 c003WorkingTreeBaseline：请求级格式共用治理路径、strict 原生 Schema、删除 JSON 客户端缓存；官方 1.19.0 源码签名核对，工厂三个调用点已同步。'
    - task: Task 19
      files: [src/main/java/com/ai/agent/runtime/planning/ModelSemanticRequestRouter.java, src/main/java/com/ai/agent/runtime/planning/AgentPlanningProperties.java, src/main/java/com/ai/agent/runtime/planning/RequestExecutionPlan.java, src/main/resources/application.yml]
      evidence: '相对 c003WorkingTreeBaseline：七字段 Schema 与 Decision 对齐；默认原生输出、显式 JSON_OBJECT；严格 reader/权限校验共用，版本 v7。'
    - task: Task 20
      files: [CLAUDE.md, docs/核心逻辑详解/Agent运行时与Harness.md, docs/核心逻辑详解/目录索引.md, docs/interview/当前进度与验收交接.md, openspec/changes/modernize-agent-context-routing/structured-routing-verification.md]
      evidence: '官方调研和轻量模型配置限制已同步；git diff --check、生产引用检查与测试目录缺失检查通过。未编译、构建或调用模型，不宣称在线兼容/质量通过。'
    - task: Task 14
      files: [src/main/java/com/ai/agent/runtime/planning/ModelSemanticRequestRouter.java, src/main/java/com/ai/agent/runtime/planning/SemanticRequestRouter.java]
      evidence: '相对 c002WorkingTreeBaseline：类型化 Decision 直接绑定，候选列表保留重复项；严格 reader 代替逐字段 JsonNode，保留语义和候选准入。'
    - task: Task 15
      files: [src/main/java/com/ai/memory/MemoryManager.java, src/main/java/com/ai/memory/dto/MemoryContext.java, src/main/java/com/ai/memory/MemoryContextPromptFormatter.java, src/main/java/com/ai/agent/runtime/ConfiguredAgentExecutionService.java, src/main/java/com/ai/agent/runtime/planning/RequestExecutionPlan.java]
      evidence: '相对 c002WorkingTreeBaseline：按 Session/画像/长期检索装配，删除摘要注入字段，长期检索入口集中，版本 v6。'
    - task: Task 15a
      files: [src/main/java/com/ai/memory/ShortTermMemory.java, src/main/java/com/ai/repository/MemoryEntryRepository.java, docs/核心逻辑详解/用户语义记忆.md]
      evidence: '相对 c002WorkingTreeBaseline：仅删除因停止自动摘要注入而无调用的 recall/markAccessed/findActiveByTenantUserAndTier；摘要写入/管理/维护保留。同步记忆说明开头及更新记录。'
    - task: Task 16
      files: [src/main/java/com/ai/agent/tool/AgentKnowledgeToolService.java, src/main/java/com/ai/rag/RagRetrievalService.java]
      evidence: '相对 c002WorkingTreeBaseline：searchKnowledge 共用双身份混合 RAG，删除旧纯向量旁路和问候词跳过；searchMemory 复用长期检索。'
    - task: Task 17
      files: [CLAUDE.md, docs/核心逻辑详解/Agent运行时与Harness.md, docs/核心逻辑详解/目录索引.md, docs/interview/当前进度与验收交接.md, openspec/changes/modernize-agent-context-routing/context-retrieval-verification.md]
      evidence: '仅新增/修订 v6 说明、链接与静态验证记录；git diff --check、生产引用检查、测试目录缺失检查及 Jackson API 签名检查完成。'
    - task: Task 10
      files: [src/main/java/com/ai/agent/runtime/planning/SemanticRequestRouter.java, src/main/java/com/ai/agent/runtime/planning/ModelSemanticRequestRouter.java, src/main/java/com/ai/agent/runtime/planning/AgentToolCandidateSelector.java]
      evidence: '相对 workingTreeBaseline/planning：新增能力元数据输入及精确候选；替换关键词选择器；严格 JSON 合同；模型返回后检查根 Run 状态。见验证记录静态检查。'
    - task: Task 11
      files: [src/main/java/com/ai/agent/runtime/planning/PersonalAgentRequestPlanner.java, src/main/java/com/ai/agent/runtime/planning/RequestExecutionPlan.java, src/main/java/com/ai/agent/capability/AgentCapabilityService.java]
      evidence: '相对 workingTreeBaseline：删除意图词表、动词计数和未使用 Skill 判定；版本 v5；自动模式根据候选推导；删除 toolSelectionFallback 计划字段。'
    - task: Task 12
      files: [src/main/java/com/ai/agent/ConfigurableAgentExecutor.java, src/main/java/com/ai/agent/runtime/AgentRunCoordinator.java, src/main/java/com/ai/agent/runtime/planning/RequestPlanningEvidence.java, src/main/java/com/ai/agent/capability/AgentCapabilityBindingSnapshot.java, src/main/java/com/ai/agent/durable/AgentResumeWorker.java]
      evidence: '相对 workingTreeBaseline：执行工具规格严格交集；添加规划边界及专家描述提示；planning 作用域按实际记录；执行和审批恢复共用 callableToolNames，保持 Checkpoint 交集。'
    - task: Task 13
      files: [src/main/resources/application.yml, CLAUDE.md, docs/核心逻辑详解/Agent运行时与Harness.md, docs/interview/当前进度与验收交接.md, docs/核心逻辑详解/目录索引.md, openspec/changes/modernize-agent-context-routing/proposal.md, openspec/changes/modernize-agent-context-routing/design.md, openspec/changes/modernize-agent-context-routing/tasks.md, openspec/changes/modernize-agent-context-routing/sdd-assumptions.md, openspec/changes/modernize-agent-context-routing/sdd-state.md, openspec/changes/modernize-agent-context-routing/routing-revision-verification.md]
      evidence: 'application.yml 只修改 planning 注释；同步 v5 描述及历史证据边界，记录人工验收输入；git diff --check 与测试目录缺失检查通过。'
taskProgress: { completed: [Task 10, Task 11, Task 12, Task 13, Task 14, Task 15, Task 15a, Task 16, Task 17, Task 18, Task 19, Task 20, Task 21, Task 22, Task 23, Task 24, Task 25], current: null }
approvals:
  - phase: sdd-coding-plan
    artifactRevision: 2026-09-23-C004
    approvedBy: user
    evidence: '在两项记忆误写的真实验收报告后明确要求继续修复；此前要求同类问题统一解决、不堆补丁。统一准入为范围内实现决策。'
  - phase: sdd-verify
    artifactRevision: 2026-09-23-runtime
    approvedBy: user
    evidence: '开始验证项目；截图四处编译错误后要求改一下。允许本次编译/启动/接口验收，仍不新增测试类。'
  - phase: sdd-coding-plan
    artifactRevision: 2026-09-23-C003
    approvedBy: user
    evidence: '两种方式哪个好哪种好直接改；明确授权调研后选择并实施，不需要再次确认。'
  - phase: sdd-coding-plan
    artifactRevision: 2026-09-23-C002
    approvedBy: user
    evidence: '本轮知识检索要统一的指令及此前按照你的来吧的架构改造授权；类型绑定和来源解耦为范围内实现选择，不扩大外部操作。'
  - phase: sdd-coding-plan
    artifactRevision: 2026-09-23-C001
    approvedBy: user
    evidence: '按照你的来吧，我也觉得需要去改（承接上轮明确方案；实施细节按授权落地）'
pendingChanges:
  - id: C004
    source: '真实验收发现回忆问句及临时任务要求误写长期记忆，用户要求继续修复'
    impact: design
    affectedTasks: [Task 24, Task 25]
    returnPhase: sdd-verify
    status: RESOLVED
    resolution: '已同步单一语义提取准入设计与任务；用户授权持续有效，保留原摘要/异步/记账和用户数据边界。'
  - id: C003
    source: '用户要求择优直接修改结构化路由并调研通行实现'
    impact: design
    affectedTasks: [Task 18, Task 19, Task 20]
    returnPhase: sdd-verify
    status: RESOLVED
    resolution: '已同步设计与任务，按现有授权实现请求级原生合同与显式兼容模式。'
  - id: C002
    source: '用户要求优化意图理解接口、记忆装配，并统一知识检索；承接本会话已授权的架构改造'
    impact: design
    affectedTasks: [Task 14, Task 15, Task 15a, Task 16, Task 17]
    returnPhase: sdd-verify
    status: RESOLVED
    resolution: 'design/tasks 已同步，沿用会话授权实现；引用审查补充 Task 15a 清理因本轮解耦失去调用的摘要方法。不改变模型服务配置、部署或用户数据。'
  - id: C001
    source: 用户批准关键词路由收敛
    impact: design
    affectedTasks: [Task 10, Task 11, Task 12, Task 13]
    returnPhase: sdd-verify
    status: RESOLVED
    resolution: 已同步 design/tasks；用户已授权该方案，继续实施。
testLevel: low
```

以下为上轮历史状态，51/51 不能作为本轮改动的验证证据。

- Change: `modernize-agent-context-routing`
- Phase: Verify
- Explore: completed
- Propose: completed
- Approval: 用户已明确要求按既定顺序改造并完成
- Current task: completed
- Verification: passed (JDK 17 backend 51/51 before test-source removal, frontend production build, diff check, real Router/RAG/Tool/Eval runtime)
- Test sources: removed after verification per user's repository policy
- Archive: pending
