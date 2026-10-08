schemaVersion: 1
change: framework-rag-components
phase: { name: sdd-coding, status: COMPLETED }
outcome: PASS_WITH_NOTES
nextPhase: null
returnPhase: null
sourceEvidence:
  baseRevision: 10cdefabaa802ed80c2b6051ac044e06f2dfb169
  preexistingWorktreeFilesEvidence: baseline.txt
  taskLedger:
    - task: T1
      evidence: [baseline.txt, design.md, verification.md]
    - task: T2
      files: [pom.xml, src/main/java/com/ai/service/file/FrameworkDocumentFileContentParser.java, src/main/java/com/ai/service/file/PlainTextFileContentParser.java, src/main/java/com/ai/service/file/FileParsingContext.java, src/main/java/com/ai/service/file/PdfFileContentParser.java, src/main/java/com/ai/service/file/DocxFileContentParser.java, src/main/java/com/ai/service/file/PptxFileContentParser.java]
      scope: 新增官方解析模块/对齐 POI，通用解析委派与三个旧类删除；保留已有依赖改动
    - task: T3
      files: [src/main/java/com/ai/vector/DocumentSplitterConfiguration.java, src/main/java/com/ai/vector/TextChunker.java, src/main/java/com/ai/vector/ChunkSourceLocator.java]
      scope: 独立配置/注入中文句界分块器，累计覆盖终点定位和递归纯重叠清理；保留已有章节与 Unicode 适配
    - task: T4
      files: [src/main/java/com/ai/rag/RagQueryTransformer.java, src/main/java/com/ai/rag/RagQueryRewriter.java, src/main/java/com/ai/rag/RagProperties.java, src/main/java/com/ai/rag/EnhancedRagPipeline.java, src/main/java/com/ai/rag/RagRetrievalService.java, src/main/java/com/ai/rag/eval/RagBenchmarkService.java, src/main/java/com/ai/service/knowledge/KnowledgeService.java, src/main/resources/application.yml]
      scope: 官方查询变换/历史入口/显式 query 配置与旧分析器替换；保留既有管道和服务改动
    - task: T5
      files: [src/main/java/com/ai/vector/EmbeddingGateway.java, src/main/java/com/ai/vector/MilvusVectorStoreGateway.java, src/main/java/com/ai/service/VectorMemoryService.java, src/main/java/com/ai/rag/retrieval/DefaultHybridRetriever.java]
      scope: 框架接口薄适配/真实官方 Retriever 与有界变体召回；不改变底层治理和恢复
    - task: T6
      files: [CLAUDE.md, docs/核心逻辑详解/文档解析与框架检索.md, docs/核心逻辑详解/文档分块与框架适配.md, docs/核心逻辑详解/框架集成与消息生命周期.md, docs/核心逻辑详解/RAG检索链路.md, docs/核心逻辑详解/目录索引.md, docs/核心逻辑详解/中文分块与检索改造规划.md, docs/interview/当前进度与验收交接.md, docs/interview/PROJECT_INTERVIEW_GUIDE.md, docs/interview/person.md]
      scope: 本轮说明、学习入口与历史笔记提示片段；保留原有文档修改
      evidence: [verification.md, /tmp/data-agent-framework-rag-dNhgbz, /tmp/data-agent-chunking-OcW5Q3]
taskProgress: { completed: [T1, T2, T3, T4, T5, T6], current: null }
approvals:
  - phase: conversation-implementation
    artifactRevision: 用户明确要求迁移文档解析、优化分块、迁移查询变换并核对 ContentRetriever
    approvedBy: user
    approvedAt: 2026-10-03
pendingChanges: []
testLevel: middle
