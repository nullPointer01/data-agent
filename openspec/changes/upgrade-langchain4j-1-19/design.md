## Context

LangChain4j 1.19 移除了项目在 0.36.2 使用的 `ChatLanguageModel`、`StreamingChatLanguageModel`、`Tokenizer` 和 `OpenAiTokenizer`。新的模型契约使用 `ChatModel` / `StreamingChatModel`，响应使用 `ChatResponse`，流式回调使用 `StreamingChatResponseHandler`，token 预算使用 `TokenCountEstimator`。工具调用不再通过旧 `generate(messages, tools)` 重载，而是由 `ChatRequest` 携带 messages 和 toolSpecifications。

Milvus 1.19 集成只发布了 `1.19.0-beta29`，用户已明确接受这一预发布依赖。实际 JAR 字节码检查显示核心和 Milvus 均为 major version 61，符合 JDK 17。

## Goals / Non-Goals

**Goals:**

- 核心和 OpenAI 升级至 1.19.0，Milvus 升级至 `1.19.0-beta29`。
- 使用 1.19 原生 Chat、Streaming、Response 和 token 估算 API。
- 保留同步、流式、JSON 模式、工具调用、token 记账和重试行为。
- 通过 JDK 17 完整编译生产源码。

**Non-Goals:**

- 不包装或恢复 0.x 已删除接口。
- 不升级其他技术栈或重构业务流程。
- 不声明 beta Milvus 集成具备稳定版承诺。

## Decisions

### 核心与 Milvus 使用独立版本属性

`langchain4j.version=1.19.0` 用于核心和 OpenAI，`langchain4j-milvus.version=1.19.0-beta29` 单独管理 Milvus。不能让 Milvus引用核心属性，因为 `langchain4j-milvus:1.19.0` 不存在。

### 全链路迁移到 ChatResponse

`McpModelService` 及 ReAct 调用方直接使用 `ChatResponse`，通过 `aiMessage()`、`tokenUsage()` 和 `finishReason()` 读取结果。替代方案是把新响应转换回旧 `Response<AiMessage>`，但会继续扩大遗留 API 的生命周期。

### 工具调用统一构造 ChatRequest

同步和流式调用都使用 `ChatRequest.builder().messages(...).toolSpecifications(...).build()`，无工具时仍允许直接调用 message 列表重载。这样保留工具 schema，同时贴合 1.19 原生契约。

### Tokenizer 迁移为 TokenCountEstimator

共享实例改为 `OpenAiTokenCountEstimator("gpt-4")`，保持既有编码选择和单例复用。调用点改用 `estimateTokenCountInText` / `estimateTokenCountInMessages`。

## Risks / Trade-offs

- [风险] Milvus beta29 可能存在兼容性缺陷 → 明确独立版本属性和 beta 状态，编译验证后仍保留运行时风险说明。
- [风险] Streaming 回调协议变化导致 token 丢失 → 使用 `onPartialResponse` 原样转发，使用 `onCompleteResponse` 完成 future。
- [风险] 工具规格遗漏导致 ReAct 退化 → 同步和流式请求都集中通过 `ChatRequest` 设置 toolSpecifications。
- [风险] 编译成功不覆盖真实模型与 Milvus 行为 → 不宣称运行时已验证，后续用真实环境 smoke test。

## Migration Plan

1. 拆分 Maven 版本属性并更新四个 LangChain4j artifact 解析版本。
2. 迁移 TokenCountEstimator、模型 Bean 和 ModelClientRegistry 类型。
3. 迁移 McpModelService 同步/流式请求与返回类型。
4. 迁移 ReAct 响应类型并编译修复剩余 1.x API。
5. 更新当前文档，核对依赖树、JDK 17 编译和 OpenSpec。

回滚时恢复 0.36.2 单版本属性，并恢复上一变更记录中的 0.36 API 调用；不涉及数据迁移。

## Open Questions

无。Milvus beta 风险已由用户明确接受。
