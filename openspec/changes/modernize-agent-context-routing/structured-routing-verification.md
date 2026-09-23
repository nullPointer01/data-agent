# C003 原生结构化路由：调研与静态核查

日期：2026-09-23。版本：request-planner-v7。责任人：本会话实现者。

范围：用户授权择优直接改造结构化路由、调研其他方案；保留原 Java Harness。代码基线备份位于 `/tmp/data-agent-schema-c003.iWgj1t/`，原工作树已有改动未重置。

## 选择与公开依据

| 来源 | 官方做法 | 项目采用方式 |
| --- | --- | --- |
| [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs/) | JSON Mode 只约束 JSON 格式；受支持模型可用严格 Schema 约束字段和类型，仍可能拒答或截断 | 通过 OpenAI-compatible response_format 发送原生合同，保留本地严格解析 |
| [LangChain4j Structured Outputs](https://docs.langchain4j.dev/tutorials/structured-outputs/) | 原生 Schema 比 JSON Mode 和提示词约束更可靠，支持 ChatRequest 与 AI Services | 使用现有模型门面中的 ChatRequest，不另建绕过治理的 AI Services 客户端 |
| [LangChain Structured Output](https://docs.langchain.com/oss/python/langchain/structured-output) | ProviderStrategy 使用原生能力，不支持时可选 ToolStrategy；自动选择依赖模型能力信息 | 当前没有能力画像，采用显式 JSON_SCHEMA/JSON_OBJECT 配置，不根据厂商标签猜支持 |
| [LangChain Router](https://docs.langchain.com/oss/python/langchain/multi-agent/router) | 路由器可分类并分发到专长不同的 Agent；这是特定架构模式 | 本项目 Router 主要选择上下文和能力，不把所有问题都委派，也不要求所有 Agent 产品有独立小模型 |
| [Anthropic Structured Outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs) | 原生结构通过 output_config.format 指定，严格工具参数是另一种能力 | 作为设计参考；本轮未新增 Anthropic Native 客户端，不能把协议字段当作通用格式 |

以上是公开框架/厂商能力，不是对未公开商业产品内部实现的断言。原生 Schema 改善结构可靠性，不证明意图识别更准，也不保证耗时更短。

## 已实现

- Router 默认以 JSON_SCHEMA 调用；固定七字段全必填、禁止额外字段，intent/上下文枚举取 Java 定义。动态工具白名单和上限、置信度、有界文本等仍由 Java 检查。
- Schema 不随当前工具目录变化；工具描述仍作为路由输入，避免为每个目录创建不同 Schema。
- `McpModelService.callModelStructured` 与 TEXT/JSON_OBJECT 共用治理路径。输出格式放入 ChatRequest，删除 getJsonChatModel 和重复缓存，默认 YAML 客户端也发送格式要求。
- 同步客户端 strictJsonSchema=true；根据 SDK 源码，仅原生 Schema 分支传递 strict，JSON_OBJECT 和普通请求不受影响。普通连接探测同步调整工厂签名，仍仅测 Chat。
- `planning.response-format` 默认 JSON_SCHEMA，兼容端点可显式选 JSON_OBJECT。失败不追加另一格式调用；现有 429/5xx 等瞬时错误重试仍在。
- 拒答、非法输出、格式不支持最终进入现有保守规划，不扩大工具授权；子 Agent 调用后仍检查根 Run 是否取消/预算终止。
- 日志显示 responseFormat，Trace 计划版本为 v7，未新增表或迁移。

## 独立轻量模型怎么接

1. 在模型管理新增并启用一条模型记录，填写供应商地址、凭据和模型名；优先选中文理解、Schema 输出可靠且延迟低的模型，而不是单看参数规模。
2. 把这条记录的 modelId 写入 `app.agent.planning.model-id`，不是供应商模型名。为空时沿用当前 Agent 模型，再为空则使用租户默认模型。
3. 根据实际能力选 response-format。连接测试只验证聊天；Schema 需用真实规划请求验收。
4. 当前 model-id 是公共应用配置，指定记录仍受租户访问控制。多租户不应使用一个其他租户的私有 ID；本轮未扩展租户级 Router 角色配置。
5. 使用相同 Eval 样本比较意图、上下文计划、工具候选、失败率、P95 和实际 Token。先验证再替换，不能声称“小模型必然更快更省”。

C003 实现时未新增/启用任何远端模型，model-id 为空，没有调整现有模型温度或输出上限。后续用户要求直接配置：local 已显式绑定数据库中现有且启用的 `gpt-5.6-terra`，modelId 为 `6fac3eb2-aea9-4bc7-a61a-ee65e3290e3c`，属于可共享读取的 default 租户；复用地址、凭据与参数，不新建模型、不修改数据库。公共默认仍为空，其他环境不携带本地绑定。只读核实记录及配置优先级、完成 diff 检查，未重启或在线调用。路由的链路是：问题+有限历史+能力描述 → 结构化计划 → 分源装配历史/画像/记忆/知识 → 主模型回答或调用受治理工具。

## 静态核查与边界

- 对照 LangChain4j `1.19.0` 官方源码核实 ChatRequest.Builder.responseFormat、ResponseFormat、JsonSchema/JsonObjectSchema/JsonArraySchema、JsonStringSchema 构造器和 strictJsonSchema 的签名。
- 对照 [OpenAiUtils](https://github.com/langchain4j/langchain4j/blob/1.19.0/langchain4j-open-ai/src/main/java/dev/langchain4j/model/openai/internal/OpenAiUtils.java) 核实：有 Schema → json_schema + strict；无 Schema 的 JSON → json_object；无格式 → 不发送 response_format。
- 全源码引用检查覆盖工厂的注册中心、YAML 配置、连接探测三个调用点；旧 JSON 缓存和接口不再引用。Decision 七字段和 Schema 对齐，兼容模式共享同一解析/授权路径。
- `git diff --check` 通过，`src/test` 目录不存在；本轮没有恢复测试文件。
- 不新增测试类，不主动测试/编译/构建/外部模型调用，遵从用户要求和 CLAUDE.md。静态核查不是编译成功或在线验收证明，旧 51/51 不可复用。
- 配额、模型重试、真实 Token 记账沿用现有实现。上下文预估主要计算消息，Schema 协议额外开销未单独计入事前估算；真实供应商 usage 返回后按其计账，缺失 usage 的估算仍有误差。
- 根 Router 仍在业务 Run 外，子 Router 在根 Run 内；本轮没有重构预算口径。代理可能拒绝、忽略或部分实现 Schema，尚未实测。

## 人工验收步骤（未执行）

1. 启动新后端，用“解释一下 SSE 和异步任务的关系”发起非快捷问句。检查日志 `responseFormat=JSON_SCHEMA`、Trace `request-planner-v7`，及实际 Router 决策是否有效，而非保守降级。
2. 同一会话继续问“那它和 WebSocket 有什么区别”，检查会话指代及独立检索问题；不应凭技术名词打开私有知识检索。
3. 用真实私有资料问题和已授权工具请求分别验收 REQUIRED、OPTIONAL 与候选集合；无授权工具不能因模型输出而获得权限。
4. 在端点明确拒绝 json_schema 的受控环境，确认没有自动追加 json_object 请求；配置 JSON_OBJECT 并重启后，日志应切换且仍执行本地合同校验。认证/超时错误不能靠切格式绕过。
5. 使用 YAML 默认模型（无数据库默认模型）验收 JSON 请求确实携带格式；普通聊天、记忆提取和连接探测分别回归。

当前结论：实现和静态审查完成；编译与真实兼容性、准确率、延迟待验。
