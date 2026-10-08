# 任务

- [x] T1 ES ICU 镜像、显式 v3/analyzer 配置、严格索引契约与临时失败检查（SC1）。Files: docker/elasticsearch/Dockerfile、docker-compose.yml、ElasticsearchProperties、ElasticsearchIndexContract、ElasticsearchFullTextClient、application.yml。
- [x] T2 统一一次分块双索引，替换知识/文件二次分块，空内容与失败传播回归（SC2）。Files: RagDocumentIndexer、VectorMemoryService、KnowledgeVectorIndexService、FileProcessingWorker。
- [x] T3 FILE/MEMORY 持久任务与维护重建管理入口、分页和越权验证（SC3）。Files: IndexTask/Store/Worker、IndexTaskProcessor、IndexRebuildService/Controller、RagIndexMaintenance/Filter、FileMetadataLifecycleService、FileProcessingServiceImpl、删除 FileProcessingSubmissionService/Result/FileVectorCleanupService、MemoryDecayScheduler、KnowledgeSyncService、AgentResumeWorker、SecurityConfig、AsyncConfig、application.yml。
- [x] T4 黄金集实际来源绑定与报告对照，补索引/查询变换快照（SC4）。Files: scripts/rag-golden-dataset.mjs、scripts/rag-compare-reports.mjs、RagBenchmarkReport、RagBenchmarkService、RagBenchmarkDatasetLoader、examples/rag-evaluation-lab/README.md。
- [x] T5 干净编译、临时关键回归与中文交接；真实服务不可用明确未验（SC5）。Files: 本 change 验证文档、CLAUDE.md、README.md、docs/核心逻辑详解/中文分块与检索改造规划.md、中文检索与索引维护.md、RAG检索链路.md、目录索引.md、文档解析与框架检索.md、框架集成与消息生命周期.md、docs/术语/枚举速查/运行与索引状态.md、docs/interview/当前进度与验收交接.md。

Files 补充仅包括原授权方案的必要依赖：注册 ADMIN 路由、暂停会写索引的后台入口、移除旧文件线程池和重复清理、拒绝占位标签、同步新 Kind 说明。不是新增权限产品或更换调度器。T2 的 MEMORY 失败传播属于重建不能伪成功的同类缺陷修复，保留正常记忆提取流程。

上述完成指本轮开发与离线验证。规划的真实基线、ICU 部署、全用户历史迁移、三组至少三轮评测和浏览器验收仍未完成，不能据此宣称整体上线验收通过。
