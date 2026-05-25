现在我已经充分了解了代码结构。让我向用户确认几个关键决策点，然后制定完整计划。

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目简介

Data Agent 是基于 Spring Boot 3.2 + LangChain4j 的数据分析智能体后端，实现 ReAct (Reason-Act) 循环，支持动态技能(Skill)、向量记忆、多模型路由、文件/知识库索引、JWT 多租户。

源码位于 `data-agent/` 子目录（项目根有 `docker-compose.yml`，Maven 工程在子目录）。

## 常用命令

所有 Maven 命令都需要在 `data-agent/` 子目录下执行：

```bash
cd data-agent

# 推荐启动方式：脚本会切换 JDK 17 + 阿里云镜像 + 独立本地仓库
./mvnw17.sh spring-boot:run                  # 默认 profile (MySQL + Milvus)
./mvnw17.sh spring-boot:run -Dspring-boot.run.profiles=dev   # H2 + 内存向量库

# 普通 Maven（需要本机 JDK 17）
mvn spring-boot:run
mvn -Dtest=ClassName#methodName test         # 跑单个测试
mvn clean package                            # 打 fat jar 到 target/

# 启动外部依赖（在仓库根目录）
docker-compose up -d                          # etcd + minio + milvus + attu
```

应用监听 8080（Spring Boot 默认），Milvus 19530，Attu 控制台 8000。

**注意**：`mvnw17.sh` 写死了 `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.9.jdk`，且依赖 `-s .mvn/settings.xml`（阿里云镜像 + 独立本地仓库）。在其他机器上需改路径或用普通 `mvn`。

## Profile 与外部依赖

- **默认 profile**（`application.yml`）：MySQL `data_agent` 库 + Milvus `localhost:19530`。库表通过 JPA `ddl-auto: update` 自动同步，完整脚本见 `data-agent/sql/init.sql`（8 张表 + 默认模型/技能种子数据）。
- **dev profile**（`application-dev.yml`）：H2 文件库 `./data/agent_db`，自动开启 H2 console（`/h2-console`）。
- **Milvus 不可用时自动回退**：`VectorMemoryService` 启动时 socket 探测 Milvus 端口，不可达则使用 `InMemoryEmbeddingStore`，持久化到 `data/vector-store/embeddings.json` + `registry.json`（每 60 秒 dirty 时落盘，`@PreDestroy` 强制落盘）。这意味着开发时不必启动 Docker 也能跑完整链路。

`langchain4j.open-ai.*` 在两份 yml 中都硬编码了一个内网 OpenAI 兼容网关（`http://openai.vip.elong.com/v1`，模型 `qwen-plus`）——它是 `LangChain4jConfig` 注入的**默认 fallback 模型**，不是唯一模型（详见下文模型路由）。

## 整体架构

### 请求链路（`POST /agent/analysis/analyze`）

```
AnalysisController → DataAnalysisAgentImpl.analyze
  ├─ request.isCommand() (问题以 "/" 开头)  → SkillManager.processWithCommand → 命中即返回，否则降级到 ReActAgent
  ├─ request.hasSkill()  (指定 skillId)     → SkillManager.findSkillByName → DynamicSkill.processWithContext
  └─ 默认                                     → ReActAgent.execute  (ReAct 循环)
```

`/analyze/stream` 走 SSE，先把 `analyze()` 的 `thinkingSteps` 一条条 emit，再 emit `result` 和 `done`，是同步执行后流式输出结果，而非真正的 token 流。

### ReAct 循环（`agent/ReActAgent.java`）

- 不使用 LangChain4j 原生 ToolCall，而是**自实现文本协议**：在 system prompt 后追加工具列表，要求 LLM 用 `[CALL:工具名("参数1", "参数2")]` 调用，然后 `tryExecuteToolCall` 用正则提取并 dispatch 到 `AgentTools` 的 12 个 `@Tool` 方法。原因之一是要兼容 OpenAI 兼容网关（很多代理不支持 function calling）。
- 最大 8 轮迭代（`MAX_ITERATIONS`）。每轮把 LLM 输出 + 工具结果追加到 `messages`，直到 LLM 不再返回 `[CALL:...]` 或工具结果命中 `isFinalAnswer` 关键词（如"不存在""没有可用的""需要用户输入"）。
- `AgentTools` 的 12 个工具：`listAvailableSkills`、`useSkill`、`getFileContent`、`listFiles`、`getConversationHistory`、`askUserForInfo`、`searchMemory`、`calculate`（自实现 shunting-yard 表达式求值，**不**调用 LLM）、`getCurrentTime`、`analyzeFileData`、`searchKnowledge`、`getMemoryStats`。新增工具时需同时改：① `AgentTools` 加 `@Tool` 方法；② `ReActAgent.tryExecuteToolCall` 的 switch；③ `ReActAgent.buildToolSpecifications` 的 spec 列表。

### Skill 系统（`skill/` + `service/`）

- `Skill` 是接口，**唯一实现是 `DynamicSkill`** —— 一切技能都从 `skill_config` 表加载并实例化，没有硬编码的 Java 技能类。
- 启动时 `DataInitializer` 把 `skill_config` 中 `enabled=true` 的记录注册到 `SkillManager`，默认技能调 `registerDefaultSkill`。
- `SkillManager.findSkillWithScore` 同时做**关键词匹配**（`canHandle` + name/description）和**向量语义匹配**（查 vector store 中 `type=skill` 的条目，score × 15 作为加权）。
- `DynamicSkill.buildPrompt` 支持 `{{query}}` / `{{data}}` 占位符，`steps` 字段（JSON 数组）会渲染为编号工作流，`apiUrl` 非空则先调外部 API 拿数据再喂给 LLM。
- 新建技能两条路径：① `POST /api/skills/create` 直接传 config；② `POST /api/skills/generate` 让 AI 从样例数据/对话历史**自动生成** Skill 配置（`SkillGenerator`）。

### 模型路由（`mcp/MCPModelService.java`）

- `LangChain4jConfig` 注册一个 `defaultModel` Bean（从 yml 读配置）作为 fallback。
- `MCPModelService.getModel(modelId)` 用 `ConcurrentHashMap` 缓存按模型 ID 构建的 `OpenAiChatModel`，支持 provider = `openai|qwen|deepseek|zhipu`（都走 OpenAI SDK，只是 baseUrl/apiKey/modelName 不同）。
- 缓存失效靠 Spring 事件：`ModelConfigService` 在 add/update/toggle/delete 时发布 `ModelConfigChangeEvent`，`MCPModelService.onModelConfigChange` 监听并清缓存。**修改 ModelConfig 字段后必须保证事件被发布**，否则缓存的旧 model 不会刷新。
- 每次模型调用都会写 `token_usage` 表（estimateTokens 是字符数 ÷ 4 的近似值），通过 `SecurityContextHelper` 自动注入当前用户/租户。

### 向量记忆（`service/VectorMemoryService.java`）

- 嵌入模型固定为 `AllMiniLmL6V2EmbeddingModel`（384 维，本地推理，无需联网）。
- 所有索引数据带 metadata `type` 区分：`conversation`、`file`、`skill`、`knowledge`。`searchKnowledge` 工具只过滤 `knowledge`/`file`，`SkillManager` 的语义匹配只过滤 `skill`。
- `removeFromStore` 在两种模式下都做真正删除：InMemory 模式靠**重建整个 store**（LangChain4j 的 InMemoryEmbeddingStore 没有按 ID 删除 API）；Milvus 模式独立维护一个 `MilvusServiceClient`，通过 `delete` + `id in [...]` 表达式按 PK 批量删除。`index()` 会捕获 `embeddingStore.add()` 返回的 PK 存到 `IndexEntry.pk`，registry.json 一并落盘；旧 registry 没有 pk 字段时 load 进来 pk=null，删除时跳过 Milvus delete 只清 registry（向后兼容）。
- 文件分块：`chunkSize=500`，`overlap=100`（`indexFile` / `indexKnowledge`）。

### 安全与多租户

- `SecurityConfig`：无状态 JWT，`/api/auth/**`、`/actuator/**`、静态资源放行，其余全部需要认证。CORS 全开。
- `SecurityContextHelper.getCurrentTenantId()` 在所有数据写入路径被调用——新增 Repository 查询时**必须按 tenantId 过滤**，模型 / 技能 / 知识 / 文件 / token_usage / 会话 表都有 `tenant_id` 列。
- JWT 密钥写在 yml 里（`jwt.secret`），AccessToken 2 小时、RefreshToken 7 天。

## 文件与数据存储

以下目录都在**仓库根目录**（与 `docker-compose.yml` 同级），不在 `data-agent/` 子目录内：

- `uploads/` —— 用户上传的原始文件（Excel、PDF 等）。
- `tmp/` —— 临时文件（解析过程中的中间文件）。
- `data/` —— H2 数据库文件（dev profile 的 `./data/agent_db` 实际落在此处）和 InMemory 向量持久化文件（`vector-store/embeddings.json`、`registry.json`）。

`data-agent/target/` 是 Maven 构建输出。`src/main/resources/static/index.html` 是内嵌的单文件前端，跟着 jar 一起发布，根路径 `/` 直接访问。

## 关键约定与陷阱

- 新增或修改代码时，Javadoc、块注释和行内注释统一使用中文；如果改到旧文件，优先把相关注释一并改成中文。

- **`@EnableScheduling` 已开启**：`MCPContextManager.cleanupExpiredContexts`（每 5 分钟）、`VectorMemoryService.autoPersist`（每 60 秒）、`SessionManager` 的过期清理 都依赖这个。新增 `@Scheduled` 直接加即可。
- **`MCPContext` 必须配对 destroy**：`DataAnalysisAgentImpl.handleWithSkill` 用 try/finally 确保 `mcpContextManager.destroyContext`，新增使用 context 的代码必须遵循同样模式，否则虽然 5 分钟会过期回收但会污染统计。
- **文件内容截断**：`MAX_FILE_CHARS=3000`（`DataAnalysisAgentImpl`），`AgentTools.getFileContent` 也是 3000，`DynamicSkill.MAX_DATA_CHARS=3000`。改其中一处别忘改其他。
- **会话历史**：内存中 `ConversationSession`（`SessionManager.sessions` Map）+ MySQL `conversation_session/conversation_message` 双写。`SessionManager` 30 分钟超时清内存，但数据库记录保留。
- **测试**：目前只有一个空的 `AppTest.java`，没有现成测试基础设施。新增功能时需自行考虑验证方式。

## 不要做

- 不要把新工具直接写在 `ReActAgent` 内部 —— 加到 `AgentTools` 并在 switch 和 spec 列表同时注册，保持三处一致。
- 不要直接 new `DynamicSkill` 注入 Spring —— 它通过 `DataInitializer` 从数据库加载，Spring 不管理它。
- 不要假设 Milvus 一定可用 —— 任何用 `vectorMemoryService.searchMatches` / `searchRelevant` 的代码，必须能容忍空结果（InMemory 早期可能没数据）。
- 不要在 yml 里改 `langchain4j.open-ai.*` 的同时忘记 `DataInitializer.initDefaultModel` —— 后者首次启动时把这些值写入 `model_config` 表，之后改 yml 不会同步到数据库，需要手动改库或删默认模型让其重建。
