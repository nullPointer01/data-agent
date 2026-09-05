## Why

项目目前使用 LangChain4j 0.36.2，用户决定追随最新 1.19 版本线，并明确接受 Milvus 集成仍为 beta。升级需要同时迁移 1.x 已移除的 Chat Model、Streaming、Response 和 Tokenizer API，确保项目继续在 JDK 17 下编译。

## What Changes

- **BREAKING**：将 `langchain4j` 与 `langchain4j-open-ai` 从 0.36.2 升级到 1.19.0。
- **BREAKING**：将 `langchain4j-milvus` 从 0.36.2 升级到 `1.19.0-beta29`，这是用户明确接受的当前 1.19 Milvus 发布版本。
- 将核心版本和 Milvus beta 版本拆成两个 Maven 属性，避免引用不存在的 `langchain4j-milvus:1.19.0`。
- 迁移到 `ChatModel`、`StreamingChatModel`、`ChatResponse`、`StreamingChatResponseHandler`、`TokenCountEstimator` 和 `OpenAiTokenCountEstimator`。
- 使用 `ChatRequest` 传递工具规格，保持 Function Calling 行为。
- 更新当前项目依赖说明和 SDK 重试知识文档。

## Capabilities

### New Capabilities

- `langchain4j-1x-baseline`: 约束项目使用 LangChain4j 1.19 原生 API、JDK 17 兼容依赖和明确接受的 Milvus beta29 集成。

### Modified Capabilities

无。本次只迁移依赖和 SDK API，不改变 Agent、RAG、Embedding 或外部接口业务契约。

## Quantified Success Criteria

- SC-1：Maven 实际解析 `langchain4j`、`langchain4j-core`、`langchain4j-open-ai` 为 1.19.0，`langchain4j-milvus` 为 `1.19.0-beta29`。
- SC-2：代码中不再引用 1.19 已移除的 `ChatLanguageModel`、`StreamingChatLanguageModel`、`Tokenizer` 或 `OpenAiTokenizer`。
- SC-3：Oracle JDK 17 下执行 `mvn -Dfrontend.skip=true -DskipTests compile` 返回 0。
- SC-4：工具调用同步与流式路径均通过 `ChatRequest.toolSpecifications` 保留工具规格传递。
- SC-5：不新增或运行测试，不启动应用、模型服务、Milvus 或 Elasticsearch。

## Assumptions

- A-1：用户已明确接受 `langchain4j-milvus:1.19.0-beta29` 的 beta 状态。
- A-2：Maven Central 当前核心与 OpenAI 最新稳定版为 1.19.0，Milvus 对应发布版为 `1.19.0-beta29`。
- A-3：目标四个 JAR 的 class-file major version 为 61，兼容 JDK 17。

## Boundaries

- Always：使用真实存在的 Maven 坐标，使用 1.19 原生 API，并显式用 JDK 17 编译。
- Ask first：若迁移需要改变外部 API、索引格式或模型调用业务语义，先暂停确认。
- Never：不把不存在的 Milvus 1.19.0 写入 POM，不覆盖已有 RAG/Embedding 改动，不改写上一版稳定升级的历史记录。

## Anti-Scope

- 不升级 Spring Boot、Elasticsearch 或 Milvus 服务端。
- 不重构 Agent Runtime、RAG 算法或 Embedding 治理策略。
- 不新增、恢复或运行测试。

## Impact

主要影响 Maven 依赖、模型 Bean 与缓存类型、同步/流式模型调用、ReAct 响应类型、共享 token 估算器和当前依赖文档。Milvus Collection、数据库和 HTTP API 不迁移。
