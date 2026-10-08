schemaVersion: 1
change: aiservices-governed-tools
phase: { name: sdd-coding, status: COMPLETED }
outcome: PASS_WITH_NOTES
nextPhase: sdd-verify
sourceEvidence:
  baseRevision: 10cdefabaa802ed80c2b6051ac044e06f2dfb169
  preexistingWorktreeFiles:
    - "CLAUDE.md"
    - "README.md"
    - "docs/interview/INTERVIEW_TECH_STORIES.md"
    - "docs/interview/PROJECT_INTERVIEW_GUIDE.md"
    - "docs/interview/当前进度与验收交接.md"
    - "docs/核心逻辑详解/Agent运行时与Harness.md"
    - "docs/核心逻辑详解/RAG检索链路.md"
    - "docs/核心逻辑详解/目录索引.md"
    - "frontend/src/agent/runEvidence.js"
    - "frontend/src/pages/KnowledgePage.jsx"
    - "pom.xml"
    - "sql/init.sql"
    - "src/main/java/com/ai/agent/AgentPromptComposer.java"
    - "src/main/java/com/ai/agent/AgentRuntimeService.java"
    - "src/main/java/com/ai/agent/ConfigurableAgentExecutor.java"
    - "src/main/java/com/ai/agent/DataAnalysisAgent.java"
    - "src/main/java/com/ai/agent/DataAnalysisAgentImpl.java"
    - "src/main/java/com/ai/agent/capability/AgentCapabilityScope.java"
    - "src/main/java/com/ai/agent/capability/AgentCapabilityService.java"
    - "src/main/java/com/ai/agent/context/AgentContextGovernor.java"
    - "src/main/java/com/ai/agent/context/AgentContextProperties.java"
    - "src/main/java/com/ai/agent/context/ConservativeContextReducer.java"
    - "src/main/java/com/ai/agent/durable/AgentDurableRunStore.java"
    - "src/main/java/com/ai/agent/durable/AgentResumeWorker.java"
    - "src/main/java/com/ai/agent/durable/AgentRunStateEntity.java"
    - "src/main/java/com/ai/agent/durable/AgentRunStateRepository.java"
    - "src/main/java/com/ai/agent/react/ReActModelCaller.java"
    - "src/main/java/com/ai/agent/runtime/AgentExecutionMode.java"
    - "src/main/java/com/ai/agent/runtime/AgentExecutionStrategy.java"
    - "src/main/java/com/ai/agent/runtime/AgentRunContext.java"
    - "src/main/java/com/ai/agent/runtime/AgentRunCoordinator.java"
    - "src/main/java/com/ai/agent/runtime/AgentRunRegistry.java"
    - "src/main/java/com/ai/agent/runtime/AgentRunRouteResolver.java"
    - "src/main/java/com/ai/agent/runtime/AgentRunScope.java"
    - "src/main/java/com/ai/agent/runtime/ChatExecutionStrategy.java"
    - "src/main/java/com/ai/agent/runtime/ConfiguredAgentExecutionService.java"
    - "src/main/java/com/ai/agent/runtime/planning/ModelSemanticRequestRouter.java"
    - "src/main/java/com/ai/agent/runtime/planning/SemanticRequestRouter.java"
    - "src/main/java/com/ai/agent/tool/AgentConversationRecorder.java"
    - "src/main/java/com/ai/agent/tool/governance/AgentToolRegistry.java"
    - "src/main/java/com/ai/config/AsyncConfig.java"
    - "src/main/java/com/ai/config/ContextPropagatingTaskDecorator.java"
    - "src/main/java/com/ai/config/SharedTokenizer.java"
    - "src/main/java/com/ai/controller/KnowledgeController.java"
    - "src/main/java/com/ai/knowledge/dto/KnowledgeItemResponse.java"
    - "src/main/java/com/ai/mcp/McpContext.java"
    - "src/main/java/com/ai/mcp/McpContextManager.java"
    - "src/main/java/com/ai/mcp/McpModelService.java"
    - "src/main/java/com/ai/mcp/ModelRetryExecutor.java"
    - "src/main/java/com/ai/mcp/TokenMonitor.java"
    - "src/main/java/com/ai/memory/MemoryContextPromptFormatter.java"
    - "src/main/java/com/ai/memory/SemanticMemoryExtractor.java"
    - "src/main/java/com/ai/memory/dto/MemoryContext.java"
    - "src/main/java/com/ai/model/ConversationSession.java"
    - "src/main/java/com/ai/rag/EnhancedRagPipeline.java"
    - "src/main/java/com/ai/rag/rerank/HttpCrossEncoderRerankProvider.java"
    - "src/main/java/com/ai/rag/retrieval/RrfFusionRanker.java"
    - "src/main/java/com/ai/repository/ConversationSessionRepository.java"
    - "src/main/java/com/ai/service/AgentExecutionTraceService.java"
    - "src/main/java/com/ai/service/AnalysisStreamService.java"
    - "src/main/java/com/ai/service/SessionManager.java"
    - "src/main/java/com/ai/service/VectorMemoryService.java"
    - "src/main/java/com/ai/service/knowledge/KnowledgeService.java"
    - "src/main/java/com/ai/service/knowledge/KnowledgeSyncResultWriter.java"
    - "src/main/java/com/ai/service/knowledge/KnowledgeVectorEvent.java"
    - "src/main/java/com/ai/service/knowledge/KnowledgeVectorEventListener.java"
    - "src/main/java/com/ai/service/knowledge/KnowledgeVectorEventPublisher.java"
    - "src/main/java/com/ai/service/knowledge/KnowledgeVectorEventType.java"
    - "src/main/java/com/ai/service/knowledge/KnowledgeVectorIndexService.java"
    - "src/main/java/com/ai/vector/EmbeddingCallExecutor.java"
    - "src/main/java/com/ai/vector/EmbeddingCallRejectedException.java"
    - "src/main/resources/application-local.yml"
    - "src/main/resources/application-prod.yml"
    - "src/main/resources/application.yml"
    - "src/main/resources/static/assets/index-CQB4o72U.js"
    - "src/main/resources/static/index.html"
    - "docs/术语/"
    - "docs/核心逻辑详解/框架集成与消息生命周期.md"
    - "openspec/changes/harden-agent-lifecycle/"
    - "openspec/changes/modernize-langchain4j-assistant/"
    - "sql/upgrade-index-tasks.sql"
    - "src/main/java/com/ai/agent/AgentChatAssistant.java"
    - "src/main/java/com/ai/agent/AgentChatService.java"
    - "src/main/java/com/ai/config/TaskContextSnapshot.java"
    - "src/main/java/com/ai/indexing/"
    - "src/main/java/com/ai/mcp/ModelCallExecutor.java"
    - "src/main/java/com/ai/mcp/ModelInvocationException.java"
    - "src/main/java/com/ai/mcp/ModelResponseStream.java"
    - "src/main/java/com/ai/mcp/ModelStreamRuntime.java"
    - "src/main/java/com/ai/mcp/StructuredAssistantFactory.java"
    - "src/main/java/com/ai/resilience/"
    - "src/main/java/com/ai/service/knowledge/KnowledgeIndexScheduler.java"
    - "src/main/resources/static/assets/index-CEJF2R4g.js"
  taskLedger:
    - task: Task 1
      files: [/tmp/data-agent-aiservices-d1LQuL/FrameworkProbe.java]
      evidence: 4 个真实 AiServices 框架组合检查通过，无仓库测试类
    - task: Task 2
      files:
        - src/main/java/com/ai/agent/AgentChatAssistant.java
        - src/main/java/com/ai/agent/AgentChatService.java
        - src/main/java/com/ai/agent/AgentChatToolService.java
        - src/main/java/com/ai/agent/tool/ai/GovernedToolSession.java
        - src/main/java/com/ai/agent/tool/ai/AgentToolSuspendedException.java
        - src/main/java/com/ai/agent/ConfigurableAgentExecutor.java
        - src/main/java/com/ai/agent/durable/DurableToolContinuation.java
        - src/main/java/com/ai/agent/durable/AgentResumeWorker.java
        - src/main/java/com/ai/mcp/McpModelService.java
      evidence: 本轮只归属工具助手接入、流式 callback 异常转发和审批续跑替换；不归属此前生命周期与持久索引修改。172 项临时断言通过，含真实 Pipeline 和原生恢复检查。
    - task: Task 3
      files:
        - src/main/java/com/ai/agent/react/ReActLoopRunner.java
        - src/main/java/com/ai/agent/react/ReActModelCaller.java
        - src/main/java/com/ai/agent/react/ReActStepHandler.java
        - src/main/java/com/ai/agent/react/ReActSynchronousStepProcessor.java
        - src/main/java/com/ai/agent/react/ReActStreamingStepProcessor.java
        - src/main/java/com/ai/agent/react/ReActResponseParser.java
        - src/main/java/com/ai/agent/react/ReActRecoveryTracker.java
        - src/main/java/com/ai/agent/react/ReActRecoveryDecision.java
        - src/main/java/com/ai/agent/react/ReActLoopStepResult.java
        - src/main/java/com/ai/agent/react/ReActStepOutcome.java
        - src/main/java/com/ai/agent/react/SelfReflector.java
        - src/main/java/com/ai/agent/react/ReActExecutionResult.java
        - src/main/java/com/ai/agent/react/ReActWorkingMemoryService.java
        - src/main/java/com/ai/agent/AgentReasoningProperties.java
        - src/main/resources/application.yml
        - CLAUDE.md
        - README.md
        - docs/核心逻辑详解/框架集成与消息生命周期.md
        - docs/核心逻辑详解/Agent工具治理.md
        - docs/核心逻辑详解/Agent运行时与Harness.md
        - docs/interview/PROJECT_INTERVIEW_GUIDE.md
        - docs/interview/INTERVIEW_TECH_STORIES.md
        - docs/interview/当前进度与验收交接.md
      evidence: 删除 11 个已替代类；配置仅删除 reflection-enabled；文档只归属 AiServices 工具链路更新。生产源引用检查和 diff --check 通过。
    - task: Task 4
      files: [/tmp/data-agent-aiservices-d1LQuL/GovernedToolsCheck.java, openspec/changes/aiservices-governed-tools/verification.md]
      evidence: JDK17 clean compile 和最终 compile 通过（547 源码），172 项断言通过；真实厂商、审批数据库与浏览器验收未运行。
taskProgress: { completed: [Task 1, Task 2, Task 3, Task 4], current: null }
approvals:
  - phase: conversation-plan
    artifactRevision: 会话五步计划（必要时保留审批专用续跑）
    approvedBy: user
    approvedAt: 2026-09-30
pendingChanges: []
testLevel: middle
