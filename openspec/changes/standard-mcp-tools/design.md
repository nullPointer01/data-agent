# 标准 MCP 工具接入

用户已明确选择“继续技术改造，先接标准 MCP”。本次按授权直接实施协议工具接入；不重做已有 Agent Runtime，不升级 Java/Spring。

## 范围与分工

使用同系列官方 langchain4j-mcp 1.20.2-beta30，Streamable HTTP Transport + DefaultMcpClient + McpToolExecutor。协议协商、分页 tools/list、tools/call、JSON/SSE 响应由框架负责。项目管理连接生命周期、显式工具映射、租户边界和治理合同。

配置使用 app.mcp 的可见文件配置，无数据库/管理 UI 新表；默认 servers 为空，因此不虚构已经连接外部服务。每个启用服务必须配置精确 allowed-tenants、工具别名到远端名称的映射、用途和只读确认；不信任远端 annotation 来自行授权。首版只读、幂等、禁止自动业务重试，不声称支持远端写审批和 exactly-once。

现有个人/专家设置只有固定能力包，需要在同一 CapabilityPackPicker 中增加外部工具逐项开关，闭合“发现→显式绑定→运行”的产品入口，不新增连接管理页。首次创建默认 Agent 排除 MCP 自动绑定；已失联工具仍可取消绑定。

McpToolCatalog 提供固定配置名称及懒加载的远端 Schema；AgentToolRegistry 合并已有工具与 MCP 工具。模型名称 mcp_<server>_<alias> 必须唯一且不超 64 字符，工具还要经 Profile 显式绑定。目录和执行准入都检查租户；服务共享凭据只可接入管理员确定可向该租户授权的数据域，不能代替远端逐用户数据授权。

首次 tools/list 固化模型 Schema；执行前重读远端规格，变化则拒绝，重启后重新确认配置/合同。断连使用有界发现超时与冷却，不让失败初始化无限重试；已知注册规格保留，调用失败由原 Pipeline 记录真实失败。关闭应用释放客户端，不记录远端指令、凭据和原始错误内容。

URL 使用既有 OutboundUrlGuard，禁重定向；只允许 Authorization 自定义凭据头，不允许用户问题提供 URL/命令/header。使用框架协议协商，关闭主动日志、远端日志回调、自动健康重连/订阅和多轮服务器请求执行。

既有参数校验只支持顶层标量，改用 networknt JSON Schema 库验证框架暴露的同一 Schema，保留长度上限、默认顶层未知字段拒绝、脱敏摘要；支持嵌套对象、数组、枚举、引用。禁止外部 Schema 引用。官方 MCP → ToolSpecification 转换不保留所有原始 JSON Schema 约束，远端仍负责完整业务校验，文档明确此限制。

## 成功标准（执行者：Codex；环境：本地临时协议服务，无业务库写入）

1. 真实官方客户端经本地 HTTP 完成协商、分页发现和一次真实工具调用；有连接/工具名称映射证据。
2. 禁止未配置/未绑定/其他租户工具调用，嵌套参数错误在 tools/call 前拒绝；原内置工具回归通过。
3. isError、断连、超时、Schema 漂移不能算作成功；文本/结构化 JSON 结果均走原脱敏与截断管道；应用关闭连接可回收。
4. 两种 Agent 编排均共用该 Registry/Pipeline，无旁路 AiServices.toolProvider；编译和相关临时检查通过，不新增仓库测试类。
5. 中文学习文档提供配置、调用链、断点、真实外部服务器尚未配置的事实。

不实现：MCP Server 产品、Stdio 任意进程执行、Resources/Prompts/OAuth/采样与用户交互、多租户远端身份代理、热更新管理台、收费模型性能评测。
