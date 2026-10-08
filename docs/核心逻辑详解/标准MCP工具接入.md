# 标准MCP工具接入

## 本次实现了什么

项目作为 MCP 客户端，通过 LangChain4j 官方 `langchain4j-mcp:1.20.2-beta30` 连接外部工具服务，使用 Streamable HTTP。握手、协议协商、分页工具发现、远端调用、JSON/SSE 解码交给框架；本地负责服务配置、租户范围、工具绑定、执行准入和生命周期。

新增实现位于 `com.ai.integration.mcp`。原 `com.ai.mcp` 是模型网关的历史包名，仍负责调用大模型，不是标准 MCP 客户端。当前 `app.mcp.servers: {}`，没有默认连接或虚构远端业务工具。

## Function Calling、Tool 与 MCP 如何串起来

本地 `@Tool` 和 MCP 工具都是模型可选择的工具。模型看到名称、用途和参数 Schema，返回工具名与参数；AiServices 驱动后续调用。区别在执行位置：本地工具通过 `DefaultToolExecutor` 调 Java 方法，MCP 工具通过 `McpToolExecutor` 发 HTTP 请求到远端服务。

```text
个人 Agent / 专家 Profile 显式绑定工具
  → 当前请求规划收窄工具候选
  → AiServices 向模型提供本次 ToolSpecification
  → 模型返回 tool_calls（名称、JSON 参数）
  → GovernedToolSession 整批准入
  → AgentToolInvoker / AgentToolExecutionPipeline
      检查租户、RBAC、绑定、参数、风险与 Run 预算
  → McpToolCatalog 检查远端合同是否变化
  → 官方 McpToolExecutor → DefaultMcpClient → StreamableHttpMcpTransport
  → 外部 MCP 服务 tools/call
  → 结果脱敏、截断、Tool Journal / Trace
  → AiServices 将结果送回模型，得到最终回答
```

原 Tool Calling 编排和 Supervisor 节点都复用此工具目录和管道，没有另建绕过治理的 `.toolProvider(...)` 路径。CHAT 不提供可执行工具；Auto 仍由请求规划决定是否进入 REACT / ORCHESTRATED。MCP 本身不负责理解意图，也不会让所有请求多一次 LLM 调用。

## 配置与操作

把下面示例合并到实际使用的 `application-local.yml` 的既有 `app` 节点下。生产使用 `application-prod.yml`；没有覆盖时继承公共配置的空目录。不要在同一个 YAML 文件重复创建顶层 `app`。

```yaml
app:
  mcp:
    servers:
      docs:
        enabled: true
        url: https://your-mcp-server.example/mcp
        authorization: "" # 无鉴权服务留空；否则填写真实 Bearer 凭据
        allowed-tenants: [default] # 替换成允许访问该服务数据的真实租户 ID
        discovery-timeout: 3s
        tool-timeout: 20s
        reconnect-cooldown: 30s
        tools:
          search:
            remote-name: search_docs # 必须与服务实际 tools/list 中名称一致
            description: 检索已接入的文档中心
            read-only: true
            required-permission: app:use
```

示例地址和工具名是占位符，不能直接连通；参数 Schema 从真实服务发现，不在配置里手抄。当前只接纳经部署者确认的只读工具，读写属性不能仅依据远端自报。一个服务的凭据由所列租户共享，只有当这些租户有权访问该凭据对应的数据域时才可这样配置；不是远端逐用户授权代理。

1. 替换地址、凭据、租户和远端工具名，重启后端。本地 HTTP loopback 可用于开发；远程带凭据连接要求 HTTPS，私网还受现有 `app.security.outbound.allow-private-network` 控制。不要提交真实凭据。
2. 打开“我的 Agent”设置或编辑自己的专家。已有“能力/专长”区域会出现用途为“检索已接入的文档中心”的外部只读工具，逐项开启后保存。首次生成个人 Agent 不自动勾选 MCP，旧 Agent 也不自动扩权。
3. 本地注册名是 `mcp_docs_search`，绑定身份是 `tool:mcp_docs_search`。如果另配了 `app.agent.tool-governance.enabled-tools` 显式列表，需要把这个工具名列入；仍需相应 RBAC 权限和允许 MEDIUM 风险。
4. 新会话输入“请通过文档中心检索关于 XXX 的资料，给出依据”。XXX 替换为服务中真实存在的内容；模型自动填参数。若工具只绑定给专家，还需将该专家绑定给主 Agent，并在提问中明确委派该专家。
5. 查看本次运行证据中的工具名称、调用结果、耗时与失败状态。HTTP MCP 请求从后端发出，浏览器 Network 仍主要看到 `/analyze/stream`，不会看到服务端的 `tools/call`。

已绑定但失联/移除的工具仍能在设置中关闭，不会因为目录暂时缺项而无法取消授权。连接状态可由管理员请求 `GET /api/v1/mcp/servers` 查看；接口只返回本节点服务别名与最近状态，不含地址和凭据，也不会主动发起连接。`CONNECTED` 不是实时健康探测的承诺。

## 为什么还保留项目自己的适配代码

- 固定本地名称、用途、租户和权限；不把远端 `instructions` 或工具顶层描述拼进系统提示词。参数 Schema 来自远端，仍须信任和审核接入的服务及其参数描述。
- 首次发现固定框架暴露的规格，每次执行前再次 `tools/list`。合同变化则拒绝发出 `tools/call`；确认新合同后重启并核对原有绑定（不会自动清空数据库中的旧绑定）。远端恢复原合同后可继续执行。这不能消除远端在检查与执行之间自行变更的竞态。
- 有界发现线程池、超时和失败冷却，失联服务不影响内置工具目录。首次访问目录需要发现，执行前也有网络检查，均有额外时延，不能宣称 MCP 自动提速。
- 工具复用原 Pipeline 的有效超时：取服务配置、全局工具超时与根 Run 剩余时间的最小值，覆盖排队和执行。取消按现有 Run 检查与 Future 中断处理，是尽力停止，不保证远端立刻停工。
- SDK 在本版本把 `isError` 抛为类型异常，超时还可能返回普通文本。适配层将其归入失败/超时，避免错误文本被记成成功；业务失败不直接断开连接，发现失败或明确网络异常再进入冷却。
- 禁止业务自动重试，最大尝试一次；官方客户端在旧协议会话失效时可能重新握手并重发，这也是首版仅开放只读工具的原因。

## 参数校验与当前边界

原校验器仅支持顶层简单类型，MCP 常见的嵌套对象、数组会被错误拒绝。本次使用 networknt JSON Schema 校验框架暴露的同一个合同，保留输入长度限制、参数摘要哈希和默认顶层未知字段拒绝；覆盖嵌套、枚举、可空组合、本地定义引用，拒绝暴露合同中的外部引用。

**不等于完整原始 MCP Schema 校验**：当前官方 MCP → ToolSpecification 转换会丢失部分约束，如数值范围、pattern、对象级 anyOf 和 Schema 型 additionalProperties 的值类型。变更检测也只能检测转换后保留的部分。远端必须自己校验完整业务合同，不应把本地校验视作替代。

本次只覆盖 Tools + Streamable HTTP，没有实现 MCP Server、Stdio 子进程、Resources、Prompts、OAuth、采样、交互请求或远端写操作审批/持久化恢复。返回值按当前文本/结构化 JSON 结果处理，未提供多模态工具结果 UI。结果脱敏/截断发生在接收后，没有增加网络响应体字节上限。出站 URL 复用既有守卫并禁重定向，仍有既有 DNS 重绑定窗口。

## 学习与断点

按顺序读：

1. [配置合同](../../src/main/java/com/ai/integration/mcp/McpIntegrationProperties.java)：先明确哪些连接和工具可进入系统。
2. [工具目录](../../src/main/java/com/ai/integration/mcp/McpToolCatalog.java)：`find → connection → connect` 看第一次发现；`execute` 看固定名称映射和真实调用。
3. [统一注册](../../src/main/java/com/ai/agent/tool/governance/AgentToolRegistry.java)：比较本地 DefaultToolExecutor 与 MCP 执行器。
4. [工具准入](../../src/main/java/com/ai/agent/tool/governance/AgentToolAdmissionService.java) 和 [执行管道](../../src/main/java/com/ai/agent/tool/governance/AgentToolExecutionPipeline.java)：框架会执行工具，但哪些工具能执行由项目控制。
5. [AiServices 治理会话](../../src/main/java/com/ai/agent/tool/ai/GovernedToolSession.java)：模型提议如何进入同一执行管道。

本次真实本地 HTTP 协议检查和脚本模型驱动的 AiServices 链路已验证，未连接用户实际外部 MCP 服务或付费模型。证据与未验项见 [验证记录](../../openspec/changes/standard-mcp-tools/verification.md)。

## 更新记录

- 2026-09-30：接入官方 MCP 客户端、文件配置、租户治理和外部工具逐项绑定，记录 SDK 错误/超时行为与 Schema 转换边界。
