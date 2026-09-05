## Why

项目仍固定在 LangChain4j 0.30.0，缺少后续稳定版本中的修复，同时三个模块分别写死版本，存在未来误升级和版本漂移风险。由于 Milvus 1.x 适配器当前只有 beta 版本，本次选择核心、OpenAI 与 Milvus 三者最后一个同版本且无预发布标记的 0.36.2，保证 JDK 17 和依赖 API 基线一致。

## What Changes

- 将 `langchain4j`、`langchain4j-open-ai`、`langchain4j-milvus` 统一升级到稳定版 0.36.2。
- 在 Maven properties 中集中管理 LangChain4j 版本，避免三个模块发生版本漂移。
- 适配 0.36.2 中 `ToolExecutor` 与 `DefaultToolExecutor` 的包迁移。
- 更新直接声明旧版本的项目说明和依赖行为文档。
- 使用 JDK 17 执行 Maven 编译，按实际编译错误完成必要的兼容改造。

## Capabilities

### New Capabilities

- `stable-langchain4j-baseline`: 约束 LangChain4j 核心、OpenAI 和 Milvus 模块使用统一、JDK 17 兼容且无预发布标记的稳定版本，并保持项目可编译。

### Modified Capabilities

无。本次不改变 Agent、RAG、Embedding 或 Milvus 的业务契约。

## Quantified Success Criteria

- SC-1：三个 LangChain4j 直接依赖的解析版本均为 0.36.2，且版本字符串不包含 alpha、beta、RC 或 snapshot。
- SC-2：项目在 Oracle JDK 17 下执行 `mvn -Dfrontend.skip=true -DskipTests compile` 返回 0。
- SC-3：代码库中不再存在生产文档或 Maven 配置对 LangChain4j 0.30 的当前态声明；历史 OpenSpec 证据保持不变。
- SC-4：本次不新增测试、不启动应用，也不访问真实模型、Milvus 或 Elasticsearch。

## Assumptions

- A-1：Maven Central 在 2026-09-02 提供核心与 OpenAI 1.19.0，但 Milvus 对应版本为 `1.19.0-beta29`；用户要求全稳定版本，因此不能采用 1.19.0 组合。
- A-2：0.36.2 是三个目标 artifact 最后一个同版本且均无预发布标记的版本。
- A-3：0.36.2 class-file major version 为 61，兼容 JDK 17。

## Boundaries

- Always：三个 LangChain4j 模块必须同版本，编译必须显式使用 JDK 17。
- Ask first：若编译要求改变 Agent/RAG 对外行为或引入新的基础设施依赖，先暂停确认。
- Never：不混用 LangChain4j 1.x 与 0.x，不引入 beta/RC，不删除或覆盖工作区已有改动。

## Anti-Scope

- 不升级 Spring Boot、Milvus 服务端或 Elasticsearch。
- 不重构 Agent Runtime、RAG 检索和 Embedding 业务逻辑。
- 不新增、恢复或运行测试。

## Impact

直接影响 `pom.xml`、LangChain4j 工具执行适配代码、项目依赖版本说明和本 change 文档。运行时外部服务配置、索引数据和 API 契约不变。
