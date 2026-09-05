> 张原铭专版 · 2026-06-07

---

## 目录

- 第 1  章 开场自我介绍
- 第 2 章 项目深度讲解 + 魔鬼追问
- 第 3 章 Redis 八股 + 追问链
- 第 4 章 MySQL 八股 + 追问链
- 第 5 章 JVM 八股 + 追问链
- 第 6 章 并发 / JUC + 追问链
- 第 7 章 Spring / MyBatis + 追问链
- 第 8 章 Kafka 八股 + 实战追问
- 第 9 章 网络协议
- 第 10 章 操作系统
- 第 11 章 @Transactional 失效与传播
- 第 12 章 线上排查实战
- 第 13 章 系统设计题
- 第 14 章 手撕代码（10 题）
- 第 15 章 算法真题（口述题）
- 第 16 章 AI / Agent 八股
- 第 17 章 分布式理论
- 第 18 章 设计模式
- 第 19 章 行为面 / 软问题
- 第 20 章 Linux 排查命令清单
- 第 21 章 临场建议 + 反问 + 冲刺清单
- 第 22 章 华为 OD 成都专项查漏补缺

---

# 第 1 章 开场自我介绍（90 秒版）

> 面试官你好，我叫张原铭，目前在同程旅行产品研发部，负责酒店商品库与促销研发。
>
> 我的核心工作是：**数据拉取系统**——负责 300+ 维度的数据从多个数据源拉取、PB 格式化、推送给下游检索服务，数据规模千万级，全量 1 小时内闭环；**检索业务开发**——参与酒店核心 Handler 开发与索引加载；此外独立解决过数据拉取服务 Full GC 几秒一次甚至 OOM 的严重性能问题。
>
> 个人项目方面，我从零搭建了 Data Agent，基于 LangChain4j + Milvus 实现 ReAct Agent、RAG 检索、多模型热切换，探索 AI 协作开发方法论。
>
> 今天很高兴有机会和您深入交流。

---

# 第 2 章 项目深度讲解 + 魔鬼追问

## 2.1 商品库数据拉取系统（重点！）

### 一句话定位

> "这是一个为下游检索服务稳定供给业务数据的**数据拉取与格式化推送系统**，覆盖酒店库存/价格/房型/产品/促销等 300+ 维度，数据规模千万级，全量 1 小时内闭环。"

### 整体架构（两层服务）

```
【goods-task · 调度分发层】
  定时触发 → 加载全量维度列表 → 放入 PriorityBlockingQueue
  → 每台 Puller 对应一个调度线程 → HTTP 分发（DataPullerClient.dispatchDimension）
  → 轮询 getDimensionTaskStatus → 成功移出/失败重入队列
  → 全部完成后触发结果校验 & 文件配送

【goods-data-puller · 实际拉取层】（多台机器并行）
  接收维度任务 → 多线程拉取数据源（DB / HTTP API / MQ）
  → Supplier2PbUtil 序列化 → 写本地文件 & JuiceFS 网盘
  → 下游 DS（C++）检索服务消费
```

**技术栈**：SpringBoot + MyBatis + Redis（凤凰+KVM 双集群）+ Protobuf + JuiceFS + SCP + JavaMail

---

### 六个核心模块深挖

#### ① DimensionScheduleManager —— 双队列调度（代码级）

**两级队列设计**（不是一个，是两个！）：

```java
PriorityBlockingQueue<DimensionScheduleItem> waitingDimensions;   // 按 classification 优先级排序
LinkedBlockingQueue<DimensionScheduleItem>   secondWaitingDimensions; // FIFO 缓冲
```

**为什么两个**：维度有"维度级 Semaphore"控制单个维度的并行度上限。当某维度并行度已满（`dimensionSemaphoreMap.get(dim).tryAcquire()` 失败），该维度进入二级 FIFO 等待，不重新放回优先级队列反复排序——减少排序开销，也防止高优先级维度饥饿低优先级。

**分发主循环**：

```java
if (handling.size() < dispatchUnit) {          // 还能分发更多
    // 1. 先轮询二级队列（等待并行度释放的维度）
    DimensionScheduleItem item = secondWaitingDimensions.poll();
    if (item != null) {
        Semaphore s = dimensionSemaphoreMap.get(item.getDimension());
        if (s != null && s.tryAcquire()) {     // 成功拿到维度级信号量
            dispatchDimension(item);
        } else {
            secondWaitingDimensions.add(item); // 还是满，放回继续等
        }
    }
    // 2. 再从一级优先级队列拿新维度
    item = waitingDimensions.poll();
}
```

**HTTP 超时特殊处理**（DataPullerClient）：

```java
// 超时时认为分发已到达 Puller，不重试，返回空列表
if (ex instanceof SocketTimeoutException) {
    return Collections.emptyList();
}
return dimensions; // 其他异常：原样返回，等待重试
```

理由：超时≠失败，Puller 可能已在执行，重试会造成重复拉取。

**失败重试**：每维度 `failed_count++`，超过 `maxRetryTimes` 进 `failedDimensions`，否则重入 `waitingDimensions`。

#### ② Puller 三态状态机（DOWN → WAITTING → OK）

```java
private enum PullerStatus { OK, DOWN, WAITTING }
```

**为什么三态，不是二态**：DOWN 直接切回 OK 会导致宕机机器旧任务还在执行，立刻分配新任务产生重复处理。WAITTING 是一个"清场等待"状态：

| 状态 | 触发条件 | 动作 |
|------|---------|------|
| OK → DOWN | 心跳检测连续失败 | `resetPullerDimensions(1)` 把正在处理+已成功的维度全部重入队列，servers 列表移除该机 |
| DOWN → WAITTING | 调 `DataPullerClient.stopPullerTask()` 成功 | 等该机所有运行中维度清空 |
| WAITTING → OK | `getRunningDimensions(statsMap)` 返回空 | 调 `DataPullerClient.resetPullerTask()`，重新加入 servers |

**宕机时已拉成功的维度为何也重入**：宕机机器的文件可能写入不完整或挂载点已断，宁可重拉，后置冗余文件清理会处理重复。

#### ③ Master Puller 优先保证（Semaphore）

增量任务（INC）下 `workPullerCount` 可配为 1，全局只有一个 Puller 级 Semaphore 令牌：

```java
void start() {
    // Master 先启动，抢 Semaphore
    for (String server : masterServers) {
        exec.submit(new Puller(server));
    }
    // 等 3 个心跳周期确认 Master 已经拿到令牌
    int count = 3;
    while (semaphore.availablePermits() > 0 && count-- > 0) {
        ThreadUtil.sleep(1);
    }
    // 非 Master 后启动，令牌已被抢光则阻塞等待
    for (String server : servers) {
        if (!masterServers.contains(server)) exec.submit(new Puller(server));
    }
}
```

全量任务（ALL）不走这段逻辑，所有机器平等竞争。

#### ④ 红包匹配结果集优化（12 亿 → 1400 万）

- **根因**：所有红包 × 所有酒店全量笛卡尔积，随业务增长无限膨胀
- **方案**：只让"星级红包"（覆盖 99%+ 业务场景的子集）参与匹配 + 定时清理过期红包
- **上线前验证**：与业务方对齐星级红包的覆盖率，灰度对比新旧差异，A/B 验证下游转化
- **效果**：**12 亿 → 1400 万，降幅 99.9%**，拉取耗时大幅下降

#### ⑤ PB 序列化（Supplier2PbUtil）

```java
IndexEntity.Supplier.Builder builder = IndexEntity.Supplier.newBuilder();
builder.setSupplierId(supplier.getSupplierId());
// 字符串必须用 ByteString 避免内存复制
builder.setSupplierName(ByteStringUtil.copyFromUtf8(supplier.getSupplierName()));
builder.setOptype(IndexEntity.kOpType.kOpTypeLoad);
return builder.build();
```

- proto 定义在 goods-pb 公共仓库，Maven plugin 编译生成 Java 类
- 体积小 3-10x、序列化快 5-10x、强 schema、下游 DS 是 C++ 跨语言
- **ByteString 而非 String**：减少一次内存拷贝，批量序列化效果明显

#### ⑥ JVM 调优（真实改法）

**改前**（全量暂存堆中）：

```java
List<Data> allData = dao.queryAll(dimension);   // 千万条全部在内存
writeToFile(convert(allData));
```

**改后**（分页流式，堆中最多 5k 条）：

```java
int offset = 0, pageSize = 5000;
while (true) {
    List<Data> batch = dao.queryPage(dimension, offset, pageSize);
    if (batch.isEmpty()) break;
    appendToFile(convert(batch));               // 序列化后追加写文件
    offset += pageSize;
    // batch 出作用域，下次 YGC 即可回收
}
```

**GC 参数（实际使用）**：

```bash
-Xms8g -Xmx8g -Xmn3g              # 堆固定防扩缩容，新生代 3G
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200           # 目标停顿 200ms
-XX:G1HeapRegionSize=16m
-XX:InitiatingHeapOccupancyPercent=45
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/data/dump/
-Xloggc:/data/gc/gc.log
```

**效果**：Full GC 从几秒一次降到几小时一次（频率降 99%+）

---

### 商品库魔鬼追问

#### Q：为什么要两个队列，一级用优先级、二级用 FIFO？

优先级队列的每次 `poll` 都需要堆调整，O(log n)。如果并行度满的维度一直放回一级队列，会不停触发堆调整且永远拿不到令牌，浪费 CPU。二级 FIFO 是"等待室"——已经获得调度机会只是在等并行度释放，不需要再和其他维度竞争优先级，FIFO 即可，插入/删除 O(1)。

#### Q：机器宕机，已拉成功的维度为何还要重入队列？

宕机机器的文件可能写入一半（磁盘 flush 未完成）、或 JuiceFS 挂载点已断，下游拿到截断文件比没文件更危险。所以宁可多拉一次，后置冗余文件清理会把旧机器残留的同维度文件删掉，再由新机器产出新文件覆盖。**宁可多拉不可少拉**。

#### Q：HTTP 超时后为什么不重试分发？

分发是一个"可能已到达"的操作，不是幂等的。Puller 可能已经开始拉取，如果 task 重试分发，同一维度就会在多台机器上并行执行，产生重复文件。所以超时直接认为"成功分发"（返回空列表），让后续状态轮询去确认结果。

#### Q：凤凰 + KVM 双 Redis 集群怎么用？

- 凤凰：公司内高可用 Redis Cluster，主链路缓存
- KVM：基于 KVM 虚拟机的备用集群
- 写：同步写凤凰，异步写 KVM；读：默认凤凰，故障时切 KVM
- `JedisShardedFacade` 内部对分片 key 一致性哈希路由，跨 shard mset 不原子，batchSet 拆成多个单独 set，异常时 alert 告警不静默

#### Q：MDC 透传为什么做？怎么做？

MDC 底层是 ThreadLocal，线程池子线程拿不到主线程的 traceId。写 `MdcPropagatingExecutorService`：包装 ExecutorService，提交任务前 copy 当前线程的 MDC 快照，子线程执行前 set、执行后 clear——防 ThreadLocal 泄漏。实现单 traceId 串联整个跨线程日志链路。

#### Q：Sharded Redis mset 有什么坑？

Sharded Redis 按 key hash 路由，mset 的多个 key 可能落在不同 shard，不能一次原子操作。`JedisShardedFacade` 内部按 shard 分组，各自执行 set，**不是原子的**，部分 key 失败时其他 key 已经写入。所以 `batchSet` 有 try-catch，任何异常立刻 alert，让业务层感知，不静默丢失。

#### Q：G1 换之前是什么，为什么换？

之前是 Parallel GC（JDK 8 server 默认）。Parallel GC 触发 Full GC 时整堆扫描，STW 几秒甚至几十秒；流式改造前老年代长期高水位，频繁 Full GC 是必然。G1 的 Region 化 + Mixed GC 只增量回收"垃圾最多的 Region"，停顿可控（MaxGCPauseMillis=200ms）。

#### Q：prepare 服务怎么防重复消费？Redis key 过期 60s 合理吗？

```java
String setResult = pullerCountRedis.set(
    "HotelOrderGeneralMessage_" + reqId,
    JSON.toJSONString(request), "NX", "EX", 60);
if (StringUtils.isBlank(setResult)) {
    return new OrderMessageResponse(-1, "重复数据！reqId=" + reqId);
}
```

60s 是"消费最大处理时间"的上界。如果处理耗时超过 60s 后重复消息来了，锁已过期会被重新处理，属于已知权衡——设置过长 KEY 永久堆积内存，60s 是合理经验值。生产中消费耗时一般 <1s。

#### Q：Handler 链中断（stopChain）的场景是什么？

Handler 调 `context.stopChain()` 后 OrderMessageProcessor 的循环 `!context.isContinueChain()` 提前 break。典型场景：会员信息查询失败（会员 id 不存在）时，后续依赖会员信息的 Handler 全部无意义，中断节省 IO 开销；或者黑名单检测命中，直接拒绝整条消息。异常触发 break 会先打 AlertUtil 告警。

---

## 2.2 酒店收益策略预测系统

### 一句话定位

> "策略上线前的**影响评估系统**——策略提交即触发预测，三类样本模拟用户访问，对比新旧策略指标差异，AI 自动生成分析报告，让运营无需人工对比就能决策'上/不上'。"

### 整体架构

```
策略提交触发
  ↓
DB CAS 抢占（multi-machine，affectedRows=1 的机器执行）
  ↓
MultiThread（CountDownLatch 双门控）× 2 环境并发
  ↓
每环境独立令牌桶 500 QPS → 29 万次服务调用
  ↓
结果落库 → 异步触发 AI 分析（OpenAiClient，不阻塞主流程）
  ↓
HTML 模板引擎渲染报告 → 运营决策
```

---

### 五个核心模块深挖

#### ① 多机任务抢占（DB CAS，不用 Redisson）

```sql
UPDATE predict_task
SET status='RUNNING', machine_ip=?, start_time=NOW()
WHERE id=? AND status='PENDING'
```

`affectedRows = 1` 的机器执行任务，其他机器 `affectedRows = 0` 放弃。MySQL InnoDB 行级锁 + MVCC 保证原子性，天然互斥。

**为什么不用 Redisson**：这是一个低频触发（策略上线前才跑）的轻量场景，DB CAS 少一个 Redis 外部依赖，且故障模式更简单——Redis 挂了整个系统就完了，DB 同时也挂则整个业务都挂，依赖粒度一致。

**状态机（枚举 + CAS 每步流转）**：

```
PENDING → RUNNING → AI_ANALYZING → DONE
                 ↘ FAILED
超时回滚：定时扫 status=RUNNING AND now()>expire_time → 回滚 PENDING 重试
```

每次流转都用 `UPDATE ... WHERE status=:old AND id=?`，防止并发状态覆盖。

#### ② 高性能并发架构（双门控 CountDownLatch）

自研 `MultiThread<H, T>` 工具类（不是线程安全问题，是为了精确控制"所有线程就绪后同时开始"）：

```java
CountDownLatch startLock = new CountDownLatch(1);   // 发令枪
CountDownLatch endLock   = new CountDownLatch(list.size()); // 终点线

// 工作线程
public T call() throws Exception {
    startLock.await();   // 所有线程就绪，等发令枪
    try {
        return execute(currentThread, data);
    } finally {
        endLock.countDown(); // 报告完成
    }
}

// 主线程
for (H data : list) {
    queue.add(exec.submit(new Task(data)));
}
startLock.countDown(); // 发令
endLock.await();       // 等全部完成
```

**为什么用发令枪模式**：预测调用量巨大，希望两个环境的线程"几乎同时"发出请求，避免因线程启动时序差异导致令牌桶统计窗口错位，影响限流精度。

**量化数据**：
- 端到端：20min → 10min（↓50%）
- 系统吞吐：493 QPS（贴近 500 QPS 令牌桶上限）
- 单次预测：29 万次服务调用

#### ③ AI 分析调用（OpenAiClient，动态超时 + 指数退避）

```java
// 超时动态计算：避免大 Prompt 被截断
int timeout = Math.max(90_000, Math.min(300_000,
    baseTimeout + (jsonBody.length() / 1000) * 8_000));
// 公式：min=90s, max=300s, 每增加 1KB prompt 多等 8s

// 重试：只重试 5xx，不重试 4xx
for (int i = 0; i < retryCount; i++) {   // retryCount=3
    try {
        return RestAndHttpClient.post(apiUrl, jsonBody, headers, timeout);
    } catch (HttpStatusException e) {
        if (e.getStatusCode() < 500) throw e;  // 4xx 立即失败
        // 5xx 指数退避：第1次1s，第2次2s
        ThreadUtil.sleep(1000L * (i + 1));
    }
}
```

**降级**：AI 调用失败时按"27 个指标差异度降序"自动生成纯数字兜底报告，异步线程池挂了也不影响预测主结果落库。

#### ④ goods-batch-job 规则缓存（LoadingCache + 定时刷新）

```java
ruleCache = CacheBuilder.newBuilder()
    .expireAfterWrite(10, TimeUnit.MINUTES)      // 10 分钟强制过期
    .build(new CacheLoader<String, List<BaseRule>>() {
        public List<BaseRule> load(String key) {
            return revenueRulePullerService.getTransformedRuleList(); // 未命中自动加载
        }
    });

// 另起定时任务主动刷新（初延迟3分钟，周期5分钟）
scheduler.scheduleAtFixedRate(
    () -> ruleCache.refresh(RULE_CACHE_KEY), 3, 5, TimeUnit.MINUTES);
```

**为什么既有 expireAfterWrite 又有定时刷新**：`expireAfterWrite` 是最后防线，`refresh` 是主动更新。`refresh` 是异步的——读操作拿到旧值，后台加载新值，读不阻塞；`expireAfterWrite` 是强制失效，到期后第一次读会阻塞等待加载——两者互补，平时用 refresh 保持新鲜，极端情况 expireAfterWrite 兜底。

#### ⑤ RuleMatchResultKafkaProducer（异步批发、背压告警）

```java
// 参数
capacity = 800_000;          // 队列最大 80 万条
BATCH_SIZE = 500;             // 每批发 500 条
IDLE_FLUSH_MS = 60_000;       // 最多等 60s 空闲也发一次

// Kafka 配置
acks = "1"                    // Leader 确认，非 all
linger.ms = 30                // 等 30ms 收集更多消息再发
batch.size = 512 * 1024       // 批次 512KB
compression = lz4
retries = 3
```

**背压**：队列满时 `tryEnqueue` 返回 false，触发 `AlertUtil.alert`，不阻塞调用方。原因：调用方是规则匹配主流程，不能被 Kafka 拖慢；宁可丢告警让运维介入，不卡主链路。

---

### 收益预测魔鬼追问

#### Q：AI 超时为什么动态计算，不固定 30s？

固定 30s 对于小 Prompt 绰绰有余，但对于注入 27 个指标 × 多期数据的大 Prompt（可达几 KB），模型 TTFT（首 token 延迟）本身就可能超过 30s。动态公式 `60s + 每 1KB 多等 8s`，最多 5 分钟，避免有效请求被误杀。

#### Q：为什么 acks="1" 不用 acks="all"？

规则匹配结果是离线批计算产出，可以通过重新跑 job 重算，不是不可恢复的事务数据。acks="all" 需要等所有 ISR 副本确认，延迟高、吞吐低。这里"高吞吐"比"强可靠"更重要，80 万条队列消化速度优先。订单级数据才用 acks="all"。

#### Q：LoadingCache 为什么不用 HashMap + Timer？

1. HashMap 需手动加锁，读写竞争自己管；CacheBuilder 内置读写分离
2. Timer 单线程，任务抛异常后整个 Timer 停摆；ScheduledExecutorService 多线程，单任务异常不影响下次调度
3. `refresh` 是异步加载，读线程不阻塞；手动 Timer 刷新期间如果加锁会短暂阻塞所有读
4. Cache 有 stats() 监控命中率，HashMap 需手动实现

#### Q：发令枪（startLock.countDown）的必要性？

不用发令枪时，线程提交后立刻开始执行，先提交的线程比后提交的线程早发出请求。两个环境（预发+线上）的请求在时间上存在错位，令牌桶统计窗口不同步，实际有效并发比设计的低。发令枪让所有线程在 `startLock.await()` 处聚齐，一声令下同时发出，最大化并发效果。

#### Q：任务超时后回滚 PENDING，机器会不会永远抢不到？

会的，如果那台机器一直在但某任务反复超时。现有方案是每台机器 30s 轮询一次 PENDING，超时任务被任何一台机器轮询到都会 reset 回 PENDING。实际场景是机器宕了才超时，宕机的机器再也不会抢——其他机器会把任务重新抢走执行。

#### Q：三类样本是什么？

1. **圈选酒店规则变更样本**：该规则覆盖的特定酒店的历史订单
2. **通用规则变更样本**：规则生效范围内所有酒店的历史订单
3. **价格公式变更样本**：受定价算法影响的订单（按价格敏感度筛选）

三类分开是因为变更影响范围不同，混合采样会稀释低影响场景的信号。

---

## 2.3 Data Agent（个人项目）

### 一句话定位

> "从零独立设计并落地的 AI Agent 系统，核心亮点是：5 层请求路由 + Orchestrator 多专家编排 + RRF 混合检索 RAG + 3 层记忆架构 + 模型熔断重试。438 个 Java 文件，约 1.8 万行代码，涵盖对话/知识库/向量/安全/追踪全链路。"

### 整体架构（5 层路由）

```
HTTP 请求
  ↓
AgentRuntimeService（路由决策）
  1. 斜杠命令：SkillManager.processWithCommand
  2. 指定 agentId：MultiAgentRuntimeService
  3. 指定 skillId：SkillExecutionService
  4. 默认路径：OrchestratorAgent（含多专家编排）
  5. Orchestrator 不可用时兜底：ReActAgent
  ↓
执行层（ReActLoopRunner / OrchestratorAgent）
  ↓
工具层（AgentTools 分发 → 7 个 ToolService）
  ↓
模型层（McpModelService → ModelRetryExecutor → ModelHttpClient）
```

**技术栈**：Spring Boot 3.2 + LangChain4j + Milvus + MySQL + Spring Security + JWT

---

### 六个核心模块深挖

#### ① ReAct 执行引擎（ReActLoopRunner）

- **上限**：`MAX_ITERATIONS = 8`，同步和流式两套实现（`run` / `runStreaming`）
- **每轮状态持久化**：每次迭代都调 `ReActWorkingMemoryService.recordIteration`，把"当前部分答案 + 模型输出"写进 WorkingMemory，下次迭代模型能读到，避免中断后状态丢失
- **Recovery**：每轮维护 `ReActRecoveryTracker`，工具异常时作为 Observation 追加，让模型自主决策换策略，而非直接报错
- **退出逻辑（三路）**：
  1. 模型输出含最终答案 → `!stepResult.shouldContinue()` 退出
  2. 内存索引完成 → `stepResult.memoryIndexed()` = true → `iterations--` 不消耗轮次
  3. 超 8 轮 → 把消息历史所有 `AiMessage` 清洗拼接成兜底答案，追加 reflection 步骤
- **流式特殊处理**：流式模式下，SSE token 由 `ReActStreamEventWriter.emitToken` 实时推送，超时后 fallback 到同步

#### ② RAG 检索管道（EnhancedRagPipeline）

六阶段串行执行（有 `StopWatch` 分段计时落 Trace）：

```
RagQueryRewriter.analyze(query)          → 查询改写 + 类型分类
HybridRetriever.retrieveWithTrace(...)   → 向量 + 全文双路检索
RrfFusionRanker.fuse(...)               → RRF 融合排序
RagReranker.rerankHybrid(...)           → 重排 + filter(min-score=0.45)
RagParentContextResolver.resolve(...)   → 补充父级 chunk 上下文
RagContextCompressor.compress(...)      → 截断到 maxContextChars
```

**RRF 公式**：`rrfContribution(rank) = 1.0 / (60.0 + rank)`，归一化分 `= min(1.0, rrfScore/maxRrfScore + rawScore × 0.05)`，`RANK_CONSTANT = 60` 是经典参数，让头部文档得到充分奖励但不完全压制低排名文档

**每条候选带 9 维元数据**：section_path / char_start / char_end / contains_table / contains_code / contains_list / parent_chunk_id / parent_char_start / parent_char_end —— 方便前端显示来源定位

**全文召回固定 ES BM25**：`ElasticsearchFullTextRetriever` 是唯一全文实现；索引 `data-agent-rag-v2` 显式区分 `keyword` 过滤字段与 `text` 检索字段，不再使用 JPA LIKE 伪全文降级

#### ③ 三层记忆架构（MemoryManager）

```
WorkingMemory（会话级，Redis key-value）
  → 当前 ReAct 执行状态，key = tenantId:userId:sessionId

ShortTermMemory（近期对话，MySQL memory_entry）
  → recall() 返回最近 5 条记忆摘要

LongTermMemory（语义检索，Milvus 向量）
  → recall(query, topK=5) 用 embedding 检索最相关历史

UserProfileMemory（用户画像，MySQL user_profile）
  → 每次 LongTerm 写入后异步刷新画像
```

**配额控制**：写短/长期记忆前调 `MemoryQuotaService.pruneBeforeCapture`，超配额先淘汰最旧记忆，防止单用户无限膨胀

**记忆注入**：`buildContext(sessionId, query)` 四层全部组装成 `MemoryContext`，最终由 `MemoryContextPromptFormatter` 格式化注入系统 Prompt

#### ④ 编排器（OrchestratorAgent）

两阶段决策：

```java
// 路由优先级
1. 文本匹配（question 含 profile.name 或 profile.description）
2. 意图类型匹配（IntentAnalyzer.analyze → preferredType → matchProfileByType）
3. 兜底 ReActAgent
```

多任务计划执行（`executeStructured`）：

- `OrchestratorTaskPlanner.plan()` 拆解任务为 `OrchestrationPlan`（含 `executionPhases`）
- 按阶段顺序执行，**每阶段任务完成后把结果填入 `sharedContext`**，下游任务通过 `collaborationManager.injectSharedContext` 读取上游输出
- 失败任务检查 `blocking dependencies`，上游失败 → 下游跳过，记录 `skippedTasks`
- 最终由 `ResultIntegrator.integrate` 把多任务答案合并成最终输出

#### ⑤ 模型重试与熔断（ModelRetryExecutor）

```
MAX_RETRIES = 3，指数退避（1s → 2s → 4s）
FAILURE_THRESHOLD = 3，连续 3 次失败触发熔断
CIRCUIT_OPEN_MS = 60_000ms，60 秒内拒绝所有请求

不重试（isRetriable=false）：401 / 403 / 404
  → 避免错误的 Key / URL 消耗大量配额
重试：429 / 5xx / 网络超时
  → 429 是限流，应该等等再试
```

**ModelHttpException** 携带 `statusCode + retriable` 两个字段，`ModelRetryExecutor` 统一判断，不在业务层散落重试逻辑

**熔断后恢复**：60 秒后下次请求检查 `circuitOpenUntil.get(modelKey)`，如果时间已过则自动放行，成功后 `recordSuccess` 清除计数器

#### ⑥ 技能系统（SkillManager）

- 注册用 `ReadWriteLock`：写锁注册/注销，读锁匹配/执行，在并发请求下安全
- 启动时只调 `registerSkillWithoutVectorRefresh`（不写 Milvus），创建/更新/启用才调 `registerSkill`（刷 Milvus 向量）
  - 原因：向量写入是重型 IO，启动时批量刷会让应用延迟 10-30 秒，且无必要
- 斜杠命令：`processWithCommand("/分析 xxx")` → 解析命令名 → 找对应 Skill → 执行，未命中当正常问题路由给 Agent

---

### Data Agent 魔鬼追问

#### Q：ReAct 8 轮上限打到了会怎样？代码怎么处理？

超 8 轮后 `appendMaxIterationAnswer` 被调用：遍历消息历史中所有 `AiMessage`，调 `responseParser.cleanAssistantAnswer` 清洗标记，拼接成兜底答案。流式模式额外 `emitReflection` 推一条"建议缩小问题范围"的提示给前端。**不会返回空**。

#### Q：RRF 里的 RANK_CONSTANT=60 是什么意思？为什么是 60？

`1/(60+rank)` —— 60 是平滑参数，防止第 1 名（1/(60+1)=0.016）和第 60 名（1/(60+60)=0.0083）相差太大。如果常量为 0，rank1 是无穷大，完全压制其他来源。60 是学术论文推荐值，实验结果比 10、100 都好。

#### Q：RAG 命中率低怎么排查？

1. 看 `EnhancedRagPipeline.getLastTrace()`，比对 `vectorRawCount` / `fullTextRawCount` / `fusionCandidateCount` / `finalMatchCount`，哪一步漏了
2. `vectorRawCount=0`：Milvus 连不上或 collection 未 load
3. 命中但重排后被过滤：`min-score` 配置过高，可调低
4. 命中且高分但答案不对：父级上下文没补全，检查 `parentContext` 字段是否为空

#### Q：模型热切换 5 秒内生效，具体怎么做到的？

```java
// McpModelService
@EventListener
public void onModelConfigChange(ModelConfigChangeEvent event) {
    modelClientRegistry.refreshModelCache(event.getModelId());
}
```

`ModelConfigService` 更新 DB 后发 Spring 事件 → `McpModelService` 的 `@EventListener` 收到 → 调 `ModelClientRegistry.refreshModelCache` 清掉对应 `modelId` 的缓存 → 下次请求重新从 DB load 配置构建 `ChatLanguageModel`。全程同一进程内，无网络调用，毫秒级生效。API Key 落库用 `EncryptedStringConverter` 做 AES 加密，读时自动解密。

#### Q：三层记忆选哪层？由谁决定？

由调用方（ReAct 工具 / 前端 API）传 `MemoryTier` 枚举。
- `WORKING`：直接存 Redis，**不走数据库，不消耗 quota**，只管当前 session
- `SHORT_TERM`：写 MySQL，按时间倒序召回最近 N 条（N=5），适合"上次讲到哪了"
- `LONG_TERM`：写 MySQL + 写 Milvus embedding，相似性召回（topK=5），适合"我的偏好/历史结论"
- 写长期记忆后**异步**触发 `UserProfileMemoryRefreshService.refresh`，把用户兴趣/风格凝炼成 Profile

#### Q：Orchestrator 多任务计划中，任务 A 失败，任务 B 依赖 A，怎么处理？

`collaborationManager.findBlockingDependencies(task, resultIndex)` 检查 `task.dependencies` 里每个 id 是否在 `resultIndex` 中且 success=true；有一个不满足就把当前任务包成 `SkipResult` 写进 `taskResults`，`sharedContext.put("skippedTasks", ...)` 记录。最终 `hasFailure=true` 或 `skippedTasks` 非空时 `OrchestratorResult.success=false`，前端可感知链路断了哪里。

#### Q：熔断和限流都有，429 触发哪个？

429 是可重试的（`retriable=true`），`ModelRetryExecutor` 会指数退避重试最多 3 次，**不触发熔断**。只有连续 3 次最终失败（重试耗尽后仍失败）才调 `recordFailure`，计数到阈值才开熔断。

设计意图：429 通常是短暂限流，等一等就好；熔断是应对模型接口本身挂掉的场景，要快速失败避免线程堆积。

#### Q：AI 协作开发，哪些是你做的，哪些 AI 做的？

**我独立设计**：
- 5 层路由架构、Orchestrator 编排决策链、三层记忆分层
- RRF 融合参数调优（为什么 60 而不是 10）
- 熔断策略：401/403/404 不重试，429/5xx 重试（这个判断需要理解 HTTP 语义）
- 租户隔离双层方案（MySQL + Milvus filter 必须同步）
- 启动时不刷 Milvus 向量的决策（避免启动慢、向量库抖动）

**AI 协助**：
- DTO/Repository/Mapper 等结构化样板代码
- Service 初稿，我 review 改关键路径
- Javadoc 和注释

#### Q：多租户隔离怎么保证向量层也不串？

MySQL 层：所有查询带 `AND tenant_id = ?`，`SecurityContextHelper.getCurrentTenantId()` 线程安全获取。

Milvus 层：每次 `MilvusEmbeddingStore.search` 追加 filter `tenant_id == "xxx"`，向量相似度再高也只在本租户内命中。

**不用分库分表或多 collection**：单 collection 加 metadata filter，成本低、维护简单；数据量超亿条再考虑 partition by tenant。

---

## 2.4 酒店商品检索服务（goods-ds）

### 一句话定位

> "千万级 SKU 的酒店商品实时检索引擎——800MB~1.2GB 全量索引常驻内存，三层 Handler 链完成搜索→计价→促销，Kafka 秒级增量保证数据实时性，Thrift 服务 P99 <100ms。"

### 整体架构

```
下游搜索调用（Thrift）
  ↓
selectorThreads=8 NIO Selector + workerThreads=64 业务线程池
  ↓
DsSearchHandler（Handler 责任链）
  搜索阶段：SRoomSearchHandler → SHotelSearchHandler
  计价阶段：Bargaining → PromotionBeat → BonusBack → Coupon → ResourceControl
  后处理：ProductPostSearchHandler（排序/分页/序列化）
  ↓
内存索引（Index 对象，volatile 指针，切换时零停机）
```

---

### 五个核心模块深挖

#### ① 三层索引模型（内存中的核心数据结构）

```
Index（顶层容器，单例，volatile 引用）
├── MHotelTable   → MHotel（物理酒店，200~300万）→ MRoom
├── SHotelTable   → SHotel（售卖视角，OTA维度）  → SRoom → Product（500~800万）
├── PromotionTable / PolicyTable / ActivityTable（促销/政策/活动）
├── PriceTable（180天价格点，1000万+）
├── InventoryTable（库存，16 shard）
└── 50+ 专业 Table（黑名单、优惠券、红包、权益、资金池...）
```

**关键容量（代码硬编码）**：

```java
// 供应商信息：35 万条
Map<Integer, Supplier> supplierInfoMap = new CustomMap<>(35_0000, DsInt2ObjectOpenHashMap.class);
// 票券规则：20 万条
Map<Long, TicketPromotionRuler> ticketPromotionRulerMap = new CustomMap<>(20_0000, ...);
// 价格缓存天数：180 天（价格点超 1000 万）
public static final int MAX_PRICE_DAYS = 180;
// 库存分片数：16
public static final int inventory.shardcount = 16;
```

MHotel/SHotel/Product 三层分离是因为同一物理酒店可能在携程、去哪儿、艺龙等多个 OTA 分别有"售卖视角"（SHotel），计价逻辑按售卖维度独立处理，不污染物理维度。

#### ② 索引加载（两种模式 + 热更新）

**全量加载两种模式**，由 `ds.index.load-type` 控制：

| 模式 | 方式 | 耗时 | 场景 |
|------|------|------|------|
| SNAPSHOT | 直接反序列化快照文件到内存 | 20~30 秒 | 7.0+ 默认，快速重启 |
| DISK | 按维度 Loader 递归加载 PB 文件 | 2~5 分钟 | 降级或初次建索引 |

**热更新切流（零停机双 Buffer）**：

```
IndexCoordinator 四队列流转：
ReadyQueue → CanLoadQueue → RealLoadQueue → CompleteQueue

1. 后台创建 newIndexBuilder，加载新全量
2. notifyReady → notifyCanLoad → notifyRealLoad
3. 原子切换：volatile indexBuilder = newIndexBuilder
4. 旧 indexBuilder 异步释放内存
```

搜索线程一直读 `volatile indexBuilder.index`，切换瞬间拿到新指针，旧指针继续服务直到 GC 回收，**全程零停机**。

#### ③ Handler 链（责任链，8 步计价）

```java
// Context 作为"数据总线"贯穿整个链
HotelContext {
    MHotel mHotel;
    List<SHotel> sHotels;
    PromotionCompatibleConfig promotionCompatibleConfig; // 各 Handler 写入中间结果
    ResourceControlConditionTable resourceControlConditionTable;
    StringBuilder debugLog;   // 每个 Handler 追加日志，排查时完整还原
}
ProductContext {
    Product product;
    PricePair finalPrice;     // 最终价格（底价/售价）
    List<ProductPromotion> promotions;
}
```

**搜索主流程（有序）**：

```
① SRoomSearchHandler    —— 过滤不可售房型
   └─ CompatibleHandler —— 检查促销相容性（互斥/叠加规则）
② SHotelSearchHandler   —— 房型聚合成酒店
③ BargainingHandler     —— 议价（谈判价格）
④ PromotionBeatHandler  —— 促销定价（促销价 < 底价则采用）
⑤ BonusBackHandler      —— 红包返现计算
⑥ HotelXCalcCouponPriceHandler —— 优惠券折扣
⑦ AdvanceCouponSelectProductHandler —— 预售券选品
⑧ ResourceControlHandler—— 资源控制（价格天花板/地板）
⑨ ProductPostSearchHandler —— 排序/分页/序列化
```

每个 Handler 无状态，只读写 Context，**异常被 try-catch 吃掉后写 debugLog**，不中断链，保证高可用（降级返回）。

#### ④ Kafka 增量更新 + 限流（令牌桶）

**监听 24+ 个 Topic**（价格/库存/促销/供应商/秒杀/免房等），Kafka consumer poll 超时 **100ms**，消费后直接更新内存中对应 Table 字段，`synchronized(INDEX_LOCK)` 保证写原子。

**每类消息独立令牌桶**（Guava RateLimiter，默认 **100 QPS**）：

```java
public class PriceQpsLimiter extends BaseUpdateLimiter {
    static RateLimiter rateLimiter = RateLimiter.create(100); // 初始 100QPS
    
    public static double acquire(int n) {
        updateQpsLimit(rateLimiter, PRICE_MSG_TYPE); // 从 HotSwitch 动态读取最新配置
        return rateLimiter.acquire(n);  // 令牌不足时 BLOCK（背压，不丢消息）
    }
}
```

**HotSwitch 动态调整**：运维后台改配置立刻生效，无需重启。消费线程阻塞不影响搜索线程——两者完全解耦。

**全量与增量协调**：

```
T0        全量开始加载（SNAPSHOT 20s 或 DISK 3min）
T0        Kafka consumer 启动（seekToEnd，不消费历史堆积）
T0+加载完  发布 FullIndexLoadedEvent
T0+30s    追赶增量（消费全量时间窗口内的 Kafka 消息）
T0+完成    秒级更新持续消费，正式对外服务
```

#### ⑤ Thrift 服务端（NIO + 线程池）

```
TNonblockingServerSocket（监听 12180 端口）
  ↓
selectorThreads=8 个 NIO Selector（Java NIO，事件驱动）
  ↓
workerThreads=64 的 ArrayBlockingQueue(10000) 业务线程池
  ↓
GoodsInvocation（Runnable）→ ProductSearchServiceImpl → Handler 链
```

协议：**TCompactProtocol**（紧凑二进制，比 JSON 省 50~70% 空间）
传输：**TFastFramedTransport**（帧式压缩传输）

**队列满拒绝**：`Statistical.incrRejectedCount(1)` + ActionLog 告警，客户端收到 `TTransportException` 后重试或切实例。

**性能指标**：P50=20~40ms，P90=60~80ms，**P99 <100ms**，单机吞吐 2000~3000 QPS

---

### goods-ds 魔鬼追问

#### Q：内存 1GB 的索引怎么热更新不停机？期间请求打哪里？

`volatile indexBuilder` 指针：搜索线程每次请求直接读当前指针，不加锁。后台新建 `newIndexBuilder` 完整加载新全量，加载完成后一行原子赋值 `indexBuilder = newIndexBuilder`——Java volatile 写保证可见性，下一个搜索请求立刻看到新索引。旧 `indexBuilder` 没有被任何线程持有后 GC 回收内存。整个过程对业务线程透明。

#### Q：Kafka 消息来不及处理，100 万条堆积怎么办？

令牌桶是背压，消费线程会减速但不停止。消费线程和搜索线程完全独立，堆积只影响"索引更新延迟"，不影响搜索可用性——最坏情况用户看到的价格延迟几秒。同时监控消费 lag，超阈值告警 → 运维通过 HotSwitch 临时提升 QPS 上限或扩容 consumer。

#### Q：Handler 链里某一步计价抛异常，会怎样？

每个 Handler try-catch，异常写 `hotelCtx.debugLog`，不 rethrow，继续执行后续 Handler。比如 `BonusBackHandler`（红包计算）异常，红包金额为 0 但其他计价继续正常运行，最终返回无红包的价格结果。这是"降级优先于失败"的设计原则——宁可少给优惠，不能整个搜索 500。

#### Q：MHotel 和 SHotel 有什么区别？为什么要两层？

MHotel 是物理维度（Mapping Hotel）：一个真实酒店只有一个 MHotel，存静态信息（位置/星级/设施）。SHotel 是售卖维度（Selling Hotel）：同一物理酒店在携程、艺龙、去哪儿各有一个 SHotel，存渠道专属信息（OTA 政策/渠道价格/供应商库存）。计价、促销、库存都在 SHotel 维度独立处理，各 OTA 互不干扰。

#### Q：Thrift 队列设了 10000，为什么不能无限大？

无限大队列会掩盖真实过载——当后端处理速度跟不上入队速度时，队列一直增长，内存耗尽才 OOM，并且已入队的请求等了很久才被处理，用户早就超时重试了（重试又入队，正反馈雪崩）。有界队列 10000 在真正过载时快速拒绝，客户端立刻得到错误重试其他实例，**fail fast 比 slow fail 好**。

#### Q：TCompactProtocol 比 JSON 省在哪里？

JSON 是文本格式：字段名每次都传（"supplierId":12345），数字是字符串，有大量冗余引号和逗号。TCompactProtocol 是二进制：字段用编号（tag）标识，整数用 ZigZag 变长编码（小整数占 1-2 字节），字符串只传内容不传名称。典型场景省 50~70%，网络传输和 GC 压力都降低。

## 2.5 跨项目通用追问

#### Q：Kafka 消费者遇到过什么坑？

**坑 1 重复消费**：prepare 服务 offset 没正确提交 → 手动提交（`enable.auto.commit=false`）+ 业务成功后 `commitSync()` + Redis SET NX 幂等。
**坑 2 积压**：`max.poll.records` 调大 + 内部多线程并发处理。

#### Q：监控体系怎么建的？

三层：

1. 基础指标（Grafana + 夜鹰）
2. 业务指标（goods-dashboard + IndexMonitorUtil）
3. 三通道告警（AlertUtil 统一封装）

#### Q：深分页慢怎么解决？

游标分页：`WHERE id > :lastId AND condition ORDER BY id LIMIT 5000`，复杂度 O(offset) → O(batchSize)。
`PullIndex/PullIndexs` 接口的 `getStartIndex/getEndIndex` 就是这个思路。

#### Q：让你重新做技术选型，会怎么改？

1. **文件传输**：SCP 改 Kafka，去掉文件中间态，根治冗余文件
2. **任务调度**：用 XXL-Job / Elastic-Job 替代 schedule，扩缩容更友好
3. **prepare Redis 幂等 TTL**：60s 与消息最大 RT 对齐

---

# 第 3 章 Redis 八股 + 追问链

## 3.1 常用数据类型 & 项目场景

- String / Hash / List / Set / ZSet
- 商品库用 String/Hash 存 PB 序列化数据，下游检索服务直读

## 3.2 缓存雪崩 / 击穿 / 穿透

**雪崩**：大量 key 同时过期 → TTL 加随机值 + 集群高可用
**击穿**：热点 key 过期 → 互斥锁 / 逻辑过期 / 预加载
**穿透**：查不存在 key → 布隆过滤器 / 缓存空值

### 追问：互斥锁怎么实现？会死锁吗？

```java
Boolean locked = redis.setIfAbsent("lock:hotkey", "1", 30, TimeUnit.SECONDS);
if (locked) {
    try { data = db.query(); redis.set(key, data, ttl); }
    finally { redis.delete("lock:hotkey"); }
} else {
    Thread.sleep(50);
    return redis.get(key);
}
```

- 不会死锁，30s 过期兜底
- 释放锁用 Lua 判断 + 删除原子：

```lua
if redis.call("GET", KEYS[1]) == ARGV[1] then
    return redis.call("DEL", KEYS[1])
end
return 0
```

### 追问：互斥锁 vs 逻辑过期？


|     | 互斥锁   | 逻辑过期 |
| --- | ----- | ---- |
| 一致性 | 强     | 弱    |
| 可用性 | 低（等锁） | 高    |
| 复杂度 | 简单    | 复杂   |


我们数据拉取用**预加载**，从根本上避免击穿。

## 3.3 Redis 为什么快？

1. 纯内存
2. 单线程，无锁
3. epoll I/O 多路复用
4. 高效数据结构（SDS、跳表、ziplist）

### 追问：单线程不会瓶颈吗？

不会，CPU 不是瓶颈，网络 IO 和内存带宽才是。Redis 6.0+ 引入多线程 IO（网络读写多线程，命令执行仍单线程）。

### 追问：epoll 为什么快？

- select/poll：每次拷贝所有 fd，遍历检查，O(n)，1024 上限
- epoll：`epoll_ctl` 注册到红黑树，内核回调，`epoll_wait` 只返回就绪 fd，O(1)

## 3.4 持久化 RDB vs AOF


|     | RDB      | AOF    |
| --- | -------- | ------ |
| 方式  | 快照（fork） | 追加日志   |
| 安全  | 可能丢数据    | 最多丢 1s |
| 恢复  | 快        | 慢      |
| 大小  | 小        | 大      |


### 追问：混合持久化？

4.0 引入 `aof-use-rdb-preamble yes`：AOF 头部 RDB 全量 + 后面增量命令。重启快 + 安全，生产推荐。

### 追问：BGSAVE fork 代价？

1. **Copy-on-Write**：父子共享内存页，主进程写时复制，写多翻倍
2. **fork 本身耗时**：内存大复制页表慢，10GB 可能几百 ms STW

大内存实例（32GB+）避免业务高峰触发 BGSAVE。

## 3.5 集群方案

- 主从：手动切
- 哨兵：自动故障转移
- Cluster：16384 slot 分片 + 自动故障转移

### 追问：为什么 16384 个 slot？

1. 心跳包 bitmap：16384/8 = 2KB（65536 是 8KB 太大）
2. 设计上最多约 1000 主节点，16 slot/节点够用
3. CRC16 & 16383 位运算高效

## 3.6 分布式锁

- `SET key value NX PX 30000`
- Lua 脚本释放
- Redisson WatchDog 自动续期
- Redlock：N/2+1 节点都加锁；Martin Kleppmann 质疑过其安全性，强一致用 ZooKeeper

## 3.7 大 Key / 热 Key

- 大 Key：`--bigkeys` 检测 → 拆分 / 压缩
- 热 Key：`--hotkeys` 检测 → 本地缓存（Caffeine） + 多副本 + 分片打散

## 3.8 缓存一致性（Cache Aside）

- 读：先缓存，没有读 DB 回填
- 写：先更 DB，再**删**缓存（不是更新）
- 进阶：延迟双删 / binlog + Canal

---

# 第 4 章 MySQL 八股 + 追问链

## 4.1 索引

- B+ 树：叶子节点存数据 + 双向链表，支持范围
- vs B 树：B 树数据散落所有节点，范围查询要中序遍历
- vs Hash：不支持范围、排序、前缀

### 追问：聚簇 vs 二级索引？

- 聚簇：主键索引，叶子存完整行，一张表只有一个
- 二级：叶子存主键值，需要回表；查询字段全在索引 = **覆盖索引**（`Using index`）
- 主键建议自增 int；UUID 随机写导致页分裂

### 追问：覆盖索引项目用过？

联合索引 `(dimension_id, status, update_time)`，只 SELECT 这几个字段，避免回表，千万级数据效果明显。

### 追问：索引下推 ICP？

5.6 引入。联合索引 + 回表场景下：

- 无 ICP：联合索引只用最左过滤，剩余条件在 Server 层过滤，回表多
- 有 ICP：剩余过滤在存储引擎层（索引扫描时）做，只有通过的才回表
- `EXPLAIN` 显示 `Using index condition`

### 追问：最左前缀

联合索引 (a, b, c)：

- `WHERE a=1 AND b=2` ✓
- `WHERE b=2` ✗（跳过 a）
- `WHERE a=1 AND b>2 AND c=3`：c 失效（b 是范围）

### 追问：索引失效场景

1. 违反最左前缀
2. 索引列做函数/计算
3. 隐式类型转换（varchar 列 = int）
4. `LIKE '%xx'` 开头通配
5. OR 含非索引列
6. NOT IN / != 可能不走

## 4.2 事务隔离级别


| 级别           | 脏读  | 不可重复读 | 幻读              |
| ------------ | --- | ----- | --------------- |
| RU           | ✓   | ✓     | ✓               |
| RC           | ✗   | ✓     | ✓               |
| **RR（默认）**   | ✗   | ✗     | ✓（MVCC+间隙锁基本解决） |
| Serializable | ✗   | ✗     | ✗               |


## 4.3 MVCC

每行隐藏 `trx_id` + `roll_pointer`。
快照读靠 ReadView 判可见性，当前读加锁。

### 追问：ReadView 可见性规则？

ReadView 字段：`m_ids`（活跃事务列表）、`min_trx_id`、`max_trx_id`、`creator_trx_id`。

判断行的 `trx_id`：

1. == creator → 自己改的，可见
2. < min → 之前提交，可见
3. >= max → 之后开始，不可见
4. min ≤ trx_id < max → 看是否在 m_ids 里，在 → 未提交 → 不可见

不可见沿 undo log 找更早版本。

RC 每次 SELECT 新建 ReadView；RR 第一次 SELECT 后复用。

### 追问：MVCC 解决幻读了吗？

- 快照读：MVCC 完全解决
- 当前读（FOR UPDATE / UPDATE / DELETE）：读最新版本，靠**间隙锁 + Next-Key Lock** 解决
- "基本"解决，特殊场景仍可能幻读，别说"完全"

## 4.4 锁

- 表锁：MyISAM 用，InnoDB 也有意向锁
- 行锁：InnoDB，加在**索引**上
- 间隙锁：锁索引间间隙
- Next-Key Lock：行锁 + 间隙锁

### 追问：行锁什么时候退化表锁？

1. 没走索引（全表扫描每行都锁）
2. 隐式类型转换索引失效
3. 索引列做函数

排查：`SHOW ENGINE INNODB STATUS` → TRANSACTIONS 段 → EXPLAIN。

## 4.5 慢 SQL 排查

1. 慢查询日志 `slow_query_log`，`long_query_time=2`
2. EXPLAIN：type（ALL 最差，ref/range/eq_ref 好）、key、rows、Extra（filesort/temporary）
3. 优化：加索引、覆盖索引、避免函数操作、深分页游标化、必要时分库分表

## 4.6 深分页优化

```sql
-- 慢
SELECT * FROM order WHERE status=1 ORDER BY id LIMIT 1000000, 10;
-- 优化 1：游标
SELECT * FROM order WHERE status=1 AND id > :lastId ORDER BY id LIMIT 10;
-- 优化 2：子查询定位
SELECT * FROM order WHERE id IN (
    SELECT id FROM order WHERE status=1 ORDER BY id LIMIT 1000000, 10);
-- 优化 3：JOIN
SELECT o.* FROM order o
JOIN (SELECT id FROM order WHERE status=1 ORDER BY id LIMIT 1000000, 10) t
  ON o.id=t.id;
```

## 4.7 死锁排查

- `SHOW ENGINE INNODB STATUS` → LATEST DETECTED DEADLOCK
- 避免：固定加锁顺序、缩短事务、隔离级别

## 4.8 PB vs JSON

- 二进制小 3-10x
- 序列化快 5-10x
- 强 schema、跨语言
- 代价：可读性差

---

# 第 5 章 JVM 八股 + 追问链

## 5.1 内存结构

- 堆：新生代（Eden + S0 + S1） + 老年代
- 方法区/元空间（JDK8+）
- 栈：每线程栈帧
- PC 寄存器
- 本地方法栈

## 5.2 OOM 排查

1. `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/dump.hprof`
2. MAT 看 dominator tree
3. 定位代码 → 流式处理

## 5.3 G1 vs CMS


|      | CMS         | G1                  |
| ---- | ----------- | ------------------- |
| 算法   | 标记-清除       | 标记-整理（Region）       |
| 碎片   | 有           | 无                   |
| 停顿   | 不精确         | 可设 MaxGCPauseMillis |
| 并发失败 | 可能退化 Serial | Mixed GC 可控         |
| 适合   | 低停顿，堆 <4G   | 大堆（4G+）             |


### 追问：G1 Region 解决了什么？

- 堆划成 N 个等大 Region（1-32MB），动态角色（Eden/Survivor/Old/Humongous）
- 优先回收垃圾最多的 Region（GC First 由来）
- Mixed GC 增量回收老年代，停顿可控
- 无碎片

### 追问：对象一定在堆上吗？逃逸分析？

不一定。JVM 逃逸分析：

1. **栈上分配**：不逃逸方法的对象在栈帧上
2. **标量替换**：拆成基本类型
3. **同步消除**：不逃逸对象的 synchronized 去掉

意义：循环临时对象 JIT 优化后无 GC 压力。

### 追问：怎么确认 GC 参数有效？

三步：

1. **GC 日志**：对比 Full GC 次数、STW 时间、吞吐量
2. **jstat**：`jstat -gc <pid> 1s`，看 OU 趋势、FGC 次数、FGCT
3. **业务指标**：接口 P99、吞吐量

我的实际：Full GC 几秒一次 → 几小时一次（频率降 99%+）。

## 5.4 类加载（双亲委派）

### 追问：为什么双亲委派？什么情况打破？

**意义**：安全（防替换核心类）+ 唯一性
**打破**：

1. SPI（JDBC Driver）：DriverManager 在 Bootstrap，需加载厂商 Driver，通过线程上下文 ClassLoader
2. 热部署 / OSGi
3. Tomcat WebApp ClassLoader

---

# 第 6 章 并发 / JUC + 追问链

## 6.1 AQS 原理

- `state`（volatile int）+ CLH 队列 + CAS
- 加锁：CAS state 0→1 成功持有，失败入队 park
- 释放：unpark 唤醒前驱
- 金句：AQS 把同步抽象成 state + 队列管理，独占/共享、公平/非公平只是语义不同

## 6.2 CAS + ABA

- CMPXCHG 指令保证原子
- ABA：值 1→2→1 中间变化丢失 → `AtomicStampedReference` 加版本号 / DB 乐观锁加 version 字段

## 6.3 synchronized 锁升级

```
无锁 → 偏向锁 → 轻量级锁 → 重量级锁
```

- 偏向锁：对象头记线程 ID
- 轻量级：CAS 自旋
- 重量级：OS Mutex

### 追问：static vs 实例方法 synchronized 区别？

- 实例方法：锁 this
- 静态方法：锁 Class
- 坑：一个调 instance，一个调 static，**锁不同**不互斥

### 追问：锁升级能降级吗？

**不能**。JDK 15 默认关闭偏向锁（`-XX:-UseBiasedLocking`），因为撤销开销大。

## 6.4 ConcurrentHashMap 1.7 vs 1.8


|     | 1.7                     | 1.8                |
| --- | ----------------------- | ------------------ |
| 结构  | Segment[] + HashEntry[] | Node[] + 链表/红黑树    |
| 锁粒度 | Segment                 | 桶（Node）            |
| 实现  | ReentrantLock           | synchronized + CAS |


1.8 改回 synchronized：偏向/轻量级锁优化后性能不输 ReentrantLock，JIT 友好。

## 6.5 ThreadLocal 内存泄漏

- key 弱引用，被 GC → key null，value 强引用泄漏
- 线程池场景特别严重
- 解决：用完 `remove()`，try-finally

## 6.6 线程池 7 参数

```java
new ThreadPoolExecutor(
    int corePoolSize,
    int maximumPoolSize,
    long keepAliveTime,
    TimeUnit unit,
    BlockingQueue<Runnable> workQueue,
    ThreadFactory threadFactory,
    RejectedExecutionHandler handler);
```

**流程**：核心 → 队列 → 非核心 → 拒绝
**拒绝策略**：Abort / CallerRuns / Discard / DiscardOldest
**队列**：LinkedBlockingQueue（慎用无界）/ ArrayBlockingQueue（推荐）/ SynchronousQueue / PriorityBlockingQueue

### 追问：核心线程数怎么算？

- CPU 密集：核数 + 1
- IO 密集：核数 × (1 + 等待/计算)，经验值 × 2
- 实际：公式估算 + 令牌桶兜底 + 压测调整

### 追问：CallerRunsPolicy 什么时候用？

- 调用者线程执行任务，自然背压
- 适合生产者-消费者，不能丢任务的场景
- 不适合主线程/关键业务线程

## 6.7 volatile

- 保证可见性 + 有序性
- **不保证原子性**：`count++` 三步（读、+1、写回），多线程仍有问题
- 原子用 `AtomicInteger` 或 synchronized

## 6.8 ThreadLocal 父子线程

- 普通 ThreadLocal 不传递
- `InheritableThreadLocal`：子线程**创建时**复制，但创建后改不同步；线程池场景失效
- 线程池场景：`TransmittableThreadLocal`（阿里）或手动 MDC 透传

## 6.9 Synchronized vs ReentrantLock


|     | synchronized | ReentrantLock     |
| --- | ------------ | ----------------- |
| 实现  | JVM monitor  | AQS               |
| 可中断 | ✗            | lockInterruptibly |
| 超时  | ✗            | tryLock(timeout)  |
| 公平锁 | ✗            | ✓                 |
| 条件  | wait/notify  | newCondition（多个）  |
| 释放  | 自动           | 手动 finally        |


---

# 第 7 章 Spring / MyBatis + 追问链

## 7.1 Bean 生命周期

```
实例化 → 属性赋值 → Aware → BeanPostProcessor.Before
→ 初始化方法（@PostConstruct → InitializingBean → @Bean(initMethod)）
→ BeanPostProcessor.After（AOP 在此织入！）
→ 使用 → 销毁
```

## 7.2 AOP 原理

- 有接口 → JDK 动态代理（`Proxy.newProxyInstance`）
- 无接口 → CGLIB（字节码生子类）
- Spring Boot 2.x 默认 CGLIB（即使有接口）

### 追问：JDK 和 CGLIB 各自局限？

- JDK：必须有接口
- CGLIB：final 方法/类不能代理 → @Transactional final 方法失效

### 追问：AOP 在哪里织入？

`BeanPostProcessor.postProcessAfterInitialization` 阶段，`AbstractAutoProxyCreator` 判断需要代理就创建代理对象替换。

这也解释了**类内部 this.method() 调用不走代理**。

## 7.3 循环依赖三级缓存

- singletonObjects（一级）：完整 Bean
- earlySingletonObjects（二级）：半成品
- singletonFactories（三级）：ObjectFactory，产生代理

**为什么三级**：保证循环依赖时拿到的是代理对象而非原始对象，且只创建一次。
**只解决 setter**，构造器注入不行。

## 7.4 SpringBoot 自动装配

1. `@SpringBootApplication` 含 `@EnableAutoConfiguration`
2. `@Import(AutoConfigurationImportSelector)`
3. 加载 `META-INF/spring.factories`（2.x）或 `AutoConfiguration.imports`（3.x）
4. `@ConditionalOnXxx` 按需生效

## 7.5 Prototype 注入 Singleton 坑

Singleton 只创建一次，注入的 Prototype 也只一次，每次新建语义失效。
**解决**：

1. 手动 getBean
2. 注入 `ObjectFactory<T>`，每次 `factory.getObject()`
3. `@Lookup` 注解方法

## 7.6 MyBatis

### 一级 vs 二级缓存

- 一级：SqlSession，默认开，Session 关失效
- 二级：Mapper namespace，需 `<cache/>`，要 Serializable
- 分布式环境都有问题，生产宁愿用 Redis

### #{} vs ${}

- `#{}`：预编译 PreparedStatement，防 SQL 注入
- `${}`：字符串拼接，注入风险，仅动态表名/排序字段用

---

# 第 8 章 Kafka 八股 + 实战追问

## 8.1 为什么高吞吐

1. 顺序写磁盘
2. 零拷贝（sendfile/mmap）
3. 批量压缩
4. 分区并行

## 8.2 消息不丢

- 生产者：`acks=all` + 重试
- Broker：`replication.factor=3`
- 消费者：手动 commit offset

## 8.3 重复消费 → 幂等（消息 ID + Redis NX 或 DB 唯一索引）

## 8.4 Rebalance

- 触发：消费者变化 / 分区变化 / 订阅变化
- 流程：Coordinator 选 Leader → 制定方案 → 同步
- 影响：STW，停止消费
- 避免：心跳保持、`max.poll.records` 不过大、StickyAssignor

## 8.5 顺序消费

- 全局：单 partition + 单消费者，性能差
- 分区：key 哈希（如 orderId）→ 同一 partition
- 业务保证：不能用线程池异步

## 8.6 HW / LEO

- LEO：分区下一条消息位置
- HW = min(所有 ISR 的 LEO)
- 消费者只能消费 HW 之前

---

# 第 9 章 网络协议

## 9.1 TCP 三次握手 / 四次挥手

```
客户端                          服务端
  |        SYN, seq=x            |
  |----------------------------->|
  |    SYN+ACK, seq=y, ack=x+1   |
  |<-----------------------------|
  |       ACK, ack=y+1           |
  |----------------------------->|
```

### 追问：为什么 3 次不 2 次？

防止失效的连接请求晚到造成资源浪费；3 次让双方都确认对方收发能力正常。

### 追问：四次挥手为什么 4 次？

TCP 全双工，关闭时双方都要单独说"发完了"，ACK 和 FIN 分两次。

### 追问：第二次握手为什么发 SYN？

让客户端确认服务端的发送/接收能力都正常，且服务端愿意建连。

### TIME_WAIT 2MSL

- 确保最后一个 ACK 能到
- 让本连接遗留报文消失

线上 TIME_WAIT 多：`tcp_tw_reuse` 复用 / 客户端连接池；不要开 `tcp_tw_recycle`。

## 9.2 TCP vs UDP


|      | TCP | UDP |
| ---- | --- | --- |
| 连接   | 有   | 无   |
| 可靠   | 是   | 否   |
| 有序   | 是   | 否   |
| 拥塞控制 | 有   | 无   |
| 头    | 20B | 8B  |


## 9.3 HTTP

- 1.0 短连接 / 1.1 Keep-Alive、Host、管线 / 2.0 二进制分帧、多路复用、Header 压缩、Push / 3.0 QUIC 基于 UDP

### HTTPS 握手

1. ClientHello（R1 + 加密套件）
2. ServerHello + 证书（R2 + 公钥）
3. 客户端验证证书
4. 生成 Pre-Master Secret，公钥加密发回
5. 服务端私钥解密
6. R1+R2+Pre-Master 推导对称密钥
7. 后续对称加密

TLS 1.3 改进为 1-RTT，支持 0-RTT。

### 状态码

- 2xx 200 201 204
- 3xx 301 302 304
- 4xx 400 401 403 404 429
- 5xx 500 502 503 504
- 401 没登录 vs 403 没权限

## 9.4 零拷贝

- 传统 IO：4 拷贝 4 切换
- sendfile：2 拷贝 2 切换，磁盘 → PageCache → 网卡
- Kafka、Nginx 都用

## 9.5 IO 多路复用


|     | select | poll | epoll    |
| --- | ------ | ---- | -------- |
| 结构  | bitmap | 链表   | 红黑树+就绪链表 |
| 上限  | 1024   | 无    | 无        |
| 方式  | 遍历     | 遍历   | 回调返回就绪   |
| 性能  | O(n)   | O(n) | O(1)     |


epoll 三函数：`epoll_create` / `epoll_ctl` / `epoll_wait`。

---

# 第 10 章 操作系统

## 10.1 进程 / 线程 / 协程


|     | 进程  | 线程   | 协程         |
| --- | --- | ---- | ---------- |
| 资源  | 独立  | 共享   | 共享 + 用户态调度 |
| 切换  | 大   | 中    | 极小         |
| 通信  | IPC | 共享内存 | 直接         |
| 数量  | 数百  | 数千   | 百万         |


Java 21 虚拟线程 ≈ 协程。

## 10.2 用户态 vs 内核态

- 切换触发：系统调用、异常、中断
- 高性能要减少切换（零拷贝原理）

## 10.3 死锁四条件

互斥 / 持有等待 / 不可剥夺 / 循环等待。破坏任一即可避免。

---

# 第 11 章 @Transactional 失效与传播

## 11.1 失效 7 场景

1. 非 public 方法
2. **同类内部调用**（this.b() 走原对象不走代理）
3. 异常类型不匹配（默认只 RuntimeException/Error）→ `rollbackFor=Exception.class`
4. try-catch 吞异常 → 手动 `setRollbackOnly()`
5. 引擎不支持事务（MyISAM）
6. 多线程
7. 类没被 Spring 管理

### 同类调用解决

- 注入自己 `@Autowired UserService self;`
- `AopContext.currentProxy()`
- 拆 Service

## 11.2 传播行为 7 种


| 类型            | 含义           |
| ------------- | ------------ |
| REQUIRED（默认）  | 有则加入，无则新建    |
| REQUIRES_NEW  | 总是新建，挂起当前    |
| NESTED        | savepoint 嵌套 |
| SUPPORTS      | 有就加入，无就非事务   |
| NOT_SUPPORTED | 总是非事务，挂起     |
| MANDATORY     | 必须有          |
| NEVER         | 必须没          |


实战：日志/统计用 REQUIRES_NEW。

---

# 第 12 章 线上排查实战

## 12.1 CPU 100%

```bash
top                          # 找进程 PID
top -Hp <pid>                # 找线程 TID
printf "%x\n" <tid>          # 16 进制
jstack <pid> | grep -A 30 "nid=0x<hex>"
```

常见：死循环 / 大量 GC（`jstat -gc <pid> 1s`）/ 死锁（jstack 自动报）/ 正则回溯。

## 12.2 内存泄漏

```bash
jstat -gc <pid> 1s           # 看 OU 趋势
jmap -dump:format=b,file=/tmp/heap.hprof <pid>
# MAT: Dominator Tree / Path to GC Roots / Histogram
```

我的实战：MAT 发现某 DTO 几十万实例 → 拉取主循环全量暂存 → 改流式。

## 12.3 接口变慢

1. SkyWalking/Arthas trace 定位
2. DB：`SHOW PROCESSLIST` / 慢日志 + EXPLAIN / `SHOW ENGINE INNODB STATUS`
3. 缓存：`redis-cli --latency` / 大热 Key
4. 下游：tcpdump / 监控
5. GC：jstat
6. 网络：ping / mtr / netstat -s

Arthas：

```bash
trace com.xxx.Service method '#cost > 100'
watch com.xxx.Service method '{params,returnObj}' -x 3
thread -n 3
```

---

# 第 13 章 系统设计题

## 13.1 秒杀系统

**分层**：

- 前端：CDN、按钮防抖、验证码
- 接入：Nginx 限流、网关风控
- 应用：Redis 预扣库存 + Lua 原子扣减 + MQ 削峰 + 本地缓存 Caffeine
- 存储：Redis+MySQL 双写、订单分库分表

**Lua 扣库存**：

```lua
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock and stock > 0 then
    redis.call('DECR', KEYS[1])
    return 1
end
return 0
```

**关键**：不超卖（Lua 原子）/ 防重复（Redis SET NX）/ 削峰（MQ）/ 降级。

## 13.2 分布式 ID


| 方案         | 优点      | 缺点    |
| ---------- | ------- | ----- |
| UUID       | 本地      | 无序、长  |
| DB 自增      | 简单      | 单点    |
| Redis INCR | 高性能     | 持久化丢失 |
| Snowflake  | 趋势递增高性能 | 时钟回拨  |
| 美团 Leaf    | 兼顾      | 实现复杂  |


**Snowflake**：1 + 41 时间戳 + 10 机器 + 12 序列。

**时钟回拨**：小幅 sleep 等；大幅抛异常告警；进阶用历史最大时间戳 + 序列号+1。

## 13.3 数据拉取系统重构

**先讲真实架构**（goods-task + goods-data-puller 两层、PriorityBlockingQueue、HTTP 分发、机器状态机）。

**改进方向**：

1. Kafka 替 SCP/JuiceFS，去掉文件中间态根治冗余文件
2. 调度层改注册中心模式，puller 心跳上报，感知更及时
3. 维度优先级动态调整，高价值维度永远优先

---

# 第 14 章 手撕代码（必背 10 题）

## 14.1 单例 DCL

```java
public class Singleton {
    private static volatile Singleton instance;
    private Singleton() {}
    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) instance = new Singleton();
            }
        }
        return instance;
    }
}
```

**为什么 volatile**：防止 (1)分配 (2)初始化 (3)引用赋值 重排为 (1)(3)(2)，B 线程拿到未初始化对象。

**静态内部类**：

```java
public class Singleton {
    private Singleton() {}
    private static class Holder {
        private static final Singleton INSTANCE = new Singleton();
    }
    public static Singleton getInstance() { return Holder.INSTANCE; }
}
```

**枚举**（防反射、防序列化攻击）：

```java
public enum Singleton { INSTANCE; }
```

## 14.2 生产者消费者

BlockingQueue 版：

```java
private final BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(10);
// 生产
queue.put(val);
// 消费
int val = queue.take();
```

wait/notify 版：

```java
public void produce(int val) throws InterruptedException {
    synchronized (lock) {
        while (queue.size() == CAPACITY) lock.wait(); // while 防虚假唤醒
        queue.add(val);
        lock.notifyAll();
    }
}
public int consume() throws InterruptedException {
    synchronized (lock) {
        while (queue.isEmpty()) lock.wait();
        int val = queue.removeFirst();
        lock.notifyAll();
        return val;
    }
}
```

## 14.3 LRU

```java
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;
    public LRUCache(int capacity) {
        super(capacity, 0.75f, true);
        this.capacity = capacity;
    }
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}
```

手写 HashMap+双向链表：见原稿（核心 addToHead / remove / moveToHead 三个私有方法）。

## 14.4 反转链表

```java
public ListNode reverseList(ListNode head) {
    ListNode prev = null, cur = head;
    while (cur != null) {
        ListNode next = cur.next;
        cur.next = prev;
        prev = cur;
        cur = next;
    }
    return prev;
}
```

## 14.5 两数之和

```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> map = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        if (map.containsKey(complement)) return new int[]{map.get(complement), i};
        map.put(nums[i], i);
    }
    return new int[0];
}
```

## 14.6 二分查找

```java
public int binarySearch(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (nums[mid] == target) return mid;
        else if (nums[mid] < target) left = mid + 1;
        else right = mid - 1;
    }
    return -1;
}
```

## 14.7 快排

```java
public void quickSort(int[] arr, int left, int right) {
    if (left >= right) return;
    int pivot = partition(arr, left, right);
    quickSort(arr, left, pivot - 1);
    quickSort(arr, pivot + 1, right);
}
private int partition(int[] arr, int left, int right) {
    int pivot = arr[right];
    int i = left - 1;
    for (int j = left; j < right; j++) {
        if (arr[j] < pivot) {
            i++;
            int tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
        }
    }
    int tmp = arr[i+1]; arr[i+1] = arr[right]; arr[right] = tmp;
    return i + 1;
}
```

### 追问：最坏情况？优化？

有序数组选首元素 → O(n²)。优化：随机 pivot / 三数取中 / 三路快排。

### 追问：vs 归并？


|     | 快排                     | 归并            |
| --- | ---------------------- | ------------- |
| 时间  | 平均 O(n log n)，最坏 O(n²) | 始终 O(n log n) |
| 空间  | O(log n)               | O(n)          |
| 稳定  | 否                      | 是             |


归并用于：稳定排序（Java TimSort）/ 外部排序 / 链表排序。

## 14.8 ABC 交替打印

```java
static int state = 0;
static void print(String s, int target) {
    for (int i = 0; i < 10; i++) {
        synchronized (lock) {
            while (state % 3 != target) lock.wait();
            System.out.print(s);
            state++;
            lock.notifyAll();
        }
    }
}
```

## 14.9 死锁示例 + 排查

```java
new Thread(() -> { synchronized(lockA){ Thread.sleep(100); synchronized(lockB){} }}).start();
new Thread(() -> { synchronized(lockB){ Thread.sleep(100); synchronized(lockA){} }}).start();
```

排查：jps → jstack → 末尾 "Found one Java-level deadlock"。
预防：固定加锁顺序、tryLock(timeout)、缩小锁粒度。

## 14.10 Spring 三级缓存（口述）

见 7.3。

---

# 第 15 章 算法真题（口述题）

1. **括号匹配**：栈
2. **最长无重复子串**：滑动窗口 + HashMap
3. **合并有序链表**：dummy + 双指针
4. **岛屿数量**：DFS 染色
5. **爬楼梯**：DP，`a,b,c=a+b`
6. **二叉树层序**：BFS Queue
7. **最大子数组和**：Kadane，`cur=max(nums[i], cur+nums[i])`
8. **字符串反转**：双指针

---

# 第 16 章 AI / Agent 八股

## 16.1 RAG

解决三问题：幻觉、知识时效、私有知识。
流程：问题 → 向量化 → 向量库检索 → 文档+问题给模型 → 生成答案。

## 16.2 Embedding

- 文本 → 固定维度向量
- 项目通过 OpenAI-compatible 外部服务生成 Embedding，模型和维度显式配置
- 入库与查询必须使用同一模型身份、维度和归一化策略
- 余弦相似度

## 16.3 Milvus ANN

- HNSW：多层图，查询 O(log n)
- IVF：聚类，只查最近簇
- 项目用 HNSW

## 16.4 ReAct

Thought → Action → Observation 循环，让模型调外部工具。

## 16.5 控制输出格式

1. Prompt 工程
2. Few-shot
3. **Function Calling**（最稳）

## 16.6 LangChain4j

- 统一 ChatLanguageModel
- 内置 Embedding / VectorStore
- Tool / Function Calling
- Memory 抽象
- Java 生态原生，Spring 集成自然

---

# 第 17 章 分布式理论

## 17.1 CAP

- CP：ZooKeeper、etcd
- AP：Eureka、Nacos
- P 必须有，实际是 CP/AP 二选一

## 17.2 BASE

基本可用 / 软状态 / 最终一致。互联网首选。

## 17.3 分布式事务

1. **2PC**：阻塞、单点
2. **TCC**：Try-Confirm-Cancel，业务侵入大
3. **Saga**：长事务，反向补偿
4. **MQ 最终一致**：互联网最常用
5. **Seata**：阿里开源，AT 模式无侵入

## 17.4 一致性 Hash

- 0-2^32 圆环，节点散布，key 顺时针找
- 增减节点只影响相邻
- 虚拟节点解决倾斜
- 应用：Redis Cluster slot、Dubbo、CDN

## 17.5 Redlock 争论

单机 SETNX 不够（主从切换丢锁）→ Redlock N/2+1 节点；强一致场景 Martin Kleppmann 建议 ZooKeeper。

---

# 第 18 章 设计模式

## 18.1 单例

见 14.1。

## 18.2 工厂

- 简单工厂 / 工厂方法 / 抽象工厂
- Spring BeanFactory 是工厂模式

## 18.3 代理

- 静态 / JDK 动态（接口） / CGLIB（字节码子类）

## 18.4 模板方法

项目：`AbstractRuleResolveTask` 子类实现规则解析。

## 18.5 责任链（项目用）

`OrderMessageProcessor` 维护 Handler 链。

## 18.6 策略

项目：促销策略、限流策略。避免 if-else。

## 18.7 观察者

Data Agent `ModelConfigChangeEvent` 发布 → `McpModelService` 监听清缓存。

---

# 第 19 章 行为面 / 软问题

## Q1：为什么离开同程？为什么 OD？

> 同程学到很多（千万级数据、JVM 调优）。但作为应届进来的同学成长路径相对固定。希望更大平台接触更复杂业务和技术栈，华为云/大数据/AI 都有积累。OD 是华为正常人才渠道，技术氛围和正编无本质区别。

## Q2：成都能长期稳定吗？

> 成都是目标城市，家人在这边，希望长期稳定 3-5 年把业务和技术做深。

## Q3：能接受加班吗？

> 同程 11-9-6 节奏已习惯。重点不是多少，是是否值得——有意义的攻坚没问题，无效内卷我会用 Claude Code 这些 AI 工具提效。

## Q4：OD 和正编区别介意吗？

> 我了解：签德科/慧通，工作内容、技术栈、绩效都在华为体系。我看重工作含金量、成长空间、团队契合，OD/正编不是首要因素，有转正机会会争取。

## Q5：AI 工具用得多，担心你写不出代码？

> 直接展示：
>
> - OOM 排查我用 jmap + MAT 独立定位
> - 红包 12 亿降 1400 万是我和业务对齐的
> - JVM 调优、G1 换 CMS 是我看实际表现决策
> AI 只在 DTO/Repository/Mapper 等重复结构化代码提速，架构决策和疑难排查替代不了。

## Q6：最大缺点？

> 技术广度集中在数据链路（拉取/批处理/缓存/JVM 调优）。C 端高并发（真秒杀、亿级网关、复杂分布式事务）有理论但实战不够。这是接下来想补的方向，也是加入华为的原因。

## Q7：独立出差三个月？

> 没问题，配合业务是基本职业素养，接触新业务能学更多。

## Q8：薪资？

> 上家月薪 XX，年包约 X 万（**真实数据，HR 会调档**）。这次主要看平台和成长，期望合理范围提升，可接受合理 offer。

## Q9：带新人怎么做？

> 三步：(1) 第一周陪过代码/业务/文档 (2) 前两周分独立但不致命的小任务 (3) 后续 1-1 同步、鼓励主动提问、错误让他踩。核心是独立思考，不要变成"代码生成器"。

## Q10：3 年规划？

> 1 年熟透业务/技术栈，独立承接核心模块；2 年深入架构主导子系统；3 年某垂直方向（数据链路/AI 工程化）成为专家。技术深度型，不走管理。

---

# 第 20 章 Linux 排查命令清单

```bash
# CPU / 内存 / IO
top                 # P CPU排序，M 内存排序
top -Hp <pid>       # 进程内线程
free -h
vmstat 1            # cs 上下文切换、wa IO 等待
iostat -x 1

# 网络
netstat -anp
ss -anp
lsof -i:8080
tcpdump -i eth0 port 80
mtr <ip>

# 磁盘
df -h
du -sh *

# 进程/线程
ps -ef | grep
jps
jstack <pid>
jmap -heap <pid>
jstat -gc <pid> 1s
jinfo <pid>

# 文本
grep -A 3 -B 3 "error" log.txt
awk '{print $1}' file
sed -i 's/old/new/g' file

# 实时日志
tail -f app.log | grep ERROR
```

---

# 第 21 章 临场建议 + 反问 + 冲刺清单

## 21.1 时间节奏

- 00:00-02:00 自我介绍
- 02:00-15:00 项目深挖（主推数据拉取系统）
- 15:00-25:00 Redis + MySQL + JVM
- 25:00-35:00 手撕代码 / 系统设计
- 35:00-45:00 行为面 + 反问

## 21.2 主动引导

> "另外补充一下，这个项目里还有一个有意思的优化点，关于 XX（红包 12 亿→1400 万），不知道您是否感兴趣？"

## 21.3 不会的题怎么救场

**❌**：不会 / 没做过
**✅**：

> "这块我目前理解到 X 层。从原理上推断应该是 Y，但我没在代码层验证过，不能 100% 确定。"
> 或：
> "我没深入研究，但类比 Z 机制，猜测是…您看这个方向对吗？"

## 21.4 追问三层次


| 层次    | 验证什么   | 策略          |
| ----- | ------ | ----------- |
| 概念    | 知不知道   | 清晰定义        |
| 原理    | 理不理解底层 | 讲为什么        |
| 场景/权衡 | 有没有实战  | 结合项目，讲选择和代价 |


华为 OD 二面通常到第二层，你有实战可冲第三层。

## 21.5 反问（必备 2-3 个）

1. "华为云/AI 方向，这个岗位会接触吗？"
2. "团队目前最大的技术挑战是什么？"
3. "对我这种背景，建议入职前 3 个月重点补哪些方向？"
4. **不要问**：加班多吗 / 薪资多少

## 21.6 临睡前 30 分钟必看

1. 手撕：单例 DCL、LRU、生产者消费者
2. 三个项目 90 秒开场
3. 量化数字：12 亿→1400 万、20min→10min、493 QPS、29 万次
4. 行为面：为什么离开、为什么 OD、AI 工具担心吗
5. CPU 100% 4 步：top → top -Hp → printf %x → jstack
6. @Transactional 失效 7 种
7. goods-task 两层架构 + DimensionScheduleManager（**千万别说错！**）

## 21.7 上车路上听

- Redis 缓存三连
- MySQL 索引失效场景
- TCP 3 次为什么不是 2 次

## 21.8 进面试室前 3 分钟

- 深呼吸
- 默念："2 年同程实战 + AI 项目，OOM 调优我独立做过，红包优化是亮点"
- **自信、真诚、节奏稳**

## 21.9 心态

- 自信但不傲
- 真诚最重要——不会就说思考过程，编造比不会糟
- 每个回答 1-2 分钟
- 项目讲到 80%，剩 20% 留追问

---

> 你已经准备得比 90% 候选人都充分。面试不是考试，是双向选择。把擅长的讲清楚，不会的承认并展示思考过程。状态比技术更重要——稳住节奏，offer 是你的。

*张原铭 · 2026-06-07 · 华为 OD 二面总手册*

---

# 第 22 章 华为 OD 成都专项查漏补缺

> 华为 OD 成都风格：简历每行都可能被追问，重 JVM/OOM 实战，必带手撕，喜欢追"为什么 A 不 B"，行为面必问稳定性。本章把前面章节没覆盖到、或者覆盖太浅的题集中补齐。

## 22.1 简历"诱饵"应答模板（必背！）

> 华为面试官看到简历会优先咬这些字眼，要把每一条都准备成 "5 步法" 完整故事：**现象 → 定位 → 根因 → 方案 → 效果 + 反思**。

### 22.1.1 JVM Full GC 几秒一次 → OOM 完整讲法（必背 3 分钟版本）

```
【现象】
拉取服务大概十几分钟就 Full GC 一次，每次 STW 几秒，
线上偶尔直接 OOM 重启，下游收不到数据告警。

【定位】
第一步看监控：goods-dashboard 上看到老年代水位从启动就一路上涨，
不掉头；Full GC 后只回收掉 10% 左右，说明老年代里大量存活对象。

第二步 jmap dump：用 jmap -dump:live,format=b,file=heap.hprof <pid>
拿到 1.8G 的 dump 文件，scp 拉到本地用 MAT 打开。

第三步 MAT 看 Dominator Tree：排名第一的是 ArrayList，
retained heap 占 1.4G；点进 Path to GC Roots 一路追到拉取主流程，
是把一个维度的千万条数据全 load 进 List 后再统一序列化。

【根因】
拉取代码用 dao.queryAll() 一次性查千万条，
全量数据在 List 里强引用，GC 回收不掉，
慢慢挤压新生代→对象过早晋升→老年代堆满→Full GC。

【方案】三个组合手段
1. 流式分页：dao.queryPage(offset, 5000) 循环，
   每批序列化完追加写文件后局部变量出作用域，下次 YGC 即可回收。
2. JVM 参数：堆 8G 固定（-Xms=-Xmx 避免动态扩容抖动），
   新生代 3G（短命对象多，让 YGC 兜住），G1 + MaxGCPauseMillis=200。
3. 收集器从 Parallel 换 G1：Parallel Full GC 是整堆扫描 STW，
   G1 Mixed GC 增量回收老年代 Region，停顿可控。

【效果】
Full GC 频次从几分钟一次降到几小时一次，
内存水位稳定在 60% 以下，再没 OOM 过。

【反思】
其实根因是写代码时没考虑数据量级，
后来沉淀到团队规范：所有循环处理大数据必须显式声明分页大小。
```

**为什么这套讲法华为爱听**：现象具体（不是"很卡"）、定位有工具（jmap+MAT）、根因有代码层细节（queryAll+强引用）、方案有组合拳（代码+参数+收集器）、还有自我反思——这是工程师成熟度的信号。

### 22.1.2 红包 12 亿 → 1400 万 业务侧追问

| 追问 | 答案 |
|---|---|
| 这是产品决策还是技术决策？ | 技术发起，业务签字。我先用 SQL 拉了过去半年红包使用日志，**99.2% 红包是星级红包**，拿这个数据找产品 leader 确认"非星级红包按默认红包处理"对用户没感知后才上线 |
| 灰度怎么做的？ | 先在预发跑 1 周，对比新旧匹配结果差异酒店数；再线上灰度 5% 流量看下游订单转化率，连续 3 天无负向后全量 |
| 怎么证明没有负面影响？ | A/B 对照组指标看转化率、订单金额、客诉率，三项指标 7 天内无显著差异（p > 0.05）才结题 |
| 业务又新增 VIP 红包，方案会失效吗？ | 不会。优化的是"匹配关系冗余"，不是"红包类型"。VIP 红包按业务规则正常参与匹配即可，**匹配结果集天然只包含命中规则的酒店**，不会再爆 12 亿 |

### 22.1.3 Data Agent 时间敏感问题答法

简历写 2026-04 至今，到面试时才 2 个月，面试官可能怀疑深度。**主动说出来**：

> "这个项目时间不长，但代码量 1.8 万行覆盖 5 层路由、ReAct、RAG、3 层记忆。能这么快产出是因为我把它当成 AI 协作开发的方法论实验——我主导设计和技术选型，AI 工具做代码实现，过程中沉淀了团队可复用的 Prompt 模板和协作流程。这本身就是我想验证的东西：**一个有经验的 Java 工程师 + AI 工具，能产出什么量级的项目**。"

---

## 22.2 Java 基础查漏

### 22.2.1 HashMap 1.7 头插法死循环（高频）

**死循环成因**：

```
1.7 扩容 transfer 时头插，多线程并发 resize：
- 线程 A 执行到 e.next = newTable[i] 后被挂起
- 线程 B 完整跑完 resize（链表反转）
- 线程 A 恢复，此时它持有的 e、next 引用是旧顺序，
  但 newTable[i] 已经被 B 反转过
- A 继续头插会让两个节点的 next 互相指向，形成环
- 后续 get(key) 走到这个环 → 死循环，CPU 100%
```

**1.8 改尾插就解决了**：尾插不会改变节点相对顺序，扩容时即使并发也只是丢失更新（数据丢失），不会成环。但 1.8 的 HashMap 仍然不是线程安全的，并发用 ConcurrentHashMap。

### 22.2.2 CopyOnWriteArrayList 适用场景

**实现**：写时复制整个数组，读不加锁。

**适用**：**读远多于写，且能容忍弱一致性**——比如配置类（启动加载几乎不变）、监听器列表。

**不适用**：
- 写多场景：每次写都 `Arrays.copyOf` 整个数组，O(n) 复制 + GC 压力
- 大数据量：复制成本随容量线性增长
- 强一致性要求：迭代器是写时刻的快照，迭代过程中其他线程写入不可见

**项目场景**：goods-ds 里的 Handler 链注册用 CopyOnWriteArrayList——启动注册完几乎不变，所有请求线程并发读，读无锁高吞吐。

### 22.2.3 类加载打破双亲委派的三大案例

| 案例 | 打破点 | 原因 |
|---|---|---|
| **JDBC SPI** | Bootstrap ClassLoader 加载的 DriverManager 要加载 AppClassLoader 路径上的 Driver 实现 | 父加载器看不到子加载器的类，借助 Thread.currentThread().getContextClassLoader() 反向委托 |
| **Tomcat WebApp** | 每个 Webapp 一个 WebAppClassLoader，**先自己找再交父**（违反双亲委派） | 不同 webapp 用相同类名不同版本（如 Spring 4 和 Spring 5）必须隔离 |
| **OSGi / 模块化** | 网状委托，每个 Bundle 有自己的 ClassLoader | 支持模块热部署、版本共存 |

**SPI 的代码影子**：`ServiceLoader.load(Driver.class)` 内部用 `Thread.currentThread().getContextClassLoader()` 而不是 `Driver.class.getClassLoader()`，这就是"线程上下文类加载器"机制。

### 22.2.4 AQS 原理完整版

```
AQS 核心三件套：
1. state (volatile int)        —— 同步状态（ReentrantLock 重入计数、Semaphore 令牌数）
2. CLH 双向队列                  —— 等待线程的 FIFO 队列（Node 节点 + 前驱指针）
3. CAS + LockSupport.park/unpark —— 状态变更原子性 + 线程阻塞唤醒

获取锁流程（独占模式）：
tryAcquire(int)
  ├─ 成功 → 直接返回
  └─ 失败 → addWaiter(EXCLUSIVE) 加入队尾
       └─ acquireQueued()
            ├─ 前驱是 head → 再尝试 tryAcquire
            ├─ 失败 → shouldParkAfterFailedAcquire 设置前驱 SIGNAL
            └─ parkAndCheckInterrupt → LockSupport.park 阻塞

释放锁流程：
tryRelease(int)
  └─ state == 0 → unparkSuccessor(head) 唤醒后继
```

**子类实现的方法**：`tryAcquire`/`tryRelease`（独占）、`tryAcquireShared`/`tryReleaseShared`（共享）。

**典型应用**：ReentrantLock（独占）、Semaphore（共享）、CountDownLatch（共享，state 表示剩余 count）、ReentrantReadWriteLock（state 高 16 位读锁，低 16 位写锁）。

---

## 22.3 MySQL 查漏

### 22.3.1 explain type 字段从好到坏（必背）

```
system > const > eq_ref > ref > range > index > ALL
```

| type | 含义 | 例子 |
|------|------|------|
| **const** | 主键 / 唯一索引等值查询 | `WHERE id = 1` |
| **eq_ref** | 关联查询用主键 | `JOIN t2 ON t1.id = t2.id` |
| **ref** | 普通索引等值 | `WHERE name = 'x'` (name 有非唯一索引) |
| **range** | 索引范围 | `WHERE age BETWEEN 10 AND 20` |
| **index** | 全索引扫描（覆盖索引） | 比 ALL 好在不回表 |
| **ALL** | 全表扫描 | 需要优化 |

线上排查口诀：**type 至少要到 range，扫描行数 rows 看千行内，Extra 出现 "Using filesort" / "Using temporary" 立刻警惕。**

### 22.3.2 死锁排查完整流程

```sql
-- 1. 查看最近一次死锁日志
SHOW ENGINE INNODB STATUS;
-- 看 "LATEST DETECTED DEADLOCK" 段落

-- 2. 看锁等待情况（实时）
SELECT * FROM information_schema.INNODB_TRX;
SELECT * FROM performance_schema.data_locks;
SELECT * FROM performance_schema.data_lock_waits;
```

**典型死锁场景**：

```
Tx1: UPDATE t SET x=1 WHERE id=1;  -- 持有 id=1 的 X 锁
Tx2: UPDATE t SET x=1 WHERE id=2;  -- 持有 id=2 的 X 锁
Tx1: UPDATE t SET x=1 WHERE id=2;  -- 等 Tx2 释放 id=2
Tx2: UPDATE t SET x=1 WHERE id=1;  -- 等 Tx1 释放 id=1 → 死锁
```

**InnoDB 死锁检测**：自动 rollback 回滚成本较小的事务（undo log 少的）。

**避免**：
1. 多表更新顺序保持一致
2. 短事务，少持锁时间
3. 同一事务尽量按主键升序更新
4. 加索引避免间隙锁过宽

### 22.3.3 慢 SQL 排查完整 SOP

```bash
# 1. 开慢查日志
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 1;  # 超过 1s 记录

# 2. 用 mysqldumpslow 聚合分析
mysqldumpslow -s t -t 10 /var/log/mysql/slow.log
# -s t 按总耗时排序，-t 10 取前 10

# 3. 拿到具体 SQL → EXPLAIN
EXPLAIN SELECT ... ;
# 重点看：type、key、rows、Extra

# 4. profiling 看每阶段耗时
SET profiling = 1;
SELECT ...;
SHOW PROFILES;
SHOW PROFILE FOR QUERY 1;
```

---

## 22.4 Redis 查漏

### 22.4.1 Redisson 看门狗（WatchDog）完整原理

**问题**：SETNX 设的锁过期时间如果太短，业务还没执行完锁就过期；设太长，宕机后其他线程要等很久。

**Redisson 方案**：

```
1. 客户端拿到锁，默认 30s 过期
2. 启动一个后台 ScheduledFuture，每 10s（过期时间 / 3）执行一次
3. 后台任务用 Lua 脚本判断"锁是不是我的"，是就 PEXPIRE 续到 30s
4. 业务执行完调 unlock()，主动停止后台续期任务并释放锁
5. 如果客户端 JVM 进程挂了，ScheduledFuture 不再续期，
   30s 后锁自动过期，其他线程可以接管
```

**核心 Lua（释放锁）**：

```lua
if redis.call('hexists', KEYS[1], ARGV[3]) == 0 then return nil; end;
local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1);
if (counter > 0) then
  redis.call('pexpire', KEYS[1], ARGV[2]);
  return 0;
else
  redis.call('del', KEYS[1]);
  redis.call('publish', KEYS[2], ARGV[1]);
  return 1;
end;
```

用 hash 存重入计数，可重入；用 pub/sub 通知等待者锁已释放。

**陷阱**：如果调 `tryLock(leaseTime)` 显式传了过期时间，**不会启动看门狗**！只有不传 leaseTime 才有看门狗保护。

### 22.4.2 大 Key / 热 Key 治理

**大 Key 怎么发现**：

```bash
# 1. redis-cli --bigkeys 扫描（生产慎用，会全量 scan）
redis-cli -h xxx --bigkeys

# 2. RDB 离线分析（推荐）
# 用 rdb-tools 把 dump.rdb 解析后按 key size 排序
rdb -c memory dump.rdb --bytes 10240 -f bigkey.csv

# 3. 内存采样：MEMORY USAGE <key>
```

**治理**：
- String 大 key → 拆分压缩或迁出 Redis 改用专门存储
- Hash/List/Set 大 key → 按 hash 分桶（如 user:profile:{userId%100}）
- 删除大 key 用 UNLINK 异步删，不用 DEL（DEL 大 key 会阻塞）

**热 Key 怎么发现**：

```bash
redis-cli --hotkeys  # 需要 maxmemory-policy 为 LFU
# 或者 monitor 抽样（慎用，性能开销大）
```

**治理**：
- 本地缓存（Caffeine）一级 + Redis 二级，热 key 在本地命中不打 Redis
- 多副本分散：把同一热 key 复制到 hotkey:1 / hotkey:2 / ... 客户端随机读
- 限流降级：单 key QPS 超阈值直接读 DB 或返回默认值

### 22.4.3 BloomFilter 的缺陷

- **假阳性**：可能"误判存在"，所以适合做"判定不存在直接返回"的场景，不能反过来用
- **不能删除**：CountingBloomFilter 才能删，但空间翻倍
- **容量预估错误代价大**：装满后假阳性率飙升，必须预先估好

---

## 22.5 场景设计补强

### 22.5.1 亿级实时检索系统设计（套你 goods-ds 经验）

```
【关键挑战】
1. 千万级 SKU 不能每次查 DB（DB 扛不住）
2. 数据要实时（价格库存变化要秒级感知）
3. 高并发查询（P99 < 100ms）

【方案分层】

数据层：MySQL 持久化 + Kafka binlog 捕获变更

加载层：
  - 全量：从 DB / 数仓拉静态信息，PB 序列化后生成快照文件
  - 增量：Kafka consumer 实时消费 binlog，更新内存索引

服务层（每实例独立内存索引）：
  - volatile Index 指针 + 双 Buffer 热切换
  - 三层索引模型（按维度切分 Table）
  - Handler 责任链做计价 / 过滤
  - Thrift NIO + 业务线程池（64 worker + 10k 队列）

容灾：
  - 多机部署 + 注册中心摘除
  - 索引快照失败回退磁盘加载
  - Kafka 消费 lag 监控 + 自动扩容
```

**重点讲法**：先点"读多写少 → 内存索引"，再点"实时 → Kafka + 全量双通道"，最后点"高可用 → 双 Buffer 热切 + 多实例"。

### 22.5.2 线上 CPU 100% 排查模板

```bash
# 1. 定位高 CPU 进程
top                              # 看 %CPU 最高的 PID
# 假设 PID = 12345

# 2. 定位高 CPU 线程
top -Hp 12345                    # -H 显示线程，找 %CPU 最高的 TID
# 假设 TID = 12399

# 3. 线程 ID 转 16 进制
printf "%x\n" 12399              # 输出 30af

# 4. jstack 看线程栈
jstack 12345 > stack.log
grep -A 30 "0x30af" stack.log    # 看这个线程在干啥
```

**常见根因**：
- 死循环（业务代码 while + 没退出条件）
- HashMap 1.7 死循环（多线程并发 put）
- GC 线程飙高（看是不是 Full GC 频繁）
- 正则回溯（正则表达式写得不好）
- 序列化反序列化爆栈

### 22.5.3 线上接口 RT 飙到 5s 排查模板

**分层定位**（从外到内）：

```
1. 监控大盘看是哪一层慢
   ├─ 是不是网关 / Nginx 层慢？看 access log
   ├─ 是不是 RPC 序列化慢？
   ├─ 是不是业务逻辑慢？
   ├─ 是不是 DB 慢？看慢查日志
   ├─ 是不是 Redis 慢？看 slow log
   ├─ 是不是下游接口慢？看下游监控
   └─ 是不是 GC 慢？看 gc.log

2. 业务逻辑慢的话用 Arthas trace
   trace com.xxx.YourService methodName '#cost > 100'
   # 打印每一层耗时，定位到具体方法

3. 看是不是线程池满了
   ├─ ThreadPool dashboard
   └─ jstack 看 BLOCKED / WAITING 线程数
```

**常见原因（按概率）**：
1. DB 慢 SQL（没索引 / 锁等待 / 大表扫描）
2. 下游接口超时（依赖服务出问题）
3. 线程池打满（任务排队）
4. GC 卡顿（Full GC STW）
5. 网络抖动（同机房 vs 跨机房）

---

## 22.6 手撕代码补充 5 题

### 22.6.1 K 个一组翻转链表（LC 25，华为高频）

```java
public ListNode reverseKGroup(ListNode head, int k) {
    ListNode dummy = new ListNode(0, head);
    ListNode pre = dummy;
    while (head != null) {
        ListNode tail = pre;
        // 检查剩余是否够 k 个
        for (int i = 0; i < k; i++) {
            tail = tail.next;
            if (tail == null) return dummy.next;
        }
        ListNode next = tail.next;
        // 翻转 [head, tail]
        ListNode[] reversed = reverse(head, tail);
        head = reversed[0]; tail = reversed[1];
        // 拼接
        pre.next = head;
        tail.next = next;
        pre = tail;
        head = tail.next;
    }
    return dummy.next;
}

private ListNode[] reverse(ListNode head, ListNode tail) {
    ListNode prev = tail.next, p = head;
    while (prev != tail) {
        ListNode next = p.next;
        p.next = prev;
        prev = p;
        p = next;
    }
    return new ListNode[]{tail, head};
}
```

### 22.6.2 二叉树最近公共祖先（LC 236）

```java
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    if (root == null || root == p || root == q) return root;
    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);
    if (left != null && right != null) return root;  // p, q 分居两侧
    return left != null ? left : right;
}
```

口述：**左右子树各找到一个，当前节点就是 LCA；只在一侧找到，LCA 就在那一侧**。

### 22.6.3 接雨水（LC 42，双指针法）

```java
public int trap(int[] height) {
    int left = 0, right = height.length - 1;
    int leftMax = 0, rightMax = 0, ans = 0;
    while (left < right) {
        if (height[left] < height[right]) {
            if (height[left] >= leftMax) leftMax = height[left];
            else ans += leftMax - height[left];
            left++;
        } else {
            if (height[right] >= rightMax) rightMax = height[right];
            else ans += rightMax - height[right];
            right--;
        }
    }
    return ans;
}
```

口诀：**哪边低先动哪边，能装多少水取决于较低那侧的历史最大**。

### 22.6.4 最长无重复子串（LC 3，滑动窗口）

```java
public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> map = new HashMap<>();
    int left = 0, max = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (map.containsKey(c)) {
            left = Math.max(left, map.get(c) + 1);  // 注意 max！
        }
        map.put(c, right);
        max = Math.max(max, right - left + 1);
    }
    return max;
}
```

**坑**：`left = Math.max(left, map.get(c) + 1)` 必须取 max，否则 "abba" 这种回头会让 left 倒退。

### 22.6.5 三数之和（LC 15，排序 + 双指针）

```java
public List<List<Integer>> threeSum(int[] nums) {
    Arrays.sort(nums);
    List<List<Integer>> ans = new ArrayList<>();
    for (int i = 0; i < nums.length - 2; i++) {
        if (i > 0 && nums[i] == nums[i-1]) continue;  // 去重
        int l = i + 1, r = nums.length - 1;
        while (l < r) {
            int sum = nums[i] + nums[l] + nums[r];
            if (sum == 0) {
                ans.add(Arrays.asList(nums[i], nums[l], nums[r]));
                while (l < r && nums[l] == nums[l+1]) l++;
                while (l < r && nums[r] == nums[r-1]) r--;
                l++; r--;
            } else if (sum < 0) l++;
            else r--;
        }
    }
    return ans;
}
```

---

## 22.7 行为面 / 软问题补强

### 22.7.1 离职原因话术（细化版）

> "同程的工作我学到非常多，特别是数据拉取和检索这块对千万级数据的全链路。但有两个考虑：第一，我女朋友/家人在成都，长期异地不是办法；第二，我想接触更大规模、更体系化的系统，华为云这块的技术沉淀和我现在做的方向匹配。所以这次出来主要是看成都的机会。"

**三个不要说**：钱少、加班多、上司有问题。即使是真的也不能说。

### 22.7.2 OD 认知（华为必问）

> "我了解 OD 是德科派遣，工资由德科发，社保也是。但我看重的是华为云的技术平台和真实项目机会——OD 在华为内部和正编同岗同酬同晋升路径（A → B → C），转正机会也是开放的。我目前阶段更想要的是能接触大体量系统的平台，OD 完全符合预期。"

### 22.7.3 AI 工具用太多会不会写不出代码

> "AI 工具是我提效的杠杆，不是大脑替代品。我现在的工作流是：**问题理解和方案设计自己做，代码生成让 AI 协作，但每行代码都要 review 才提交**。简单验证：你随便从我简历哪个亮点追问，我都能解释到代码细节——比如 G1 为什么 IHOP 设 45、Semaphore 为什么用 availablePermits 检查、ReAct 8 轮上限怎么定，这些都是我自己思考的结果。"

### 22.7.4 AI 编码规范你会怎么定？

> "我会从三个维度定规范：
> 1. **能力边界**：明确哪些场景必用 AI（样板代码、单测、文档），哪些场景禁用（核心算法、安全相关、第一次接触的领域）；
> 2. **质量门禁**：AI 产出代码必须人工 review + 跑测试 + 静态扫描三道关，PR 标签注明 AI 参与比例；
> 3. **知识沉淀**：团队共享 Prompt 库 + 项目专属规范（CLAUDE.md），让 AI 在我们的领域上下文里工作，而不是凭空生成。"

---

## 22.8 性能 / 工具补强

### 22.8.1 Arthas 必会命令（线上排查必带）

```bash
# 1. attach 到 Java 进程
java -jar arthas-boot.jar

# 2. 查看 JVM 信息
dashboard            # 实时大盘（CPU / GC / 线程）
thread               # 看线程状态
thread -n 3          # 看 CPU 最高的 3 个线程
thread -b            # 看死锁

# 3. 方法追踪
trace com.xxx.Service * '#cost > 100'   # 调用链耗时
watch com.xxx.Service method '{params, returnObj}' -x 2  # 看入参出参

# 4. 反编译看实际加载的类
jad com.xxx.Service

# 5. 动态修改类（线上热修）
mc Test.java        # 编译
redefine Test.class # 替换
```

### 22.8.2 Native 内存泄漏排查（堆没问题但进程内存涨）

```bash
# 启动时加 NativeMemoryTracking
-XX:NativeMemoryTracking=detail

# 运行中查看
jcmd <pid> VM.native_memory summary
jcmd <pid> VM.native_memory baseline   # 设基线
jcmd <pid> VM.native_memory summary.diff  # 看增量

# 常见 Native 泄漏点：
# - DirectByteBuffer 没释放（Netty / NIO）
# - JNI 调用泄漏
# - GZIP / Deflater / Inflater 没 close
# - 编译器代码缓存膨胀（CodeCache）
```

---

## 22.9 最后冲刺优先级清单（按性价比）

| 优先级 | 准备内容 | 性价比理由 |
|---|---|---|
| ⭐⭐⭐⭐⭐ | 22.1 JVM 5 步法完整故事 | 简历最大诱饵，3 分钟讲完拿 30 分 |
| ⭐⭐⭐⭐⭐ | 22.1 红包业务侧追问 | 唯一量化业务亮点 |
| ⭐⭐⭐⭐ | 22.6 手撕 5 题 + 14 章 10 题 | 大概率出 1 道，准备充分稳拿 |
| ⭐⭐⭐⭐ | 22.5 CPU 100% / RT 飙升模板 | 场景题必出之一 |
| ⭐⭐⭐ | 22.4 Redisson 看门狗 + 大热 key | 缓存追问深度 |
| ⭐⭐⭐ | 22.3 explain type + 死锁排查 | MySQL 实战必问 |
| ⭐⭐⭐ | 22.7 行为面话术 | OD 必问，背了不丢分 |
| ⭐⭐ | 22.2 类加载打破双亲委派 | 冷门但加分 |
| ⭐⭐ | 22.8 Arthas 命令 | 提到一次就显得有经验 |

---

> **华为 OD 面试三个核心信号**：1) 工程经验有真实代码细节支撑；2) 出问题有系统化定位思路；3) 稳定性和适应性强。本章每一条都是按这三个信号反推出来的。背完这章 + 前 21 章，OD 二面基本无死角。
