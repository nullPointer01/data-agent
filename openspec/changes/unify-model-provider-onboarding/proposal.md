## Why

当前模型管理虽然已经通过 LangChain4j 的 OpenAI-compatible 客户端接入多家厂商，但 OpenAI 被错误地包装成“自定义扩展”，厂商模板在前后端重复维护，配置切换会残留旧端点，保存前也无法验证真实 Chat 调用。结果是“名单很多”，但用户难以判断该填什么、是否可用以及失败原因，不符合企业 Agent 对多模型接入的可治理要求。

本变更把模型管理升级为统一模型接入中心：明确支持 GPT 与主流国产 OpenAI-compatible 模型，提供服务端单一厂商目录、模型发现、真实连接测试和专用配置体验，同时保留未来接入专有协议 Adapter 的边界。

## What Changes

- 将 OpenAI/GPT 作为明确的一级厂商，并新增独立的“自定义 OpenAI-compatible”入口，不再混用一个 provider key。
- 后端目录内置 OpenAI、13 个国产/聚合入口和自定义兼容端点；目录统一提供分组、默认端点、推荐模型、鉴权要求和模型发现能力元数据。
- 删除前端硬编码的 18 份模型模板，厂商与推荐模型全部以后端目录为唯一来源。
- 抽取 OpenAI-compatible 客户端工厂，供运行时缓存客户端和临时连接探测共同使用，保持 Chat、Streaming、Tool Calling 与 JSON 输出能力。
- 增加真实 Chat 连接测试接口，返回是否成功、耗时、最终端点、模型名和安全错误分类；编辑场景可安全复用数据库中的加密密钥。
- 支持自定义兼容端点无 API Key 时发现或测试，并保留模型列表接口不可用时的手动模型名兜底。
- 强化服务端配置校验：解析后的 Base URL、模型名必须有效，Temperature 与 Max Tokens 必须在允许范围内，厂商鉴权按目录规则处理。
- 将模型管理页从通用 ResourcePage 改为专用工作界面：厂商分组选择、模型搜索/推荐、连接状态、高级参数折叠、默认/启用状态和紧凑列表。
- 保持现有 `model_config` 表和已有配置兼容，不新增数据库迁移。

## Capabilities

### New Capabilities

- `model-provider-integration`: 定义统一厂商目录、OpenAI-compatible 客户端构建、模型发现、真实连接探测、配置校验与专有协议扩展边界。
- `model-management-experience`: 定义面向管理员的模型选择、凭据配置、模型发现、连接测试、保存和配置列表交互。

### Modified Capabilities

无。当前仓库尚无持久化 OpenSpec capability，本次创建新能力规格。

## Impact

- 后端：`com.ai.mcp` 模型目录/客户端构建与 HTTP 管理能力，`com.ai.service.ModelConfigService`，模型配置 Controller 与 DTO。
- 前端：`ModelsPage.jsx` 和模型页专属样式；不再依赖 ResourcePage 的通用新增/编辑表单。
- API：扩展 `GET /api/v1/models/providers` 响应，新增 `POST /api/v1/models/probe`；保留现有 CRUD 与 `/available` 路径兼容。
- 数据：不修改表结构，不回写连接测试结果，不暴露或记录明文 API Key。
- 依赖：第一阶段继续使用 LangChain4j 1.19 OpenAI-compatible 实现，不新增厂商 SDK。

## Objective And Scope

目标是在一个模型管理入口中可靠配置 GPT、主流国产模型和任意 OpenAI-compatible 服务，并能在保存前知道配置是否真的可调用。

### Anti-Scope

- 不承诺所有厂商、所有模型版本都支持完全一致的 Tool Calling、JSON Schema、推理或多模态能力。
- 不在本阶段实现 Anthropic、AWS Bedrock、Google Vertex AI、华为盘古等专有协议 Adapter。
- 不维护会快速过期的“全部模型 ID”静态清单；优先动态发现，推荐列表仅作起点。
- 不持久化探测 Prompt、模型回复或明文密钥，不把探测接口做成通用模型调用代理。
- 不修改 Embedding 接入、Agent 路由或 RAG 链路。

### Quantified Success Criteria

- SC-1：厂商目录包含 15 个入口：OpenAI、12 家国产厂商、1 家聚合平台和自定义 OpenAI-compatible；OpenAI 与 Custom 是配置页的优先入口。
- SC-2：前端模型页不再包含厂商 URL 或模型模板硬编码，所有目录信息来自一个后端接口。
- SC-3：新增连接探测能真实执行一次最大输出不超过 32 token 的 Chat 请求，并在配置超时内返回成功、耗时或稳定错误分类。
- SC-4：无 Key 的自托管 Custom 兼容端点可以发现或测试；要求 Key 的公网厂商在缺少 Key 时于发请求前失败。
- SC-5：任何不支持 `/models` 的兼容厂商仍可手动填写模型名并执行连接探测、保存配置。
- SC-6：模型管理页在 375px、768px、1024px、1440px 下无横向页面溢出，所有字段有标签，保存/探测有 loading、success 和 error 状态。
- SC-7：API 响应、应用日志和前端状态均不包含明文 API Key；编辑时保留旧密钥的现有语义不变。
- SC-8：已有模型配置无需数据库迁移即可继续读取、编辑、启停、设为默认并参与 Agent 调用。

## Assumptions And Boundaries

详细假设记录在 `sdd-assumptions.md`。

- Always：保持租户隔离、密钥加密/掩码、SSRF 校验、模型缓存失效和 JDK 17 基线。
- Always：OpenAI-compatible 是第一阶段默认协议，厂商差异通过目录元数据和端点解析收敛。
- Ask first：新增专有厂商 SDK、数据库字段、持久化健康状态或自动周期探测。
- Never：提交真实密钥、绕过出站 URL 防护、把用户模型调用降级到未授权的其他厂商。

## Verification Strategy

按项目当前约束，本提案不新增或运行自动化测试、不执行 Maven 编译或前端构建，除非用户后续明确要求。实现阶段以静态检查、OpenSpec 严格校验和浏览器人工流程核对为主；真实连接探测只使用用户主动提供并授权的模型配置。
