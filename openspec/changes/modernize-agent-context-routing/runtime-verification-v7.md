# v7 首轮运行验收

日期：2026-09-23。Test Level：low。执行者：本会话实现者。

结论：截图中的四条编译错误已修复，后端编译和前端构建通过，主要执行链路已真实跑通；项目整体不能判为全部通过，记忆写入存在误判。Decision：FAIL（功能验收发现问题，不是编译失败）。未归档，后续应修复发现项再回归。

## 范围与授权

用户“开始验证项目”允许本轮编译、启动和真实模型调用；随后截图要求“改一下”，明确授权修复四条编译错误。仍不新增测试类。保留原有脏工作树，修复前两个源码副本保存于 `/tmp/data-agent-verify-v7.2h5Qqm/`。本轮只改编译错误，不扩大到重新设计记忆写入。

## 编译修复

- `MemoryContextPromptFormatter`：删除摘要逻辑时误删 safeList，但画像仍有三个调用。改用 Spring CollectionUtils.isEmpty，画像格式化和存在性判断采用一致判空方式。
- `AgentExecutionTraceService`：旧 toolSelectionFallback 字段已从计划移除，Trace 写入仍调用其访问器。新 Trace 明确写 false，保留历史数据库列和读取兼容，不恢复扩大工具候选的兜底。
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.9.jdk/Contents/Home mvn -Dfrontend.skip=true -DskipTests compile`：首次失败 4 条，修复后 546 个源码编译成功，4.290 秒。
- 前端 `npm run build -- --outDir /tmp/data-agent-verify-v7.2h5Qqm/frontend-build`：1734 模块成功，1.15 秒。输出到临时目录，未覆盖用户已跟踪的静态资源。

## 实际环境与结果

local 配置、MySQL、Redis、Milvus、Elasticsearch；新后端健康状态 UP。规划模型为已绑定的 gpt-5.6-terra，日志为 JSON_SCHEMA；回答与背景记忆分别经过 TEXT/Streaming 和 JSON_OBJECT。每次模型规划均产生 SEMANTIC_ROUTER 证据，没有 Router 失败降级日志。这证明端点接受该请求且本次输出通过校验，不证明代理内部严格约束实现方式或所有输入都有效。

创建两个隔离普通验收账号，均属于 default 租户。资料和问题均为合成数据。

| 场景 | 实际结果 | 总耗时 |
| --- | --- | --- |
| 完整能力介绍 | CHAT，本地回答，业务模型/工具调用均为 0 | 0.423 秒 |
| SSE 与异步任务解释 | GENERAL_KNOWLEDGE，记忆/知识 OFF，CHAT | 5.809 秒 |
| 同会话追问 WebSocket | 历史 REQUIRED，查询补全为 SSE 与 WebSocket，真实 text/event-stream 含 token 和 done | 6.866 秒 |
| 私有项目口令/负责人 | 知识 REQUIRED，正确返回合成事实和 [R1] | 10.982 秒 |
| 显式要求 searchKnowledge | 知识 REQUIRED 预加载，答案正确，但 CHAT/工具调用 0，未按用户要求执行具名工具 | 7.383 秒 |
| 按需补全验收清单 | 知识 OPTIONAL，REACT，searchKnowledge 成功 1 次并引用资料 | 60.375 秒 |
| 指定 calculate 计算 | REACT，真实调用 1 次，结果 83814526 正确 | 11.173 秒 |
| 显式保存回答偏好 | 新长期 PREFERENCE 写入，回答确认 | 9.153 秒 |
| 新会话回忆偏好 | semanticMemory OPTIONAL，searchMemory 成功召回偏好；但事后写入产生错误记忆，见下文 | 12.606 秒 |
| 同租户第二用户查询第一用户资料 | 知识 REQUIRED，未泄漏合成口令/姓名，说明证据不足 | 11.047 秒 |

总耗时为临时验收脚本测量，含路由与响应完成，不等同于 Trace 的 Run duration。第一用户 8 次真实 Router 采样约 3.031–5.906 秒；没有足够样本报告 P95/P99。长清单的 ReAct 第二次模型生成约 50 秒，检索约 0.6 秒，不能声称整体性能已经达标。能力介绍的后台记忆提取仍可能调用模型；“0 调用”只描述规划/业务 Run，不代表整个请求生命周期完全无模型消耗。

## 发现的问题

1. P1：跨会话提问“我之前要求你记住的回答格式偏好是什么？”被写成新的 USER_EXPLICIT/PREFERENCE，content 为问句本身，confidence=1。它是回忆提问，不是新的保存指令，会污染长期记忆。证据：临时记忆 `ee8f59ce-1c47-464a-8232-4fe31570efba`，本轮清理前已留存响应。
2. P1：单次验收任务要求“不在规划阶段预加载资料”被自动写成 USER_IMPLICIT 长期偏好，并在后续回答格式偏好检索中一并召回。需要区分本次任务约束与跨会话稳定偏好。证据：`c28695e7-4e8d-4f13-8d98-d42fd23e84e4`。
3. P2：用户明确点名使用 searchKnowledge 时仍可能转为 REQUIRED 预加载；检索确实完成，但回答出现“本次无工具调用能力”。当前策略强调预加载不重复检索，与具名工具执行要求存在体验差异，需明确期望后处理。

上述问题未通过修改验收输入、增加关键词特判或恢复宽泛工具兜底规避。

## 成功标准与质量审查

- SC1/SC2：空会话 OFF、追问历史 REQUIRED、三态上下文均有真实 Trace；通过本次样本。
- SC3：精确能力入口、其他请求走 Schema Router 已验证；非法 Schema/拒答/低置信度、取消和预算异常仍只有静态检查，未故障注入。
- SC4：追问生成独立 SSE 查询，回答保留原问题；通过本次样本。
- SC5：OPTIONAL searchKnowledge/searchMemory 经过 ReAct，calculate 真实执行；同租户双用户未泄漏知识。未覆盖所有 RBAC/子 Agent/审批恢复。
- SC6：普通验收账号访问管理员 Trace 接口得到 403，证据改由只读查询自身 Trace 获取；未提权、未重跑管理员 Agent Eval 指标链。
- SC7：本轮后端编译与前端构建通过；没有恢复 src/test，也未重跑历史 30+ 黄金集，不能引用旧 51/51 或宣称完整回归。
- Correctness：编译与主要读取执行路径通过；记忆写入语义误判使整体正确性未通过。
- Readability/Architecture：修复只复用集合工具、移除旧访问器依赖；无新增旁路或运行时。
- Security：普通账号管理员接口拒绝、双用户知识隔离样本通过；未运行高风险真实写工具。
- Performance：记录实际延迟，长输出明显慢；无压力测试或百分位结论。
- D2：发现显式保存/提问边界及临时约束持久化问题；D4：清理后访问器遗留已通过编译修复；D6：null 集合用现有工具判空，错误路径全面运行覆盖尚缺。
- Anti-Scope/Boundaries：无新增框架、无关键词验收特判、无权限扩大、无重置用户改动；未新增测试类。

## 假设状态

- confirmed（本次样本）：A013 类型解析；A014 来源解耦；A015 同租户知识隔离；A017 Schema 请求可用；A020 普通/JSON/流式兼容。
- 部分 confirmed：A007 工具候选与模式；A008 本次合法规划输出，未形成统计准确率；A010 已测 Router 延迟，未比较轻量模型成本；A016 正常 Redis/RAG 读取成功，故障延迟未测。
- unverified：A012 子 Agent 根预算/取消；全部格式异常与审批恢复路径；规模上限边界。
- A019 已更新：local 明确绑定现有 GPT，公共默认仍为空；未新建小模型。
- A011 由本轮用户授权更新：可编译和接口验收，不新增测试类；旧成绩不作本轮证据。

## 验收数据与进程收尾

已通过接口删除本轮临时知识 1 条、第一账号记忆 12 条和会话 8 个。两个验收账号已按精确 ID + 用户名核对后停用，可重新启用；保留 Trace、Token 记录及第二账号的一次隔离会话供追溯。只清理本轮创建的合成数据，没有修改原有用户资料。

停止本轮启动的后端进程 PID 94449，释放 8080 供用户从 IDE 启动。Docker 依赖保持原状。

证据目录：`/tmp/data-agent-verify-v7.2h5Qqm/`。其中 state.json 含临时账号令牌，仅本机权限 0600，不应提交或分享；报告不包含凭据。
