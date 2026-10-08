# 通用角色与 Agentic 工作流验证

日期：2026-09-30。结果：本地实现与检查通过；真实模型、业务数据库和浏览器联调未执行。

## 本轮范围

通用默认角色、公共行为规则、旧内置个人角色的精确文本兼容；新增官方 Agentic 依赖与专家委派的顺序工作流。旧的手工规划→执行调用已由框架路径替换，没有并行保留第二条委派实现。

节点适配现有语义规划器与配置化执行器，不绕过 AiServices/工具管道。框架顺序工作流没有额外模型调用；实际父模型仍通过 Tool Calling 选择专家。没有实施 Supervisor、标准 MCP、Java/Spring 升级或另一套审批持久化。

## 验证环境与结果

JDK 17.0.9、Maven 3.8.8；Core 1.20.2、Agentic 1.20.2-beta30。检查源码位于 `/tmp/data-agent-agentic-rW9Tks`，未向仓库新增测试类。

| 检查 | 结果 |
|---|---|
| Maven `-Dfrontend.skip=true -DskipTests compile dependency:build-classpath` | 通过，生产源码与新增依赖兼容 |
| PromptPolicyCheck | 9 项通过：通用空角色、无固定工具名、无全局串行限制、能力/审批边界、自定义专家角色、旧工厂精确兼容、用户改写保留、空白归一化 |
| AgenticWorkflowCheck | 97 项通过：真实 AgenticServices 顺序执行与参数绑定，失败短路、原异常传播、取消、同一根模型预算、20 次并发状态隔离，实际 delegateToAgent 入口、父请求保持、子模型覆盖清空、绑定/停用/跨租户/自循环/第四层拒绝、Guard 拒绝后清理 |
| GovernedToolsCheck（前轮临时脚本） | 通过：真实 AiServices 同步/Flux 工具循环、完整批预检、审批信号、Checkpoint/恢复预算及真实 Registry/Pipeline/calculate 回归；本次运行报告 172 项断言 |
| PromptFluxCheck（前轮临时脚本） | 19 项通过：声明式提示词、真实框架 Flux、整批准入与取消传播 |

提示词检查先在旧代码复现失败：“empty role must default to general agent”；Agentic 检查初始编译缺少新工作流类，接入后成功执行。使用真实框架构造器与代理、实际委派入口及 Guard；规划/模型执行/数据仓库为替身，不等于真实业务端到端验收。

临时复现命令（脚本及 Maven 生成的 classpath 文件存在时）：

```sh
task_cp="target/classes:/tmp/data-agent-flux-UQAPq4/classes:$(< /tmp/data-agent-agentic-rW9Tks/classpath)"
/Library/Java/JavaVirtualMachines/jdk-17.0.9.jdk/Contents/Home/bin/javac -cp "$task_cp" -d /tmp/data-agent-agentic-rW9Tks/classes /tmp/data-agent-agentic-rW9Tks/PromptPolicyCheck.java /tmp/data-agent-agentic-rW9Tks/AgenticWorkflowCheck.java
/Library/Java/JavaVirtualMachines/jdk-17.0.9.jdk/Contents/Home/bin/java -cp "/tmp/data-agent-agentic-rW9Tks/classes:$task_cp" PromptPolicyCheck
/Library/Java/JavaVirtualMachines/jdk-17.0.9.jdk/Contents/Home/bin/java -cp "/tmp/data-agent-agentic-rW9Tks/classes:$task_cp" AgenticWorkflowCheck
```

## 行为和架构核对

- @Agent 标注现有 Java 服务适配节点，sequenceBuilder 指定顺序，outputKey/@V 传递 plan 和 response。不是单纯新增依赖或孤立演示。
- 无 @MemoryId、无 AgenticScopeStore，工作流状态为本次临时状态；不新增业务数据表或覆盖聊天历史。
- 节点同步顺序执行，仍处于原有受治理委派线程和作用域，不增加默认无界线程池。
- Guard 提前覆盖规划阶段；非法委派在规划模型调用前拒绝。两个步骤都检查原 Run，模型计数仍由原网关负责。
- 框架节点异常会经过 AgentInvocationException/InvocationTargetException 包装，在工作流边界解包；不自动重试，不改变失败为文本成功。
- 通用提示词不保证实际执行权限，服务端工具准入仍是强制边界。旧工厂默认仅运行时兼容，数据库原文不会自动变化。

## 重启后怎么学与验收

1. 默认个人 Agent 输入“你能帮我做什么”，观察能力介绍；再用一个自定义专家验证角色仍保留。运行期兜底已通用化，但数据库自定义角色不会自动改写。
2. 在“我的 Agent”绑定一个已启用、当前用户可用的专家。输入“请让【实际专家名称】完成它擅长的这项任务：【具体任务】”。是否选择委派由实际路由/父模型决定，单写一个专家名不构成执行保证。
3. 断点放在 AgentDelegationToolService.delegateToAgent、AgenticDelegationWorkflow.execute、PlanningStep.plan、ExecutionStep.execute。观察执行节点的 plan 由框架注入，root Run ID 不变。
4. 核对父 Agent 最终回答和工具轨迹；没有专家绑定的普通聊天应继续走 AiServices，不进入该工作流。
5. 取消进行中的委派、验证预算上限，并回归普通工具审批与恢复。真实厂商对取消的响应、浏览器展示及数据库持久恢复仍需运行验收。

本次没有更改 SSE 接口/前端，没有做全应用启动或浏览器验收，没有调用外部模型、写业务库或提交 Git。当前模块为实验性，范围是受控的顺序工作流集成，不能描述成通用图恢复或任意嵌套审批已经实现。

官方依据：[Agentic 文档](https://docs.langchain4j.dev/tutorials/agents/)，本轮同时核对 Maven Central 对应 1.20.2-beta30 源码中的 SequentialPlanner、AgentInvoker 与临时 AgenticScope 清理行为。
