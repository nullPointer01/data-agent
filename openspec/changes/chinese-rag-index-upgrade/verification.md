# 中文检索与索引维护验证

日期：2026-10-03。本轮开发与离线检查：通过，带真实环境未验说明。此结果不是规划整体部署或检索效果验收结论。

## 环境与范围

- 仓库实际路径：`/Users/zhangyuanming/Documents/projects/data-agent`，起始 HEAD/脏文件记录保留在 baseline.txt；本轮归属以 sdd-state.md taskLedger 的文件和新增职责为准。
- JDK：17.0.9；Maven：3.8.8；LangChain4j Core 1.20.2，对应 beta30 模块；ES SDK 8.12.0。
- 用户要求不新增仓库测试类。本轮 Java 验证位于 `/tmp/data-agent-chinese-rag-BktWYD`，之前框架回归位于 `/tmp/data-agent-framework-rag-dNhgbz` 与 `/tmp/data-agent-chunking-OcW5Q3`。临时 fixture 使用明确的 mock ID，不作为真实黄金集/指标交付。
- 未提交 Git、未改业务数据库或历史索引、未删除原 ES volume、未调用付费模型。移除的旧文件提交/清理类是 Git 跟踪源码，可从版本历史恢复。

## 失败复现与修复

IndexContractRed 在原仅检查 exists 的客户端上出现 AssertionError：既有索引没有 ICU 仍继续使用。后续正式 GREEN 检查先用完整有效响应通过实际 SDK 解析，再逐项破坏合同，防止把 mock 响应缺字段造成的反序列化错误当成业务校验成功。

MemoryFailureCheck 先用真实 VectorMemoryService 和不可用网关直接调用 indexMemory：原实现跳过写入却正常返回，AssertionError 复现。修复后移除吞异常 helper，统一批量 indexAll，返回 IllegalStateException；持久重建任务可进入失败/重试。正常长期记忆仍使用原业务事务，并未迁移为完整记忆 Outbox，不宣称解决全部跨存储原子性问题。

## 核心检查结果

| 检查 | 结果与证据 |
| --- | --- |
| 干净编译 | JDK17 `mvn -q -DskipTests -Dfrontend.skip=true clean compile`，exit 0；清除 target 后重新编译，无旧类残留依赖 |
| ES SDK 契约 | IndexContractCheck：18 项通过；本地 HttpServer + 官方 ElasticsearchClient/RestClientTransport，验证有效 ICU、settings.index 包装、缺插件、旧 meta、错误字段、search analyzer、analyzer 组成、并发 already-exists 后仍校验、其他创建错误传播、单物理索引、缓存、精确过滤与跨用户主键 |
| 双索引 | DualIndexCheck：68 项通过；真实官方分块、真实 VectorMemoryService/VectorDocumentIndexer，外部模型/存储替身；知识与文件仅分块一次，同 ID/正文/身份/偏移，保留文件前缀与父上下文；空正文双清理、向量删除/Embedding/ES 失败传播 |
| 任务与维护 | IndexTasksCheck：61 项通过；任务身份读取最新来源，删除文件/知识清理，压缩记忆/过期/短期过滤，对话合同保留，Worker 失败与执行器拒绝，文件事务注解/入队，来源/任务分页，JDBC 参数绑定，整批先校验后入队、ADMIN 注解、维护 409/业务 503/允许认证与管理 |
| 记忆失败 | MemoryFailureCheck：通过，原吞异常路径已删除 |
| 文件消费者 | FileWorkerCheck：15 项通过；真实 TextDocumentParser 读取临时文件，索引完成才更新状态；失败保存并抛出，空正文提交清理，审计和状态使用原任务身份 |
| 黄金集加载 | DatasetLoaderCheck：4 项通过；真实 30 题模板未替换占位符拒绝，临时 mock 标注可加载，空 ID 和占位分级标注拒绝 |
| 绑定/公平对照工具 | ToolingCheck.mjs 通过；模板不被修改，六来源绑定、缺失/重复拒绝，mock HTTP 游标分页、403/缺 Token/重复游标拒绝；报告模型/配置/查询/标签变化、执行错误/精排降级/门槛变化拒绝，保留原门槛结果 |
| 静态检查 | 两个脚本 `node --check`、`docker compose config --quiet`、`git diff --check` 通过；29 个关联本地文档链接有效，没有创建 src/test |

Java 核心检查通过实际生产方法执行；外部资源替身仅用于断开网络/数据库。JDBC 条件、队列身份与事务注解检查不能代替真实 MySQL 的事务回滚、行锁或多实例租约测试。

## 已有框架回归

干净编译后重新编译并运行临时检查，全部 exit 0：ChineseBoundaryCheck（中文完整句界）、OfficeOrderCheck（段落/表格顺序）、ParserCheck（9 项真实 PDF/PPTX/TXT/XLSX 与空/坏文件）、QueryCheck（10 项官方变换）、RetrieverCheck（20 项官方 Retriever/混合/管线）、ChunkingCheck（70,812 项来源覆盖/Unicode/章节/重叠与随机样本断言）。这不是 70,812 条独立业务样例，不作为效果指标。

旧 ChunkConsumersCheck 依赖已删除的私有二次分块转换方法，本轮未继续运行；改用新统一入口的 DualIndexCheck 验证真实下游合同。

## 一致性检查

Objective：ICU/索引合同、一次分块、历史任务入口和评测工具均在授权规划范围。没有增加另一套分块引擎或索引调度器。

Anti-Scope / Never：未升级 ES 大版本，未清空业务库/向量集合，未自动改写历史，没有仓库测试类，没有声称双索引原子发布或真实收益。

Files：补充的 SecurityConfig、AsyncConfig、MemoryDecayScheduler、KnowledgeSyncService、AgentResumeWorker、DatasetLoader 和旧清理类删除均是原合同的必要依赖，已记录在 tasks 和 ledger；AgentResumeWorker 的其他既有变更未归入本轮。MEMORY 失败传播补齐同一任务不可伪成功的合同。

SC1–SC5 开发/离线范围有上述证据。配置与代码完成不能代替真实插件/历史块迁移证据。

## 未验范围与下一步

`docker info --format '{{.ServerVersion}}'` 返回失败：`/Users/zhangyuanming/.docker/run/docker.sock` 不存在，Docker daemon 未运行。因此没有构建/启动 ICU 镜像，没有真实 ES `_analyze`、BM25 编号样本、跨用户真实查询、历史 FILE/MEMORY/KNOWLEDGE 批次或三组至少三轮评测，也没有浏览器引用验收。

来源绑定只提供工具，当前没有真实来源 ID/黄金集文件或基线报告。现有 30 题为虚构教学材料，不满足正式五类各 6 条的分类设计。真实 Recall、MRR、NDCG、Hit、P95、中文收益和“不退化”均未测量，原质量门槛未降低。

旧基线实验环境需只移植新报告快照采集并保留原检索算法；旧报告缺字段、上游模型补全/扩展改变实际问句、语料内容变化均不能作为公平比较。主实现没有保留 standard 或第二套切分器运行开关。

维护管理接口只允许当前 ADMIN 自己的来源，不覆盖其他用户历史，不含新前端界面。重建前须停机/排空全部实例，维护 Worker 继续收敛；外部副作用不能被数据库租约撤销，极端失租仍需真实联调。操作见 [中文检索与索引维护](../../../docs/核心逻辑详解/中文检索与索引维护.md)。
