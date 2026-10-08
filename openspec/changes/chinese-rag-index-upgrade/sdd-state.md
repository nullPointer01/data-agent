schemaVersion: 1
change: chinese-rag-index-upgrade
phase: { name: sdd-coding, status: COMPLETED }
outcome: PASS_WITH_NOTES
nextPhase: sdd-verify
returnPhase: null
sourceEvidence:
  baseRevision: 10cdefabaa802ed80c2b6051ac044e06f2dfb169
  preexistingWorktreeFilesEvidence: baseline.txt
  taskLedger:
    - task: T1
      files: [docker/elasticsearch/Dockerfile, docker-compose.yml, src/main/java/com/ai/rag/elasticsearch/ElasticsearchProperties.java, src/main/java/com/ai/rag/elasticsearch/ElasticsearchIndexContract.java, src/main/java/com/ai/rag/elasticsearch/ElasticsearchFullTextClient.java, src/main/resources/application.yml]
      ownedHunks: ICU build、v3/analyzer、strict typed 契约、周期/并发校验、ES 身份主键；保留此前 SDK 搜索实现
      evidence: verification.md / IndexContractCheck，Docker compose config
    - task: T2
      files: [src/main/java/com/ai/rag/indexing/RagDocumentIndexer.java, src/main/java/com/ai/service/VectorMemoryService.java, src/main/java/com/ai/service/knowledge/KnowledgeVectorIndexService.java, src/main/java/com/ai/service/file/FileProcessingWorker.java]
      ownedHunks: 单次分块、replaceDocumentChunks、知识/文件委派、移除重复分块与吞异常记忆写入；保留既有框架 Retriever 迁移
      evidence: verification.md / DualIndexCheck、MemoryFailureCheck、FileWorkerCheck
    - task: T3
      files: [src/main/java/com/ai/indexing/IndexTask.java, src/main/java/com/ai/indexing/IndexTaskStore.java, src/main/java/com/ai/indexing/IndexTaskWorker.java, src/main/java/com/ai/indexing/IndexTaskProcessor.java, src/main/java/com/ai/indexing/IndexRebuildService.java, src/main/java/com/ai/indexing/IndexRebuildController.java, src/main/java/com/ai/indexing/RagIndexMaintenance.java, src/main/java/com/ai/indexing/RagIndexMaintenanceFilter.java, src/main/java/com/ai/service/file/FileMetadataLifecycleService.java, src/main/java/com/ai/service/file/FileProcessingServiceImpl.java, src/main/java/com/ai/service/file/FileProcessingSubmissionService.java, src/main/java/com/ai/service/file/FileProcessingSubmissionResult.java, src/main/java/com/ai/service/file/FileVectorCleanupService.java, src/main/java/com/ai/config/AsyncConfig.java, src/main/java/com/ai/security/auth/SecurityConfig.java, src/main/java/com/ai/memory/MemoryDecayScheduler.java, src/main/java/com/ai/service/knowledge/KnowledgeSyncService.java, src/main/java/com/ai/agent/durable/AgentResumeWorker.java]
      ownedHunks: FILE/MEMORY、任务路由、scoped 文件事务/状态、删除旧旁路、维护入口与暂停；AgentResumeWorker 仅新增 maintenance 注入/入口判断，其余既有改造不归本轮
      evidence: verification.md / IndexTasksCheck、FileWorkerCheck
    - task: T4
      files: [scripts/rag-golden-dataset.mjs, scripts/rag-compare-reports.mjs, src/main/java/com/ai/rag/eval/RagBenchmarkReport.java, src/main/java/com/ai/rag/eval/RagBenchmarkService.java, src/main/java/com/ai/rag/eval/RagBenchmarkDatasetLoader.java, examples/rag-evaluation-lab/README.md]
      ownedHunks: 报告快照/实际查询、占位标签校验、绑定与公平对照工具、教学操作修正；原指标/门槛不变
      evidence: verification.md / ToolingCheck.mjs、DatasetLoaderCheck、node --check
    - task: T5
      files: [CLAUDE.md, README.md, docs/核心逻辑详解/中文检索与索引维护.md, docs/核心逻辑详解/中文分块与检索改造规划.md, docs/核心逻辑详解/RAG检索链路.md, docs/核心逻辑详解/目录索引.md, docs/核心逻辑详解/文档解析与框架检索.md, docs/核心逻辑详解/框架集成与消息生命周期.md, docs/术语/枚举速查/运行与索引状态.md, docs/interview/当前进度与验收交接.md]
      ownedHunks: 当前实现、操作和未验边界；既有交接与技术改造记录保留
      evidence: verification.md / JDK17 clean compile、此前框架回归、diff/链接检查
taskProgress: { completed: [T1, T2, T3, T4, T5], current: null }
approvals:
  - phase: conversation-implementation
    artifactRevision: 中文分块与检索改造规划已讨论，用户明确要求开始干
    approvedBy: user
    approvedAt: 2026-10-03
pendingChanges: []
testLevel: middle

## 恢复说明

本轮实现与离线验证完成，不等于规划整体真实验收通过。Docker daemon 未运行，未构建镜像、未执行历史数据重建、未调用付费模型。下一步在恢复真实环境后完成部署、样本与历史批次验收，再按三组固定变量做效果对照。没有新增仓库测试类。完整证据及缺失环境见 verification.md。

必要 Files 依赖补充沿用本轮用户对规划实施的授权，详见 tasks.md；没有 pending scope 变更。sourceEvidence.baseRevision 与起始脏文件基线保留，不把工作区所有变化归给本次任务。
