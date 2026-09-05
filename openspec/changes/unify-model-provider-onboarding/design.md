## Context

运行时模型调用已经统一迁移到 LangChain4j 1.19，但模型配置仍有三套事实来源：`ModelProviderCatalog` 保存厂商默认值，`ModelsPage.jsx` 保存 18 份模板，数据库保存用户最终配置。前端模板与后端目录会漂移，且通用 ResourcePage 无法表达厂商分组、动态模型发现、连接状态和高级参数。

当前 `ModelClientRegistry` 直接构建 `OpenAiChatModel` 和 `OpenAiStreamingChatModel`，`ModelHttpClient` 则独立解析端点、鉴权和 Kimi URL。运行时 Chat 请求没有经过 `OutboundUrlGuard`，而模型列表请求经过该校验，形成不一致的出站安全边界。连接测试若直接依赖 Registry，又会与 Registry 对 `ModelConfigService` 的依赖形成循环。

约束如下：JDK 17、Spring Boot 3.2、LangChain4j 1.19；已有 `model_config` 数据必须兼容；API Key 继续由 JPA Converter 加密并在响应中掩码；用户此前要求默认不新增/运行测试和编译。

## Goals / Non-Goals

**Goals:**

- 用一个服务端目录描述 OpenAI、主流国产厂商和自定义兼容端点。
- 让运行时调用、模型发现和连接探测共享相同的端点解析与安全校验。
- 保存前真实验证选定模型的 Chat 能力，并返回可操作的错误。
- 用专用模型管理界面替代通用资源表单，减少认知负担和误配置。
- 为未来专有协议 Adapter 保留清晰边界，但不提前引入无实际消费者的复杂框架。

**Non-Goals:**

- 不实现专有协议厂商 SDK。
- 不探测或承诺每个模型的 Tool Calling、JSON Schema、多模态与推理能力。
- 不持久化健康状态，不增加定时探测任务。
- 不修改 Agent、Embedding、RAG 或数据库 Schema。

## Decisions

### 1. OpenAI-compatible 作为默认协议，而不是把 OpenAI 当成唯一厂商

目录新增 `protocol` 字段，第一阶段所有内置项均使用 `OPENAI_COMPATIBLE`。OpenAI 使用独立 `openai` key，显示名为 `OpenAI / GPT`；自定义端点使用独立 `custom` key。

这样 GPT 与国产模型在产品概念上是不同厂商，在传输层仍复用同一个成熟协议。替代方案是为每家厂商引入 LangChain4j 模块，但会扩大依赖、配置和行为差异，而且多数厂商已经提供兼容端点。

### 2. 厂商目录是 UI 模板的唯一来源

`ModelProviderCatalog` 扩展为以下元数据：

- `key`、`displayName`、`group`
- `protocol`
- `defaultBaseUrl`、`defaultModelName`
- `recommendedModels`
- `apiKeyRequired`
- `modelDiscoverySupported`

Provider API 原样输出安全元数据。前端不再保存 URL 和模型 ID 模板。推荐模型是配置起点，不是完整或永久有效的型号清单；用户始终可以手动输入。

### 3. 抽取无状态 ModelClientFactory

新增无状态 `ModelClientFactory`，接收解析后的连接参数并创建同步/流式 LangChain4j 客户端。`ModelClientRegistry` 继续负责租户缓存和失效，但不再自行拼 Builder；连接探测直接使用 Factory 创建短生命周期客户端，不进入缓存。

Factory 统一执行：

1. 目录别名解析和默认值补齐；
2. Base URL 规范化；
3. `OutboundUrlGuard` 校验；
4. 按目录规则校验 API Key，允许 Custom 自托管服务无 Key；
5. `maxRetries(0)`，由正式调用链的业务执行器决定是否重试。

替代方案是让探测 Service 调用 `ModelClientRegistry`，但 Registry 依赖 ModelConfigService，会造成循环依赖且会污染正式客户端缓存。

### 4. 模型发现与 Chat 探测分离

`POST /available` 仍只负责尝试厂商 `/models`，失败后明确允许手填，不作为保存门禁。新增 `POST /probe` 使用与正式调用相同的 ChatModel，发送固定最小提示词并限制最大输出为 32 token，不经过应用层重试。

探测响应只返回：状态、错误码、安全消息、耗时、最终 provider/baseUrl/modelName、finish reason。不会返回模型正文、请求 Prompt 或密钥。错误码至少区分 `AUTHENTICATION`、`MODEL_NOT_FOUND`、`INVALID_ENDPOINT`、`RATE_LIMITED`、`TIMEOUT`、`NETWORK`、`UNSUPPORTED`、`UNKNOWN`。

编辑场景传入掩码密钥时，由 ModelConfigService 在当前租户权限内取回已保存密钥；新增场景使用请求中的临时密钥，不持久化。

### 5. 保存校验使用“解析后配置”

创建和更新都在服务端解析厂商默认值，再校验最终 Base URL、modelName、鉴权、temperature 和 maxTokens。建议范围为 temperature `0..2`、maxTokens `1..1_000_000`。更新时空值只表示不修改已有可选字段，不允许把必填名称、厂商或解析后的模型配置置为不可运行状态。

保存不强制先探测，因为离线部署、临时网络故障和不支持模型发现都不应阻止管理员录入；UI 会明确显示“未测试”并建议测试。

### 6. 模型页采用单页管理 + 分阶段编辑器

列表保持适合运维扫描的表格，不改成卡片墙。新增/编辑弹窗使用以下分区：

1. 厂商：OpenAI 与 Custom 优先展示，国内厂商和聚合平台分组选择；
2. 凭据和模型：API Key、动态发现、推荐型号、手动输入；
3. 连接测试：稳定状态区显示耗时和错误；
4. 高级设置：Base URL、temperature、maxTokens、默认和启用开关。

切换厂商时，系统覆盖由上一个厂商自动填充且未被用户编辑的默认值；用户手工改过的值不会静默覆盖。OpenAI 在全局分组首位。使用 lucide 图标、现有颜色变量和最大 8px 圆角，不引入新 UI 依赖。

### 7. 第一阶段不引入通用 Adapter 注册框架

`protocol` 与 Factory 是扩展边界。只有出现首个专有协议需求时，再引入 `ModelProviderAdapter` 接口和多实现注册表。现在只有一个协议，提前做 SPI 会增加抽象但没有消除分支。

## Risks / Trade-offs

- [厂商兼容端点并非完全一致] → 目录只承诺 Chat 基线，模型发现失败可手填，真实探测作为最终判断。
- [推荐模型名会过期] → 推荐列表由后端单点维护，UI 不把推荐项标成“全部可用模型”。
- [探测产生少量费用] → 固定最小 Prompt、最多 32 输出 token、无重试、仅用户主动触发。
- [私网模型被 SSRF 防护拦截] → 继续使用既有 `allow-private-network` 显式配置，不为模型页创建绕过路径。
- [旧配置 provider 使用别名] → Catalog 继续大小写不敏感地解析 aliases，响应展示 canonical key，但不强制数据迁移。
- [Custom 无 Key 可能误配公网端点] → 允许是为了自托管场景，UI 明示安全责任，服务端仍执行 URL 校验。
- [浏览器页面改造与通用 ResourcePage 风格不完全一致] → 复用现有基础变量、按钮、Modal 与表格视觉，不改变全站设计系统。

## Migration Plan

1. 扩展 Provider Catalog 与响应 DTO，新增 `custom`，移除不再需要的 Ollama 专用入口。
2. 抽取端点解析和客户端 Factory，让 Registry 切换到 Factory；此时正式模型行为保持不变。
3. 扩展模型发现的无 Key 语义，新增 Probe DTO、Service 和 Controller 路径。
4. 替换 ModelsPage 的通用 ResourcePage 接入，删除前端模板常量并增加专属样式。
5. 用既有配置检查读取/编辑兼容性；用用户主动提供的配置手工验证 GPT、一个国产厂商和一个无 Key 本地端点。

回滚时恢复旧 ModelsPage 与 Registry 构建逻辑即可；数据库无迁移，不需要数据回滚。新 `custom` 配置回滚后可继续按未登记 provider + 显式 Base URL 的既有逻辑工作。

## Open Questions

- 专有协议 Adapter 的首个目标厂商尚未确定，本阶段只保留扩展边界。
- 是否在后续版本持久化最近探测状态与时间，需要单独评估数据库字段和定时健康检查成本。
