# v6 上下文与检索检查

日期：2026-09-23。变更：C002 / Task 14、15、15a、16、17。

结论：实现和静态审查完成；仅静态范围 PASS_WITH_NOTES。遵从用户不保留测试类及 CLAUDE.md 不主动测试/编译/构建的约束，没有运行模型、应用、数据库或浏览器验收。旧 v4/v5 成绩不适用；不能声称编译成功、准确率达标或性能提升。

## 来源与范围

HEAD 基线仍是 `94069f8507276bf29d5556a3a41245ffe20c80ad`。本轮源文件编辑前副本位于 `/tmp/data-agent-context-c002.sMY4h4`；只用于区分本轮增量，不是长期备份。已有 staged/unstaged 工作保留，未提交或暂存。

- Task 14：Decision 候选改为列表以保留重复项，严格 ObjectReader 绑定类型；移除逐字段 JsonNode 读取和重复字段名集合。
- Task 15：MemoryManager 拆分工作状态、画像和长期检索；MemoryContext 移除自动摘要注入字段及不再使用的拼装方法；ConfiguredAgentExecutionService 按计划组装；版本 v6。
- Task 15a：移除因本轮解耦而无调用的 ShortTermMemory.recall、markAccessed 及唯一配套仓储查询。
- Task 16：searchMemory 复用长期检索；searchKnowledge 复用混合 RAG；移除旧向量知识分支和底层问候词匹配。
- Task 17：同步核心说明、目录索引、交接记录及本报告。

## 已执行的检查

- `git diff --check` 通过；`test ! -e src/test` 通过。
- 全生产源码与前端引用搜索确认旧 buildSemanticContext、shortTermMemories、shortTermCount、withConversationHistory、withUserProfile、findActiveByTenantUserAndTier 和 RAG_SKIP_GREETINGS 无残留。
- 核对 MemoryContext 构造、Decision 构造与候选使用、Spring 构造注入和所有新增/改名方法引用。
- 使用本地 Jackson 2.15.3 的 `javap` 确认 MapperBuilder 的 enable/disable、MutableCoercionConfig.setCoercion、ObjectReader.readValue(String) API 存在；这不是编译或运行测试。
- 知识预加载及 searchKnowledge 都调用 RagRetrievalService，再进入 EnhancedRagPipeline → DefaultHybridRetriever。向量检索显式传 tenantId+userId；ElasticsearchFullTextClient 构造两个过滤条件；父文档/来源名称按租户和用户查询。
- 记忆中心仍通过 MemoryGovernanceService 的用户/层级查询查看摘要；ConversationMemoryCaptureService 写入与 MemoryDecayScheduler 维护没有删除，未修改用户数据。

## 五轴审查与相关成功条件

| 维度 | 静态观察 | 实测限制 |
|---|---|---|
| Correctness | SC1/SC2 的会话和画像装配独立；SC3 严格类型合同；SC4/SC5 统一检索及身份过滤 | 未编译、未做模型合同故障注入 |
| Readability | 业务消费 Decision；删除旧手工字段解析、知识旁路和无调用摘要召回 | JSON 仍是模型传输格式，不是厂商原生 Schema |
| Architecture | 复用既有模型治理、MemoryManager 和 RagRetrievalService，无新模型客户端/框架 | 原有写记忆和压缩策略未重构 |
| Security | 候选白名单不变；两路知识检索及来源解析均显式传双身份；缺身份不检索 | 同租户多用户隔离需运行验收 |
| Performance | 不再读取/注入全用户最近摘要；两记忆入口统一 topK=5、长期检索阈值=0.45 | searchMemory 原阈值为0.5，召回范围略放宽；同会话会读 Redis；按需知识增加全文召回/精排成本 |

SC6 保留已有意图、模式、上下文计划证据，规划版本升级 v6；未重新跑 Eval。SC7 的测试/构建仍按后续约束未执行，未标为通过。

异常与边界（D2/D6）：缺字段/null/未知字段、枚举序号、字符串冒充数字、数字冒充文本、重复 JSON 字段和工具候选、非法枚举、越界置信度继续拒绝。检索无命中返回空证据；预加载单来源故障降级，工具故障继续作为异常交给治理管道。工作状态提示标明是先前记录。

演化（D4）：Decision/MemoryContext 是内部类型，生产调用点已同步；没有修改持久化的 Context Plan 字段、SQL 或 HTTP 请求合同。移除的 shortTermCount 是内部观测字段，前端无消费者；旧历史证据保留。

Anti-Scope/Boundaries：未切换框架，未新增自治权限，未改 RAG 算法本身，未配置新模型，未运行外部写入或部署，未覆盖用户原有改动。所有业务源码在任务 Files 清单内。

## 待运行的人工验收

1. 同会话输入“解释一下 SSE”，再输入“它和 WebSocket 有什么区别”：观察 v6 Trace 的会话历史 REQUIRED，确认不依赖长期检索也能理解指代。
2. 在另一个会话聊无关主题，再返回原会话追问：Prompt 不应自动附带全用户近期摘要。
3. 已存跨会话事实时，询问该事实：REQUIRED 预加载与 OPTIONAL/searchMemory 使用相同用户、topK 和阈值；无匹配不得编造。
4. 两个同租户用户分别上传不同唯一事实，各自询问对方的唯一事实：预加载和 searchKnowledge 都不得返回对方内容，包括引用和父文档。
5. 使用含编号或专有词的自有文档，对相同查询分别验证预加载和工具入口的向量/BM25/RRF/精排链路；引用编号保留。按需调用证据在工具 Journal 中，本轮未补独立 SSE 引用卡片。
6. 上传包含“HI-2026”编号的资料后查询该编号：不得因为包含 hi 被底层问候规则跳过。
7. 隔离环境检索服务不可用：预加载仍保留已有历史/画像；工具结果应表示检索失败，不能误报“没有资料”。Redis 不可用则工作状态降级为空，记录实际等待时间。
8. 使用受控 Router 响应验证缺字段、null、重复字段、重复候选、未知工具、confidence="0.9"、reason=123、非法枚举：均应降级且不增加候选能力。这是人工合同验收建议，不新增测试类。

## 假设状态

- A013：类型绑定与传输格式分工已静态确认；模型服务实际输出兼容性未验。
- A014：会话、画像、跨会话来源已静态解耦；摘要管理功能调用路径保留。
- A015：RAG 两入口双身份过滤已静态确认；真实数据隔离待验。
- A016：Redis、混合检索和阈值统一的实际耗时/效果未测；不对延迟和准确率作量化承诺。
