# SDD State

- Change: `rag-retrieval-quality-upgrade`
- Mode: Full SDD
- Current Stage: Coding implementation complete; external verification pending
- Explore: Completed
- Proposal: Phase 1 approved by user's direct instruction to continue RAG optimization
- Coding: Phase 0 through Phase 3 and Task 20 completed; standalone teaching lab prepared; Phase 3 business baseline run pending real dataset
- Verify: Not started
- Archive: Not started

## Stage Gate

用户已直接要求实现 RAG 评测。Phase 3 生产代码已按“无测试、无编译”约束完成：使用外部真实黄金集，不在仓库伪造30条业务标注。由于真实数据集尚未配置且用户禁止运行应用，Checkpoint C 的基线指标仍未运行，不得宣称SC-4/SC-5已实测通过。Phase 4 及后续原有测试型任务仍需在执行前重写。

用户随后确认增加 `examples/rag-evaluation-lab/` 独立教学实验。该目录中的虚构文档、30题模板和答案册只用于学习入库与评测流程，不进入生产默认配置，也不改变真实业务黄金集仍待提供的状态。

2026-09-03 用户确认按企业 Agent 应用方向进入 Coding，并保留此前“不新增测试、不编译”的限制。Task 20 已完成：Compose 移除 Ollama Embedding 服务和数据卷，local profile 默认使用硅基流动 `BAAI/bge-m3`、1024 维和 `bge-m3-v1`，真实密钥仍只从环境变量读取。

Task 21 仍等待当前 Shell 注入 `EMBEDDING_API_KEY` 与 `RERANK_API_KEY`。2026-09-05 再次确认两者在当前 Codex 进程均未配置，因此没有发起外部请求；API 响应契约、真实向量维度和限流信息仍未验证。Task 22 继续受用户“不新增或运行测试”决定约束。

Task 23、24、24B、24C 已完成静态实现：新增厂商无关 Rerank Provider、HTTP Cross-Encoder、规则 fallback、候选索引完整性校验、有限重试、熔断、逐请求 Trace 证据，以及可区分 fallback 的评测报告。没有运行测试、Maven 编译、应用或 Docker；Task 25 的规则/Cross-Encoder 同集指标对比仍待真实环境执行。

## Expanded Phase 0 Scope

用户要求把项目完全统一到 JDK 17。Phase 0 已扩展为 Maven Enforcer、`release=17`、仓库版本声明、JDK 17 CI/应用容器、构建文档和现有测试源码清理。

JDK 17 干净编译已确认 451 个生产源码可编译。原 `src/test/java` 下 129 个测试源码已按用户要求整体移出项目，不恢复已淘汰的生产接口。

在用户发出“不再测试或编译”指令前，已完成一次 JDK 8 拒绝校验和一次 JDK 17 `clean verify`；此后未再执行编译或测试，Docker 构建也未执行。
