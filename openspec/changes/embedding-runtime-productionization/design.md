## Context

当前 `EmbeddingGateway` 已经只有 OpenAI-compatible API 一条路径，并在启动和每次调用后验证向量维度。知识、文件和记忆索引仍在 `VectorMemoryService` 中逐 Chunk 调用 `VectorDocumentIndexer`，最终每个 Chunk 单独执行一次 `embed` 和一次 Milvus `add`。查询同样调用通用 `embed`，无法表达模型要求的 query/document 不同输入格式。

LangChain4j 0.30 静态 API 检查确认：`EmbeddingModel.embedAll(List<TextSegment>)`、`OpenAiEmbeddingModel.Builder.timeout/maxRetries/dimensions` 和 `EmbeddingStore.addAll` 均存在。项目已有 `ModelRetryExecutor`，但其策略为硬编码且服务于 Chat Model；直接复用或修改会扩大到现有模型调用链，不符合本变更边界。

用户要求不新增/运行测试、不编译、不启动服务。因此设计把可静态验证的行为写成明确控制流和配置契约，所有真实性能、兼容性和召回结论保持未验证。

## Goals / Non-Goals

**Goals:**

- 文档类索引使用受控批量 Embedding 和批量 Milvus 写入，减少每 Chunk 网络往返。
- 查询与文档输入语义显式区分，适配需要前缀的非对称检索模型。
- Embedding 调用具有独立、可配置、可解释的超时、分类重试和熔断。
- 每个返回向量在任何 Milvus 写入前完成整批数量、非空和维度校验。
- 暴露不含敏感文本和向量的 Micrometer 指标。
- 保持 API-only Profile、版本化 Collection、Milvus/ES/RRF 和 Agent Runtime 不变。

**Non-Goals:**

- 不提供或部署实际 Embedding 推理服务，不选择厂商和模型。
- 不增加缓存、消息队列、并行批次、GPU 调度和自动扩缩容。
- 不重构 Harness，也不改变 Embedding 当前的启动必需语义。
- 不解决 MySQL、Milvus、Elasticsearch 的跨存储事务。
- 不自动删除或迁移历史 Collection。

## Decisions

### D1. 在现有 Gateway 内暴露用途明确的调用，而不是让上游拼前缀

Gateway 提供 `embedQuery(String)`、`embedDocument(TextSegment)` 和 `embedDocuments(List<TextSegment>)`。内部使用 `EmbeddingInputFormatter` 根据 `EmbeddingPurpose` 追加 query/document 前缀，启动探测使用独立 `PROBE` 用途且不添加业务前缀。

把前缀散落在 `VectorMemoryService` 或 RAG 查询代码会导致不同入口形成不一致输入，因此拒绝。前缀默认空字符串；变更前缀属于向量输入契约变化，运维必须提升 `EMBEDDING_INDEX_VERSION` 并全量重建。

### D2. 一次批量先完整校验，再一次写入 Milvus

`VectorDocumentIndexer.indexAll` 接收已构造的 `TextSegment` 列表，按 `batch-size` 分片：

```text
segments batch
  -> embedAll
  -> response count check
  -> every embedding null/dimension/normalize check
  -> Milvus addAll
  -> primary-key count check
  -> registry records
```

响应数量或任一向量不合法时，本批次在 Milvus 写入前失败。Milvus `addAll` 本身失败后的服务端原子性由 Milvus/LangChain4j 决定，本变更不宣称跨存储事务；知识和文件索引必须向上暴露批次失败，不能把未完成索引记录为成功。会话、技能和长期记忆的现有单条入口暂不改变一致性策略。

在 Gateway 内并行发送多个批次会增加限流和顺序治理复杂度，因此第一版串行执行受控批次。

### D3. 使用专用 EmbeddingCallExecutor，不修改 Chat Model 重试器

新增专用执行器，策略键使用不含凭据的 Profile identity。SDK 内置重试设置为 `1`，避免两层重试放大。执行器遍历异常 cause：

- `OpenAiHttpException` 429 或 5xx：可重试。
- `IOException`、连接超时、读取超时等瞬时 I/O：可重试。
- 400、401、403、404：立即失败。
- 配置、响应数量、空向量、维度不兼容：立即失败。
- 未识别的业务 RuntimeException：默认立即失败，避免重复副作用和费用。

重试使用可配置指数退避但不加入随机抖动；第一版调用是单实例串行批次，先保持确定性。最大尝试次数范围 `1..5`。连续逻辑调用最终失败达到阈值后打开熔断；窗口到期允许一次调用尝试，成功关闭，失败重新打开。熔断只阻止 Embedding 请求，不触发模型回退。

直接复用 `ModelRetryExecutor` 会共享硬编码阈值并影响 Chat Model，使用 Resilience4j 又会新增依赖和配置体系，两者均拒绝。

### D4. 一个显式 timeout 控制 SDK 全部 HTTP 阶段

LangChain4j 0.30 将 Builder 的单个 timeout 同时传给 connect/call/read/write timeout。配置使用 Spring `Duration`，必须为正数；`.env.example` 给出 `30s` 示例。该版本不伪装成已分别控制连接和读取超时，未来升级客户端后再拆分。

### D5. 期望维度与请求维度是两个概念

`app.embedding.dimension` 继续表示 Profile 期望维度和 Milvus Schema 契约。新增可选 `app.embedding.api.output-dimensions`：

- 未配置：不调用 Builder `dimensions`，兼容固定维度和不接受该字段的服务。
- 已配置：必须为正数且等于 Profile 期望维度，然后传给服务请求。

不能把期望维度无条件发给所有兼容服务，因为部分自建服务只接受模型固定输出；也不能只相信请求参数，返回后仍逐向量校验。

### D6. 指标只使用低基数、安全标签

新增计数器和 Timer，标签限定为 `operation`（probe/query/document/batch）、`outcome` 和规范化 `modelId`。批大小使用 DistributionSummary，不把 batch size 放入 tag。指标与日志禁止包含 API Key、完整输入、向量数组、tenantId、userId 或 Chunk 正文。

建议指标名：

- `data_agent_embedding_calls_total`
- `data_agent_embedding_retries_total`
- `data_agent_embedding_circuit_rejections_total`
- `data_agent_embedding_call_duration`
- `data_agent_embedding_batch_size`

### D7. 保持检索路径和 Harness 边界

查询侧只把 `embeddingGateway.embed(query)` 替换为 `embedQuery(query)`；向量仍进入 Milvus 检索。Elasticsearch BM25 和 RRF 不接触 Embedding 运行时。Harness 继续依赖上层 RAG/Context 能力，不注入 Gateway。

Embedding 故障是否只禁用 RAG 而允许 Chat 运行，是后续 capability isolation 设计；本变更保持现有启动探测快速失败，避免无意改变产品可用性语义。

## Risks / Trade-offs

- **部分服务声明 OpenAI-compatible 但不支持数组 input** → 首次真实接入必须用两条输入验证批量协议；失败时不能静默退回逐条模式，因为会隐藏能力和成本差异。
- **批次越大越容易超出 Token/请求大小限制** → `batch-size` 限制为 `1..128`，默认示例 `32`；真实值由服务限额和 P95 测量决定。
- **批量响应只能依赖 SDK/API 的 index 顺序契约** → 静态确认 SDK `embedAll` 接口并校验返回数量；不宣称能从纯向量内容反推出错序。
- **熔断状态仅在单 JVM 内有效** → 每个实例独立保护自己；分布式熔断不是本期目标。
- **指数退避没有 jitter，多个副本可能同步重试** → 第一版保持简单；上线多副本前根据 429 数据决定是否增加抖动。
- **前缀变化依赖人工提升 indexVersion** → 文档和启动日志显式输出 Profile 与前缀是否启用，但不记录前缀正文；未来可将输入契约摘要纳入 Profile。
- **无自动测试降低行为置信度** → 执行依赖 API、异常分支、配置字段、敏感信息和数据流静态审查，不伪造运行结论。

## Migration Plan

1. 实现配置、用途格式化、专用调用执行器和安全指标，SDK 内置重试固定为 `1`。
2. 增加 Gateway 批量方法和整批兼容性校验，再接入 `VectorDocumentIndexer.indexAll`。
3. 将知识和文件索引切到串行批次；查询切到 `embedQuery`，保持其他单条入口。
4. 更新环境模板，设置 timeout、batch、retry 和 circuit 示例值；如启用输入前缀或请求维度，提升 `EMBEDDING_INDEX_VERSION`。
5. 按用户约束完成静态检查，不启动真实服务。
6. 后续获得服务配置后，先验证单条和两条批量响应，再重建新的 `__api__` Collection，运行黄金集。
7. 只有新 Collection 验证完成，才回到 `embedding-service-only` task 3.1 清理精确确认的旧 Collection。

回滚代码时恢复逐条 Gateway/Indexer，但不得复用由不同前缀、请求维度或模型生成的 Collection；回滚配置必须指向兼容 Profile，否则提升版本并重建。

## Open Questions

- 真实供应商是否支持数组 input、可选 `dimensions` 和当前模型名，需要拿到 Endpoint 后验证。
- 默认 batch size `32`、timeout `30s`、最大尝试 `3`、失败阈值 `5`、熔断窗口 `30s` 只是保守起点，不是实测最优值。
- 缓存键、缓存存储和成本收益需要真实重复率后决定。
