# 后台 Run 与事件续读

本批把产品聊天从“POST 请求订阅期间执行”改为“先持久提交，再订阅同一个 Run”。LangChain4j 继续负责 AiServices、工具对话和模型事件；Spring AMQP 负责唤醒，MyBatis 负责任务与日志事务，Reactor/Spring MVC 负责有界 SSE。没有新增第二套 Agent 推理器。

## 请求与执行

1. 前端先创建/选择会话，向 `POST /api/v1/agent-runs` 发送 `{requestKey,request:{question,sessionId,...}}`。`requestKey` 是一次提交的 UUID，网络重试复用原值。
2. `BackgroundRunStore.submit` 在会话行锁内固定目标 Agent 和服务端五类预算，同事务保存 CREATED Run、AES-GCM 请求及用户/助手占位轮次。相同租户/用户/键和请求返回同一 Run，换请求复用键拒绝；同一会话已有活动/待审批 Run 时拒绝新提交。
3. 提交后向持久 quorum 队列 `data-agent.run` 发送 Run 引用。消息只是唤醒提示，ACK 不代表任务完成；丢通知、执行池满和进程退出由每分钟、最多 50 条未领取任务补偿。
4. `BackgroundRunWorker` 在独立有界 `agentRunExecutor` 中领取。会话、Run、提交行按相同顺序加锁；首次执行只允许 CREATED 且 started=false，重复消息不会再执行。领取之后由 BackgroundUserScope 重读启用用户、租户、app:use，重新准备会话/文件并鉴权当前 Agent 配置。
5. `AgentRunCoordinator.executeQueued` 复用持久 Run ID、提交时预算，执行原 Chat/ReAct/Orchestrated 策略。排队不消耗活动预算，语义规划仍计入 Run；没有因重连重建预算或重新调用规划器。
6. 普通回答更新同一占位消息，补齐用户/助手 Token，并按原请求计划登记记忆任务。占位消息不生成摘要或长期记忆；模型窗口查询排除尚未完成的后台轮次，防止重复注入本次问题。

## 日志与连接

`PersistedRunEventSink` 合并文本：累计 512 字符或距离上次冲刷超过 250ms 时落库，非文本事件/段结束前强制冲刷。250ms 是后续片段到达时检查的门槛，不是每个 token 都写库，也不承诺最后一个孤立片段在 250ms 内出现。

日志复用 `AgentSseEventWriter` 的 JSON 事件格式，AES-GCM 加密保存。每条明文最多 128KiB，每 Run 最多 8192 条/2MiB；过程事件超限/写入失败向执行传播。已经提交终态后的通知写入失败不能撤销业务结果，订阅通过 run_state 和已保存回答收敛，日志不冒充完整审计。同一 Run 的写入由行锁串行，`agent_run_event.id` 为全局递增游标，不能把不同 Run 的游标混用，也不能假定连续编号。

`GET /api/v1/agent-runs/{runId}/events?after=游标` 先注册现有广播订阅，再分批读取日志，每页 128 条。事件帧携带 SSE `id`；浏览器应用完整帧成功后推进游标，断网/60 秒连接到期后只重新 GET，不重发问题。页面刷新丢失内存游标时从 0 重放，重新构建回答，避免把旧回答再追加一次。运行中刷新/重新登录可从占位消息的 Run 证据发现 CREATED/RUNNING 任务。

广播沿用 `data-agent.approval-events` 的引用通知通道；历史命名保留，但普通 Run 通知不触发管理员审批刷新。MQ 不携带正文，每实例有自己的临时广播队列。订阅仍限制每实例 512/每用户 4、提示缓冲 32；慢客户端断开后重连，执行不受影响。心跳不查库；日志页回放复核当前启用用户、租户与会话归属，不依赖异步线程的 JWT 上下文。通知漏投/失败由最长一分钟的重连重新读主库收敛，不保证断网实时。

执行段结束后清空请求载荷，保留幂等键和指纹。事件默认保留 7 天，复用会话小时清理器，每轮最多 10 个 Run、每 Run 默认 200 行，属于到期后的渐进回收；不删除聊天原文。会话删除继续立即禁止读取/执行，到期先清事件/提交行再清 Run。日志过期后仍可读取 Run 状态和聊天最终结果，不能承诺永久回放。

## 取消和进程故障

关闭页面、切断网络、SSE 超时只结束订阅；用户点“停止”才 POST `/{runId}/cancel`。取消先写主库，再协作取消本机 Registry。模型/工具调用前强制验证执行租约，流式活动检查最多每秒查询一次指定 Run；结果写回持锁并强制复核。审批恢复控制器也验证原恢复租约，取消 READY/RUNNING 审批执行时保留已经做出的审批决定。

本机内存“完成”与其他实例主库“取消”竞争时，最终事件和 Trace 采用已提交的主库终态。取消不是撤销已经发出的外部操作，不能承诺远端工具立即停止。

租约使用已有 `app.agent.durable.lease-duration`，数据库时钟判定首次执行租约；`scan-interval` 只续本机活动执行。每分钟最多处理 50 条过期租约：

- 未开始的 CREATED 请求可以重新领取。
- 已开始的普通 RUNNING 请求没有完整恢复现场，标记 FAILED / INTERRUPTED，显示中断说明，**绝不自动重跑写工具**。
- WAITING_APPROVAL/RESUMING 继续交给原加密 Checkpoint 和审批租约链路；普通提交 worker 不接管审批。

当前持久日志覆盖普通执行至首次暂停/终态。审批恢复仍通过独立审批通知同步当前结果，尚未把恢复段所有 token 写入同一事件日志；Supervisor 已增加直接节点审批边界续跑，更深层嵌套审批仍拒绝。Run 证据的 `eventHistoryComplete` 保持 false，不能把事件续读说成任意进程现场恢复。现场说明见 [Supervisor 检查点与审批续跑](./Supervisor检查点与审批续跑.md)。

## 配置与升级

新增 Flyway `V8__background_runs_and_events.sql`，创建 `agent_run_submission`、`agent_run_event`。没有执行本机业务库迁移；启动同版后端时由 Flyway 管理。不要修改已执行 V1～V7。local/prod 继承公共显式配置：

```yaml
app:
  agent:
    background:
      core-pool-size: 2
      max-pool-size: 8
      queue-capacity: 100
      event-retention-days: 7
```

新前后端与 V8 必须同版；旧 `/analysis/analyze/stream` 已移除。同步 `/analysis/analyze` 及评测入口保留，共用原 Coordinator。原 `AnalysisStreamService`、前端单次 POST SSE 解析器、无人调用的 analyzeStreaming/Runtime.executeStreaming 门面已删除。普通 Run worker 在等待完成时占用有界任务线程，不是全系统非阻塞。

## 待执行人工验收

本批只做静态检查，不代表以下运行场景通过。

1. 正常发“解释 SSE 与异步任务的关系”：Network 应出现一次 POST 提交及 GET events，回答写入原占位消息，最终只有一轮问答。
2. 回答中途断网再恢复：后台继续，GET 使用上次事件游标，文字不重复；刷新页面后从头重放同一 Run，模型调用次数不因刷新增加。
3. 同一 requestKey/相同请求提交两次，返回同一 runId；改问题复用键被拒绝，两个不同键并发同会话只有一项活动任务。
4. 提交成功但丢失 HTTP 响应：重试原键，或刷新历史找回占位任务，不新建第二个 Run。
5. 运行中点停止、跨实例点停止、运行中删除会话：主库取消；后续模型/工具准入被拒绝，不能由晚到回答覆盖取消提示。已发送的远端操作另行核对结果。
6. 排队阶段重启后端：重新领取原 Run；模型/工具执行阶段强制结束进程再启动：失租后显示 INTERRUPTED，不重放工具。
7. 触发人工审批：占位消息更新等待状态；同意后仍走原审批恢复链路，最终回答可通过审批通知同步。重复批准和快速批准不得覆盖已完成回答。
8. MQ 不可用、任务池满、慢订阅、日志到容量、过期清理、用户禁用/跨租户读取：分别确认补偿、拒绝、断开后重读、明确失败和权限隔离；日志回收后聊天最终结果仍可读。

学习断点：BackgroundRunController.submit → BackgroundRunStore.submit/claim → BackgroundRunWorker.execute → AgentRunCoordinator.executeQueued；另开浏览器连接看 RunEventStream.open/replay，理解执行生命周期和连接生命周期的区别。
