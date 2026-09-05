# Design: RAG Retrieval Quality Upgrade

## Context

当前链路已经具备结构化 Chunk、Milvus 向量召回、JPA/Elasticsearch 可选全文召回、RRF 融合、规则重排、父上下文扩展和引用生成。主要缺口如下：

- `pom.xml` 虽声明 Java 17，但仍重复使用 `source/target`，没有 Maven Enforcer；当前终端实际以 JDK 8 运行 Maven，缓存构建可能掩盖错误。
- 仓库没有 `.java-version`、后端 CI Workflow 或应用 Dockerfile，JDK 17 尚未覆盖所有构建入口。
- JDK 17 干净测试编译会被旧 `ReActToolCall` 测试引用阻断；相关测试仍停留在已经移除的文本工具调用协议，而生产代码已经使用 LangChain4j 原生 Function Calling。
- `app.rag.full-text-provider` 默认值为 `jpa`，JPA 检索在查询时扫描业务数据并重新分块，不是真正的全文索引。
- 本地 Embedding 固定为 AllMiniLmL6V2 384 维；API 默认模型名为 `bge-large-zh-v1.5`，但默认维度仍为 384，存在明显配置冲突风险。
- Milvus 只检查 Collection 和索引是否存在，没有验证实际 Schema 维度、Metric 和 Embedding 模型身份。
- 当前 `RagReranker` 是融合分数、关键词和来源权重的规则排序，不是 Cross-Encoder。
- 现有 `/rag/evaluate` 评估单次响应的关键词和引用完整性，没有批量黄金集与标准排序指标。
- `application-local.yml` 与 `docker-compose.yml` 仍残留一次已中止的 Ollama 尝试；本地端口 11434 没有可用服务，配置与 API-only 目标不一致。

## Target Flow

```text
文档解析 -> 结构化 Chunk
              |
              +-> BAAI/bge-m3 API -> EmbeddingProfile -> 维度探测 -> 版本化 Milvus Collection
              |
              +-> Elasticsearch Chunk 索引

用户查询 -> 同版本 Embedding -> Milvus Top20 ----+
       \-> Elasticsearch BM25 Top20 -------------+-> RRF Top20
                                                   -> BAAI/bge-reranker-v2-m3
                                                   -> Top6 + 父上下文
                                                   -> 引用化上下文 -> Chat LLM

黄金数据集 -----------------------------------------> Recall/MRR/NDCG/P95 报告
```

## Goals

- 让本地、Maven、CI 和容器对 JDK 17 形成同一份可执行契约，并恢复可信的干净测试基线。
- 让模型、维度、索引版本之间的契约在启动阶段即可验证。
- 让检索质量能够通过固定数据重复测量，支持模型和参数前后对比。
- 使用真实相关性模型进行精排，同时保证外部服务异常时主链路可控降级。
- 删除 RAG JPA 全文召回，减少“全文检索”概念混淆和查询时重复分块成本。
- 让本地开发环境无需运行模型即可使用真实中文 Embedding 和 Reranker，并明确本地、测试、生产之间的配置与数据隔离。

## Non-Goals

- 不升级 JDK 21、Spring Boot 或依赖大版本，不修改用户全局 Java 环境。
- 不实现自动模型训练、Embedding 微调或 LLM Judge 平台。
- 不实现在线双写、Milvus Alias 热切换和零停机索引迁移。
- 不重构业务实体持久化层。
- 不把 Ollama、TEI 或 vLLM 加入本地 Compose；私有推理集群属于未来生产部署选择。

## Decisions

### D0. JDK 17 是唯一后端构建基线

保留 Spring Boot 使用的 `java.version=17`，用 `maven.compiler.release=17` 替换重复的 `source/target`，确保同时约束字节码版本和可用 JDK API。Maven Enforcer 在 `validate` 阶段要求 Java 版本为 `[17,18)`，因此 JDK 8/11 和 JDK 21 都不会被误用。

仓库根目录增加 `.java-version`，CI 使用 Temurin 17 并只执行 `validate` 版本校验，应用 Dockerfile 的构建与运行阶段均使用 Java 17 镜像。`docker-compose.yml` 当前只承载 MySQL、Milvus、Redis、Elasticsearch 等基础设施，不包含 Java 进程，因此不为版本对齐而改变其服务拓扑。

按用户最新决策，现有 `src/test/java` 测试源码整体移除，同时删除 H2、Spring Boot Test 和 Spring Security Test 等测试专属依赖，不恢复旧 `ReActToolCall`。JDK 基线通过干净 `verify`、CI 和容器构建验证；后续 RAG 阶段若要新增聚焦测试，必须在进入对应阶段前重新确认。

用户随后进一步明确后续不需要测试，也不需要编译，因此后续阶段改用静态一致性检查和运行时评测能力作为交付证据。该决定降低了回归保障，任何 Recall、MRR、NDCG、延迟或成功率都必须在真实运行后才能对外陈述。

### D1. Elasticsearch 是唯一全文检索实现

删除 `JpaFullTextRetriever`、`NoopFullTextIndexService` 及专属测试，去除 `full-text-provider` 选择逻辑。RAG 开启时 Elasticsearch 视为必要依赖；不可用时健康检查失败并明确返回降级状态，不能假装完成混合召回。

Elasticsearch 索引不依赖动态 Mapping：`tenantId`、`userId`、`sourceType`、`sourceId`、`chunkId` 和 `parentChunkId` 固定为 `keyword`，标题、正文、章节路径和父上下文固定为 `text`，结构标志和位置字段使用布尔/整数类型。首次访问时幂等创建索引，避免动态字符串 Mapping 与 `term` 过滤不匹配导致零召回。

ES 索引写入和删除失败必须抛给调用方，不允许只记录日志后返回成功。跨 Milvus 与 ES 的原子双写不在本阶段实现，失败后的补偿与重放将在后续索引治理阶段设计。

保留 Spring Data JPA，因为知识条目、文件元数据、会话和执行轨迹仍以 MySQL 为事实源。

### D2. EmbeddingProfile 是索引契约

新增不可变 `EmbeddingProfile`：

```text
provider
modelId
indexVersion
dimension
normalize
metric
```

启动时用固定探测文本调用一次 Embedding API，以真实向量长度验证 `dimension`。Profile 生成稳定指纹并写入向量元数据。Milvus Collection 使用 `baseName__indexVersion`，版本必须满足安全字符约束。

第一阶段采用维护窗口方式重建索引：新版本上线前显式执行全量重建。本变更不会自动删除旧 Collection。

### D3. Milvus 验证现有 Collection 契约

已有 Collection 不能只判断“存在索引”。需要读取 Schema 和 Index 描述，至少验证：

- 向量字段维度等于 Profile 维度。
- 索引 Metric 与配置一致。
- 写入和查询使用同一个物理 Collection。

不一致时快速失败，禁止在业务请求阶段才暴露写入异常。

### D4. Cross-Encoder 通过 Provider 接口接入

定义 `RerankProvider`，输入 query 与候选内容列表，输出候选索引和相关性分数。首个真实实现使用可配置 HTTP `/v1/rerank` 协议，可对接 BGE Reranker、Jina、Cohere 或兼容服务。

现有规则打分迁移为 `HeuristicRerankProvider`，只作为显式 fallback。`RagReranker` 负责：

1. 限制送入模型的候选数量和单条长度。
2. 校验返回索引、去重并处理缺失项。
3. 记录 Provider、耗时、是否降级和模型分数。
4. 外部服务失败时按配置 fail-open 或 fail-closed；默认 fail-open。

首个已选 Provider 使用硅基流动国内站的 `/v1/rerank`，模型固定为 `BAAI/bge-reranker-v2-m3`。Provider 接口和配置保持厂商无关，后续切换到企业内网 TEI/vLLM 或其他兼容服务时不改 RAG Pipeline。

### D5. 黄金集以检索标注为主

每条用例包含：

```json
{
  "caseId": "rag-001",
  "query": "...",
  "expectedSourceIds": ["..."],
  "expectedChunkIds": ["..."],
  "relevanceGrades": {"chunk-id": 2}
}
```

评测先关注 Retrieval，不依赖答案生成的随机性。核心公式：

- Recall@K：前 K 中是否覆盖所有期望来源或 Chunk。
- MRR@K：第一个相关结果排名的倒数。
- NDCG@K：考虑多条结果相关等级的排序质量。
- P95：批量查询的 95 分位检索耗时。

结果同时记录 Embedding Profile、Reranker Provider、TopK、阈值和时间，保证可复现。

### D6. 先测基线，再改模型

实施顺序固定为：

1. ES-only 与黄金评测能力落地。
2. 使用 `BAAI/bge-m3`、1024 维和规则重排生成首个真实 API 基线。
3. 保持相同 Embedding Profile 和 RRF 候选，启用 Cross-Encoder。
4. 启用 Cross-Encoder，再次运行同一数据集。
5. 只根据指标选择参数，不凭主观示例判断。

### D8. 本地、测试和生产只切换部署配置

本地 Java 进程通过环境变量访问外部 Embedding/Reranker API，ES 和 Milvus 仍由本地 Docker 提供。测试环境使用独立 API Key、ES 索引和 Milvus Collection；生产环境可继续使用托管 API，也可切换为企业私有推理集群。

三个环境共享模型身份契约：模型名、模型修订、维度、Query/Document 前缀、归一化和 Metric。任一字段变化都提升 `EMBEDDING_INDEX_VERSION` 并全量重建，不共享物理 Collection。仓库只提供变量名和非敏感默认值，真实密钥仅保存在 IDEA Run Configuration、Shell 环境或部署平台 Secret 中。

### D7. 教学实验集与业务黄金集隔离

为了让项目维护者能够亲自跑通“入库、标注、评测、归因”的完整流程，在 `examples/rag-evaluation-lab/` 提供一套虚构产品教学材料。教学集包含结构化文档、30道标注问题、人工答案册和 sourceId 占位模板，但不进入生产默认配置。

教学集只能验证评测机制、检索阶段差异和参数影响，不能用于证明真实业务 Recall、MRR、NDCG、延迟或线上收益。模板必须在文档实际上传后使用当前租户返回的真实 `knowledgeId` 替换占位符；业务质量结论仍必须使用真实用户问题与人工确认的业务来源。

## Alternatives

| 方案 | 结论 | 原因 |
|---|---|---|
| 保留 JPA 作为 ES 降级 | 拒绝 | 行为和分数不可比，容易把 SQL LIKE 误称为 BM25 |
| 直接把 Top20 交给大模型排序 | 拒绝 | 成本高、延迟高、输出不稳定 |
| 只用向量召回 | 拒绝 | 编号、专有名词和精确字段匹配能力下降 |
| 使用免费 BGE-M3 作为首个真实 API 基线 | 接受 | 1024 维、多语言、无需本地模型，当前国内站标记免费；最终质量仍由黄金集决定 |
| Collection 中仅靠元数据隔离模型版本 | 拒绝 | 不同维度无法共存，同维度不同模型也不可比较 |

## Risks And Trade-offs

- Maven Enforcer 会让当前默认 JDK 8 的终端立即失败；这是预期的快速失败，README 会给出切换到本机 JDK 17 的明确命令。
- 固定 `[17,18)` 会拒绝 JDK 21，即使其通常能够编译 Java 17；换取本地、CI 与容器运行时完全一致。
- 删除现有测试会失去回归保护，也无法用覆盖率证明工程质量；这是用户明确选择，后续 RAG 变更进入 Coding 前必须重新确认测试策略。
- ES 变为必要依赖后，本地启动门槛提高；通过 Docker Compose、健康检查和明确错误降低影响。
- 启动探测会产生一次 Embedding 调用和少量延迟；换取配置错误快速暴露。
- HTTP Reranker 增加网络耗时；通过候选上限、超时和 fallback 控制。
- 第三方免费服务可能限流、变更价格或下线模型；本地演示使用 fail-open，生产环境不能依赖免费 SLA。
- 文档会发送给第三方模型服务；默认只用仓库中的虚构教学资料，真实企业数据必须先取得授权。
- 黄金数据集质量依赖人工标注；至少双人或二次复核高价值问题，避免指标优化到错误标签。
- 版本化 Collection 会增加存储占用；旧版本清理由独立运维动作完成，不在运行时自动删除。
