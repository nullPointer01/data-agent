# Tasks: RAG Retrieval Quality Upgrade

> 2026-09-02 用户最新决定：后续不新增或运行测试，也不执行编译验证。Phase 0 已按此决定完成；Phase 1 及之后原有测试型任务仅保留为历史草案，进入后续 Coding 前必须重写，当前不得执行。

## Phase 0: Baseline

- [x] Task 1: 固化 Maven 与仓库 JDK 17 契约（实现任务）
  - Acceptance: 使用 `maven.compiler.release=17`；Maven Enforcer 只接受 JDK 17；仓库版本文件声明 17。
  - Verify: JDK 8 执行 `mvn validate` 明确失败，JDK 17 执行 `mvn validate` 成功；`mvn help:effective-pom` 中编译 release 为 17。
  - Files: `pom.xml`, `.java-version`
  - Covers: SC-0

- [x] Task 2: 删除现有测试源码和测试专属依赖（实现任务）
  - Acceptance: `src/test/java` 不存在；POM 不再包含 H2、Spring Boot Test 和 Spring Security Test；不改动生产源码行为。
  - Verify: `find src/test/java -name '*.java'` 无结果；`mvn dependency:tree` 无上述测试依赖。
  - Files: `src/test/java/**`, `pom.xml`
  - Covers: SC-0

- [x] Task 3: 对齐 CI 与应用容器的 JDK 17（实现任务）
  - Acceptance: 后端 CI 在 Temurin 17 上只执行 Maven `validate`；应用镜像构建和运行阶段均基于 Java 17，且不改变现有基础设施 Compose 拓扑。
  - Verify: 静态检查 Workflow 与 Dockerfile 的 Java 版本；不执行编译或 Docker 构建。
  - Files: `.github/workflows/backend-ci.yml`, `Dockerfile`, `.dockerignore`
  - Covers: SC-0

- [x] Task 4: 更新 JDK 17 构建文档并验证干净构建（验证任务）
  - Acceptance: 根目录和后端 README 明确 JDK 17 唯一基线、切换方式、验证命令和低版本失败行为；项目知识文档与实际构建一致。
  - Verify: JDK 17 下 `mvn -Dfrontend.skip=true clean verify` 通过；文档命令可直接执行。
  - Files: `README.md`, `CLAUDE.md`
  - Covers: SC-0, SC-8

### Checkpoint 0

- JDK 8/11/21 无法误跑后端构建，JDK 17 可完成干净 `verify`。
- 本地声明、Maven、CI、容器和文档中的 Java 版本一致。
- 现有测试源码已按用户要求移除；后续测试策略在进入 RAG Coding 前重新确认。
- Dockerfile 仅完成静态版本对齐，Docker 镜像构建验证按用户最新指令停止。

## Phase 1: Elasticsearch-only

- [x] Task 5: 删除 RAG JPA 全文实现与专属查询（实现任务）
  - Acceptance: 删除 JPA Retriever 和 Noop IndexService；仅删除两个 Repository 中专供 RAG 模糊检索的方法，保留业务 JPA CRUD。
  - Verify: 静态检索不存在 `JpaFullTextRetriever`、`NoopFullTextIndexService` 和 `searchByTenantAndKeyword`。
  - Files: `src/main/java/com/ai/rag/fulltext/JpaFullTextRetriever.java`, `src/main/java/com/ai/rag/fulltext/NoopFullTextIndexService.java`, `src/main/java/com/ai/repository/KnowledgeEntryRepository.java`, `src/main/java/com/ai/repository/FileMetadataRepository.java`
  - Covers: SC-1, SC-8

- [x] Task 6: 固定 Elasticsearch 装配与 Provider 身份（实现任务）
  - Acceptance: ES Retriever、IndexService 和客户端配置不再受 provider 条件控制；RAG Provider 固定为 `elasticsearch`；配置中不存在 provider 选择项。
  - Verify: 静态检查不存在 `full-text-provider` 和 ES Bean 上的 `ConditionalOnProperty`。
  - Files: `src/main/java/com/ai/rag/elasticsearch/ElasticsearchFullTextConfiguration.java`, `src/main/java/com/ai/rag/elasticsearch/ElasticsearchFullTextRetriever.java`, `src/main/java/com/ai/rag/elasticsearch/ElasticsearchFullTextIndexService.java`, `src/main/java/com/ai/rag/RagProperties.java`, `src/main/resources/application.yml`
  - Covers: SC-1, SC-8

- [x] Task 7: 建立 ES 显式 Mapping 与失败快速暴露（实现任务）
  - Acceptance: 首次读写前幂等确保索引存在；过滤字段使用 `keyword`，检索字段使用 `text`；批量写入错误包含失败项原因；写入和删除异常不被吞掉。
  - Verify: 静态检查 Mapping 覆盖所有写入字段，搜索和删除过滤字段与 Mapping 一致，异常路径均抛出带上下文的异常。
  - Files: `src/main/java/com/ai/rag/elasticsearch/ElasticsearchFullTextClient.java`, `src/main/java/com/ai/rag/elasticsearch/ElasticsearchFullTextIndexService.java`
  - Covers: SC-1, SC-8

- [x] Task 8: 同步健康语义和项目文档（文档任务）
  - Acceptance: 健康检查只报告 ES；接口注释、开发指南、README 和环境模板均不再宣称 JPA 全文兜底或 ES 可选。
  - Verify: 对运行代码与主要项目文档执行关键词静态检查，无旧 provider 说明。
  - Files: `src/main/java/com/ai/rag/RagHealthService.java`, `src/main/java/com/ai/rag/fulltext/FullTextRetriever.java`, `CLAUDE.md`, `README.md`
  - Covers: SC-1, SC-8

- [x] Task 9: 同步环境模板和面试技术故事（文档任务）
  - Acceptance: 环境模板提供 ES 连接参数；面试故事准确描述 ES-only、显式 Mapping、RRF 和当前规则重排边界。
  - Verify: 文档不把规则权重排序称为 Cross-Encoder，不给出未运行的 Recall/延迟数字。
  - Files: `.env.example`, `INTERVIEW_TECH_STORIES.md`, `PROJECT_INTERVIEW_GUIDE.md`, `person.md`
  - Covers: SC-1, SC-8

- [x] Task 10: 沉淀 RAG 核心链路与踩坑知识（文档任务）
  - Acceptance: 项目知识库记录 ES-only 混合检索链路、Mapping 契约、失败语义、迁移边界和面试时可陈述/不可陈述的证据边界。
  - Verify: 核心逻辑文档已在目录索引注册，常见陷阱与源码字段、索引名一致。
  - Files: `docs/核心逻辑详解/目录索引.md`, `docs/核心逻辑详解/RAG检索链路.md`, `docs/演化/常见陷阱.md`, `ARCHITECTURE_PLAN.md`
  - Covers: SC-1, SC-8

### Checkpoint A

- ES 是唯一 FullText Retriever/IndexService。
- 业务 JPA CRUD 保留，只有 RAG 模糊检索查询被移除。
- ES 精确过滤字段具有显式 Mapping，索引异常不会被静默吞掉。

## Phase 2: Embedding Profile And Index Isolation

- [x] Task 11: 实现 Embedding Profile 与真实维度探测（实现任务）
  - Acceptance: Profile 统一描述 provider、modelId、indexVersion、dimension、normalize 和 metric；启动时及每次向量化后校验真实维度，冲突异常包含模型和实际/期望维度。
  - Verify: 静态检查所有 `embed` 出口均经过兼容性校验，API provider 不会因拼写错误静默回退 local。
  - Files: `src/main/java/com/ai/vector/EmbeddingProperties.java`, `src/main/java/com/ai/vector/EmbeddingProfile.java`, `src/main/java/com/ai/vector/EmbeddingCompatibilityValidator.java`, `src/main/java/com/ai/vector/EmbeddingGateway.java`
  - Covers: SC-2, SC-8

- [x] Task 12: 按模型身份解析版本化 Milvus Collection（实现任务）
  - Acceptance: 物理 Collection 名自动包含 provider、model、dimension、metric 和 indexVersion；名称满足 Milvus 字符与长度限制；Milvus 维度和 Metric 只读取 Profile。
  - Verify: 静态检查 Milvus Gateway 不再单独读取 `milvus.dimension` 或 `milvus.metric-type`，所有读写删除均使用解析后的物理 Collection。
  - Files: `src/main/java/com/ai/vector/MilvusCollectionNameResolver.java`, `src/main/java/com/ai/vector/MilvusVectorStoreGateway.java`, `src/main/resources/application.yml`
  - Covers: SC-2, SC-3, SC-8

- [x] Task 13: 将 Embedding 身份写入向量元数据（实现任务）
  - Acceptance: 每条新向量包含 provider、modelId、indexVersion、dimension 和 metric，索引器始终使用当前 Profile 构建元数据。
  - Verify: 静态检查两个向量索引入口均传入 Profile，元数据键和工厂字段一一对应。
  - Files: `src/main/java/com/ai/vector/VectorMetadataKeys.java`, `src/main/java/com/ai/vector/VectorMetadataFactory.java`, `src/main/java/com/ai/vector/VectorDocumentIndexer.java`
  - Covers: SC-3, SC-8

- [x] Task 14: 同步 Embedding 配置与知识文档（文档任务）
  - Acceptance: 环境模板、项目 README、RAG 逻辑文档和陷阱文档说明维度探测、版本化 Collection 与迁移要求，不宣称未运行的质量数据。
  - Verify: 配置和文档中 Embedding 字段名称一致，不再保留重复 Milvus dimension/metric 配置。
  - Files: `.env.example`, `README.md`, `docs/核心逻辑详解/RAG检索链路.md`, `docs/演化/常见陷阱.md`
  - Covers: SC-2, SC-3, SC-8

### Checkpoint B

- API Embedding 维度配置错误在启动阶段失败。
- 不同 indexVersion 的物理 Collection 与检索过滤完全隔离。

- [x] Task 14B: 校正项目约束与 Embedding 面试故事（文档任务）
  - Acceptance: 项目指令与生产代码使用同一套 Profile 配置；面试故事讲清真实的双维度配置风险、解决方案、代价和未评测边界。
  - Verify: 不再声称 Milvus 独立维护 dimension/metric，不再声称启动阶段完全不发起 Collection load。
  - Files: `CLAUDE.md`, `INTERVIEW_TECH_STORIES.md`
  - Covers: SC-2, SC-3, SC-8

## Phase 3: Golden Dataset And Metrics

- [x] Task 15: 实现真实黄金集配置、模型和加载校验（实现任务）
  - Acceptance: 从运维配置的固定 JSON 路径加载数据集；不少于30条；caseId唯一；每条具有query以及sourceId或chunkId标注；错误包含数据集路径和具体原因。
  - Verify: 静态核对配置绑定、JSON模型和所有失败分支；仓库不内置伪造业务标注。
  - Files: `src/main/java/com/ai/rag/eval/RagBenchmarkCase.java`, `src/main/java/com/ai/rag/eval/RagBenchmarkDataset.java`, `src/main/java/com/ai/rag/eval/RagBenchmarkDatasetLoader.java`, `src/main/java/com/ai/rag/RagProperties.java`, `src/main/resources/application.yml`
  - Covers: SC-4, SC-8

- [x] Task 16: 实现确定性排序指标和报告模型（实现任务）
  - Acceptance: 分阶段输出Recall@5/10/20、MRR@10、NDCG@10、Hit@6和P95；报告包含逐案排名、失败阶段和配置快照。
  - Verify: 静态推演空结果、source-only、chunk级多相关与graded relevance分支；所有公式不依赖LLM Judge。
  - Files: `src/main/java/com/ai/rag/eval/RagRankingMetrics.java`, `src/main/java/com/ai/rag/eval/RagBenchmarkReport.java`
  - Covers: SC-4, SC-5, SC-7, SC-8

- [x] Task 17: 保留 Vector、BM25、RRF 原始阶段快照（实现任务）
  - Acceptance: HybridRetrievalResult 保留两个原始有序列表和融合列表，正常RAG响应不暴露大候选正文；评测可判断正确候选在哪一阶段丢失。
  - Verify: 静态检查唯一构造点与所有消费点，普通Trace继续只保存计数和耗时。
  - Files: `src/main/java/com/ai/rag/retrieval/HybridRetrievalResult.java`, `src/main/java/com/ai/rag/retrieval/DefaultHybridRetriever.java`
  - Covers: SC-4, SC-8

- [x] Task 18: 增加批量评测服务和管理员接口（实现任务）
  - Acceptance: 管理员可对当前租户运行固定黄金集；同一查询一次召回后比较Vector、BM25、RRF和规则重排；报告记录Embedding Profile、参数、耗时和失败案例。
  - Verify: 静态检查ADMIN权限、当前租户隔离、同候选比较、敏感信息不进入报告。
  - Files: `src/main/java/com/ai/rag/eval/RagBenchmarkService.java`, `src/main/java/com/ai/rag/eval/RagBenchmarkController.java`, `src/main/java/com/ai/rag/RagReranker.java`
  - Covers: SC-4, SC-5, SC-7, SC-8

- [x] Task 19: 同步黄金集格式和评测边界文档（文档任务）
  - Acceptance: 环境模板、README和RAG详解说明数据集格式、管理员接口、指标含义和未运行边界。
  - Verify: 不写入任何虚构指标，配置名与生产代码一致。
  - Files: `.env.example`, `README.md`, `docs/核心逻辑详解/RAG检索链路.md`
  - Covers: SC-4, SC-8

- [x] Task 19B: 准备结构化 RAG 教学知识文档（文档任务）
  - Acceptance: 提供六篇独立 Markdown 文档，覆盖精确编号、语义改写、表格、列表、相似概念、跨段与跨文档检索场景；明确教学数据不代表业务效果。
  - Verify: 静态核对文件可读、标题唯一、文档内容与答案册事实一致；不运行应用、测试或编译。
  - Files: `examples/rag-evaluation-lab/README.md`, `examples/rag-evaluation-lab/knowledge/01-account-security.md`, `examples/rag-evaluation-lab/knowledge/02-subscription-refund.md`, `examples/rag-evaluation-lab/knowledge/03-offline-content.md`, `examples/rag-evaluation-lab/knowledge/04-team-workspace.md`
  - Covers: SC-4, SC-8

- [x] Task 19C: 准备30题教学黄金集和人工答案册（文档任务）
  - Acceptance: JSON模板恰好包含30个唯一caseId，每条具有query、非空sourceId占位标注和tags；六类来源均有直接问题，并包含多来源问题；答案册逐题给出可回查原文的答案。
  - Verify: 使用结构化JSON解析器静态检查题数、caseId唯一性、必填字段、占位符集合和多来源用例；不运行应用、测试或编译。
  - Files: `examples/rag-evaluation-lab/knowledge/05-privacy-export.md`, `examples/rag-evaluation-lab/knowledge/06-error-codes.md`, `examples/rag-evaluation-lab/answer-key.md`, `examples/rag-evaluation-lab/golden-dataset.template.json`
  - Covers: SC-4, SC-8

### Checkpoint C

- 用当前模型与规则重排生成并保存基线报告。
- 未达到SC-5时保留失败案例，不通过调低验收线伪造成功。

## Phase 4: External Model Environment And Cross-Encoder Reranker

- [x] Task 20: 清理 Ollama 残留并准备外部模型配置（实现任务）
  - Acceptance: Compose 不再包含 Ollama 服务或数据卷；local profile 默认使用硅基流动 `BAAI/bge-m3` 1024维配置；真实密钥仅从环境变量读取；README 和准备清单一致。
  - Verify: 静态检查不存在 `ollama/ollama`、11434 和仓库内真实密钥；未获用户确认前不启动应用或编译。
  - Files: `docker-compose.yml`, `src/main/resources/application-local.yml`, `README.md`, `openspec/changes/rag-retrieval-quality-upgrade/preparation.md`
  - Covers: SC-2, SC-3, SC-8, SC-9

- [ ] Task 21: 探测 Embedding 与 Reranker 真实 API 契约（验证任务）
  - Acceptance: 使用虚构短文本确认 Embedding 返回1024维；Reranker 返回候选索引和相关性分数；响应样例脱敏记录，不发送真实业务数据。
  - Verify: 用户在本地注入 API Key 后执行最小 curl；记录状态码、维度、结果字段和限流头，不记录密钥或完整向量。
  - Files: `openspec/changes/rag-retrieval-quality-upgrade/verify-report.md`
  - Covers: SC-2, SC-6, SC-8, SC-9

- [ ] Task 22: 先定义 Reranker Provider 契约测试（测试任务，等待用户重新确认测试策略）
  - Acceptance: 覆盖HTTP正常响应、乱序索引、重复索引、缺失结果、超时和fail-open降级。
  - Verify: Reranker测试全部通过。
  - Files: `src/test/java/com/ai/rag/rerank/HttpCrossEncoderRerankProviderTest.java`, `src/test/java/com/ai/rag/rerank/RagRerankerTest.java`
  - Covers: SC-6, SC-8

- [x] Task 23: 实现真实 Cross-Encoder 与规则降级（实现任务）
  - Acceptance: 支持可配置 `/v1/rerank`、模型名、超时、候选限制和fail-open；模型分数映射回原候选。
  - Verify: 静态检查请求使用结构化 JSON，响应索引经过越界、重复、缺失和有限分数校验；真实 API 契约仍由 Task 21 验证，未运行测试或编译；日志和结果不包含 API Key。
  - Files: `src/main/java/com/ai/rag/rerank/RerankProvider.java`, `src/main/java/com/ai/rag/rerank/HttpCrossEncoderRerankProvider.java`, `src/main/java/com/ai/rag/rerank/HeuristicRerankProvider.java`, `src/main/java/com/ai/rag/RerankerProperties.java`, `src/main/java/com/ai/rag/RagReranker.java`
  - Covers: SC-6

- [x] Task 24: 把精排证据写入 RAG Trace 与评测报告（实现任务）
  - Acceptance: Trace包含provider、model、耗时、输入候选数、输出数、fallback和错误类别。
  - Verify: 静态检查 Pipeline 每次请求直接携带独立 evidence，不通过单例共享最近结果；健康状态和逐案评测可区分模型成功与规则 fallback；未运行测试或编译。
  - Files: `src/main/java/com/ai/rag/EnhancedRagPipeline.java`, `src/main/java/com/ai/rag/dto/RagRetrievalTrace.java`, `src/main/java/com/ai/rag/RagHealthService.java`, `src/main/java/com/ai/rag/eval/RagBenchmarkService.java`, `src/main/java/com/ai/rag/eval/RagBenchmarkReport.java`
  - Covers: SC-6, SC-7

- [x] Task 24B: 同步 Reranker 配置和核心知识文档（配置/文档任务）
  - Acceptance: `RERANK_*` 环境变量、故障治理、响应校验、Trace 和未实测边界与生产代码一致；不记录真实密钥。
  - Verify: 静态检查配置占位符、核心链路与常见陷阱；未发现仓库内真实 Reranker 密钥。
  - Files: `src/main/resources/application.yml`, `README.md`, `CLAUDE.md`, `docs/核心逻辑详解/RAG检索链路.md`, `docs/演化/常见陷阱.md`
  - Covers: SC-6, SC-8, SC-9

- [x] Task 24C: 同步 RAG 文档索引和面试技术故事（文档任务）
  - Acceptance: 目录索引能定位 Cross-Encoder 详解；面试故事区分“已实现链路”和“尚未证明效果”。
  - Verify: 文档不宣称 Recall、MRR、NDCG 或 P95 已提升。
  - Files: `docs/核心逻辑详解/目录索引.md`, `docs/interview/INTERVIEW_TECH_STORIES.md`
  - Covers: SC-6, SC-8

## Phase 5: Verification

- [ ] Task 25: 运行模型前后对比与工程门禁（验证任务）
  - Acceptance: 在 `BAAI/bge-m3` 和相同RRF Top20候选上输出规则重排、Cross-Encoder两份同集报告；满足SC-1至SC-9或明确标记失败。
  - Verify: 黄金集评测必须运行；自动化测试和JDK17编译仅在用户重新批准后运行，未运行项明确标记。
  - Files: `openspec/changes/rag-retrieval-quality-upgrade/verify-report.md`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-6, SC-7, SC-8, SC-9
