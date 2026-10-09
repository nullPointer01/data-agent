schemaVersion: 1
change: official-skills-runtime
phase: { name: sdd-coding, status: COMPLETED }
outcome: PASS_WITH_NOTES
nextPhase: null
returnPhase: null
sourceEvidence:
  baseRevision: 9d0a625f4d731edf2bc0d1e08bc2bfa41a62c5bc
  preexistingWorktreeFiles:
    - docs/interview/INTERVIEW_TECH_STORIES.md
    - docs/interview/当前进度与验收交接.md
    - docs/核心逻辑详解/框架集成与消息生命周期.md
    - docs/核心逻辑详解/目录索引.md
    - src/main/java/com/ai/agent/agentic/AgenticDelegationWorkflow.java
    - src/main/java/com/ai/agent/agentic/AgenticSupervisorService.java
    - src/main/java/com/ai/integration/mcp/McpToolCatalog.java
    - src/main/java/com/ai/logging/StructuredLogger.java
    - src/main/java/com/ai/mcp/McpModelService.java
    - src/main/java/com/ai/mcp/ModelClientFactory.java
    - src/main/java/com/ai/agent/agentic/AgentLifecycleListener.java
    - src/main/java/com/ai/mcp/ModelCallListener.java
  taskLedger:
    - task: T1
      files: [pom.xml, src/main/java/com/ai/skill/SkillCatalog.java, src/main/java/com/ai/skill/Skill.java, src/main/java/com/ai/skill/DynamicSkill.java, src/main/java/com/ai/skill/SkillManager.java, src/main/java/com/ai/agent/tool/AgentSkillToolService.java, src/main/java/com/ai/mcp/McpContext.java, src/main/java/com/ai/mcp/McpContextManager.java, src/main/java/com/ai/mcp/McpModelService.java]
      scope: 官方 Skill 适配与六个旧类删除；McpModelService 仅删除旧 Skill 专用入口/依赖/无用内部参数，保留既有 Hook 修改
      evidence: verification.md
    - task: T2
      files: [src/main/java/com/ai/agent/AgentChatService.java, src/main/java/com/ai/agent/ConfigurableAgentExecutor.java, src/main/java/com/ai/agent/capability/AgentCapabilityBindingSnapshot.java, src/main/java/com/ai/agent/capability/AgentCapabilityRegistry.java, src/main/java/com/ai/agent/runtime/planning/AgentToolCandidateSelector.java, src/main/java/com/ai/agent/runtime/planning/ModelSemanticRequestRouter.java, src/main/java/com/ai/agent/runtime/planning/PersonalAgentRequestPlanner.java, src/main/java/com/ai/agent/tool/AgentTools.java, src/main/java/com/ai/agent/tool/ai/GovernedToolSession.java, src/main/java/com/ai/agent/tool/governance/AgentToolRegistry.java, src/main/java/com/ai/agent/tool/governance/AgentToolFailureClassifier.java]
      evidence: verification.md
    - task: T3
      files: [src/main/java/com/ai/config/DataInitializer.java, src/main/java/com/ai/config/CacheNames.java, src/main/java/com/ai/config/RedisConfig.java, src/main/java/com/ai/controller/SkillUploadController.java, src/main/java/com/ai/model/SkillConfig.java, src/main/java/com/ai/repository/SkillConfigRepository.java, src/main/java/com/ai/service/SkillGenerator.java, src/main/java/com/ai/service/SkillServiceImpl.java, src/main/java/com/ai/skill/dto/SkillRequest.java, src/main/java/com/ai/skill/dto/SkillResponse.java, frontend/src/pages/admin/SkillsPage.jsx, frontend/src/components/admin/ResourcePage.jsx, src/main/resources/static]
      scope: 移除启动/列表缓存、管理与 API 清理合同、前端失败提示及生成资源
      evidence: verification.md
    - task: T4
      files: [CLAUDE.md, README.md, docs/核心逻辑详解/Skill官方框架迁移.md, docs/核心逻辑详解/Agent运行时与Harness.md, docs/核心逻辑详解/框架集成与消息生命周期.md, docs/核心逻辑详解/目录索引.md, docs/interview/PROJECT_INTERVIEW_GUIDE.md, docs/interview/person.md, docs/interview/当前进度与验收交接.md]
      scope: 新增 Skill 学习文档；已有脏文档仅追加或纠正 Skill 说明，保留前轮 Hook 记录
      evidence: verification.md
taskProgress: { completed: [T1, T2, T3, T4], current: null }
approvals:
  - phase: conversation-implementation
    artifactRevision: 用户明确要求按照已讨论方案迁移 Skill 模块
    approvedBy: user
    approvedAt: 2026-10-08
pendingChanges: []
testLevel: middle
