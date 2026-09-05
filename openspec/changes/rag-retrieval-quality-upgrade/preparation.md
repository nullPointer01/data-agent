# RAG 模型接入准备清单

## 用户需要完成

1. 在硅基流动国内站创建账号和 API Key。
2. 在 IDEA 的 Spring Boot Run Configuration 中配置 `EMBEDDING_API_KEY` 和 `RERANK_API_KEY`，不要把真实值写入仓库或聊天消息。
3. 确认只使用 `examples/rag-evaluation-lab/` 中的虚构教学资料进行首次外部 API 验证；真实企业文档接入前单独确认数据合规。
4. 在开始真实联调前确认是否允许恢复少量、聚焦的 HTTP 契约测试和一次 JDK 17 编译。此前“不要测试、不要编译”的决定仍然有效，未获得新确认前不执行。

## Codex 负责准备

1. 删除 Compose 中未完成的 Ollama Embedding 服务、数据卷和 README 中的对应启动说明。
2. 将 local profile 改为硅基流动 OpenAI-compatible Embedding 配置，模型为 `BAAI/bge-m3`、维度为 1024、索引版本为 `bge-m3-v1`，密钥只读取环境变量。
3. 增加独立 Reranker 配置和 HTTP Provider，对接 `BAAI/bge-reranker-v2-m3`，支持候选上限、超时、fail-open 和规则降级。
4. 把 Provider、模型、耗时、输入/输出数量、fallback 和错误类别写入 RAG Trace，不记录正文或密钥。
5. 使用六篇教学知识文档和 30 题黄金集完成入库说明、sourceId 替换说明和评测操作说明。
6. 在相同 BGE-M3 与相同 RRF Top20 候选上比较规则重排和模型精排，输出 Recall@20、MRR@10、NDCG@10、Hit@6 和 P95。

## 本地目标配置

```text
EMBEDDING_API_BASE_URL=https://api.siliconflow.cn/v1
EMBEDDING_API_KEY=<仅配置在本地环境>
EMBEDDING_MODEL_NAME=BAAI/bge-m3
EMBEDDING_DIMENSION=1024
EMBEDDING_INDEX_VERSION=bge-m3-v1

RERANK_ENABLED=true
RERANK_API_BASE_URL=https://api.siliconflow.cn/v1/rerank
RERANK_API_KEY=<仅配置在本地环境>
RERANK_MODEL_NAME=BAAI/bge-reranker-v2-m3
```

## 执行顺序

```text
清理 Ollama Embedding 残留并对齐配置
  -> 外部 API 契约探测
  -> BGE-M3 启动维度探测
  -> 教学知识库入库并生成新 Collection
  -> 规则重排基线
  -> 接入 Cross-Encoder
  -> 同集对比报告
```

API Key、外部服务可用性和真实 sourceId 是运行阶段输入；没有这些输入时，只完成代码、配置和静态一致性检查，不虚构评测结果。
