# 注解提示词与 Flux 验证记录

日期：2026-09-30。结论：本地实现与验证通过；真实厂商、业务数据库和浏览器联调尚未执行。

## 实现范围

仅覆盖本变更的提示词注解、官方 Reactor 接入、受治理流式 SPI、MVC Flux SSE 和相关配置/文档。工作区存在之前几轮的大量未提交改动，不能把整个 Git diff 都归入本轮。逐任务范围见 sdd-state.md 的 taskLedger。

Core/OpenAI 保持 1.20.2，新增官方 langchain4j-reactor 1.20.2-beta30（beta 模块）。没有新增仓库测试类、修改前端协议、执行数据库迁移、调用付费模型或提交 Git。

## 本地证据

环境：JDK 17.0.9、Maven 3.8.8、项目当前依赖；临时 Java 检查位于 `/tmp/data-agent-flux-UQAPq4`，通过 javac 编译并在独立进程执行。

| 检查 | 结果与覆盖 |
|---|---|
| `mvn -q -Dfrontend.skip=true -DskipTests compile` | 通过；生产代码成功编译，未运行仓库测试或前端构建 |
| `GovernedToolsCheck` | 通过；真实 AiServices 同步/Flux 工具循环、完整批预检、审批暂停与恢复、根 Run 预算、参数/RBAC 拒绝、真实 Registry/Pipeline/calculate 工具回归 |
| `PromptFluxCheck` | 19 项断言通过；系统指令唯一且置顶、动态人设隔离、模板变量字面内容、历史保留、重复系统消息拒绝、提前 CompleteToolCall 不执行、整批审批零副作用、工具旁白与终答分离、首个工具异常阻止后续动作、取消 Future 向模型发布者传播 |
| `FluxLifecycleCheck` | 24 项断言通过；冷订阅、身份捕获/恢复、回答完成前发出片段、取消、绝对超时、256 事件缓冲溢出、启动池拒绝、失败安全输出、完成与审批暂停保护 |
| 嵌入式 Tomcat + DispatcherServlet 实际 HTTP | 使用生产 AnalysisController、AnalysisStreamService 和 MvcStreamingConfig，脚本 Agent；验证 HTTP 200、SSE 类型与格式、JSON 不重复编码、中文逐段到达、最后 done 与正常 EOF；TCP 断连在后续写入时触发 Run 取消，包含在上面 24 项中 |

可复现执行方式（临时文件存在时）：

```sh
task_cp="/tmp/data-agent-flux-UQAPq4/classes:target/classes:$(< /tmp/data-agent-flux-UQAPq4/classpath)"
/Library/Java/JavaVirtualMachines/jdk-17.0.9.jdk/Contents/Home/bin/java -cp "$task_cp" GovernedToolsCheck
/Library/Java/JavaVirtualMachines/jdk-17.0.9.jdk/Contents/Home/bin/java -cp "$task_cp" PromptFluxCheck
/Library/Java/JavaVirtualMachines/jdk-17.0.9.jdk/Contents/Home/bin/java -cp "$task_cp" FluxLifecycleCheck
```

这些检查加载真实框架代理、业务治理与 Servlet 传输，但模型/审批存储采用脚本替身，不等于完整应用的真实业务联调。

## 验证中修正的边界

- 注解在历史预填后生成系统消息，框架默认插到历史后面；启用官方 `alwaysKeepSystemMessageFirst(true)`，不另写消息重排器。
- 原生 MVC 字符串转换器的默认编码可令中文变成问号；明确响应 UTF-8，并配置字符串转换器默认 UTF-8。生产 Boot 默认通常已用 UTF-8，该配置让 MVC 流编码要求显式化。
- 响应式框架收到单个 CompleteToolCall 可以开始执行；治理模型过滤该事件，只在完整响应预检后允许框架执行。
- 框架默认工具执行器和失败收集不能直接套用原串行安全假设；显式使用直接执行器及失败短路，真正工具体仍走有界 Pipeline。

## 真实环境验收

重启后端后，用现有浏览器前端按顺序验收：

1. 普通聊天输入“用三句话解释什么是 SSE”，Network 中 `/api/v1/analysis/analyze/stream` 应为 `text/event-stream`，中文逐步出现，最后 done，无重复回答。
2. 两个不同 Agent 分别设置不同系统指令，连续对话确认指令和历史不串；退出重登后核对最终回答与会话历史。
3. 已授权数据分析能力的 Agent 输入“计算 (12+8)*3，并说明结果”，查看工具轨迹与最终回答；确认数据库保存的是终答而非中间旁白拼接。
4. 使用已启用且具有审批策略的沙箱工具发起动作：先等待审批，审批后由现有恢复 Worker 继续。审批存储与真实恢复本轮未联调；不能以脚本通过替代此项。
5. 较长输出中点击停止，确认 Run 取消、后续片段停止；网络断开以服务端下一次写入或时限检测为准，不承诺厂商立即停止计费。

系统仍有规划、JPA、RAG、工具等待等阻塞阶段；MVC 支持 Flux 不等于系统迁成 WebFlux。前端未修改，本轮未做浏览器或全应用启动验收。
