## 1. Provider Catalog And Resolution

- [x] 1.1 扩展服务端 Provider Catalog，拆分 OpenAI/GPT 与 Custom，补齐分组、协议、鉴权、发现能力和推荐模型元数据。
  - Acceptance: 目录返回 15 个入口，不再返回 Ollama，OpenAI 与 Custom 位于配置页优先入口。
  - Verify: 检查 Provider API 输出结构、顺序和敏感字段白名单。
  - Files: `src/main/java/com/ai/mcp/ModelProviderCatalog.java`, `src/main/java/com/ai/modelconfig/dto/ProviderCatalogResponse.java`
  - Covers: SC-1, SC-2, SC-8

- [x] 1.2 新增统一端点解析器，集中处理目录默认值、别名、URL 规范化、可选鉴权和 OutboundUrlGuard。
  - Acceptance: Runtime、Discovery、Probe 可复用同一解析结果；公网必填 Key、Custom 可选 Key；禁止地址在发请求前失败。
  - Verify: 静态检查所有用户 Base URL 的请求路径均调用统一解析器。
  - Files: `src/main/java/com/ai/mcp/ModelEndpointResolver.java`, `src/main/java/com/ai/mcp/ResolvedModelEndpoint.java`, `src/main/java/com/ai/mcp/ModelProviderCatalog.java`
  - Covers: SC-4, SC-7, SC-8

## 2. Shared Client Construction

- [x] 2.1 抽取无状态 ModelClientFactory，统一构建 LangChain4j 1.19 同步与流式客户端并关闭 SDK 同步重试。
  - Acceptance: Factory 支持普通、JSON、Streaming 三类现有客户端；API Key 不进入日志；同步 maxRetries 为 0。
  - Verify: 对照现有 Builder 参数检查 temperature、maxTokens、timeout、responseFormat 行为保持一致。
  - Files: `src/main/java/com/ai/mcp/ModelClientFactory.java`, `src/main/java/com/ai/config/LangChain4jConfig.java`, `src/main/java/com/ai/mcp/ModelClientRegistry.java`
  - Covers: SC-7, SC-8

- [x] 2.2 将 ModelClientRegistry 改为只负责租户配置解析、客户端缓存和失效，所有客户端构建委托给 Factory。
  - Acceptance: 默认模型、租户模型、JSON 模型和 Streaming 模型缓存语义不变；模型配置变更仍清理全部相关缓存。
  - Verify: 静态追踪 McpModelService 与 ReAct 调用链，确认没有新增厂商分支或双层重试。
  - Files: `src/main/java/com/ai/mcp/ModelClientRegistry.java`, `src/main/java/com/ai/mcp/ModelClientFactory.java`, `src/main/java/com/ai/mcp/McpModelService.java`
  - Covers: SC-8

## 3. Discovery, Probe And Validation

- [x] 3.1 让模型发现复用统一解析器，支持无 Key 本地端点、排序去重，并将“不支持 /models”作为可恢复结果。
  - Acceptance: Custom 不因空 Key 被拒绝；发现失败响应明确允许手填；响应不包含 Key。
  - Verify: 检查 Authorization Header 仅在解析结果含凭据时发送，检查返回模型列表排序去重。
  - Files: `src/main/java/com/ai/mcp/ModelHttpClient.java`, `src/main/java/com/ai/service/ModelConfigService.java`, `src/main/java/com/ai/modelconfig/dto/AvailableModelsResponse.java`, `src/main/java/com/ai/mcp/ModelEndpointResolver.java`
  - Covers: SC-4, SC-5, SC-7

- [x] 3.2 新增真实 Chat 连接探测服务和 `/api/v1/models/probe` 接口，支持编辑场景安全复用已保存密钥。
  - Acceptance: 临时客户端不入缓存、不重试、输出上限不超过 32 token；响应包含耗时、解析后目标、finish reason 或稳定错误码，不含正文与密钥。
  - Verify: 逐项检查异常映射、租户权限、超时、日志字段和临时配置不落库。
  - Files: `src/main/java/com/ai/service/ModelConnectionProbeService.java`, `src/main/java/com/ai/modelconfig/dto/ModelProbeRequest.java`, `src/main/java/com/ai/modelconfig/dto/ModelProbeResponse.java`, `src/main/java/com/ai/controller/ModelConfigController.java`, `src/main/java/com/ai/mcp/ModelClientFactory.java`
  - Covers: SC-3, SC-4, SC-5, SC-7

- [x] 3.3 强化创建和更新的解析后校验，保留掩码密钥与单默认模型语义。
  - Acceptance: name/provider/Base URL/modelName、temperature 0..2、maxTokens 1..1000000 均有字段级错误；无数据库迁移；失败不产生部分更新。
  - Verify: 对照 create/update 两条路径检查校验调用顺序、事务边界、默认模型清理和缓存事件。
  - Files: `src/main/java/com/ai/modelconfig/dto/ModelConfigRequest.java`, `src/main/java/com/ai/service/ModelConfigService.java`, `src/main/java/com/ai/mcp/ModelEndpointResolver.java`, `src/main/java/com/ai/controller/ModelConfigController.java`
  - Covers: SC-4, SC-7, SC-8

## 4. Dedicated Model Management Experience

- [x] 4.1 将 ModelsPage 改为专用模型工作区，并拆出厂商选择器与模型编辑器组件。
  - Acceptance: 删除前端厂商模板硬编码；支持目录加载、搜索/分组、推荐/发现/手填模型、创建、编辑、启停、默认和删除。
  - Verify: 静态检查所有 provider URL/model 推荐均来自 `/providers`，列表 key 使用 modelId。
  - Files: `frontend/src/pages/admin/ModelsPage.jsx`, `frontend/src/components/models/ModelProviderPicker.jsx`, `frontend/src/components/models/ModelEditorModal.jsx`
  - Covers: SC-1, SC-2, SC-5, SC-8

- [x] 4.2 在模型编辑器中实现连接测试状态机、字段变更失效和高级配置渐进展示。
  - Acceptance: idle/loading/success/error 状态尺寸稳定；连接参数改变后成功状态失效；保存和探测互不混淆；掩码密钥不会被当成新 Key。
  - Verify: 检查 Provider 切换的自动值/手工值规则，检查所有异步按钮 disabled 与错误反馈。
  - Files: `frontend/src/components/models/ModelEditorModal.jsx`, `frontend/src/pages/admin/ModelsPage.jsx`, `frontend/src/api/client.js`
  - Covers: SC-3, SC-6, SC-7

- [x] 4.3 增加模型页专属响应式样式，复用现有设计变量、Lucide 图标和基础 Modal/Table 组件。
  - Acceptance: 375/768/1024/1440 布局规则明确，无页面级横向滚动，文本不重叠，交互控件有 focus/loading/disabled 状态，圆角不超过 8px。
  - Verify: 在用户明确授权运行前端后，以浏览器截图检查四个视口；未授权前只执行 CSS/DOM 静态核对。
  - Files: `frontend/src/styles.css`, `frontend/src/pages/admin/ModelsPage.jsx`, `frontend/src/components/models/ModelProviderPicker.jsx`, `frontend/src/components/models/ModelEditorModal.jsx`
  - Covers: SC-6

## 5. Documentation And Verification

- [x] 5.1 更新项目模型接入文档和常见陷阱，说明兼容协议覆盖边界、模型发现兜底、探测成本和私网安全配置。
  - Acceptance: 文档不宣称支持所有专有协议，不列举密钥，不把推荐模型当成永久可用清单。
  - Verify: 搜索旧的“自定义（OpenAI 兼容扩展）”当前态描述与前端模型模板残留。
  - Files: `CLAUDE.md`, `README.md`, `docs/演化/常见陷阱.md`
  - Covers: SC-1, SC-5, SC-7

- [x] 5.2 执行提案允许的静态验收并记录未执行项。
  - Acceptance: OpenSpec strict validation、diff integrity、敏感信息扫描和旧模板扫描通过；不在未获用户明确授权时运行测试、Maven 编译或前端构建。
  - Verify: 输出 `verify-report.md`，逐项记录 SC-1..SC-8 的证据和真实外部模型未验证风险。
  - Files: `openspec/changes/unify-model-provider-onboarding/verify-report.md`, `openspec/changes/unify-model-provider-onboarding/tasks.md`, `openspec/changes/unify-model-provider-onboarding/sdd-state.md`, `openspec/changes/unify-model-provider-onboarding/sdd-assumptions.md`
  - Covers: SC-1, SC-2, SC-3, SC-4, SC-5, SC-6, SC-7, SC-8

## Test Strategy Override

用户已明确要求后续默认不新增或运行测试，也不执行 Maven 编译。该项目不是 goods-ds，但用户指令优先于通用 SDD 测试模板，因此本 change 不创建测试任务；本次为使用户明确要求的页面变化进入 Spring Boot 静态资源而执行前端生产构建，不运行真实厂商探测。
