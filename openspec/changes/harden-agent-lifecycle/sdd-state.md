```yaml
schemaVersion: 1
change: harden-agent-lifecycle
phase: {name: sdd-coding, status: COMPLETED}
outcome: PASS_WITH_NOTES
nextPhase: null
returnPhase: null
sourceEvidence:
  baseRevision: 10cdefabaa802ed80c2b6051ac044e06f2dfb169
  preexistingWorktreeFiles:
    - "CLAUDE.md"
    - "README.md"
    - "docs/interview/当前进度与验收交接.md"
    - "docs/核心逻辑详解/RAG检索链路.md"
    - "docs/核心逻辑详解/目录索引.md"
    - "pom.xml"
    - "src/main/java/com/ai/agent/AgentPromptComposer.java"
    - "src/main/java/com/ai/agent/AgentRuntimeService.java"
    - "src/main/java/com/ai/agent/ConfigurableAgentExecutor.java"
    - "src/main/java/com/ai/agent/DataAnalysisAgent.java"
    - "src/main/java/com/ai/agent/DataAnalysisAgentImpl.java"
    - "src/main/java/com/ai/agent/capability/AgentCapabilityScope.java"
    - "src/main/java/com/ai/agent/react/ReActModelCaller.java"
    - "src/main/java/com/ai/agent/runtime/AgentExecutionStrategy.java"
    - "src/main/java/com/ai/agent/runtime/AgentRunCoordinator.java"
    - "src/main/java/com/ai/agent/runtime/AgentRunScope.java"
    - "src/main/java/com/ai/agent/runtime/ChatExecutionStrategy.java"
    - "src/main/java/com/ai/agent/runtime/ConfiguredAgentExecutionService.java"
    - "src/main/java/com/ai/agent/runtime/planning/ModelSemanticRequestRouter.java"
    - "src/main/java/com/ai/agent/runtime/planning/SemanticRequestRouter.java"
    - "src/main/java/com/ai/agent/tool/governance/AgentToolRegistry.java"
    - "src/main/java/com/ai/config/AsyncConfig.java"
    - "src/main/java/com/ai/config/ContextPropagatingTaskDecorator.java"
    - "src/main/java/com/ai/mcp/McpModelService.java"
    - "src/main/java/com/ai/mcp/ModelRetryExecutor.java"
    - "src/main/java/com/ai/memory/MemoryContextPromptFormatter.java"
    - "src/main/java/com/ai/memory/SemanticMemoryExtractor.java"
    - "src/main/java/com/ai/memory/dto/MemoryContext.java"
    - "src/main/java/com/ai/model/ConversationSession.java"
    - "src/main/java/com/ai/rag/EnhancedRagPipeline.java"
    - "src/main/java/com/ai/rag/rerank/HttpCrossEncoderRerankProvider.java"
    - "src/main/java/com/ai/rag/retrieval/RrfFusionRanker.java"
    - "src/main/java/com/ai/service/AnalysisStreamService.java"
    - "src/main/java/com/ai/vector/EmbeddingCallExecutor.java"
    - "src/main/java/com/ai/vector/EmbeddingCallRejectedException.java"
    - "docs/核心逻辑详解/框架集成与消息生命周期.md"
    - "openspec/changes/modernize-langchain4j-assistant/design.md"
    - "openspec/changes/modernize-langchain4j-assistant/tasks.md"
    - "openspec/changes/modernize-langchain4j-assistant/verification.md"
    - "src/main/java/com/ai/agent/AgentChatAssistant.java"
    - "src/main/java/com/ai/agent/AgentChatService.java"
    - "src/main/java/com/ai/config/TaskContextSnapshot.java"
    - "src/main/java/com/ai/mcp/ModelInvocationException.java"
    - "src/main/java/com/ai/mcp/ModelResponseStream.java"
    - "src/main/java/com/ai/mcp/ModelStreamRuntime.java"
    - "src/main/java/com/ai/mcp/StructuredAssistantFactory.java"
    - "src/main/java/com/ai/resilience/ExternalCallPolicy.java"
  taskLedger:
    - task: T1
      files: [src/main/java/com/ai/agent/runtime/AgentRunCoordinator.java, src/main/java/com/ai/agent/runtime/AgentRunRouteResolver.java, src/main/java/com/ai/agent/runtime/AgentRunRegistry.java, src/main/java/com/ai/agent/runtime/AgentExecutionMode.java, src/main/java/com/ai/agent/durable/AgentDurableRunStore.java, src/main/java/com/ai/agent/durable/AgentRunStateRepository.java, src/main/java/com/ai/mcp/ModelCallExecutor.java, src/main/java/com/ai/service/AgentExecutionTraceService.java, frontend/src/agent/runEvidence.js]
      evidence: "归属新增 PLANNING 创建/绑定、规划预算与取消、同步模型等待及空路线 Trace；不归属此前异步框架迁移。contracts.jsh 共 144 项通过，包含本项故障路径。"
    - task: T2
      files: [src/main/java/com/ai/service/SessionManager.java, src/main/java/com/ai/repository/ConversationSessionRepository.java, src/main/java/com/ai/agent/tool/AgentConversationRecorder.java, src/main/java/com/ai/agent/runtime/AgentRunCoordinator.java, src/main/java/com/ai/agent/durable/AgentResumeWorker.java]
      evidence: "归属保存先于完成、事务任务、行锁去重、提交后缓存失效与恢复成功保存。persistence-contracts.jsh 20 项含真实事务验证；contracts.jsh 含保存失败终态。"
    - task: T3
      files: [src/main/java/com/ai/indexing, src/main/java/com/ai/service/knowledge, src/main/java/com/ai/service/VectorMemoryService.java, src/main/java/com/ai/controller/KnowledgeController.java, src/main/java/com/ai/knowledge/dto/KnowledgeItemResponse.java, src/main/java/com/ai/config/AsyncConfig.java, frontend/src/pages/KnowledgePage.jsx, sql/init.sql, sql/upgrade-index-tasks.sql]
      evidence: "归属持久任务、知识短事务、旧事件删除、对话索引重放、状态API与页面。index-contracts.jsh 36 项与知识事务回滚检查通过；真实服务联调未执行。"
    - task: T4
      files: [src/main/java/com/ai/mcp/TokenMonitor.java, src/main/java/com/ai/mcp/McpContext.java, src/main/java/com/ai/mcp/McpContextManager.java, src/main/java/com/ai/mcp/McpModelService.java, src/main/java/com/ai/agent/context, src/main/java/com/ai/model/ConversationSession.java, src/main/java/com/ai/config/SharedTokenizer.java, src/main/resources/application.yml, src/main/resources/application-local.yml, src/main/resources/application-prod.yml]
      evidence: "归属统一估算、模型词表映射、完整轮次裁剪与Skill当前输入保护；不归属此前角色化消息和AiServices适配。contracts.jsh 含长串、词表、工具组及超限检查。"
    - task: T5
      files: [docs/interview/当前进度与验收交接.md, docs/核心逻辑详解/框架集成与消息生命周期.md, docs/核心逻辑详解/Agent运行时与Harness.md, docs/核心逻辑详解/RAG检索链路.md, docs/核心逻辑详解/目录索引.md, docs/术语, openspec/changes/harden-agent-lifecycle/verification.md]
      evidence: "JDK17 clean compile、前端构建、200项临时断言及 diff --check 通过；无仓库测试类，真实联调限制见 verification.md。"
taskProgress: {completed: [T1, T2, T3, T4, T5], current: null}
approvals: []
pendingChanges: []
testLevel: middle
```

授权依据：用户已确认上一轮四项架构建议并明确要求开始实施；不虚构对尚未存在的文档的审批。既有脏文件来自本会话前两批框架迁移，本 change 仅归属后续新增修改。
