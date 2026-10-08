# RAG 检索评测教学实验

> 本目录只用于学习和验证评测流程。文档、问题和答案均为虚构教学数据，不能作为真实业务 Recall、MRR、NDCG 或延迟结论。

## 你要学会什么

完成这套实验后，你应该能够自己回答四个问题：

1. 一份知识文档怎样被切成 Chunk，并分别写入 Milvus 与 Elasticsearch。
2. Vector、BM25、RRF 和模型精排分别解决什么问题。
3. 正确文档在哪个阶段丢失，应该调整切块、Embedding、融合还是重排。
4. 怎样固定数据集、一次只改变一个变量，证明优化确实有效。

## 实验材料

```text
knowledge/
  01-account-security.md       账号与设备安全
  02-subscription-refund.md    订阅与退款
  03-offline-content.md        离线内容
  04-team-workspace.md         团队空间
  05-privacy-export.md         隐私与数据导出
  06-error-codes.md            错误码与故障处理
answer-key.md                  30 道题的人工标准答案
golden-dataset.template.json   文档级黄金集模板
```

文档故意包含精确编号、语义改写、表格、相似概念、跨段信息和跨文档问题。它不是产品说明书，而是一套可控的检索实验材料。

## 第 1 步：先人工读懂，不运行系统

先阅读 `knowledge/` 下六篇文档，再打开 `answer-key.md`。随机挑三道题，确认你能在原文中找到答案。

这一阶段的目的，是理解“黄金答案必须由人确认”。不能先看系统搜到了什么，再把搜索结果标成正确答案。

## 第 2 步：上传六篇文档

使用同一个管理员账号进入“知识库”页面，依次上传 `knowledge/` 下六个 Markdown 文件。页面上传接口为：

```text
POST /api/v1/knowledge/upload
```

每次上传成功后记录：

- 文档名称
- `knowledgeId`
- `chunkCount`

`knowledgeId` 就是黄金集中的 `expectedSourceIds`。当前 Chunk ID 的格式是：

```text
knowledgeId_chunk_0
knowledgeId_chunk_1
...
```

上传完成后，也可以用当前管理员 Token 查看映射：

```bash
curl 'http://localhost:8080/api/v1/knowledge/list' \
  -H 'Authorization: Bearer <ADMIN_TOKEN>' \
  | jq '.knowledge[] | {name, knowledgeId, chunkCount}'
```

不要为了实验修改数据库主键。以系统真实返回的 `knowledgeId` 为准。

## 第 3 步：先做五道人工检索

在“知识库 -> 检索测试”中依次输入下面五道题，Top K 设为 5：

1. 连续输错密码几次会被锁定？
2. 年付会员用了两小时，购买第六天能退款吗？
3. E-207 表示什么，应该怎么处理？
4. 谁可以转让团队空间所有权？
5. 手机丢失后，怎样同时保护账号和离线内容？

先不要看总分，只观察每条结果的 `sourceId`、分数和片段内容，并记录：

- 正确来源是否出现。
- 正确来源排第几。
- 关键词问题与语义改写问题的表现是否不同。
- 跨文档问题是否能同时找到两个来源。

## 第 4 步：生成可运行黄金集

上传完成后，使用当前管理员的真实来源生成黄金集（输出文件必须不存在）：

```bash
node scripts/rag-golden-dataset.mjs examples/rag-evaluation-lab/golden-dataset.template.json config/rag-golden-dataset.json --base-url http://localhost:8080
```

工具从进程环境 `RAG_ADMIN_TOKEN` 读取鉴权 Token，不输出 Token、不上传或改写知识；通过来源管理 API 游标分页读取当前 ADMIN 本人的文档。按下面的中文名称或原始 Markdown 文件名精确绑定，缺失、重名或重复 ID 都拒绝。也可使用 `--sources 来源数组.json`，输入为 `[{"sourceId":"实际ID","title":"文档名"}]`；它只验证映射，不能代替确认数据属于该账号。

手工替换时对应关系如下：

| 占位符 | 对应文档 |
|---|---|
| `REPLACE_WITH_ACCOUNT_SECURITY_ID` | 账号与设备安全 |
| `REPLACE_WITH_SUBSCRIPTION_REFUND_ID` | 订阅与退款 |
| `REPLACE_WITH_OFFLINE_CONTENT_ID` | 离线内容 |
| `REPLACE_WITH_TEAM_WORKSPACE_ID` | 团队空间 |
| `REPLACE_WITH_PRIVACY_EXPORT_ID` | 隐私与数据导出 |
| `REPLACE_WITH_ERROR_CODES_ID` | 错误码与故障处理 |

第一轮只使用 `expectedSourceIds`，判断正确文档是否被召回。不要急着填写 `expectedChunkIds`；文档级闭环稳定后，再从评测报告的实际排名证据中人工核对 Chunk。

配置应用读取替换后的文件，在 `src/main/resources/application.yml` 修改显式配置（默认路径已经是下列值），然后重启：

```yaml
app:
  rag:
    benchmark:
      dataset-path: ./config/rag-golden-dataset.json
```

加载器会拒绝未替换占位符。生成文件后仍需人工核对每条标注；这 30 道教学题不满足正式中文改造五类各 6 道的分类设计，正式数据需单独准备。

## 第 5 步：运行批量评测

确认 Milvus、Elasticsearch 和应用已经就绪，然后使用同一个管理员账号调用：

```bash
curl -X POST 'http://localhost:8080/api/v1/rag/benchmark/run' \
  -H 'Authorization: Bearer <ADMIN_TOKEN>' \
  -H 'Content-Type: application/json' \
  > rag-learning-baseline.json
```

报告会给出四个阶段：

```text
VECTOR -> BM25 -> RRF -> RERANK
```

重点查看：

- `RRF.recallAt20`：正确来源有没有进入候选集合。
- `RERANK.mrrAt10`：正确来源是否被排到前面。
- `RERANK.hitAt6`：最终六条上下文中是否存在正确来源。
- `failedCaseIds` 和逐案 `failureStage`：问题具体在哪一阶段失败。
- `dataset.sha256` 与 `configuration`：两次报告是否真的可比较。

## 第 6 步：做一次受控对比

保存第一次报告作为 A。中文索引改造依次固定其他变量，对照旧分块 + standard、旧分块 + ICU、中文分块 + ICU。使用旧检索实验环境时需要只接入新报告快照采集，不改变其分块/分词实现；缺少快照的旧报告不能直接比较。现有主分支只保留一套分块/ICU 实现，不提供 standard 运行开关。

只有同时满足以下条件，A/B 对比才有意义：

- `dataset.sha256` 相同。
- `corpusVersion` 相同。
- 除目标变量外，其余配置相同。
- 两次报告都没有 `EXECUTION_ERROR` 或精排降级。
- 每条用例实际 `retrievalQueries` 相同，查询扩展不能随机改变对照输入。

```bash
node scripts/rag-compare-reports.mjs baseline.json candidate.json
```

工具输出四个阶段的指标差和延迟比，非目标模型/配置/问句变化、标签哈希不一致、旧报告缺字段都会拒绝。单轮输出不是发布结论；相同预热下至少三轮，并人工检查分类收益、编号漏召回和引用位置。部署与维护重建操作见 [中文检索与索引维护](../../docs/核心逻辑详解/中文检索与索引维护.md)。

教学数据只能证明评测机制和参数影响，不能代替真实用户问题。面试中应表述为“我建立并跑通了可复现评测闭环”，不能把教学集指标说成线上业务收益。
