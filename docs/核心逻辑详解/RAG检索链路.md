# RAG 检索链路

## 目标

Data Agent 的 RAG 同时解决两类召回问题：Milvus 向量检索负责语义相似，Elasticsearch BM25 负责编号、专有名词和关键词精确命中。MySQL 只保存业务事实，不承担全文召回。

## 入库链路

```text
知识/文件正文
  -> TextChunker 结构化分块
  -> 固定 api Embedding Profile + 启动真实维度探测
  -> 串行受控批次
  -> query/document 用途格式化
  -> 外部 OpenAI-compatible Embedding 服务（批量 input）
  -> 整批数量、非空和维度校验
  -> Milvus addAll + 主键数量校验
  -> 版本化 Milvus 向量索引
  -> Elasticsearch data-agent-rag-v2 Chunk 索引
```

Embedding 推理只通过外部 OpenAI-compatible 服务完成，Java 进程不加载本地模型。`EMBEDDING_API_BASE_URL`、`EMBEDDING_MODEL_NAME` 和 `EMBEDDING_DIMENSION` 必须显式配置，内网无鉴权端点的 API Key 可以为空。服务不可用时明确失败，不回退本地模型。

`EMBEDDING_DIMENSION` 是 Profile、返回校验和 Milvus Schema 使用的期望维度。`EMBEDDING_API_OUTPUT_DIMENSIONS` 是可选的请求字段，只在兼容服务明确支持 `dimensions` 裁剪时配置；留空时客户端不发送该字段，配置时必须为正数且等于期望维度。即使发送了请求维度，返回后仍逐向量校验。

知识库和文件使用 `EMBEDDING_API_BATCH_SIZE` 控制的串行批次，范围 `1..128`。Gateway 先对整个响应执行数量、非空和维度校验，再统一归一化；校验通过后 Milvus `addAll` 批量写入，并校验返回主键与 segment 数量一致，最后才更新运行时 Registry。当前不宣称 Milvus 写入和 Registry/MySQL/ES 之间具备跨存储事务，后续批次失败时已经完成的前序批次不会自动回滚，但失败会向调用方传播，来源不会被本次操作报告为完整索引成功。

查询使用 `EMBEDDING_QUERY_PREFIX`，索引文档使用 `EMBEDDING_DOCUMENT_PREFIX`，启动 probe 不使用业务前缀。默认前缀均为空。前缀由 Gateway 边界统一添加，上游不能自行拼接；更改任一前缀或请求输出维度都必须提升 `EMBEDDING_INDEX_VERSION` 并全量重建。

外部调用必须配置正数 `EMBEDDING_API_TIMEOUT`。一个逻辑调用最多尝试 `EMBEDDING_API_MAX_ATTEMPTS` 次，范围 `1..5`，使用 `EMBEDDING_API_INITIAL_BACKOFF` 指数退避。只有 HTTP 429、5xx、连接失败、连接/读取超时、connection reset 等明确瞬时网络异常会重试；400、401、403、404、响应数量、空向量和维度错误立即失败。连续逻辑调用失败达到 `EMBEDDING_API_CIRCUIT_FAILURE_THRESHOLD` 后，该 JVM 内当前 Profile 在 `EMBEDDING_API_CIRCUIT_OPEN_DURATION` 窗口内拒绝请求；窗口后的单次恢复调用成功才关闭熔断，不会回退其他模型。

运行指标为 calls、retries、circuit rejections、call duration 和 batch size。operation/outcome/modelId 使用低基数标签，批大小作为分布值而不是标签；日志和指标不记录 API Key、完整输入、Chunk 正文或向量数组。`application.yml` 中 batch `32`、timeout `30s`、最大尝试 `3`、初始退避 `200ms`、失败阈值 `5` 和熔断窗口 `30s` 都是未实测起点，必须结合真实服务限额、429 比例、失败率和 P95 调整。

Embedding Profile 是向量索引的唯一身份契约，包含固定 provider `api`、`modelId`、`indexVersion`、`dimension`、`normalize` 和 `metric`。应用启动阶段会用固定文本真实请求服务生成向量，之后每次向量化也会校验实际维度。配置维度与服务输出不一致时直接失败，不允许把错误向量写入 Milvus。

Milvus 配置只保留逻辑基础名称和索引类型。物理 Collection 名自动包含 provider、模型、维度、Metric 和索引版本，长度超限时使用 SHA-256 摘要稳定截断。向量元数据同时保存这五项身份字段，用于追查向量来源；Collection 隔离负责从物理上阻止不同模型或版本混写。

Elasticsearch 使用显式 Mapping：

- `tenantId`、`userId`、`sourceType`、`sourceId`、`chunkId`、`parentChunkId`：`keyword`
- `title`、`content`、`sectionPath`、`parentContext`：`text`
- 字符位置：`integer`
- 表格、代码、列表标志：`boolean`

写入或删除 ES 索引失败会向上抛出，不允许仅记录日志后把入库标记成成功。Milvus 与 ES 的跨存储原子双写尚未实现，补偿重放属于后续索引治理范围。

## 查询链路

```text
query
  -> RagQueryRewriter
  -> Milvus Top20 + Elasticsearch BM25 Top20
  -> RRF 融合候选
  -> HTTP Cross-Encoder 精排（失败时规则降级）
  -> Top6
  -> 父级上下文解析
  -> 上下文字符预算压缩
  -> 引用化上下文
```

`candidateTopK=20` 是候选预算，不是正确率结论；最终 `topK=6` 用于控制进入模型的上下文数量。参数是否合理必须通过固定黄金集的 Recall@K、MRR、NDCG 和 P95 延迟对比确定。

`RagReranker` 通过厂商无关的 `RerankProvider` 调用兼容 `/v1/rerank` 的外部服务，默认模型为 `BAAI/bge-reranker-v2-m3`。它只把候选上限内的 `(query, chunk)` 发送给模型，校验返回索引是否越界、重复、缺失以及分数是否为有限值，再把分数映射回原候选；向量分数、BM25 分数和召回通道不会被覆盖。

HTTP Provider 使用 `RERANK_API_TIMEOUT`、`RERANK_MAX_ATTEMPTS`、指数退避和 JVM 内熔断限制故障放大。默认 `RERANK_FAIL_OPEN=true`：超时、限流、上游错误、认证错误、响应契约错误或熔断打开时，使用原有 RRF 分数、关键词覆盖率和来源权重执行确定性规则降级；设为 `false` 时直接暴露异常。请求和 Trace 均不记录 API Key、查询正文或 Chunk 正文。

线上 Trace 记录尝试的 Provider、模型、输入/输出候选数、耗时、是否 fallback、fallback Provider 和低基数错误类别。代码接入只证明链路具备模型精排能力，不证明精排有效；必须复用同一黄金集和同一 RRF Top20，对比规则与 Cross-Encoder 的 MRR@10、NDCG@10、Hit@6 和 P95。

## 即时质量评估

即时评估接口区分两种相关性口径：调用方传入人工标注关键词时，按标注关键词覆盖率评分；Control Plane 的无标注临时查询使用最佳引用的向量相似度，并明确展示引用编号和评分依据。不得把查询改写阶段产生的完整中文句段直接当作期望关键词做字面包含匹配，也不得通过硬编码验收问题或降低阈值掩盖误判。没有标注且引用缺少向量分数时，相关性保持未通过，由黄金集评测补充可靠证据。

即时质量分数用于定位单次检索问题，不替代离线黄金集。Recall、MRR、NDCG 等排名结论仍只来自带期望来源、片段或相关等级标注的固定数据集。

## 黄金集批量评测

评测数据从 `app.rag.benchmark.dataset-path` 配置的固定 JSON 文件加载，只允许管理员通过 `POST /api/v1/rag/benchmark/run` 对当前租户运行。加载器要求：

- 用例数量位于 `minimum-cases` 与 `maximum-cases` 之间，默认30至200条。
- `datasetId`、`corpusVersion`、`caseId` 和 `query` 非空，`caseId` 全局唯一。
- 每条用例至少提供一个真实 `expectedSourceId`、`expectedChunkId` 或正相关等级。
- 文件最大5MB；报告记录数据集 SHA-256，确保两次运行比较的是同一份标注。

一次查询只执行一次混合召回，然后对同一候选分别保存四个阶段：

```text
VECTOR原始排名 ----+
                    +-> RRF排名 -> RERANK排名
BM25原始排名 ------+
```

每阶段输出 Recall@5/10/20、MRR@10、NDCG@10、Hit@6 和 P95，并保留去除正文后的 sourceId/chunkId 排名证据。报告配置快照记录配置的 Reranker Provider 和模型，逐案记录实际尝试、fallback Provider 与错误类别，防止把规则降级误算成模型效果。失败用例按 `RECALL`、`FUSION`、`RERANK_HARM`、`FINAL_TOP_K` 或 `EXECUTION_ERROR` 分类。普通线上 RAG Trace 只保留计数、耗时和低基数精排证据，避免候选正文放大响应与日志。

运行前必须确认 Milvus 和 Elasticsearch 均就绪，同一实例只允许一个批量评测运行。当前质量门禁目标是 Recall@20 不低于0.90、MRR@10不低于0.75；这是目标值，不是已经取得的结果。Cross-Encoder 接入后必须复用相同数据集和相同 RRF Top20，才能证明提升来自精排而不是重新召回。

## 失败语义

- Elasticsearch 不可用：健康检查失败，不降级为 SQL LIKE。
- Milvus 不可用：混合检索未就绪。
- Embedding 服务不可用或维度不匹配：启动探测或当前向量操作失败，不降级到 JVM 本地模型。
- Embedding 返回数量、空向量或维度不匹配：整个当前批次在写 Milvus 前失败，不逐条降级。
- Embedding 熔断打开：窗口内请求直接失败且不触达服务，不切换到另一向量空间。
- Reranker 失败且 fail-open：规则 Provider 接管排序，Trace 记录失败类别和 fallback；召回链路继续。
- Reranker 失败且 fail-closed：当前 RAG 请求失败，不返回未经目标精排策略处理的结果。
- ES Mapping 不匹配：使用新版本索引并显式重建，不原地修改字段类型。
- 没有真实评测运行：不得宣称 Recall、MRR、NDCG 或延迟已经达标。
- Embedding 模型或维度变化：提升 `indexVersion` 并全量重建新 `__api__` Collection，不能复制或复用旧模型向量。

## Collection 迁移边界

从历史 JVM 本地模型切换到外部服务时，按以下顺序操作：

1. 配置真实 Embedding 服务的 Base URL、模型名和实际维度，并提升 `EMBEDDING_INDEX_VERSION`。
2. 启动探测通过后，全量重建知识库和文件索引，写入新的 `__api__` Collection；Elasticsearch Chunk 索引仍按原链路写入。
3. 使用同一黄金集检查 Vector、BM25、RRF 和规则重排的排名证据，不能只确认 Collection 存在。
4. 读取 Milvus 完整 Collection 清单，逐个确认历史 `__local__` Collection 的精确名称。
5. 只有新 API Collection 已完成重建并验证可用，才可删除精确确认的旧 local Collection，并记录删除目标。

应用不会自动复制、转换或删除历史向量。禁止用通配符或前缀批量删除，也禁止删除任何 `__api__` 或无关 Collection。旧 Collection 一旦删除，回滚旧模型需要重新生成旧向量。

## 更新记录

- 2026-09-02：删除 JPA 全文兜底，升级到 `data-agent-rag-v2` 显式 Mapping。
- 2026-09-02：增加 Embedding Profile、启动维度探测和版本化 Milvus Collection。
- 2026-09-02：增加真实黄金集加载、四阶段排名证据和管理员批量评测报告。
- 2026-09-02：Embedding 推理收敛为外部 API-only，并明确 local Collection 的受控下线顺序。
- 2026-09-02：增加用途前缀、串行批量、分类重试、per-Profile 熔断和安全指标契约。
- 2026-09-05：接入 HTTP Cross-Encoder、规则降级、精排 Trace 和实际 Provider 评测快照；真实同集指标仍待运行。
- 2026-09-09：即时质量评估拆分有标注关键词覆盖和无标注向量相关性两种口径，移除完整中文问句的字面匹配误判。
