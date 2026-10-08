# 文档分块迁移任务

- [x] T1 核对官方切分器并建立 /tmp 来源位置失败用例。
- [x] T2 TextChunker 接入框架，新增 ChunkSourceLocator，修正 MarkdownStructureParser 原文位置；删除 SmartTextChunker/SemanticChunker/SemanticChunk，清理 ChunkMetadata 中旧链专用方法。application.yml 显式保留 500/100 参数；验证后 HierarchicalChunker 无需修改。
- [x] T3 针对真实框架检查切分/来源/覆盖/元数据/并发，编译，verification.md 记录边界。
- [x] T4 更新 CLAUDE、README、RAG/框架/目录索引/验收交接/PROJECT_INTERVIEW_GUIDE 文档，新增文档分块学习说明。
