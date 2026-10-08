# 框架文档解析与检索迁移

2026-10-03 用户明确授权迁移文档解析、改进分块、迁移查询变换并接入框架 ContentRetriever，接续已讨论方案直接实现。

## 范围

通用 PDF 与 Office 解析使用当前 LangChain4j 对应官方 PDFBox/POI parser 模块，纯文本使用 TextDocumentParser；保留 Excel 的表格格式与文件状态/归属。移除被替代解析器。与模块要求对齐底层依赖，保留 JDK17。

DocumentSplitter 独立由 Spring 装配，中文句末边界使用官方 Regex splitter，递归装箱和重叠由框架处理。保留原文定位、章节、Unicode 和父上下文；明确私有英文重叠限制。

删除旧查询标记清理、业务词表、分类和关键词提取，使用官方 Default/Compressing/ExpandingQueryTransformer。历史明确提供时才做追问补全；已规划独立查询使用默认直通，扩展默认关闭并限制查询数。模型调用经过 McpModelService，保留预算与异常语义。

向量召回使用真实 EmbeddingStoreContentRetriever，EmbeddingModel 和 EmbeddingStore 通过薄适配复用既有用途前缀、向量契约、熔断与 Milvus 恢复；Content 分数、主键和文本元数据保持。扩展查询确实参与向量和 BM25；融合精排一次，身份不能来自模型。

## 成功标准

执行者 Codex，JDK17、本地实际框架和临时验证：SC1 官方解析器对 PDF/DOCX/PPTX/文本正确，Excel 结构与不支持类型拒绝保留；SC2 中文句界、源位置、覆盖、Unicode 和长度正确；SC3 无硬编码业务词表，默认无额外模型调用，历史补全和扩展由官方实现，变体有界且实际召回；SC4 官方 ContentRetriever 保留身份过滤、查询用途、分数/主键，恢复和模型预算不绕过；SC5 干净编译与关键本地回归通过，学习文档和未验边界清晰。

不包含 ES 分词插件部署、业务历史索引重建、数据库写入、付费模型调用、前端协议或仓库测试类；不能把本地合同验证当作真实检索质量提升。
