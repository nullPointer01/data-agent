# SDD Assumptions

## Confirmed

- [A001] 第三阶段建设持久化 Run、Checkpoint/Resume 和 Human-in-the-loop，而不是继续堆普通 Agent 或 Tool。
  - 来源: 用户确认第三阶段方向
  - 置信度: 高
  - 验证: proposal Objective 与 Anti-Scope

- [A002] 生产实现扩展现有 AgentRunCoordinator 和 Tool Governance，不引入 LangGraph 或把 graphdemo 直接装配到主链路。
  - 来源: 当前项目架构与用户接受的方案
  - 置信度: 高
  - 验证: design Decision 1

- [A003] 本阶段允许新增持久化表，因为跨进程等待与恢复不能继续依赖 JVM Registry 或最终 Trace。
  - 来源: 用户确认开始实现该方向；当前 MySQL/JPA 基线
  - 置信度: 高
  - 验证: proposal Impact 与 design Decision 2

- [A004] 人工等待不消耗 active execution deadline；审批使用独立 TTL，恢复延续而不重置既有预算。
  - 来源: 长时间审批与当前 5 分钟 Run timeout 的语义冲突
  - 置信度: 高
  - 验证: durable-agent-run spec

- [A005] 审批不能授予 Run owner 原本没有的权限，恢复必须使用当前 RBAC 和 Tool Policy 重新授权。
  - 来源: 企业 Agent 最小权限与撤权时效要求
  - 置信度: 高
  - 验证: agent-action-approval spec

- [A006] 用户继续要求不创建/运行测试，不执行 Maven 编译、前端或 Docker 构建。
  - 来源: 用户历史明确指令与 CLAUDE.md
  - 置信度: 高
  - 验证: tasks Test Strategy Override

- [A007] 第一个写动作只操作隔离的酒店价格沙箱，不连接真实酒店、支付、消息或生产数据。
  - 来源: 本阶段需要闭环证据，同时必须控制副作用
  - 置信度: 高
  - 验证: proposal Anti-Scope 与 sandbox tasks

- [A018] 同一模型响应包含审批工具与其他工具时，首版只持久化一个审批动作，其余调用由恢复后的模型重新规划。
  - 来源: OpenAI-compatible tool message 配对约束与静态恢复推演
  - 置信度: 高
  - 验证: ReActStepHandler 整组预检与 AgentCheckpointMessageMapper 单动作捕获

## Unverified

- [A008] 24 小时审批 TTL、60 秒租约和 15 秒扫描周期适合真实业务。
  - 来源: 当前没有审批耗时、GC pause 或节点故障数据
  - 置信度: 低
  - 验证: 真实部署后依据审批 P95/P99、lease loss 和恢复延迟调优

- [A009] 版本化可移植消息足以恢复当前所有 ReAct/Profile/Orchestrated 专家路径。
  - 来源: 当前工具调用最终汇聚 ReActStepHandler，但 Orchestrator 上层状态仍有额外对象
  - 置信度: 中
  - 验证: 实现时逐条追踪 capture/restore；无法可移植的模式必须 fail closed 而非伪恢复

- [A010] 现有单密钥 CryptoUtil 适合保存短期 Checkpoint 密文。
  - 来源: 已用于数据库敏感字段，但没有 key version/rotation
  - 置信度: 中
  - 验证: 静态确认密文列和大小；生产轮换方案留作后续 capability

- [A011] MySQL 条件 update + version + lease 能满足当前低吞吐恢复领取。
  - 来源: 项目已有 MySQL，尚无分布式任务基础设施
  - 置信度: 中高
  - 验证: 后续人工双 worker、过期租约和 stale writer 场景

- [A012] ADMIN 角色加 `agent:approval:review` 且禁止 self-approval 适合首版组织模型。
  - 来源: 当前只有 USER/ADMIN 两级角色
  - 置信度: 中
  - 验证: 真实业务审批职责明确后再细化权限矩阵

- [A013] 前端轮询 Run 状态足以满足第一版恢复结果体验。
  - 来源: 暂不建设持久化事件 replay
  - 置信度: 中
  - 验证: 人工体验审批到结果的延迟；需要实时推送时另提事件序列 change

## Invalidated

- [A014] “HIGH 风险就天然等于需要人工审批”。
  - 原因: 风险分类和审批要求是两个维度；HIGH 只读工具可能不需审批，MEDIUM 写动作也可能需要审批

- [A015] “数据库租约可以保证所有外部副作用 exactly-once”。
  - 原因: 进程可能在下游成功、本地记录成功前崩溃；仍需要下游幂等键、业务唯一约束或补偿

- [A016] “审批后可以继续使用 Run 创建时的旧权限快照”。
  - 原因: 人工等待可能持续数小时，期间撤权、用户禁用、工具禁用和风险策略变化必须生效

- [A017] “批准后可以继续向原来的 SSE emitter 推送结果”。
  - 原因: 原连接通常已经断开且 emitter 属于旧进程；首版通过持久化 Run 查询结果，实时 replay 需要独立事件存储
