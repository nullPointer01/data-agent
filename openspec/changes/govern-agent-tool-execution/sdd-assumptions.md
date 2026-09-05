# SDD Assumptions

## Confirmed

- [A001] 第二阶段优先建设 Tool Governance，而不是先做 Checkpoint/Resume。
  - 来源: 用户确认第二阶段方向
  - 置信度: 高
  - 验证: proposal Objective 与 Anti-Scope

- [A002] 模型提供的工具名和参数是不可信建议，服务端必须在实现调用边界再次授权和校验。
  - 来源: 当前全局 AgentToolInvoker 调用链与企业 Agent 安全目标
  - 置信度: 高
  - 验证: SC-1、SC-2

- [A003] 现有 AgentProfile `tools` 字段和数据库结构保持不变，空工具列表继续兼容当前“全部工具”语义。
  - 来源: 当前 AgentProfile 与 AgentToolInvoker 行为
  - 置信度: 高
  - 验证: SC-8

- [A004] 项目继续使用 JDK 17、Spring Boot 3.2 和 LangChain4j 1.19，不引入新 Agent 框架。
  - 来源: pom.xml、CLAUDE.md、用户历史确认
  - 置信度: 高
  - 验证: proposal Impact

- [A005] 用户要求不创建/运行测试，不执行 Maven 编译或前端构建。
  - 来源: 用户明确历史指令
  - 置信度: 高
  - 验证: tasks Test Strategy Override

- [A006] 本阶段不新增数据库表、审批状态或真实高风险写操作工具。
  - 来源: 第二阶段范围讨论
  - 置信度: 高
  - 验证: proposal Anti-Scope

## Unverified

- [A007] 现有 `app:use` 与 `*:*` 权限足以作为第一版工具治理的兼容权限基线。
  - 来源: 当前 USER/ADMIN 初始化权限
  - 置信度: 中
  - 验证: 实现时盘点所有工具资源敏感度；真实组织角色明确后再引入细粒度权限

- [A008] 当前工具的保护性 timeout、最大尝试数和结果长度默认值适合常见请求。
  - 来源: 尚无工具级 P95/P99 数据
  - 置信度: 中低
  - 验证: 上线后按 Tool Trace 和 Micrometer 分布调优

- [A009] LangChain4j 1.19 ToolSpecification 参数 Schema 足以覆盖当前全部简单标量参数校验。
  - 来源: 当前 AgentTools 仅使用字符串参数或无参数
  - 置信度: 中高
  - 验证: 实现时逐个扫描生成的参数类型 API；不扩展到嵌套/数组通用 Schema

- [A010] Future.cancel(true) 能及时停止部分工具底层客户端。
  - 来源: JDBC/HTTP 客户端对线程中断支持不一致
  - 置信度: 低
  - 验证: 仅承诺调用链及时返回和晚结果丢弃；客户端级 timeout 在真实集成中单独验证

- [A014] 静态验收可以证明调用边界、策略顺序和代码覆盖关系，但不能替代 JDK 17 编译与真实并发/超时验证。
  - 来源: 用户明确禁止测试、Maven 编译和前端构建
  - 置信度: 高
  - 验证: `verify-report.md` 明确记录已执行证据与未验证风险

## Invalidated

- [A011] “只要不把某个 ToolSpecification 发给模型，服务端就不可能执行该工具”。
  - 原因: 当前全局 Invoker 根据模型返回名称查找全部 ToolExecutor，执行边界没有 Profile 白名单参数

- [A012] “工具重试只要限制次数就安全”。
  - 原因: 非只读或非幂等操作即使只重试一次，也可能在超时未知结果下重复产生副作用

- [A013] “toolCallId 等于持久化幂等”。
  - 原因: ID 只提供逻辑相关性；跨进程去重还需要持久化唯一约束、结果缓存或下游幂等合同
