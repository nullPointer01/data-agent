# 标准 MCP 接入验证

日期：2026-09-30。结论：PASS_WITH_NOTES。框架协议接入及本地验证完成；未配置用户真实外部服务，真实模型和浏览器联调待验。

## 实施和验证证据

- JDK 17，Maven `-Dfrontend.skip=true -DskipTests compile` 通过。新增官方 MCP 1.20.2-beta30 与 networknt JSON Schema 1.5.9；Java/Spring 底座未升级。
- 前端 `npm run build` 通过。产物更新到原 `src/main/resources/static`，包含此前未打包的工作区前端修改；未改写那些源码。当前入口引用 `index-CZG8gPUi.js` / `vendor-x7Yyc7qk.js`，旧入口构建文件由 Vite 清理，可重新构建恢复产物。
- `McpProtocolCheck` 36 项断言通过：临时真实 HTTP 服务，官方客户端实际握手、分页 tools/list、本地名称到远端名称映射、Authorization/会话头、tools/call、JSON structuredContent 和 SSE 响应；实际生产 Registry/Pipeline/Run/Journal/脱敏/截断；越租户在发现前拒绝、RBAC/未绑定拒绝、嵌套参数错误、业务 isError、Schema 漂移、超时、Run 取消和关闭；脚本模型通过真实 AiServices 驱动远端工具并回到终答；失联服务不破坏内置工具。
- `SchemaCheck` 10 项检查通过：嵌套对象/数组、嵌套类型错误、枚举、null 根、尾随 JSON、局部定义引用与错误类型、显式可空 anyOf、未知字段。
- `McpWiringCheck` 8 项检查通过：真实 Spring Binder 嵌套配置与默认值、生产 CapabilityService 默认绑定排除 MCP、显式绑定允许、空配置不连接、诊断接口 ADMIN 注解。后者只验证声明；没有完整 HTTP 登录鉴权集成测试，项目已有 EnableMethodSecurity。
- `McpPickerCheck` 7 项组件交互/静态渲染检查通过：显示外部工具、开启/关闭保留其他绑定、失联绑定可移除、不可用未绑定工具不能开启、空目录保留原四个能力包。未做真实浏览器视觉验收。
- 既有回归：GovernedToolsCheck 171、SupervisorCheck 366、ConfiguredRoutingCheck 8、AgenticWorkflowCheck 97 项断言通过。使用真实框架和受治理服务，模型为脚本模型；数量含重复/循环断言，不代表独立场景数。
- `git diff --check` 通过。所有验证类仅在 `/tmp`，未新增仓库测试类，未写业务库、未调用付费模型、未提交 Git。

临时验证目录：`/tmp/data-agent-mcp-Gx4Wg9`。其中保存 MCP 精确版本官方源码、检查源码、依赖 classpath 和既有回归日志；复用 `/tmp/data-agent-flux-UQAPq4` 的辅助类，以及先前 Supervisor/委派检查源码。协议检查的实际输出包括工具 `SUCCESS`、`UNAUTHORIZED`、`INVALID_ARGUMENTS`、`EXECUTION_FAILED`、约 801ms 的 `TIMEOUT`（配置 800ms）和 `RUN_TERMINATED`。

## 验证中修正的问题

1. 原参数校验只支持顶层简单类型，嵌套 MCP 参数会失败；切换 Schema 库后上述合同检查通过。
2. 官方客户端把业务错误也抛为 ToolExecutionException，不能因此断线/冷却。按实际异常链区分网络故障，业务失败保留连接；SDK 超时普通文本单独识别为 TIMEOUT，不能只看 isError。
3. 原默认 Agent 会收集全部当前可用 Tool，新增 MCP 后会自动授权；显式排除外部工具并验证可手动绑定。
4. 原前端只有固定四类能力包，无法选择新增远端工具；复用个人/专家设置增加逐项开关和失联取消绑定。

## 限制与交接

- 当前空配置。需要真实 Streamable HTTP 地址、鉴权方式、工具名与租户范围后，按学习文档填写 local/prod 并重启，验收实际模型选工具、结果与 Trace。未伪造“外部业务系统已接通”。
- SDK 为 beta；Schema 转换会损失原始约束，本地校验和漂移检测仅针对框架暴露部分，远端必须完整校验。参数 Schema 里的描述仍来自远端，需审核接入服务；仅替换顶层工具用途并不等于完全消除提示注入。
- 只读是管理员确认的合同，不是对远端代码的技术证明；共享凭据需要事先满足租户数据授权。不支持远端写操作审批恢复、OAuth、Stdio、Resources/Prompts、采样或交互请求。
- 应用不自动业务重试，但 SDK 旧协议会话 404 可重握手并重发。取消尽力而为，JDK 17/HTTP SDK 不能保证远端立即停工；结果体仅接收后截断，未限制网络响应体字节数。
- 每次调用前重新 tools/list 有性能成本，未测真实延迟/P95。出站守卫的既有 DNS 重绑定窗口仍在。
- 本轮保留大量既有未提交修改；新源码集中在 integration/mcp，旧文件只新增本轮挂接、授权、Schema 与文档片段。归属见 sdd-state.md 的 taskLedger。
