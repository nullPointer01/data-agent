# LangChain4j 1.20.2 与框架优先实现

## 授权与基线

用户要求直接升级到最新框架、改善技术实现并保证功能正确；随后明确将框架流式能力、架构调整、记忆存储、RAG 和 Function Calling 纳入审查与改造范围。本次按该实施授权推进，不虚构额外的方案审批。沿用“不新增测试类”的偏好，通过生产源码编译、独立临时合同检查和静态链路审查验证。

源码基线：`10cdefabaa802ed80c2b6051ac044e06f2dfb169`，开始时工作区干净。

2026-09-29 查询 Maven Central：核心、OpenAI 最新正式发布为 1.20.2；Milvus 对应发布为 1.20.2-beta30，集成模块仍带 beta 标记。

## 实现边界

- 普通 CHAT 使用 AiServices 创建业务 Assistant 接口，同步返回 Result，流式返回 TokenStream。
- 模型请求统一走 McpModelService：ChatRequest 保留参数与结构化输出合同，上下文准入、租户模型选择、配额、Run 预算、重试与统计集中管理。
- 每次执行创建独立 ChatMemory，从已鉴权的会话快照装配历史。MySQL 会话记录仍是事实来源，Redis 只缓存；框架窗口不单独落库，不与会话保存重复写入。
- Chat 与 ReAct 共用角色化输入：系统指令、历史用户/助手消息、当前问题与检索证据。移除历史逐条 500 字符截断，Token 治理由上下文层负责。
- 流式输出使用框架增量、完成、失败回调。已输出部分文本后失败直接失败，不追加一次同步重发；超时/取消后停止消费并尽力取消厂商流。
- 失败以异常传播，不能作为 String/正常 AiMessage 返回。取消、预算耗尽、参数错误不进行网络重试。
- ReAct 的可持久化工具循环继续管理审批 Checkpoint 和恢复。框架原生 ChatRequest/ChatResponse 负责模型协议，工具执行仍经过唯一权限管道。
- RAG 使用框架 ReciprocalRankFuser 排名与 DefaultContentInjector 证据注入；双路去重按文档/分块身份，移除原始分数混加。排名变化必须真实黄金集复验，不能宣称质量已提升。
- 记忆存储复核后保留完整聊天的 MySQL 事务来源：ChatMemoryStore 的窗口覆盖/删除语义不等价于聊天审计表，当前使用框架的独立内存窗口，不新增重复数据库状态。长期语义记忆已有框架 Embedding/Milvus 访问，其证据准入和用户纠错保留。
- Function Calling 已使用 ToolSpecifications 与 DefaultToolExecutor；复核并用真实计算工具验证升级兼容，未新增绕过权限/审批的自动工具执行旁路。
- HTTP/SSE 业务事件、数据库结构及厂商配置保持现有语义；不扩展到 Spring Boot、服务端 Milvus 或新的模型厂商协议升级。结构感知分块、BM25、精排降级、引用和审批生命周期属于业务扩展，保留。

## 第二批实现（2026-09-29）

用户在四项深化建议后授权“可以的，继续搞”。第二批沿用第一批改动，不另行切换技术栈。

- Router 和语义记忆使用类型化 AiServices，返回值声明决定 Schema，JsonProperty(required = true) 保持字段完整性。统一严格 OutputGuardrail 拒绝宽松强转与无效 JSON，修补重试为 0；业务授权、置信度、原文证据和持久范围验证保留。
- EnhancedRagPipeline 使用请求级 DefaultRetrievalAugmentor 的转换/检索/聚合扩展点。检索身份由服务端绑定；单查询同步执行保持上下文，不使用框架默认无界线程池。检索证据只在回答组装处注入一次。
- Resilience4j 2.4.0 管理模型、Embedding、Reranker 的重试和熔断。重试在熔断内部，每个逻辑调用计一次成败；最近 N 次全部失败后打开，单次半开探测；本地取消、预算和中断不污染故障率。模型/Embedding 按身份分桶，Reranker 使用当前配置的单 Provider。
- 普通聊天的完成阶段贯通 AgentChatService、配置执行器、策略、Coordinator 和 SSE。Run 注册保留至完成，成功后才保存会话，等待审批单独保存。返回句柄取消不跳过实际执行清理。
- TaskContextSnapshot 恢复身份、MDC、Run、能力并归还原线程上下文。无作用域快照也不能借用回调线程权限。完成动作在独立有界线程池执行；流用共享定时器检查取消/超时；应用停止主动终结未完成流。
- 同步入口和可恢复 ReAct 工具循环仍可等待 Future；规划、RAG、DB 也仍为同步组件，不宣称整站非阻塞。外部模型流不能在已输出部分后重试。

## 验收方式

执行者为本会话 Agent。JDK 17 下核对 Maven 实际依赖与生产源码编译；临时可执行合同检查覆盖角色历史、AiServices 同步/TokenStream、空响应/配额失败、流中断不重发、取消及重试分类。真实外部模型、Milvus/RAG 和审批恢复若未实测，必须在验证记录中明确列为未验，不能用编译通过代替。
