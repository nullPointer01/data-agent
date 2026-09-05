## Context

项目当前直接依赖 LangChain4j 核心、OpenAI 和 Milvus 0.30.0。Maven Central 的核心稳定版本已到 1.19.0，但 Milvus 1.19.0 仍带 `beta29` 后缀；若为了核心“最新”而混用 1.19.0 与旧 Milvus，会形成不受支持的二进制组合。三个 artifact 最后一个完全同版、无预发布后缀的版本是 0.36.2，其 class-file major version 为 61，对应 JDK 17。

0.36.2 保留项目使用的 Chat Model、Streaming、Embedding、Milvus Store 和消息 API。已通过实际 JAR 字节码确认，`ToolExecutor` 与 `DefaultToolExecutor` 从 `dev.langchain4j.agent.tool` 移到 `dev.langchain4j.service.tool`，构造器 `DefaultToolExecutor(Object, Method)` 和 `execute(ToolExecutionRequest, Object)` 签名保持不变。

## Goals / Non-Goals

**Goals:**

- 使用三个目标模块共同可用的最新稳定同版本组合。
- 集中管理版本并通过 JDK 17 编译发现和修复 API 兼容问题。
- 保持 Agent、RAG、Embedding 和外部服务配置行为不变。

**Non-Goals:**

- 不使用任何 alpha、beta、RC 或 snapshot 依赖。
- 不升级到 LangChain4j 1.x，不替换 Milvus 适配器。
- 不新增测试，不验证真实模型或基础设施运行时链路。

## Decisions

### 统一采用 0.36.2

采用 `langchain4j.version=0.36.2` 同时驱动三个直接依赖。替代方案一是核心/OpenAI 1.19.0 + Milvus 1.19.0-beta29，但违反全稳定要求；替代方案二是核心 1.19.0 + Milvus 0.36.2，但存在 API 和二进制不兼容；替代方案三是继续 0.30.0，无法取得后续稳定修复。

### 只做编译驱动的 API 适配

先迁移已确认的工具执行器 import，再执行 Maven compile，以编译器列出的错误作为其他迁移依据。不会预防性重构业务逻辑。

### 保留历史变更证据

活跃 OpenSpec 中对 0.30 的描述是当时实现和字节码检查的历史事实，不批量改写。本次只更新 Maven、当前项目说明和持续维护的知识文档。

## Risks / Trade-offs

- [风险] 0.36.2 不是核心模块的最新稳定版本 → 以三个模块共同稳定和同版兼容优先；待 Milvus 1.x 发布稳定版后再整体升级。
- [风险] 编译成功不能证明模型、Milvus 和 ES 运行时兼容 → 本次只声明编译兼容，不声明运行时验证。
- [风险] Maven 镜像解析依赖缓慢或失败 → 使用 Maven Central 校验版本存在性，正式编译保留完整错误输出并重试可恢复的传输失败。

## Migration Plan

1. 统一 Maven 版本属性并迁移工具执行器 import。
2. 使用 JDK 17 编译并修复其余 API 差异。
3. 检查解析后的依赖版本和变更差异。
4. 回滚时恢复版本属性为 0.30.0，并将工具执行器 import 恢复到旧包；不涉及数据迁移。

## Open Questions

无。未来是否采用 1.x 取决于 Milvus 模块是否发布无预发布后缀的同版本 artifact。
