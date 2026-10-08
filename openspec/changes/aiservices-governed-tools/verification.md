# AiServices 工具迁移验证（2026-09-30）

## 结论

本轮实现与本地验证 PASS_WITH_NOTES。正常工具交互由 AiServices 执行；审批恢复采用批准计划中的最小原生消息续跑边界。未声称真实模型网关、数据库审批恢复、浏览器 SSE 或多实例故障验收通过。

## 证据

- JDK 17：`mvn -B -Dfrontend.skip=true -DskipTests clean compile` 通过；最后源码调整后 `compile` 再次通过，547 个生产源码。clean 清理旧 class，防止已删除的 Spring 组件残留。
- `/tmp/data-agent-aiservices-d1LQuL/FrameworkProbe.java`：4 个组合通过（同步/流式 × 正常/暂停）。使用实际 1.20.2 AiServices，模型响应由脚本提供。
- `/tmp/data-agent-aiservices-d1LQuL/GovernedToolsCheck.java`：172 项断言通过，调用本轮生产 AgentChatService、AgentChatToolService、GovernedToolSession、DurableToolContinuation，以及真实 RunControl/CheckpointMessageMapper。
- 同一脚本另接真实 Registry、Schema Validator、PolicyEngine、Pipeline、DefaultToolExecutor 和 AgentTools.calculate；同步/流式均完成 `(100+200)*0.8 = 240`。错误类型参数、用户无权限、Agent 未绑定均为 0 次工具尝试；真实 Journal/Telemetry 管道正常记录。
- `git diff --check` 通过；生产代码无已删除循环类引用。本轮没有前端改动，不重复构建前端；没有仓库测试类或 Git 提交。

临时检查运行命令（classpath 文件由 Maven dependency:build-classpath 生成）：

```sh
java -cp "target/classes:$(</tmp/data-agent-aiservices-d1LQuL/classpath)" /tmp/data-agent-aiservices-d1LQuL/GovernedToolsCheck.java
```

临时文件不作为项目永久依赖，系统清理 /tmp 后需要重新准备。

## 检查覆盖

1. 同一模型响应两个普通工具：全部 admit 后才开始 execute，框架回填两个原生工具结果，原用户消息仍在。
2. 首轮 REQUIRED 后回到 AUTO；模型不遵守 REQUIRED 时失败，不能假装已执行。
3. 普通工具在前、审批工具在后：整组 0 次执行，只有一个模型请求，Run 停在 WAITING_APPROVAL，工作记忆不写成功/失败终态。
4. CheckpointMapper 收窄为单个待审批动作；冻结/恢复预算后从 ToolExecutionResultMessage 继续，不增加用户消息、不重新请求已批准动作。
5. 恢复后可再次审批暂停；恢复时迭代额度已耗尽则不发起模型调用。
6. 取消、迭代耗尽、模型协议错误、重复 tool call ID、意外 Pipeline 异常均传播，不多发模型请求或执行工具。
7. 幻觉工具走准入；连续参数错误达到恢复上限、策略拒绝后，下一轮实际不带工具规格。
8. 异步完成线程恢复 Run/身份，回调结束还原 SDK 线程原上下文。

两次失败检查推动修复：ChatRequest 的空集合覆盖不会清空工具，已改为替换参数对象；框架默认工具异常处理会把原异常文本送回模型，已显式改为传播。复验通过。

## 实现边界和限制

- AiServices 1.20.2 的公开调用接口会构造当前用户消息；没有使用内部 ToolService、反射篡改框架状态或假的用户消息实现审批恢复。仅 DurableToolContinuation 保留约 60 行原生消息续跑，共用 Session。
- 检查中的模型与审批数据库写入为可控替身；真实 Registry/Pipeline 的参数绑定与授权检查不是替身。恢复检查验证消息协议和预算延续，不等于数据库租约、审批重授权、进程崩溃及沙箱唯一键已经完成真实回归。
- 批内工具串行执行，增量工具调用不会提前执行；普通错误修正次数按当前执行段累计，根 Run 的预算在恢复/子 Agent 间不重置。
- 框架窗口只用于本次 Run，Token 裁剪仍由原上下文治理器完成；旧聊天持久化事实来源不变。
- 本轮删除 11 个旧循环/处理/解析/反思类，由 Git 可恢复。原有未提交生命周期、RAG、索引和前端改动不归本轮。

## 重启后的人工验收

先使用支持原生流式 Tool Calling 的已配置模型，并确认当前 Agent 绑定对应工具：

1. 普通聊天：输入“用一句话解释 SSE”，确认文本流和最终会话保存。
2. 计算：输入“请使用计算工具计算 (100+200)*0.8，并告诉我结果”，确认工具轨迹真实出现 calculate、结果 240、Run 完成。
3. 多工具：输入“先查询当前时间，再用计算工具算 125*8，汇总两个结果”，确认工具结果均在 Trace 中。模型是否同批返回不可强求，整批保护由临时确定性检查验证。
4. 沙箱审批：输入“在隔离演示沙箱中，将酒店 demo-hotel-001 的标准大床房在 2026-10-10 的价格改为 399 元，请执行”。确认出现待审批状态、审批前无沙箱写入；管理员批准后查看用户端恢复结果。
5. 暂停后重启：再发一笔不同日期的沙箱请求，等待审批时重启应用，再批准，检查同一动作无重复流水、原会话更新、Run 最终状态。
6. 工具任务运行中点击停止，确认状态终止、后续工具不再执行。另选绑定子 Agent 的任务验证委派及共享预算；审批恢复的精确工具集合包含仍获授权的 Skill/委派适配器。

## 一致性与归属审查

Objective/Anti-Scope/Boundaries/Files 检查通过。采用审批专用续跑属于已批准预案；没有引入第二套权限或模型配置。所有 4 项成功标准达到本地实现验证口径，外部依赖的真实联调按上节保留待验。
