# RAG Retrieval Quality Upgrade

## Objective

将当前 RAG 从“可运行的混合检索链路”升级为“模型版本可治理、召回效果可评测、精排行为可解释”的生产化基础能力：

1. 将 JDK 17 从 POM 中的声明升级为 Maven、本地版本文件、CI 和容器共同执行的工程基线。
2. 移除 RAG 的 JPA 全文召回和空索引实现，Elasticsearch 成为唯一全文检索与 BM25 实现。
3. 为 Embedding 建立明确的模型身份、维度校验和 Milvus 索引版本隔离，禁止不同模型向量混写。
4. 建立不少于 30 条标注问题的黄金数据集，输出 Recall@K、MRR、NDCG 和延迟指标。
5. 首个可运行配置使用硅基流动国内站的 `BAAI/bge-m3` 生成 1024 维向量，并使用 `BAAI/bge-reranker-v2-m3` 对融合候选进行真实模型精排。
6. 本地只运行 MySQL、Redis、Elasticsearch 和 Milvus 等基础设施；Embedding 与 Reranker 通过外部 API 接入，清理未完成的 Ollama 本地推理配置。

目标用户是维护 Data Agent 的服务端研发人员，以及需要验证知识库问答质量的管理员。

## Anti-Scope

- 不移除业务持久化使用的 Spring Data JPA、MySQL 实体和 Repository。
- 不升级到 JDK 21，不顺带升级 Spring Boot、LangChain4j 或其他依赖大版本。
- 不修改开发者机器的全局 `JAVA_HOME` 或 shell 配置；仓库负责声明、校验并提供一致的构建入口。
- 不在本变更中实现 PDF 版面分析、OCR、多模态向量或模型微调。
- 不替换 Milvus，也不实现在线无损热迁移或自动删除历史 Collection。
- 不把 LLM Judge 作为唯一验收方式；核心召回指标必须由确定性标注计算。
- 不在开发机或 Java 进程中部署 Embedding/Reranker 模型，不下载 Ollama、TEI 或模型权重。
- 不把第三方服务的当前免费策略视为永久 SLA；生产环境仍需独立评估付费 API 或私有推理集群。

## Quantified Success Criteria

- **SC-0 JDK 17 Baseline**：Maven 使用 `release=17` 编译；JDK 8/11/21 执行 `mvn validate` 时 100% 在构建前置阶段给出明确错误；仓库版本声明、CI 构建镜像和应用容器均固定 JDK 17；现有 `src/test/java` 与测试专属依赖按用户要求移除；JDK 17 下 `clean verify` 通过。
- **SC-1 ES Only**：运行时代码中不存在 `JpaFullTextRetriever` 和 `NoopFullTextIndexService`；RAG 开启时只装配 1 个 Elasticsearch `FullTextRetriever` 和 1 个 Elasticsearch `FullTextIndexService`。
- **SC-2 Embedding Compatibility**：应用启动阶段通过真实探测向量验证配置维度；模型实际维度与配置不一致时 100% 阻止启动，并输出模型、实际维度和期望维度。
- **SC-3 Index Isolation**：每条向量元数据包含 `embeddingModelId`、`embeddingIndexVersion` 和 `embeddingDimension`；不同 `indexVersion` 解析到不同 Milvus Collection。
- **SC-4 Golden Dataset**：提供至少 30 条标注查询，每条至少包含期望 `sourceId` 或 `chunkId`；一次运行输出 Recall@5、Recall@10、Recall@20、MRR@10、NDCG@10 和 P95 检索延迟。
- **SC-5 Retrieval Quality**：在项目黄金数据集与指定 Embedding 配置下，Recall@20 不低于 90%，MRR@10 不低于 0.75。
- **SC-6 Real Reranking**：HTTP Cross-Encoder Provider 能调用 `BAAI/bge-reranker-v2-m3` 对候选批量打分并保留原始召回信息；Reranker 可用时 Top20 到 Top6 使用模型分数排序，失败降级率和 Provider 名称进入 Trace。
- **SC-7 Citation Quality**：黄金集期望来源覆盖率不低于 90%，最终上下文中的引用元数据完整率为 100%。
- **SC-8 Engineering Gate**：按用户当前决策不新增或运行自动化测试、不执行编译；每阶段必须完成调用点、配置项、失效实现和敏感信息的静态一致性检查，未实际运行的质量指标不得写成已达成事实。
- **SC-9 Environment Contract**：本地配置默认指向 OpenAI-compatible 外部服务，Embedding 模型固定为 `BAAI/bge-m3`、维度为 1024；API Key 只从环境变量读取；Compose 中不存在本地 Embedding 模型服务；测试环境使用独立凭据和独立 ES/Milvus 索引。

## Assumptions

具体假设与验证方式见 [sdd-assumptions.md](./sdd-assumptions.md)。

## Boundaries

### Always

- 所有后端构建、测试和运行入口使用 JDK 17，并以干净构建结果作为验证证据。
- 全文和向量检索都必须应用 `tenantId` 与来源类型过滤。
- 文档入库与查询必须使用同一个 Embedding 模型身份和索引版本。
- Reranker 降级、Embedding 身份和最终排序信息必须可追踪。
- 模型密钥只能来自配置或环境变量，不能写入源码、数据集或 Trace。

### Ask First

- 修改默认 Embedding 模型、维度、Milvus Collection 名称或索引参数。
- 删除或重建已有 Milvus Collection、Elasticsearch 索引。
- 引入需要联网下载大模型权重的新依赖或镜像。
- 将真实企业文档发送给第三方 Embedding 或 Reranker 服务。

### Never

- 不允许在 JDK 8/11 上继续编译，也不允许把构建基线提升到 JDK 21。
- 不允许静默回退到 JPA 全文检索。
- 不允许把不同 Embedding 模型或维度的数据写入同一个物理 Collection。
- 不允许吞掉模型维度冲突或把失败索引标记为成功。
- 不允许将用户文档内容、查询原文或模型密钥发送到未配置授权的外部服务。
- 不允许把 `EMBEDDING_API_KEY`、`RERANK_API_KEY` 或任何真实凭据写入 YAML、README、提交记录或 Trace。
