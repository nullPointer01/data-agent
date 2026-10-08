# 框架文档解析与检索迁移验证

日期：2026-10-03。执行者：Codex。结果：PASS_WITH_NOTES（限定离线组件合同与编译，不代表真实检索质量）。工作区：/Users/zhangyuanming/Documents/projects/data-agent。既有修改已记录在 baseline.txt，保留原有改动，没有提交。

## 环境与命令

JDK 17.0.9；Maven 3.8.8；LangChain4j Core 1.20.2、解析模块 1.20.2-beta30；PDFBox 3.0.7、POI 5.5.1。按对应版本源码核对 API。

编译：`mvn -q -DskipTests -Dfrontend.skip=true clean compile`，JAVA_HOME 指向 JDK17，退出 0。临时验证源码在 `/tmp/data-agent-framework-rag-dNhgbz` 和 `/tmp/data-agent-chunking-OcW5Q3`，通过 Maven dependency:build-classpath 取得实际依赖，用 javac 编译后逐个 java 执行，全部退出 0；没有仓库测试类。

## 证据与成功标准

| 标准 | 实际检查 | 结果 |
| --- | --- | --- |
| SC1 文档解析 | OfficeOrderCheck：真实 DOCX 段落/表格/后续段落顺序；ParserCheck：生成真实 PDF/PPTX/XLSX，UTF-8 中文、空 PDF/DOCX/TXT、损坏文件、未知二进制 | 顺序检查及 9 项合同通过 |
| SC2 分块 | ChineseBoundaryCheck：长度许可时中文短句完整；ChunkingCheck：70812 项断言，原文覆盖/范围/长度、引号、连续标点、CRLF、无标点、英文、emoji、Markdown 标签、随机混排、并发 | 通过；英文普通样本与官方默认递归输出一致 |
| SC2 位置与下游 | ChunkSourceLocatorCheck：9 项，遗漏/改写安全拒绝、重复文本、空白、递归纯重叠终点；ChunkConsumersCheck：13 项，知识/文件全文与向量身份/位置合同 | 通过 |
| SC3 查询变换 | QueryCheck：10 项，官方直通零模型调用、长问题不截成 300 字、无词表、官方历史补全提示词、官方扩展主查询保留/去重/上限、错误传播、输入长度 | 通过；ChatModel 替身，无厂商请求 |
| SC4 Retriever | RetrieverCheck：20 项，真实官方 EmbeddingStoreContentRetriever 调用栈、QUERY 用途、DOCUMENT 批量入口、请求阈值/数量、租户/用户/来源过滤、分数/主键/元数据、错误传播 | 通过；Gateway 底层替身，无 Milvus 网络 |
| SC4 混合链 | 同一 RetrieverCheck：变体实际向量召回、BM25 接收变体、物理 ID 最佳分数去重、一次融合、双通道证据、身份缺失短路；真实 DefaultRetrievalAugmentor 的一次查询转换/检索与引用/Trace | 通过 |
| SC5 构建与清理 | 干净编译，旧查询和解析类无源码引用/旧 class，git diff --check，学习说明与交接同步 | 通过 |

迁移前临时用例复现中文短句被硬拆、Word 表格被放到后续段落之后；迁移后两个用例通过。混排回归发现框架递归重新发出仅含重叠的短片段，来源适配已使用累计覆盖终点对齐，再去掉无新增覆盖的片段，保留完整性失败拒绝；未重写框架切分/重叠算法。

## 范围与限制

- 没有新增仓库测试类、业务数据库写入、历史索引重建、Docker 部署、付费模型调用或前端协议修改。
- 官方解析器提取文字；OCR、图片理解、Office 布局还原没有新增。Excel 输出合同保留原行为，没有引入新公式计算策略。
- 中文句界使用官方 Regex splitter，框架私有重叠仍采用英文 OpenNLP；不是模型语义分块，超长代码/表格仍可能跨块。
- 明确问题默认直通；历史补全仅在可信历史显式传入时执行。现有 Router 已补全的检索问题不再重复调用模型。扩展默认关闭；开启后成本增加，最多包含主查询在内配置数量（2–4）。
- 官方 Content 返回分数、主键和 TextSegment，不含原始向量数组；生产代码没有读取 EmbeddingMatch.embedding() 的下游。Gateway search 委派原加载恢复方法，恢复代码未变；本轮未复现真实 Milvus 服务恢复。
- 只验证受治理接口接入和业务合同，不声称真实跨进程服务、厂商效果或 RAG Recall/MRR/P95 提升。真实 Milvus/ES、黄金集和浏览器验收待运行。
- ES analyzer 仍为 standard，ICU 插件和索引契约/历史迁移在后续规划。历史数据不自动采用新分块，需受控重新处理。

学习入口：[文档解析与框架检索](../../../docs/核心逻辑详解/文档解析与框架检索.md)。
