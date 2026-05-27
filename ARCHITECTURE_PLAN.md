# Data-Agent 企业级架构升级实施计划

> 文档版本：v2.0
> 创建日期：2026-05-12
> 更新日期：2026-05-19
> 状态：草稿（待讨论）
> 负责人：架构团队

---

## 一、项目概述

### 1.1 项目背景

Data-Agent 是一个基于 Spring Boot + LangChain4j 的企业级数据分析 Agent 平台，当前已具备基础的 Agent 执行能力，但在企业级应用场景下存在记忆体系不完整、推理深度不足、RAG 能力薄弱等问题。

### 1.2 升级目标

将 Data-Agent 从"基础 Agent"升级为"企业级智能 Agent 系统"，具备：
- **三层记忆体系**：工作记忆 / 短期记忆 / 长期记忆
- **增强推理引擎**：Plan-Execute-Reflect 循环
- **深度 RAG**：混合检索 + 重排序 + 引用溯源
- **智能分块**：语义分块 + 层级分块
- **Orchestrator 编排**：多专家 Agent 协作
- **可观测性**：执行追踪 + 质量评估

### 1.3 设计哲学

```
1个强 Orchestrator（理解力强、会规划、善整合）+ N个可插拔的 Specialist（每个都很深，但不相互依赖）

- Orchestrator 的职责是"固定"的：理解 → 规划 → 协调 → 整合
- Specialist 的职责是"可扩展"的：动态注册，按需加载
- 两者通过"能力接口"通信，而不是硬编码耦合
```

---

## 二、当前现状分析

### 2.1 现有能力

| 模块 | 状态 | 说明 |
|------|------|------|
| ReAct 推理循环 | ✅ 基础版 | 最多8轮迭代，但缺少规划-反思机制 |
| 工具调用体系 | ✅ 17个工具 | 支持文件/数据源/知识库/图表/计算等 |
| 多 Agent 类型 | ✅ 4种 | REACT / SKILL / DATA / CHAT |
| RAG 检索 | ⚠️ 基础版 | 单次向量检索，无重排序/引用溯源 |
| 向量记忆 | ⚠️ 基础版 | 对话原文索引，无摘要压缩 |
| 文本分块 | ❌ 固定长度 | 纯 500字/100重叠，无语义边界 |
| 多 Agent 协作 | ❌ 路由分发 | 只是简单路由，不是真正的协作编排 |
| 可观测性 | ⚠️ 基础版 | 只有 Token 统计，无执行轨迹追踪 |

### 2.2 核心差距

```
Phase 1 核心缺口（最关键）：
├── 记忆体系不完整 → 三层记忆分层
├── 分块策略简单   → 智能语义分块
└── RAG 深度不足   → 混合检索 + 重排序

Phase 2 核心缺口（竞争力）：
├── 推理深度不足   → Plan-Execute-Reflect
├── 工具调用脆弱   → 结构化 Function Calling
└── 引用溯源缺失   → 答案标注来源

Phase 3 核心缺口（规模化）：
├── 编排能力薄弱   → Orchestrator 增强
├── 多 Agent 协作  → 专家 Agent 体系
└── 用户画像缺失   → 个性化记忆

Phase 4 核心缺口（生产化）：
├── 执行追踪缺失   → Tracing 系统
├── 质量评估缺失   → Evaluation 框架
└── 安全深度不足   → Prompt 防护 + 输出审核
```

---

## 三、目标架构设计

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Data-Agent 企业级架构                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐          │
│  │   Frontend   │    │  API Gateway │    │   Auth/JWT   │          │
│  │ React + Vite │───▶│ Spring MVC   │───▶│   Security   │          │
│  └──────────────┘    └──────────────┘    └──────────────┘          │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                   🎯 Orchestrator Layer                         │ │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐│ │
│  │  │  Intent    │ │   Task     │ │  Monitor   │ │  Quality   ││ │
│  │  │  Analyzer  │ │  Planner   │ │            │ │  Evaluator ││ │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘│ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                   🧠 Agent Execution Layer                      │ │
│  │  ┌────────────────────────────────────────────────────────────┐│ │
│  │  │  Enhanced ReAct Engine (Plan-Execute-Reflect)              ││ │
│  │  └────────────────────────────────────────────────────────────┘│ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │ │
│  │  │   Data   │ │   SQL    │ │  Chart   │ │Knowledge │        │ │
│  │  │  Analyst │ │  Expert  │ │  Expert  │ │  Expert  │        │ │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                    🧠 Memory Layer                              │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │ │
│  │  │Working Memory│  │Short-term   │  │Long-term    │        │ │
│  │  │  工作记忆     │  │  短期记忆     │  │  长期记忆     │        │ │
│  │  │  Redis       │  │  MySQL+Redis │  │  Milvus+MySQL│        │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘        │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │  MySQL   │  │  Redis   │  │ Milvus   │  │  MinIO   │          │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 四、Phase 1：记忆体系升级（最关键）

### 4.1 为什么要升级记忆体系？

#### 4.1.1 用户体验问题

```
当前问题：Agent "记不住"

用户问：我是电商公司运营，叫张三
Agent答：好的，张三先生，很高兴认识您

用户问：公司Q3卖了多少？
Agent答：抱歉，我不知道"公司"是指什么

用户内心：🤬 "我不是刚说过吗？？？"
```

#### 4.1.2 好记忆的体验

```
✅ 主动关联：不需要用户重复说，Agent自己能关联
✅ 跨会话：今天说的，明天来Agent还记得
✅ 理解上下文：知道用户的身份/角色/目的
✅ 主动推送：发现用户可能需要什么，主动提示
```

#### 4.1.3 三层记忆分别解决什么问题？

| 记忆层 | 解决什么问题 | 存储内容 | TTL |
|--------|-------------|---------|-----|
| **工作记忆** | "我正在处理的事不会乱" | 当前任务状态、中间结果、临时变量 | 会话周期 |
| **短期记忆** | "今天问过的，不用再说" | 对话摘要、主题标签、意图变化 | 30天 |
| **长期记忆** | "这个用户是什么人，我了解他" | 用户画像、知识沉淀、高频问答 | 永久 |

### 4.2 存储容量规划（1万用户场景）

#### 4.2.1 精细化存储方案

```
┌─────────────────────────────────────────────────────────┐
│              1万用户 × 精细化存储方案                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  【工作记忆】Redis                                       │
│  ├── 每个用户会话约：1KB（当前任务状态）                   │
│  ├── 并发会话假设：10%（1000个活跃会话）                  │
│  ├── 总存储：1KB × 1000 = 约 1MB                        │
│  └── ✅ 完全没问题                                       │
│                                                          │
│  【短期记忆】MySQL                                       │
│  ├── 每个用户：5个摘要/月 × 200字 = 1KB/月               │
│  ├── 保留30天 = 1KB × 1万用户 = 10MB                    │
│  └── ✅ 毫无压力                                         │
│                                                          │
│  【长期记忆】Milvus + MySQL                              │
│  ├── 每个用户画像：1KB                                   │
│  ├── 知识沉淀：10个摘要 × 200字 = 2KB                   │
│  ├── 10,000用户 × 3KB = 30MB                           │
│  └── ✅ 完全可以接受                                     │
│                                                          │
│  【向量存储】Milvus                                      │
│  ├── 每个向量：384维 × 4字节 = 1.5KB                    │
│  ├── 10,000用户 × 20个向量 = 20,000 × 1.5KB = 30MB     │
│  └── ✅ 完全没问题                                       │
│                                                          │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  总存储需求：                                           │
│  Redis: ~1MB + MySQL: ~40MB + Milvus: ~30MB            │
│  ≈ 70MB ~ 100MB                                         │
│                                                          │
│  这个规模，对于现代系统来说，完全不是问题！                │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

#### 4.2.2 存储取舍的核心原则

```
┌─────────────────────────────────────────────────────────┐
│              记忆取舍的核心原则                           │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1️⃣ 存什么比存多少更重要                               │
│     └── 100条精华 >> 1000条原文                       │
│                                                          │
│  2️⃣ 分层存储是成本和体验的平衡点                       │
│     └── 高价值 → 贵存储（Redis/内存）                   │
│     └── 中价值 → 中存储（MySQL）                        │
│     └── 低价值 → 冷存储或清理（归档）                   │
│                                                          │
│  3️⃣ 压缩是存储的关键                                   │
│     └── 原文500字 → 摘要150字 = 压缩70%               │
│     └── LLM生成摘要，成本可控，效果好                   │
│                                                          │
│  4️⃣ 时间衰减避免存储无限增长                           │
│     └── 老记忆自动降权 → 归档/清理                     │
│     └── 高频访问的记忆权重回升                          │
│                                                          │
│  5️⃣ 按用户价值差异化存储策略                          │
│     └── 付费用户 → 高配存储                           │
│     └── 普通用户 → 标准存储                           │
│     └── 低频用户 → 最小化存储                         │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 4.3 记忆数据模型设计

#### 4.3.1 MemoryEntry（记忆条目）

```java
// com.ai.memory.dto.MemoryEntry
public class MemoryEntry {

    // ========== 基础信息 ==========
    private String memoryId;              // 唯一 ID (UUID)
    private String userId;               // 用户 ID
    private String tenantId;             // 租户 ID
    private String sessionId;            // 所属会话 (可为空)

    // ========== 记忆层级与类型 ==========
    private MemoryTier tier;             // 记忆层级
    private MemoryType type;              // 记忆类型
    private MemorySource source;          // 来源

    // ========== 内容 (原文 vs 压缩) ==========
    private String content;               // 原始内容 (可能为空)
    private String compressedContent;      // 压缩后内容 (摘要)
    private List<String> vectorIds;        // Milvus 向量 ID 列表

    // ========== 元数据 ==========
    private Map<String, Object> metadata; // 元数据
        // - entities: 提取的实体列表 ["张三", "XX电商", "Q3"]
        // - topic_tags: 主题标签 ["销售分析", "电商"]
        // - importance: 重要程度 1-5
        // - source_message_id: 来源消息 ID

    private List<String> keyEntities;     // 关键实体
    private List<String> topicTags;       // 主题标签

    // ========== 权重与衰减 ==========
    private double relevanceScore;        // 关联度评分 (0-1)
    private int accessCount;             // 被访问次数
    private double decayWeight;           // 衰减权重 (时间 × 访问频率 × 重要度)

    // ========== 时间戳 ==========
    private LocalDateTime createdAt;      // 创建时间
    private LocalDateTime lastAccessedAt; // 最后访问时间
    private LocalDateTime expiresAt;      // 过期时间 (null=永久)
}

/**
 * 记忆层级
 */
public enum MemoryTier {
    WORKING,     // 工作记忆: Redis, TTL=会话
    SHORT_TERM,  // 短期记忆: MySQL, 30天
    LONG_TERM    // 长期记忆: Milvus, 永久
}

/**
 * 记忆类型
 */
public enum MemoryType {
    // 对话相关
    CONVERSATION, // 原始对话
    SUMMARY,      // 对话摘要
    INTENT,       // 用户意图

    // 实体相关
    ENTITY,       // 实体 (用户/公司/产品)
    RELATION,     // 实体关系

    // 偏好相关
    PREFERENCE,   // 用户偏好
    PATTERN,      // 行为模式

    // 知识相关
    KNOWLEDGE,    // 知识沉淀
    CONCLUSION    // 关键结论
}

/**
 * 记忆来源
 */
public enum MemorySource {
    USER_EXPLICIT,  // 用户明确说 "记住..."
    USER_IMPLICIT,  // 用户无意中透露
    AGENT_EXTRACTED, // Agent 主动提取
    SYSTEM_GENERATED // 系统生成 (如摘要)
}
```

#### 4.3.2 UserProfile（用户画像）

```java
// com.ai.memory.dto.UserProfile
public class UserProfile {

    private String profileId;
    private String userId;
    private String tenantId;

    // ========== 基础信息 ==========
    private String name;                 // 用户名
    private String role;                 // 角色/职位
    private String industry;             // 行业
    private Integer experienceYears;      // 从业年限

    // ========== 沟通偏好 ==========
    private CommunicationStyle communicationStyle; // 沟通风格
        // - CONCISE: 简洁直接
        // - DETAILED: 详细解释
        // - FORMAL: 正式商务
        // - CASUAL: 轻松随意

    private PreferredFormat preferredFormat; // 偏好格式
        // - CHART_ONLY: 只要图表
        // - TEXT_WITH_CHART: 文字+图表
        // - TEXT_ONLY: 只要文字

    // ========== 专业领域 ==========
    private List<String> expertiseAreas;   // 专业领域 ["电商运营", "数据分析"]
    private List<String> frequentlyUsedTerms; // 常用术语
    private List<String> dataSources;      // 常用数据源

    // ========== 行为模式 ==========
    private List<String> frequentlyAskedTopics; // 高频询问话题
    private Integer avgSessionLength;      // 平均会话长度
    private LocalDateTime lastActiveAt;    // 最后活跃时间

    // ========== 统计 ==========
    private Long totalConversations;       // 总对话数
    private Long totalQueries;             // 总查询数
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### 4.3.3 MemoryContext（记忆上下文）

```java
// com.ai.memory.dto.MemoryContext
public class MemoryContext {

    // ========== 工作记忆 (当前会话) ==========
    private WorkingMemorySnapshot workingMemory;
        // - currentTask: 当前任务描述
        // - taskProgress: 任务进度 0-100%
        // - intermediateResults: 中间结果 Map
        // - toolCallHistory: 工具调用记录

    // ========== 短期记忆 (近期会话) ==========
    private List<MemoryEntry> recentSummaries; // 最近 5 个摘要

    // ========== 长期记忆 (用户画像) ==========
    private UserProfile userProfile;          // 用户画像
    private List<MemoryEntry> relevantMemories; // 检索到的相关记忆

    // ========== 检索元数据 ==========
    private Double contextRelevanceScore;     // 上下文关联度评分
    private Integer totalTokenEstimate;      // 上下文 Token 估算

    /**
     * 构建注入 Prompt 的字符串
     */
    public String buildPromptContext() {
        StringBuilder sb = new StringBuilder();

        // 1. 用户画像
        if (userProfile != null) {
            sb.append("【用户背景】\n");
            sb.append("姓名: ").append(userProfile.getName()).append("\n");
            sb.append("角色: ").append(userProfile.getRole()).append("\n");
            sb.append("行业: ").append(userProfile.getIndustry()).append("\n");
            if (!userProfile.getExpertiseAreas().isEmpty()) {
                sb.append("专业领域: ").append(String.join(", ", userProfile.getExpertiseAreas())).append("\n");
            }
            if (userProfile.getPreferredFormat() != null) {
                sb.append("偏好格式: ").append(userProfile.getPreferredFormat()).append("\n");
            }
            sb.append("\n");
        }

        // 2. 当前任务
        if (workingMemory != null && workingMemory.getCurrentTask() != null) {
            sb.append("【当前任务】\n");
            sb.append(workingMemory.getCurrentTask()).append("\n");
            if (workingMemory.getIntermediateResults() != null && !workingMemory.getIntermediateResults().isEmpty()) {
                sb.append("已完成: ").append(workingMemory.getIntermediateResults().size()).append(" 步\n");
            }
            sb.append("\n");
        }

        // 3. 近期对话摘要
        if (recentSummaries != null && !recentSummaries.isEmpty()) {
            sb.append("【近期工作】\n");
            for (int i = 0; i < Math.min(3, recentSummaries.size()); i++) {
                MemoryEntry summary = recentSummaries.get(i);
                sb.append("- ").append(summary.getCompressedContent()).append("\n");
            }
            sb.append("\n");
        }

        // 4. 相关记忆
        if (relevantMemories != null && !relevantMemories.isEmpty()) {
            sb.append("【相关历史】\n");
            for (MemoryEntry memory : relevantMemories) {
                sb.append("[").append(memory.getType()).append("] ");
                sb.append(memory.getCompressedContent()).append("\n");
            }
        }

        return sb.toString();
    }
}
```

### 4.4 核心组件实现

#### 4.4.1 WorkingMemory（工作记忆）

```java
// com.ai.memory.WorkingMemory
@Service
public class WorkingMemory {

    private static final String REDIS_PREFIX = "working:";
    private static final long SESSION_TTL_MINUTES = 35;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // ========== 任务状态管理 ==========

    /**
     * 保存当前任务状态
     */
    public void saveTaskState(String sessionId, TaskState state) {
        String key = REDIS_PREFIX + sessionId + ":task";
        redisTemplate.opsForValue().set(key, state, SESSION_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 获取当前任务状态
     */
    public TaskState getTaskState(String sessionId) {
        String key = REDIS_PREFIX + sessionId + ":task";
        return (TaskState) redisTemplate.opsForValue().get(key);
    }

    /**
     * 更新任务进度
     */
    public void updateTaskProgress(String sessionId, int progress, String currentStep) {
        TaskState state = getTaskState(sessionId);
        if (state == null) {
            state = new TaskState();
            state.setSessionId(sessionId);
        }
        state.setProgress(progress);
        state.setCurrentStep(currentStep);
        state.setLastUpdatedAt(LocalDateTime.now());
        saveTaskState(sessionId, state);
    }

    // ========== 中间结果管理 ==========

    /**
     * 保存中间结果
     */
    public void saveIntermediateResult(String sessionId, String taskId, String key, Object result) {
        String hashKey = REDIS_PREFIX + sessionId + ":results";
        redisTemplate.opsForHash().put(hashKey, key, result);
        redisTemplate.expire(hashKey, SESSION_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 获取所有中间结果
     */
    public Map<String, Object> getAllIntermediateResults(String sessionId) {
        String hashKey = REDIS_PREFIX + sessionId + ":results";
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(hashKey);
        Map<String, Object> results = new HashMap<>();
        entries.forEach((k, v) -> results.put(String.valueOf(k), v));
        return results;
    }

    // ========== 对话上下文管理 ==========

    /**
     * 保存对话轮次
     */
    public void addConversationTurn(String sessionId, String role, String content) {
        String listKey = REDIS_PREFIX + sessionId + ":conversation";
        ConversationTurn turn = new ConversationTurn(role, content, LocalDateTime.now());
        redisTemplate.opsForList().rightPush(listKey, turn);
        redisTemplate.opsForList().trim(listKey, -20, -1); // 只保留最近 20 轮
        redisTemplate.expire(listKey, SESSION_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 获取最近对话
     */
    public List<ConversationTurn> getRecentConversation(String sessionId, int limit) {
        String listKey = REDIS_PREFIX + sessionId + ":conversation";
        List<ConversationTurn> turns = redisTemplate.opsForList().range(listKey, -limit, -1);
        return turns != null ? turns : List.of();
    }

    // ========== 清理 ==========

    /**
     * 清理会话工作记忆
     */
    public void clear(String sessionId) {
        Set<String> keys = redisTemplate.keys(REDIS_PREFIX + sessionId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 获取工作记忆快照
     */
    public WorkingMemorySnapshot getSnapshot(String sessionId) {
        WorkingMemorySnapshot snapshot = new WorkingMemorySnapshot();
        snapshot.setTaskState(getTaskState(sessionId));
        snapshot.setIntermediateResults(getAllIntermediateResults(sessionId));
        snapshot.setRecentConversation(getRecentConversation(sessionId, 10));
        return snapshot;
    }
}

/**
 * 任务状态
 */
@Data
public class TaskState implements Serializable {
    private String sessionId;
    private String taskDescription;    // 任务描述
    private int progress;              // 进度 0-100
    private String currentStep;       // 当前步骤
    private List<String> completedSteps; // 已完成步骤
    private Map<String, Object> metadata; // 任务元数据
    private LocalDateTime startedAt;
    private LocalDateTime lastUpdatedAt;
}
```

#### 4.4.2 MemoryCompressor（记忆压缩）

```java
// com.ai.memory.MemoryCompressor
@Service
public class MemoryCompressor {

    private final McpModelService modelService;
    private final MemoryWorthinessEvaluator worthinessEvaluator;

    private static final String SUMMARY_PROMPT = """
        请将以下对话压缩为简洁的摘要，保留关键信息。

        对话内容:
        %s

        要求:
        1. 保留关键实体 (用户/公司/产品/指标)
        2. 保留用户的主要需求和意图
        3. 保留重要的中间结论
        4. 删除冗余的对话细节
        5. 长度控制在 150 字以内

        直接输出摘要，不要其他内容。
        """;

    private static final String ENTITY_EXTRACT_PROMPT = """
        从以下文本中提取关键实体和关系。

        文本: %s

        提取以下信息:
        1. 人物: (如有)
        2. 公司/组织: (如有)
        3. 产品/服务: (如有)
        4. 时间/期限: (如有)
        5. 数字/指标: (如有)

        输出 JSON 格式:
        {
            "entities": [...],
            "relations": [...],
            "topicTags": [...]
        }
        """;

    private static final String USER_PROFILE_PROMPT = """
        从以下对话中提取用户画像信息。

        对话: %s

        提取:
        1. 用户角色/职位
        2. 用户行业
        3. 沟通偏好 (简洁/详细)
        4. 专业领域
        5. 常用数据源或工具

        输出 JSON 格式:
        {
            "role": "...",
            "industry": "...",
            "communicationStyle": "CONCISE|DETAILED",
            "expertiseAreas": [...],
            "preferredFormat": "CHART|TEXT|BOTH"
        }
        """;

    /**
     * 压缩对话为摘要
     */
    public MemoryEntry compressConversation(String userMessage, String assistantReply, String sessionId) {
        String content = "用户: " + userMessage + "\n助手: " + assistantReply;

        // 1. 判断是否值得存储
        if (!worthinessEvaluator.isWorthStoring(content)) {
            return null;
        }

        // 2. LLM 生成摘要
        String prompt = SUMMARY_PROMPT.formatted(content);
        String summary = modelService.callModel(prompt);

        // 3. 提取实体
        Map<String, Object> extracted = extractEntities(content);

        // 4. 构建记忆条目
        MemoryEntry entry = new MemoryEntry();
        entry.setMemoryId(UUID.randomUUID().toString());
        entry.setSessionId(sessionId);
        entry.setContent(content);
        entry.setCompressedContent(summary);
        entry.setType(MemoryType.SUMMARY);
        entry.setSource(MemorySource.AGENT_EXTRACTED);
        entry.setKeyEntities((List<String>) extracted.get("entities"));
        entry.setTopicTags((List<String>) extracted.get("topicTags"));
        entry.setCreatedAt(LocalDateTime.now());

        return entry;
    }

    /**
     * 批量压缩历史对话
     */
    public String compressConversationHistory(List<ConversationMessage> messages, int maxTokens) {
        StringBuilder sb = new StringBuilder();
        int tokenCount = 0;

        for (int i = messages.size() - 1; i >= 0 && tokenCount < maxTokens; i--) {
            ConversationMessage msg = messages.get(i);
            String turn = msg.getRole() + ": " + msg.getContent();
            sb.insert(0, turn + "\n\n");
            tokenCount += turn.length() / 4; // 粗略估算
        }

        String prompt = SUMMARY_PROMPT.formatted(sb.toString());
        return modelService.callModel(prompt);
    }

    /**
     * 提取实体和关系
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractEntities(String text) {
        String prompt = ENTITY_EXTRACT_PROMPT.formatted(text);
        String result = modelService.callModel(prompt);

        try {
            return objectMapper.readValue(result, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse entity extraction result", e);
            return Map.of("entities", List.of(), "topicTags", List.of());
        }
    }

    /**
     * 从对话中提取/更新用户画像
     */
    public UserProfile extractUserProfile(List<ConversationMessage> recentMessages) {
        StringBuilder sb = new StringBuilder();
        for (ConversationMessage msg : recentMessages) {
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        String prompt = USER_PROFILE_PROMPT.formatted(sb.toString());
        String result = modelService.callModel(prompt);

        try {
            return objectMapper.readValue(result, UserProfile.class);
        } catch (Exception e) {
            log.warn("Failed to parse user profile result", e);
            return null;
        }
    }
}

/**
 * 记忆价值评估器
 */
@Service
public class MemoryWorthinessEvaluator {

    // 明确要求记住的关键词
    private static final Set<String> REMEMBER_KEYWORDS = Set.of(
        "记住", "别忘了", "以后都用", "一直用", "我是", "我的公司", "我的名字"
    );

    // 敏感词 - 不存储
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
        "密码", "password", "密钥", "secret", "银行卡", "身份证"
    );

    // 无价值的对话模式
    private static final Set<String> LOW_VALUE_PATTERNS = Set.of(
        "谢谢", "好的", "收到", "了解", "好的好的"
    );

    /**
     * 判断对话是否值得存储
     */
    public boolean isWorthStoring(String content) {
        // 规则1：明确声明要记住的
        for (String keyword : REMEMBER_KEYWORDS) {
            if (content.contains(keyword)) {
                return true;
            }
        }

        // 规则2：包含敏感词 - 不存储
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (content.toLowerCase().contains(keyword.toLowerCase())) {
                return false;
            }
        }

        // 规则3：全是无价值内容 - 不存储
        if (isAllLowValue(content)) {
            return false;
        }

        // 规则4：包含关键实体
        if (containsKeyEntities(content)) {
            return true;
        }

        // 默认：值得存储（保守策略）
        return true;
    }

    private boolean isAllLowValue(String content) {
        String lower = content.toLowerCase();
        int matchCount = 0;
        for (String pattern : LOW_VALUE_PATTERNS) {
            if (lower.contains(pattern)) {
                matchCount++;
            }
        }
        return matchCount >= 2 && content.length() < 50;
    }

    private boolean containsKeyEntities(String content) {
        // 检查是否包含公司名模式、人名模式、数字等
        return content.matches(".*\\d{4,}.*") || // 大数字
               content.contains("公司") ||
               content.contains("电商") ||
               content.contains("销售") ||
               content.contains("%");
    }
}
```

#### 4.4.3 MemoryManager（统一记忆管理器）

```java
// com.ai.memory.MemoryManager
@Service
public class MemoryManager {

    private final WorkingMemory workingMemory;
    private final ShortTermMemory shortTermMemory;
    private final LongTermMemory longTermMemory;
    private final MemoryCompressor compressor;

    // 配置
    @Value("${app.memory.compression-threshold:10}")
    private int compressionThreshold; // 每N轮对话压缩一次

    @Value("${app.memory.short-term-ttl-days:30}")
    private int shortTermTtlDays;

    @Value("${app.memory.long-term-promotion-threshold:7}")
    private int longTermPromotionDays;

    // ========== 写入接口 ==========

    /**
     * 记录对话 (自动分层处理)
     */
    public void recordConversation(String sessionId, String userId, String tenantId,
                                   String userMessage, String assistantReply) {
        // 1. 直接写入工作记忆
        workingMemory.addConversationTurn(sessionId, "user", userMessage);
        workingMemory.addConversationTurn(sessionId, "assistant", assistantReply);

        // 2. 判断是否需要压缩
        List<ConversationTurn> recentTurns = workingMemory.getRecentConversation(sessionId, 100);
        if (recentTurns.size() >= compressionThreshold) {
            // 触发压缩
            triggerCompression(sessionId, userId, tenantId, recentTurns);
        }
    }

    /**
     * 触发压缩流程
     */
    private void triggerCompression(String sessionId, String userId, String tenantId,
                                   List<ConversationTurn> recentTurns) {
        // 1. 转换为消息格式
        List<ConversationMessage> messages = recentTurns.stream()
            .map(t -> {
                ConversationMessage msg = new ConversationMessage();
                msg.setRole(t.getRole());
                msg.setContent(t.getContent());
                return msg;
            })
            .toList();

        // 2. LLM 压缩摘要
        String summary = compressor.compressConversationHistory(messages, 2000);

        // 3. 提取实体
        Map<String, Object> entities = compressor.extractEntities(
            recentTurns.stream()
                .map(ConversationTurn::getContent)
                .collect(Collectors.joining("\n"))
        );

        // 4. 构建短期记忆
        MemoryEntry shortTermMemoryEntry = new MemoryEntry();
        shortTermMemoryEntry.setMemoryId(UUID.randomUUID().toString());
        shortTermMemoryEntry.setUserId(userId);
        shortTermMemoryEntry.setTenantId(tenantId);
        shortTermMemoryEntry.setSessionId(sessionId);
        shortTermMemoryEntry.setTier(MemoryTier.SHORT_TERM);
        shortTermMemoryEntry.setType(MemoryType.SUMMARY);
        shortTermMemoryEntry.setCompressedContent(summary);
        shortTermMemoryEntry.setKeyEntities((List<String>) entities.get("entities"));
        shortTermMemoryEntry.setTopicTags((List<String>) entities.get("topicTags"));
        shortTermMemoryEntry.setDecayWeight(1.0);
        shortTermMemoryEntry.setCreatedAt(LocalDateTime.now());
        shortTermMemoryEntry.setExpiresAt(LocalDateTime.now().plusDays(shortTermTtlDays));

        // 5. 保存到短期记忆
        shortTermMemory.save(shortTermMemoryEntry);

        log.info("Compressed {} conversation turns into summary for session {}",
                recentTurns.size(), sessionId);
    }

    /**
     * 保存任务状态
     */
    public void saveTaskState(String sessionId, TaskState state) {
        workingMemory.saveTaskState(sessionId, state);
    }

    /**
     * 保存中间结果
     */
    public void saveIntermediateResult(String sessionId, String taskId, String key, Object result) {
        workingMemory.saveIntermediateResult(sessionId, taskId, key, result);
    }

    /**
     * 更新/提取用户画像
     */
    public void updateUserProfile(String userId, String tenantId, List<ConversationMessage> recentMessages) {
        UserProfile profile = compressor.extractUserProfile(recentMessages);
        if (profile != null) {
            profile.setUserId(userId);
            profile.setTenantId(tenantId);
            profile.setUpdatedAt(LocalDateTime.now());
            longTermMemory.saveUserProfile(profile);
        }
    }

    // ========== 读取接口 ==========

    /**
     * 获取完整记忆上下文 (核心接口)
     */
    public MemoryContext getMemoryContext(String sessionId, String userId, String tenantId, String query) {
        MemoryContext context = new MemoryContext();

        // 1. 工作记忆: 当前会话的所有状态
        WorkingMemorySnapshot snapshot = workingMemory.getSnapshot(sessionId);
        context.setWorkingMemory(snapshot);

        // 2. 短期记忆: 最近对话摘要
        List<MemoryEntry> recentSummaries = shortTermMemory.getRecentSummaries(sessionId, 5);
        context.setRecentSummaries(recentSummaries);

        // 3. 长期记忆: 用户画像
        UserProfile profile = longTermMemory.getUserProfile(userId, tenantId);
        context.setUserProfile(profile);

        // 4. 长期记忆: 相关历史 (向量检索)
        if (query != null && !query.isBlank()) {
            List<MemoryEntry> relevantMemories = longTermMemory.searchRelevant(
                userId, tenantId, query, 3);
            context.setRelevantMemories(relevantMemories);
        }

        // 5. 上下文关联度评分
        context.setContextRelevanceScore(calculateContextRelevance(context));

        return context;
    }

    /**
     * 搜索相关记忆
     */
    public List<MemoryEntry> searchMemories(String userId, String tenantId, String query, int topK) {
        return longTermMemory.searchRelevant(userId, tenantId, query, topK);
    }

    // ========== 衰减管理 ==========

    /**
     * 应用时间衰减 (定时任务)
     */
    @Scheduled(cron = "${app.memory.decay-cron:0 0 3 * * ?}") // 每天凌晨3点
    public void applyDecay() {
        // 1. 短期记忆衰减
        shortTermMemory.applyDecay();

        // 2. 长期记忆权重更新
        longTermMemory.applyDecay();

        // 3. 晋升高质量短期记忆到长期记忆
        promoteHighValueMemories();

        // 4. 清理过期记忆
        shortTermMemory.cleanupExpired();
    }

    /**
     * 晋升高质量短期记忆到长期记忆
     */
    private void promoteHighValueMemories() {
        List<MemoryEntry> candidates = shortTermMemory.getHighValueCandidates();

        for (MemoryEntry candidate : candidates) {
            // 检查是否满足晋升条件
            if (candidate.getDecayWeight() > 0.8 && candidate.getAccessCount() >= 3) {
                // LLM 生成深度摘要
                String deepSummary = compressor.compressConversationHistory(
                    List.of(new ConversationMessage() {{
                        setContent(candidate.getCompressedContent());
                    }}),
                    500
                );

                // 移动到长期记忆
                candidate.setTier(MemoryTier.LONG_TERM);
                candidate.setCompressedContent(deepSummary);
                longTermMemory.save(candidate);

                // 从短期记忆移除
                shortTermMemory.delete(candidate.getMemoryId());
            }
        }
    }
}
```

#### 4.4.4 ShortTermMemory（短期记忆）

```java
// com.ai.memory.ShortTermMemory
@Service
public class ShortTermMemory {

    private final MemoryEntryRepository repository;
    private final VectorMemoryService vectorMemoryService;

    private static final double DECAY_RATE = 0.95; // 每天衰减 5%
    private static final double DELETE_THRESHOLD = 0.1; // 低于此值删除
    private static final double ARCHIVE_THRESHOLD = 0.3; // 低于此值归档

    /**
     * 保存短期记忆
     */
    public void save(MemoryEntry entry) {
        repository.save(entry);

        // 同时向量化存储
        if (entry.getCompressedContent() != null) {
            try {
                vectorMemoryService.indexKnowledge(
                    entry.getCompressedContent(),
                    "short_term_" + entry.getMemoryId(),
                    entry.getTenantId(),
                    entry.getUserId()
                );
            } catch (Exception e) {
                log.warn("Failed to vectorize short-term memory", e);
            }
        }
    }

    /**
     * 获取最近摘要
     */
    public List<MemoryEntry> getRecentSummaries(String sessionId, int limit) {
        return repository.findBySessionIdAndTypeOrderByCreatedAtDesc(
            sessionId, MemoryType.SUMMARY, PageRequest.of(0, limit));
    }

    /**
     * 应用衰减
     */
    public void applyDecay() {
        List<MemoryEntry> entries = repository.findByTierAndExpiresAtBefore(
            MemoryTier.SHORT_TERM, LocalDateTime.now());

        for (MemoryEntry entry : entries) {
            // 时间衰减
            long daysSinceCreation = ChronoUnit.DAYS.between(
                entry.getCreatedAt(), LocalDateTime.now());
            double timeDecay = Math.pow(DECAY_RATE, daysSinceCreation);

            // 访问频率加成
            double accessBoost = 1.0 + (entry.getAccessCount() * 0.1);
            accessBoost = Math.min(accessBoost, 2.0);

            // 重要度加成
            double importanceBoost = 1.0;
            if (entry.getMetadata() != null && entry.getMetadata().containsKey("importance")) {
                importanceBoost = (Integer) entry.getMetadata().get("importance") / 3.0;
            }

            // 最终权重
            double newWeight = timeDecay * accessBoost * importanceBoost;
            entry.setDecayWeight(newWeight);

            // 判断处理方式
            if (newWeight < DELETE_THRESHOLD) {
                repository.delete(entry);
                log.debug("Deleted low-value short-term memory: {}", entry.getMemoryId());
            } else if (newWeight < ARCHIVE_THRESHOLD) {
                // 归档处理 - 可以移到冷存储或降级
                entry.setTier(MemoryTier.LONG_TERM); // 保留但降低优先级
                repository.save(entry);
            } else {
                repository.save(entry);
            }
        }
    }

    /**
     * 获取高价值候选 (用于晋升长期记忆)
     */
    public List<MemoryEntry> getHighValueCandidates() {
        return repository.findHighValueCandidates(
            MemoryTier.SHORT_TERM,
            PageRequest.of(0, 100)
        );
    }

    /**
     * 清理过期记忆
     */
    public void cleanupExpired() {
        repository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    /**
     * 增加访问计数
     */
    public void incrementAccessCount(String memoryId) {
        repository.findById(memoryId).ifPresent(entry -> {
            entry.setAccessCount(entry.getAccessCount() + 1);
            entry.setLastAccessedAt(LocalDateTime.now());
            repository.save(entry);
        });
    }
}
```

#### 4.4.5 LongTermMemory（长期记忆）

```java
// com.ai.memory.LongTermMemory

@Service
public class LongTermMemory {

    private final UserProfileRepository profileRepository;
    private final MemoryEntryRepository memoryRepository;
    private final VectorMemoryService vectorMemoryService;

    /**
     * 保存用户画像
     */
    public void saveUserProfile(UserProfile profile) {
        UserProfile existing = profileRepository.findByUserIdAndTenantId(
            profile.getUserId(), profile.getTenantId());

        if (existing != null) {
            // 合并更新 (保留历史数据)
            mergeProfile(existing, profile);
            profileRepository.save(existing);
        } else {
            profileRepository.save(profile);
        }
    }

    /**
     * 获取用户画像
     */
    public UserProfile getUserProfile(String userId, String tenantId) {
        return profileRepository.findByUserIdAndTenantId(userId, tenantId);
    }

    /**
     * 保存长期记忆
     */
    public void save(MemoryEntry entry) {
        memoryRepository.save(entry);

        // 向量化存储
        if (entry.getCompressedContent() != null) {
            vectorMemoryService.indexKnowledge(
                entry.getCompressedContent(),
                "long_term_" + entry.getMemoryId(),
                entry.getTenantId(),
                entry.getUserId()
            );
        }
    }

    /**
     * 搜索相关记忆 (向量检索)
     */
    public List<MemoryEntry> searchRelevant(String userId, String tenantId, String query, int topK) {
        // 1. 向量检索
        String searchResult = vectorMemoryService.searchRelevant(query, topK, 0.5, tenantId);

        // 2. 解析检索结果，提取 memory ID
        List<String> memoryIds = parseMemoryIdsFromSearchResult(searchResult);

        // 3. 从数据库获取完整记录
        List<MemoryEntry> entries = memoryRepository.findByMemoryIdsAndTenantId(
            memoryIds, tenantId);

        // 4. 按相关性排序
        return entries.stream()
            .sorted((a, b) -> {
                int indexA = memoryIds.indexOf(a.getMemoryId());
                int indexB = memoryIds.indexOf(b.getMemoryId());
                return Integer.compare(indexA, indexB);
            })
            .limit(topK)
            .toList();
    }

    /**
     * 应用衰减 (长期记忆衰减较慢)
     */
    public void applyDecay() {
        // 长期记忆衰减更慢，只降低很少被访问的记忆权重
        List<MemoryEntry> entries = memoryRepository.findByTier(MemoryTier.LONG_TERM);

        for (MemoryEntry entry : entries) {
            // 90天没访问才衰减
            long daysSinceAccess = ChronoUnit.DAYS.between(
                entry.getLastAccessedAt(), LocalDateTime.now());

            if (daysSinceAccess > 90) {
                double newWeight = entry.getDecayWeight() * 0.99; // 每月只衰减 1%
                entry.setDecayWeight(newWeight);
                memoryRepository.save(entry);
            }
        }
    }

    /**
     * 合并用户画像
     */
    private void mergeProfile(UserProfile existing, UserProfile updated) {
        // 角色取最新的
        if (updated.getRole() != null) {
            existing.setRole(updated.getRole());
        }

        // 行业取最新的
        if (updated.getIndustry() != null) {
            existing.setIndustry(updated.getIndustry());
        }

        // 专业知识取并集
        if (updated.getExpertiseAreas() != null && !updated.getExpertiseAreas().isEmpty()) {
            Set<String> merged = new HashSet<>(existing.getExpertiseAreas());
            merged.addAll(updated.getExpertiseAreas());
            existing.setExpertiseAreas(List.copyOf(merged));
        }

        // 常用术语取并集
        if (updated.getFrequentlyUsedTerms() != null) {
            Set<String> merged = new HashSet<>(existing.getFrequentlyUsedTerms());
            merged.addAll(updated.getFrequentlyUsedTerms());
            existing.setFrequentlyUsedTerms(List.copyOf(merged));
        }

        existing.setUpdatedAt(LocalDateTime.now());
    }
}
```

### 4.5 Phase 1 实施任务清单

| # | 任务 | 优先级 | 涉及文件 | 工作量 | 状态 |
|---|------|--------|---------|--------|------|
| 1.1 | 设计 Memory 数据模型 | P0 | 新建 `memory/dto/` | 2d | ✅ 已完成：`MemoryEntry`、`MemoryTier`、`MemoryType`、`MemorySource` 和 DTO 已落地 |
| 1.2 | 实现 WorkingMemory (Redis) | P0 | `memory/WorkingMemory.java` | 2d | ✅ 已完成：工作记忆组件已接入，支持会话级短上下文 |
| 1.3 | 实现 MemoryCompressor | P0 | `memory/MemoryCompressor.java` | 3d | ✅ 已完成：确定性压缩和模型压缩实现已具备 |
| 1.4 | 实现 MemoryWorthinessEvaluator | P0 | `memory/MemoryWorthinessEvaluator.java` | 1d | ✅ 已完成：记忆价值判断与测试已覆盖 |
| 1.5 | 实现 ShortTermMemory | P0 | `memory/ShortTermMemory.java` | 2d | ✅ 已完成：短期记忆存储与上下文读取已具备 |
| 1.6 | 实现 LongTermMemory | P0 | `memory/LongTermMemory.java` | 2d | ✅ 已完成：长期记忆持久化和向量索引已接入 |
| 1.7 | 实现 UserProfileMemory | P1 | `memory/UserProfileMemory.java` | 2d | ✅ 已完成：用户画像提取、刷新和查询接口已具备 |
| 1.8 | 实现 MemoryManager (核心) | P0 | `memory/MemoryManager.java` | 3d | ✅ 已完成：统一记忆捕获、检索、画像和治理入口 |
| 1.9 | 改造 ConversationSession | P0 | `model/ConversationSession.java` | 1d | ✅ 已完成：会话上下文保留轻量历史，长期上下文交由 MemoryManager |
| 1.10 | 改造 VectorMemoryService | P0 | `service/VectorMemoryService.java` | 2d | ✅ 已完成：长期记忆向量索引走 Milvus，内存向量路径已移除 |
| 1.11 | 改造 ReActRequestContextBuilder | P0 | `agent/ReActRequestContextBuilder.java` | 1d | ✅ 已完成：RAG 和记忆上下文并行构建并注入 Prompt |
| 1.12 | 添加定时衰减任务 | P1 | `memory/MemoryDecayScheduler.java` | 1d | ✅ 已完成：记忆衰减、保留策略和维护任务已接入 |
| 1.13 | 单元测试 | P0 | `memory/*Test.java` | 3d | ✅ 已完成：覆盖压缩、价值判断、治理、配额、保留、画像等 |
| 1.14 | 集成测试 | P0 | `test/java/com/ai/memory/` | 3d | ✅ 已完成：跨会话记忆集成测试通过 |

**Phase 1 总工期：约 28 个工作日**

### 4.6 讨论议题

#### 议题 1：压缩触发策略

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| A: 固定轮数 | 每 N 轮对话触发压缩 | 简单可控 | 不考虑对话复杂度 |
| B: Token 阈值 | Token 达到阈值触发 | 精确控制上下文大小 | 实现复杂 |
| C: 语义检测 | LLM 判断是否需要压缩 | 质量最高 | 成本高、延迟大 |
| D: 混合模式 | 固定轮数 + 语义增强 | 平衡 | 实现中等复杂 |

**建议：方案 D（混合模式）**

#### 议题 2：短期记忆保留时长

| 选项 | 时长 | 适用场景 |
|------|------|---------|
| 保守 | 7 天 | 存储敏感或成本敏感场景 |
| 标准 | 30 天 | 大多数企业场景 |
| 宽松 | 90 天 | 高价值用户、需要更多上下文 |

**建议：30 天（标准），高价值用户可配置延长**

#### 议题 3：摘要压缩粒度

| 方案 | 描述 | Token 成本 | 质量 |
|------|------|-----------|------|
| 每轮压缩 | 每轮对话单独压缩 | 高 | 一般 |
| 批量压缩 | N 轮一起压缩 | 低 | 较好 |
| 渐进压缩 | 每轮压缩 + 定期汇总 | 中 | 好 |

**建议：渐进压缩（每 10 轮压缩 + 定期全局汇总）**

---

## 五、Phase 2：智能分块 + RAG 增强

### 5.1 为什么要升级分块和 RAG？

#### 5.1.1 当前问题：固定分块的痛苦

```
用户上传了一份 50 页的 PDF 文档：
"XX电商2024年度运营报告.pdf"

文档内容：
├── 第1章：公司介绍（1-5页）
├── 第2章：2024年销售数据（6-20页）
│   ├── 2.1 各平台销售概览
│   ├── 2.2 月度销售趋势
│   └── 2.3 爆款产品分析
├── 第3章：用户画像分析（21-30页）
├── 第4章：竞品对比（31-40页）
└── 第5章：2025年规划（41-50页）

用户问：各平台的销售占比是多少？

当前系统的处理：
1. 把 50 页文档按 500 字/块切分 → 约 50 个 chunk
2. 用户问"销售占比" → 向量检索
3. 命中的 chunk：可能是"2.1 各平台销售概览"（有关）
4. 命中的 chunk：可能是"第3章 用户画像"（无关）
5. 命中的 chunk：可能是"2.3 爆款产品分析"（部分相关）

问题：
❌ chunk 被截断在页面中间，语义不完整
❌ "各平台"在第2章，"用户画像"在第3章，被切分到一起
❌ 表格被截断，一行在上一个chunk，下一行在下一个chunk
❌ 检索结果噪声大，需要在 Prompt 里塞很多无关上下文
❌ LLM 回答时只能看到碎片，不知道这段数据属于哪个章节
```

#### 5.1.2 好分块的体验

```
用户问：各平台的销售占比是多少？

智能分块后的处理：
1. 按文档结构解析：
   - 识别标题层级（# 第2章 > ## 2.1）
   - 识别段落边界
   - 识别表格边界
   - 识别代码块边界

2. 语义分块：
   - 2.1 各平台销售概览 → 完整的一个 chunk（包含完整表格）
   - 2.3 爆款产品分析 → 完整的一个 chunk
   - 表格不会被截断

3. 元数据增强：
   - 每个 chunk 记录：章节名、页码、文档标题
   - 检索结果可以显示："来自 第2章 > 2.1 各平台销售概览"

4. 层级分块：
   - Parent Chunk：大上下文（整章，2000字）
   - Child Chunk：小检索单元（每个小节，300-500字）
   - 命中 Child → 返回 Parent 完整上下文

结果：
✅ 检索精准：只命中"各平台销售"相关内容
✅ 上下文完整：表格完整，不会被截断
✅ 可溯源：用户知道答案来自哪个章节
✅ LLM 看到的是完整的语义单元，而不是碎片
```

#### 5.1.3 当前 RAG 的问题

```
用户问：对比2024年和2023年的销售趋势

当前 RAG 的问题：
1. ❌ 查询改写缺失：
   - 用户说"2024年和2023年" → 可能文档写的是"今年"和"去年"
   - 语义不匹配，检索效果差

2. ❌ 混合检索缺失：
   - 只用向量检索 → 关键词"同比"可能匹配不到
   - 纯向量检索对专有名词不友好

3. ❌ 重排序缺失：
   - candidateTopK=20 → topK=6 只是简单截断
   - 没有精排，可能把"2024年Q1"排在"2023年总结"前面

4. ❌ 引用溯源缺失：
   - 回答中不标注来源
   - 用户无法验证答案的准确性
   - 无法追溯到具体文档位置
```

#### 5.1.4 好 RAG 的体验

```
用户问：对比2024年和2023年的销售趋势

深度 RAG 的处理：

1. 查询改写（Query Rewriting）：
   用户问："对比2024年和2023年的销售趋势"
   改写为：
   - "2024年销售趋势分析"
   - "2023年销售趋势分析"
   - "同比增长率"
   - "年度销售对比"
   同时检索多个子查询，结果融合

2. 混合检索（Hybrid Retrieval）：
   - 向量检索：语义相似度
   - BM25：关键词匹配（同比/环比/增长率）
   - Reciprocal Rank Fusion：融合排序

3. Cross-Encoder 重排序：
   - 从 20 个候选 → 精排到 6 个最相关的
   - 综合考虑：语义相关 + 关键词匹配 + 多样性

4. 引用溯源（Citations）：
   回答格式：
   """
   2024年销售额同比增长15%，主要驱动因素是...

   具体数据：
   - 天猫平台：增长 20%[来源1]
   - 京东平台：增长 12%[来源2]

   [来源1] XX电商2024年度运营报告，第2章，2.2节，月度销售趋势，P15
   [来源2] XX电商2024年度运营报告，第2章，2.1节，各平台销售概览，P12
   """
```

### 5.2 分块策略详解

#### 5.2.1 分块策略对比

```
┌─────────────────────────────────────────────────────────┐
│              分块策略对比                                 │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  【固定长度分块】当前方案 ❌                              │
│  ├── 优点：实现简单                                      │
│  ├── 缺点：语义割裂、表格截断、上下文丢失               │
│  └── 适用：非结构化纯文本                                │
│                                                          │
│  【语义分块】P0 ✅                                      │
│  ├── 优点：保持语义完整、边界清晰                        │
│  ├── 缺点：实现较复杂                                    │
│  └── 适用：结构化文档、段落分明的文本                    │
│                                                          │
│  【层级分块】P1                                         │
│  ├── 优点：大上下文+精检索、平衡精度和完整性              │
│  ├── 缺点：存储成本翻倍                                  │
│  └── 适用：长文档、需要精确检索的场景                    │
│                                                          │
│  【文档结构分块】P0 ✅                                  │
│  ├── 优点：利用文档固有结构、保真度高                    │
│  ├── 缺点：依赖文档格式规范                              │
│  └── 适用：Markdown/PDF/HTML 等结构化文档               │
│                                                          │
│  【表格分块】P1                                         │
│  ├── 优点：表格完整不截断、行列关系保留                  │
│  ├── 缺点：需要识别表格边界                              │
│  └── 适用：包含大量数据表格的文档                        │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

#### 5.2.2 分块质量评估指标

```
┌─────────────────────────────────────────────────────────┐
│              分块质量评估指标                              │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1️⃣ 语义完整性                                        │
│     └── 每个 chunk 是否有独立完整的语义？                  │
│     └── 是否避免了语义在半句处截断？                      │
│                                                          │
│  2️⃣ 上下文保持                                        │
│     └── 相关的段落是否在同一个 chunk？                   │
│     └── 表格/列表是否保持完整？                          │
│                                                          │
│  3️⃣ 长度控制                                          │
│     └── chunk 长度是否在合理范围（300-800字）？          │
│     └── 是否避免了过长/过短的极端情况？                  │
│                                                          │
│  4️⃣ 重叠度合理性                                      │
│     └── 边界处是否有足够重叠供上下文衔接？               │
│     └── 重叠是否足够小以避免重复？                      │
│                                                          │
│  5️⃣ 元数据丰富度                                       │
│     └── 是否包含章节/标题/页码等追溯信息？               │
│     └── 是否包含文档级别的大上下文？                      │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 5.3 RAG 增强详解

#### 5.3.1 深度 RAG Pipeline

```
┌─────────────────────────────────────────────────────────┐
│              深度 RAG Pipeline                           │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  用户查询: "对比2024年和2023年的销售趋势"               │
│                    │                                     │
│                    ▼                                     │
│  ┌─────────────────────────────────────────────────┐   │
│  │ 1. Query Understanding (查询理解)                │   │
│  │ ├── 查询改写: 扩展同义词/近义词                   │   │
│  │ ├── 查询分解: 拆分为多个子查询                    │   │
│  │ ├── 查询分类: 事实型/分析型/比较型               │   │
│  │ └── HyDE: 生成假设答案引导检索                   │   │
│  └─────────────────────────────────────────────────┘   │
│                    │                                     │
│                    ▼                                     │
│  ┌─────────────────────────────────────────────────┐   │
│  │ 2. Hybrid Retrieval (混合检索)                     │   │
│  │ ├── 向量检索: 语义相似度 (Embedding)             │   │
│  │ ├── 关键词检索: BM25 / 全文索引                  │   │
│  │ ├── 知识图谱检索: 实体关系 (可选)                │   │
│  │ └── RRF融合: Reciprocal Rank Fusion              │   │
│  └─────────────────────────────────────────────────┘   │
│                    │                                     │
│                    ▼                                     │
│  ┌─────────────────────────────────────────────────┐   │
│  │ 3. Reranking (重排序)                            │   │
│  │ ├── Cross-Encoder 精排 (BGE-reranker)           │   │
│  │ ├── 多样性过滤: 避免返回重复信息                 │   │
│  │ └── 上下文压缩: 冗余 chunk 合并                  │   │
│  └─────────────────────────────────────────────────┘   │
│                    │                                     │
│                    ▼                                     │
│  ┌─────────────────────────────────────────────────┐   │
│  │ 4. Generation (生成)                             │   │
│  │ ├── 上下文组装: 按相关性排序拼接                 │   │
│  │ ├── 引用标注: 标记每个答案片段的来源            │   │
│  │ └── 事实校验: LLM 自检与检索内容一致性          │   │
│  └─────────────────────────────────────────────────┘   │
│                    │                                     │
│                    ▼                                     │
│  回答: "2024年销售额同比增长15%..."                   │
│        [来源1] XX电商报告 P15                          │
│        [来源2] XX电商报告 P12                          │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

#### 5.3.2 Query Rewriting 详解

```java
/**
 * 查询改写：将用户查询转换为多个检索友好的子查询
 */
@Service
public class QueryRewriter {

    private final McpModelService modelService;

    private static final String REWRITE_PROMPT = """
        请将以下用户查询改写为多个检索友好的子查询。

        原始查询: %s

        要求:
        1. 识别查询中的时间表达（2024年 → 今年/2024）
        2. 识别查询中的指代（这家公司 → XX电商）
        3. 识别查询中的同义词（趋势 → 走势/变化/增长）
        4. 分解复杂查询为多个简单查询

        输出格式（JSON数组）:
        ["子查询1", "子查询2", "子查询3"]
        """;

    private static final String DECOMPOSE_PROMPT = """
        将以下复杂查询分解为多个可以独立检索的子查询。

        查询: %s

        分析:
        - 这是事实型/分析型/比较型/列表型查询？
        - 涉及哪些维度（时间/空间/类别/对比）？

        分解:
        - 生成 2-5 个独立的子查询
        - 每个子查询应该能独立检索到相关内容
        """;

    /**
     * 改写查询
     */
    public List<String> rewrite(String query, String context) {
        // 1. 基本改写：同义词扩展、时间表达归一化
        List<String> rewrites = basicRewrite(query);

        // 2. 如果有上下文，替换指代
        if (context != null && !context.isBlank()) {
            rewrites = replaceReferences(rewrites, context);
        }

        // 3. LLM 增强改写
        List<String> llmRewrites = llmRewrite(query);
        rewrites.addAll(llmRewrites);

        // 4. 去重
        return rewrites.stream().distinct().toList();
    }

    /**
     * 查询分解
     */
    public List<String> decompose(String query) {
        String prompt = DECOMPOSE_PROMPT.formatted(query);
        String result = modelService.callModel(prompt);

        try {
            return objectMapper.readValue(result, List.class);
        } catch (Exception e) {
            // 降级：返回原始查询
            return List.of(query);
        }
    }

    /**
     * HyDE: 生成假设答案来引导检索
     */
    public String generateHypotheticalAnswer(String query) {
        String prompt = """
            假设你是这个领域的专家，请针对以下问题生成一个假设性的答案。
            这个答案不需要完全正确，只需要包含可能出现的关键词和概念。

            问题: %s

            直接输出假设答案，不要解释。
            """.formatted(query);

        return modelService.callModel(prompt);
    }

    private List<String> basicRewrite(String query) {
        List<String> rewrites = new ArrayList<>();
        rewrites.add(query);

        // 时间表达归一化
        rewrites.add(query.replace("2024年", "2024")
                          .replace("去年", "2023")
                          .replace("前年", "2022")
                          .replace("今年", "2024")
                          .replace("明年", "2025"));

        // 同义词扩展
        rewrites.add(query.replace("趋势", "走势 变化 增长 趋势")
                          .replace("销售", "销售额 销量 营收"));

        return rewrites;
    }
}
```

#### 5.3.3 Hybrid Retriever 实现

```java
/**
 * 混合检索器：融合向量检索 + BM25 检索
 */
@Service
public class HybridRetriever {

    private final VectorMemoryService vectorMemoryService;
    private final Bm25SearchService bm25SearchService;

    private static final double VECTOR_WEIGHT = 0.6;
    private static final double BM25_WEIGHT = 0.4;

    /**
     * 混合检索
     */
    public List<RetrievalResult> retrieve(String query, int topK, String tenantId) {
        // 1. 并行执行向量检索和 BM25 检索
        List<RetrievalResult> vectorResults = vectorSearch(query, topK * 2, tenantId);
        List<RetrievalResult> bm25Results = bm25Search(query, topK * 2, tenantId);

        // 2. Reciprocal Rank Fusion (RRF)
        List<RetrievalResult> fused = reciprocalRankFusion(vectorResults, bm25Results);

        // 3. 取 Top K
        return fused.stream().limit(topK).toList();
    }

    /**
     * Reciprocal Rank Fusion
     * RRF = Σ 1/(k + rank_i)，k通常取60
     */
    private List<RetrievalResult> reciprocalRankFusion(
            List<RetrievalResult> vectorResults,
            List<RetrievalResult> bm25Results) {

        Map<String, Double> scores = new HashMap<>();
        Map<String, RetrievalResult> resultMap = new HashMap<>();

        // 向量检索得分
        for (int i = 0; i < vectorResults.size(); i++) {
            RetrievalResult r = vectorResults.get(i);
            double score = VECTOR_WEIGHT * (1.0 / (60 + i + 1));
            scores.merge(r.getId(), score, Double::sum);
            resultMap.put(r.getId(), r);
        }

        // BM25 检索得分
        for (int i = 0; i < bm25Results.size(); i++) {
            RetrievalResult r = bm25Results.get(i);
            double score = BM25_WEIGHT * (1.0 / (60 + i + 1));
            scores.merge(r.getId(), score, Double::sum);
            resultMap.put(r.getId(), r);
        }

        // 按融合分数排序
        return scores.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .map(e -> resultMap.get(e.getKey()))
            .toList();
    }
}

/**
 * BM25 检索服务（基于 MySQL 全文索引）
 */
@Service
public class Bm25SearchService {

    private final EntityManager entityManager;

    /**
     * BM25 检索
     */
    public List<RetrievalResult> search(String query, int topK, String tenantId) {
        String sql = """
            SELECT id, content, chunk_metadata,
                   MATCH(content) AGAINST(:query IN NATURAL LANGUAGE MODE) as bm25_score
            FROM knowledge_chunks
            WHERE tenant_id = :tenantId
              AND MATCH(content) AGAINST(:query IN NATURAL LANGUAGE MODE)
            ORDER BY bm25_score DESC
            LIMIT :limit
            """;

        Query nativeQuery = entityManager.createNativeQuery(sql);
        nativeQuery.setParameter("query", query);
        nativeQuery.setParameter("tenantId", tenantId);
        nativeQuery.setParameter("limit", topK);

        List<Object[]> results = nativeQuery.getResultList();

        return results.stream().map(row -> {
            RetrievalResult r = new RetrievalResult();
            r.setId((String) row[0]);
            r.setContent((String) row[1]);
            r.setScore(((Number) row[2]).doubleValue());
            return r;
        }).toList();
    }
}
```

#### 5.3.4 Reranker 实现

```java
/**
 * Cross-Encoder 重排序
 */
@Service
public class Reranker {

    private final HttpClient httpClient;
    private final String rerankerEndpoint;

    private static final int DEFAULT_TOP_K = 6;
    private static final double MIN_SCORE = 0.3;

    /**
     * 重排序
     */
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        try {
            // 构建 BGE-Reranker 请求
            RerankRequest request = new RerankRequest();
            request.setQuery(query);
            request.setDocuments(candidates.stream()
                .map(RetrievalResult::getContent)
                .toList());
            request.setTopK(topK);

            // 调用重排序模型
            RerankResponse response = httpClient.post(rerankerEndpoint, request, RerankResponse.class);

            // 合并结果
            List<RetrievalResult> reranked = new ArrayList<>();
            for (RerankItem item : response.getResults()) {
                RetrievalResult r = candidates.get(item.getIndex());
                r.setRerankScore(item.getScore());
                r.setRank(item.getRank());

                // 过滤低分结果
                if (item.getScore() >= MIN_SCORE) {
                    reranked.add(r);
                }
            }

            return reranked;

        } catch (Exception e) {
            log.warn("Reranking failed, falling back to original order", e);
            return candidates.stream().limit(topK).toList();
        }
    }
}
```

#### 5.3.5 Citation Generator 实现

```java
/**
 * 引用溯源生成器
 */
@Service
public class CitationGenerator {

    /**
     * 生成带引用的回答
     */
    public CitationResponse generateWithCitations(
            String query,
            String answer,
            List<RetrievalResult> contextResults) {

        CitationResponse response = new CitationResponse();
        response.setAnswer(answer);
        response.setCitations(new ArrayList<>());

        Map<String, CitationInfo> citationMap = new LinkedHashMap<>();
        int citationIndex = 1;

        // 为答案中的每个相关陈述添加引用
        String processedAnswer = answer;

        for (RetrievalResult result : contextResults) {
            // 检查答案中是否包含相关关键词
            if (isRelated(result.getContent(), answer)) {
                // 创建引用信息
                CitationInfo citation = new CitationInfo();
                citation.setIndex(citationIndex);
                citation.setSourceId(result.getSourceId());
                citation.setChunkId(result.getChunkId());
                citation.setScore(result.getScore());

                // 从元数据提取文档信息
                Map<String, String> metadata = result.getMetadata();
                citation.setDocumentTitle(metadata.get("document_title"));
                citation.setChapter(metadata.get("chapter"));
                citation.setSection(metadata.get("section"));
                citation.setPage(metadata.get("page"));

                // 生成引用标记
                String citationMark = "[" + citationIndex + "]";
                citation.setMark(citationMark);

                citationMap.put(result.getId(), citation);
                response.getCitations().add(citation);
                citationIndex++;
            }
        }

        // 在答案中标注引用
        processedAnswer = annotateAnswer(answer, citationMap);
        response.setAnswer(processedAnswer);

        return response;
    }

    /**
     * 检查检索结果是否与答案相关
     */
    private boolean isRelated(String content, String answer) {
        // 简单关键词匹配
        String[] answerWords = answer.split("[，。、\\s]");
        int matchCount = 0;
        for (String word : answerWords) {
            if (word.length() > 2 && content.contains(word)) {
                matchCount++;
            }
        }
        return matchCount >= 2;
    }

    /**
     * 在答案中标注引用
     */
    private String annotateAnswer(String answer, Map<String, CitationInfo> citations) {
        String annotated = answer;

        for (CitationInfo citation : citations.values()) {
            // 查找答案中与 citation 内容最匹配的位置
            String content = citation.getSourceId();
            if (content.length() > 20) {
                // 查找内容片段
                String snippet = content.substring(0, Math.min(30, content.length()));
                if (annotated.contains(snippet)) {
                    // 在匹配位置后插入引用标记
                    // 实际实现需要更复杂的文本匹配逻辑
                }
            }
        }

        return annotated;
    }
}

/**
 * 引用信息
 */
@Data
public class CitationInfo {
    private int index;
    private String mark;                    // 引用标记 [1]
    private String sourceId;               // 来源 ID
    private String chunkId;               // Chunk ID
    private String documentTitle;        // 文档标题
    private String chapter;               // 章节
    private String section;               // 小节
    private String page;                 // 页码
    private Double score;                // 相关度评分
}
```

### 5.4 数据模型设计

#### 5.4.1 ChunkMetadata（分块元数据）

```java
// com.ai.vector.dto.ChunkMetadata
@Data
public class ChunkMetadata {

    // ========== 文档信息 ==========
    private String documentId;            // 文档 ID
    private String documentTitle;        // 文档标题
    private String documentType;         // 文档类型: PDF/Markdown/HTML/TXT
    private Long fileSize;              // 文件大小

    // ========== 结构信息 ==========
    private String chapter;              // 章节名
    private String section;              // 小节名
    private Integer headingLevel;        // 标题层级 (1-6)
    private String listType;            // 列表类型: ordered/unordered/table

    // ========== 位置信息 ==========
    private Integer pageNumber;          // 页码
    private Integer paragraphIndex;     // 段落索引
    private Integer chunkIndex;         // Chunk 在文档中的顺序
    private Long charStart;             // 字符起始位置
    private Long charEnd;               // 字符结束位置

    // ========== 层级信息 (用于层级分块) ==========
    private String parentChunkId;        // 父 Chunk ID (null 表示顶层)
    private String rootChunkId;         // 根 Chunk ID (顶层父级)
    private Integer depth;              // 层级深度 (0 = 顶层)

    // ========== 内容特征 ==========
    private Boolean containsTable;      // 是否包含表格
    private Boolean containsCode;       // 是否包含代码
    private Boolean containsList;       // 是否包含列表
    private List<String> detectedLanguage; // 检测到的语言
    private String primaryLanguage;     // 主要语言

    // ========== 质量指标 ==========
    private Double semanticScore;       // 语义完整性评分
    private Integer tokenCount;         // Token 数量
    private Boolean isTruncated;        // 是否被截断

    // ========== 来源追溯 ==========
    private String sourceFileName;     // 原始文件名
    private String sourceUrl;          // 原始 URL (如果是网络文档)
    private LocalDateTime indexedAt;    // 索引时间
}
```

#### 5.4.2 RetrievalResult（检索结果）

```java
// com.ai.rag.dto.RetrievalResult
@Data
public class RetrievalResult {

    private String id;                 // 结果 ID
    private String content;            // 内容
    private Map<String, String> metadata; // 元数据

    // ========== 评分 ==========
    private Double vectorScore;        // 向量检索得分
    private Double bm25Score;          // BM25 得分
    private Double rrfScore;           // RRF 融合得分
    private Double rerankScore;        // Cross-Encoder 重排得分
    private Double finalScore;         // 最终得分

    // ========== 来源信息 ==========
    private String sourceId;           // 来源 ID (文档/知识 ID)
    private String sourceType;         // 来源类型: KNOWLEDGE/FILE/CONVERSATION
    private String chunkId;           // Chunk ID

    // ========== 位置信息 ==========
    private Integer rank;              // 最终排名
    private String documentTitle;      // 文档标题
    private String chapter;            // 章节
    private Integer page;             // 页码

    // ========== 上下文 ==========
    private String parentContext;     // 父级上下文 (如果使用层级分块)
    private String precedingText;     // 前文 (用于上下文拼接)
    private String followingText;      // 后文

    /**
     * 获取展示用的引用格式
     */
    public String getCitationMark() {
        if (documentTitle != null && page != null) {
            return "%s P%s".formatted(documentTitle, page);
        } else if (documentTitle != null) {
            return documentTitle;
        } else {
            return sourceId;
        }
    }
}
```

#### 5.4.3 RagContext（RAG 上下文）

```java
// com.ai.rag.dto.RagContext
@Data
public class RagContext {

    // ========== 检索结果 ==========
    private List<RetrievalResult> results;    // 检索结果列表
    private int totalCandidates;              // 候选总数
    private int finalCount;                  // 最终使用数

    // ========== 检索元数据 ==========
    private String originalQuery;            // 原始查询
    private List<String> rewrittenQueries;   // 改写后的查询
    private String queryType;                 // 查询类型
    private Double contextRelevanceScore;    // 上下文关联度

    // ========== 上下文内容 ==========
    private String assembledContext;          // 组装后的完整上下文
    private String citationsSection;          // 引用区域

    // ========== 统计 ==========
    private Long retrievalTimeMs;           // 检索耗时
    private Long rerankingTimeMs;           // 重排耗时
    private Long totalTimeMs;              // 总耗时

    /**
     * 构建注入 Prompt 的上下文
     */
    public String buildPromptContext() {
        StringBuilder sb = new StringBuilder();

        sb.append("【参考信息】\n\n");

        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            sb.append("资料 ").append(i + 1);
            sb.append(" [").append(r.getSourceType());
            sb.append(" / ").append(r.getCitationMark());
            sb.append(" / score=").append(String.format("%.2f", r.getFinalScore()));
            sb.append("]\n");
            sb.append(r.getContent());
            sb.append("\n\n");
        }

        if (!StringUtils.isBlank(citationsSection)) {
            sb.append("\n【信息来源】\n");
            sb.append(citationsSection);
        }

        return sb.toString();
    }

    /**
     * 构建引用区域
     */
    public String buildCitationsSection() {
        if (results == null || results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【信息来源】\n");

        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            sb.append("[").append(i + 1).append("] ");
            sb.append(r.getCitationMark());
            if (r.getChapter() != null) {
                sb.append(" - ").append(r.getChapter());
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
```

### 5.5 核心组件实现

#### 5.5.1 DocumentStructureParser（文档结构解析）

```java
/**
 * 文档结构解析器
 */
@Service
public class DocumentStructureParser {

    /**
     * 解析文档结构
     */
    public DocumentStructure parse(String content, String documentType) {
        DocumentStructure structure = new DocumentStructure();
        structure.setDocumentType(documentType);
        structure.setChunks(new ArrayList<>());

        switch (documentType.toUpperCase()) {
            case "MARKDOWN":
                parseMarkdown(content, structure);
                break;
            case "PDF":
                parsePdf(content, structure);
                break;
            case "HTML":
                parseHtml(content, structure);
                break;
            case "DOCX":
                parseDocx(content, structure);
                break;
            default:
                parsePlainText(content, structure);
        }

        return structure;
    }

    /**
     * Markdown 解析
     */
    private void parseMarkdown(String content, DocumentStructure structure) {
        String[] lines = content.split("\n");
        SectionNode currentSection = null;
        StringBuilder currentParagraph = new StringBuilder();
        int lineNumber = 0;

        for (String line : lines) {
            lineNumber++;

            if (isHeading(line)) {
                // 保存之前的段落
                if (currentParagraph.length() > 0) {
                    addChunk(structure, currentParagraph.toString(), currentSection);
                    currentParagraph = new StringBuilder();
                }

                // 创建新章节
                currentSection = parseHeading(line, lineNumber);
                structure.getSections().add(currentSection);

            } else if (isTable(line)) {
                // 表格：整表作为一个 chunk
                String tableContent = extractTable(currentParagraph.toString() + "\n" + line);
                addChunk(structure, tableContent, currentSection);
                currentParagraph = new StringBuilder();

            } else if (isCodeBlock(line)) {
                // 代码块：保持完整
                String codeBlock = extractCodeBlock(Arrays.asList(lines), lineNumber);
                addChunk(structure, codeBlock, currentSection);

            } else if (line.trim().isEmpty()) {
                // 空行：段落分隔
                if (currentParagraph.length() > 0) {
                    addChunk(structure, currentParagraph.toString(), currentSection);
                    currentParagraph = new StringBuilder();
                }
            } else {
                // 普通文本
                if (currentParagraph.length() > 0) {
                    currentParagraph.append("\n");
                }
                currentParagraph.append(line);
            }
        }

        // 处理最后一个段落
        if (currentParagraph.length() > 0) {
            addChunk(structure, currentParagraph.toString(), currentSection);
        }
    }

    private boolean isHeading(String line) {
        return line.matches("^#{1,6}\\s+.+");
    }

    private SectionNode parseHeading(String line, int lineNumber) {
        SectionNode section = new SectionNode();
        Matcher matcher = Pattern.compile("^(#{1,6})\\s+(.+)").matcher(line);
        if (matcher.find()) {
            section.setLevel(matcher.group(1).length());
            section.setTitle(matcher.group(2).trim());
            section.setLineNumber(lineNumber);
        }
        return section;
    }

    private boolean isTable(String line) {
        return line.startsWith("|") || line.matches("\\|.*\\|.+)");
    }

    private boolean isCodeBlock(String line) {
        return line.startsWith("```");
    }
}

/**
 * 文档结构
 */
@Data
public class DocumentStructure {
    private String documentType;
    private List<SectionNode> sections;
    private List<ContentChunk> chunks;
    private Map<String, Object> metadata;
}

/**
 * 章节节点
 */
@Data
public class SectionNode {
    private int level;
    private String title;
    private int lineNumber;
    private String path;           // 完整路径: "第2章 > 2.1 各平台销售概览"
    private SectionNode parent;
    private List<SectionNode> children;
}
```

#### 5.5.2 SmartTextChunker（智能分块器）

```java
/**
 * 智能文本分块器
 */
@Service
public class SmartTextChunker {

    private final DocumentStructureParser structureParser;
    private final TextChunker textChunker;

    // 配置
    @Value("${app.chunk.min-size:200}")
    private int minChunkSize;

    @Value("${app.chunk.max-size:800}")
    private int maxChunkSize;

    @Value("${app.chunk.overlap:100}")
    private int overlapSize;

    @Value("${app.chunk.enable-hierarchical:true}")
    private boolean enableHierarchical;

    /**
     * 智能分块
     */
    public List<SmartChunk> chunk(String content, String documentId, String documentType) {
        // 1. 解析文档结构
        DocumentStructure structure = structureParser.parse(content, documentType);

        List<SmartChunk> chunks = new ArrayList<>();

        // 2. 根据文档类型选择策略
        if (structure.getChunks().size() > 0) {
            // 结构化文档：使用结构分块
            chunks.addAll(chunkByStructure(structure, documentId));
        } else {
            // 非结构化文档：使用语义分块
            chunks.addAll(chunkBySemantic(content, documentId));
        }

        // 3. 如果启用层级分块，生成父级 chunk
        if (enableHierarchical) {
            chunks.addAll(generateParentChunks(chunks, documentId));
        }

        // 4. 添加元数据
        enrichMetadata(chunks, structure, documentId);

        return chunks;
    }

    /**
     * 按结构分块
     */
    private List<SmartChunk> chunkByStructure(DocumentStructure structure, String documentId) {
        List<SmartChunk> chunks = new ArrayList<>();

        for (ContentChunk chunk : structure.getChunks()) {
            // 检查 chunk 大小
            if (chunk.getContent().length() < minChunkSize) {
                // 太短：合并到上一个 chunk
                if (!chunks.isEmpty()) {
                    SmartChunk last = chunks.get(chunks.size() - 1);
                    last.setContent(last.getContent() + "\n\n" + chunk.getContent());
                }
            } else if (chunk.getContent().length() > maxChunkSize) {
                // 太长：递归拆分
                chunks.addAll(splitLargeChunk(chunk, documentId));
            } else {
                // 合适大小
                SmartChunk smartChunk = toSmartChunk(chunk, documentId);
                smartChunk.setChunkType(ChunkType.STRUCTURE_BASED);
                chunks.add(smartChunk);
            }
        }

        return chunks;
    }

    /**
     * 按语义分块
     */
    private List<SmartChunk> chunkBySemantic(String content, String documentId) {
        List<SmartChunk> chunks = new ArrayList<>();

        // 使用原始 TextChunker 进行基础分块
        List<VectorChunk> baseChunks = textChunker.split(documentId, content);

        // 合并相邻的短 chunk
        SmartChunk current = null;
        for (VectorChunk base : baseChunks) {
            SmartChunk smart = SmartChunk.builder()
                .chunkId(base.getId())
                .content(base.getText())
                .charStart(base.getId().hashCode()) // 简化
                .charEnd(base.getId().hashCode())
                .chunkType(ChunkType.SEMANTIC)
                .build();

            if (current == null) {
                current = smart;
            } else if (current.getContent().length() + smart.getContent().length() < maxChunkSize) {
                // 合并
                current.setContent(current.getContent() + "\n" + smart.getContent());
            } else {
                // 保存当前，开启新的
                chunks.add(current);
                current = smart;
            }
        }

        if (current != null) {
            chunks.add(current);
        }

        return chunks;
    }

    /**
     * 拆分过大的 chunk
     */
    private List<SmartChunk> splitLargeChunk(ContentChunk chunk, String documentId) {
        List<SmartChunk> result = new ArrayList<>();
        String content = chunk.getContent();

        // 按句子拆分
        String[] sentences = content.split("[。！？\n]");
        StringBuilder buffer = new StringBuilder();

        for (String sentence : sentences) {
            if (buffer.length() + sentence.length() > maxChunkSize) {
                // 保存当前 chunk
                if (buffer.length() > minChunkSize) {
                    SmartChunk smart = toSmartChunk(buffer.toString(), chunk, documentId);
                    smart.setChunkType(ChunkType.SEMANTIC);
                    result.add(smart);
                }
                buffer = new StringBuilder();
            }
            buffer.append(sentence).append("。");
        }

        // 处理最后一个
        if (buffer.length() > minChunkSize) {
            SmartChunk smart = toSmartChunk(buffer.toString(), chunk, documentId);
            smart.setChunkType(ChunkType.SEMANTIC);
            result.add(smart);
        }

        return result;
    }

    /**
     * 生成父级 chunk (层级分块)
     */
    private List<SmartChunk> generateParentChunks(List<SmartChunk> childChunks, String documentId) {
        List<SmartChunk> parentChunks = new ArrayList<>();

        // 按章节分组
        Map<String, List<SmartChunk>> byChapter = childChunks.stream()
            .filter(c -> c.getMetadata() != null && c.getMetadata().get("chapter") != null)
            .collect(Collectors.groupingBy(c -> c.getMetadata().get("chapter").toString()));

        for (Map.Entry<String, List<SmartChunk>> entry : byChapter.entrySet()) {
            String chapter = entry.getKey();
            List<SmartChunk> chapterChunks = entry.getValue();

            // 合并章节内的所有 chunk
            StringBuilder combined = new StringBuilder();
            for (SmartChunk chunk : chapterChunks) {
                if (combined.length() > 0) {
                    combined.append("\n\n");
                }
                combined.append(chunk.getContent());
            }

            // 创建父级 chunk
            SmartChunk parent = SmartChunk.builder()
                .chunkId(documentId + "_parent_" + chapter.replaceAll("\\s+", "_"))
                .content(combined.toString())
                .chunkType(ChunkType.PARENT)
                .depth(0)
                .childChunkIds(chapterChunks.stream().map(SmartChunk::getChunkId).toList())
                .build();

            // 设置父级引用
            for (SmartChunk child : chapterChunks) {
                child.setParentChunkId(parent.getChunkId());
            }

            parentChunks.add(parent);
        }

        return parentChunks;
    }
}

/**
 * 智能分块
 */
@Data
@Builder
public class SmartChunk {
    private String chunkId;
    private String content;
    private ChunkType chunkType;
    private Integer charStart;
    private Integer charEnd;
    private String parentChunkId;       // 父级 Chunk ID
    private List<String> childChunkIds; // 子级 Chunk ID 列表
    private Integer depth;             // 层级深度
    private ChunkMetadata metadata;
}

public enum ChunkType {
    STRUCTURE_BASED,  // 基于文档结构
    SEMANTIC,         // 基于语义
    PARENT,           // 父级 Chunk (大上下文)
    CHILD             // 子级 Chunk (小检索单元)
}
```

#### 5.5.3 EnhancedRagPipeline（增强 RAG 管道）

```java
/**
 * 增强 RAG 管道
 */
@Service
public class EnhancedRagPipeline {

    private final QueryRewriter queryRewriter;
    private final HybridRetriever hybridRetriever;
    private final Reranker reranker;
    private final ContextCompressor contextCompressor;
    private final CitationGenerator citationGenerator;

    private static final int DEFAULT_TOP_K = 6;
    private static final int CANDIDATE_TOP_K = 20;
    private static final int MAX_CONTEXT_CHARS = 5000;

    /**
     * 执行完整 RAG 流程
     */
    public RagResult execute(String query, String tenantId, String userId) {
        long startTime = System.currentTimeMillis();

        // 1. 查询改写
        List<String> rewrittenQueries = queryRewriter.rewrite(query, null);
        log.debug("Query rewritten to {} queries", rewrittenQueries.size());

        // 2. 混合检索
        List<RetrievalResult> candidates = new ArrayList<>();
        for (String rewrittenQuery : rewrittenQueries) {
            List<RetrievalResult> results = hybridRetriever.retrieve(
                rewrittenQuery, CANDIDATE_TOP_K, tenantId);
            candidates.addAll(results);
        }

        // 去重
        candidates = deduplicateResults(candidates);

        // 3. 重排序
        List<RetrievalResult> reranked = reranker.rerank(query, candidates, DEFAULT_TOP_K);

        // 4. 上下文压缩
        List<RetrievalResult> compressed = contextCompressor.compress(reranked, MAX_CONTEXT_CHARS);

        // 5. 构建上下文
        String context = buildContext(compressed);

        // 6. 生成引用
        RagResult result = new RagResult();
        result.setContext(context);
        result.setRetrievalResults(compressed);
        result.setOriginalQuery(query);
        result.setRewrittenQueries(rewrittenQueries);

        // 7. 统计
        result.setRetrievalTimeMs(System.currentTimeMillis() - startTime);

        return result;
    }

    /**
     * 带引用的回答生成
     */
    public CitationResponse generateWithCitations(
            String query,
            String answer,
            List<RetrievalResult> contextResults) {
        return citationGenerator.generateWithCitations(query, answer, contextResults);
    }

    private String buildContext(List<RetrievalResult> results) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            sb.append("【参考 ").append(i + 1).append("】");
            sb.append(" [来源: ").append(r.getCitationMark()).append("]\n");
            sb.append(r.getContent());
            sb.append("\n\n");
        }

        return sb.toString();
    }

    private List<RetrievalResult> deduplicateResults(List<RetrievalResult> results) {
        Map<String, RetrievalResult> unique = new LinkedHashMap<>();
        for (RetrievalResult r : results) {
            if (!unique.containsKey(r.getId())) {
                unique.put(r.getId(), r);
            }
        }
        return new ArrayList<>(unique.values());
    }
}
```

### 5.6 Phase 2 实施任务清单

| # | 任务 | 优先级 | 涉及文件 | 工作量 | 状态 |
|---|------|--------|---------|--------|------|
| 2.1 | 设计 ChunkMetadata 模型 | P0 | 新建 `vector/dto/ChunkMetadata.java` | 0.5d | ✅ 已完成：`ChunkMetadata`、`VectorChunk`、结构化段落元数据已落地 |
| 2.2 | 设计 RetrievalResult 模型 | P0 | 新建 `rag/dto/RetrievalResult.java` | 0.5d | ✅ 已完成：`RetrievalResult`、`HybridRetrievalResult`、`RagRetrievalTrace` 已具备 |
| 2.3 | 实现 DocumentStructureParser | P0 | 新建 `vector/DocumentStructureParser.java` | 3d | ✅ 已完成：文档结构段落解析接口和默认实现已具备 |
| 2.4 | 实现 MarkdownStructureParser | P0 | 新建 `vector/MarkdownStructureParser.java` | 2d | ✅ 已完成：Markdown 标题、表格、代码块、列表结构解析已覆盖 |
| 2.5 | 实现 SemanticChunker | P0 | 新建 `vector/SemanticChunker.java` | 3d | ✅ 已完成：语义分块模型和测试已覆盖 |
| 2.6 | 实现 HierarchicalChunker | P1 | 新建 `vector/HierarchicalChunker.java` | 3d | ✅ 已完成：层级分块支持父上下文和章节路径 |
| 2.7 | 实现 SmartTextChunker | P0 | 新建 `vector/SmartTextChunker.java` | 3d | ✅ 已完成：智能分块作为 TextChunker 默认增强能力 |
| 2.8 | 重构 TextChunker | P0 | 改造 `vector/TextChunker.java` | 2d | ✅ 已完成：统一输出 `VectorChunk` 并携带结构化 metadata |
| 2.9 | 实现 QueryRewriter | P0 | 新建 `rag/QueryRewriter.java` | 3d | ✅ 已完成：查询分析、关键词提取和重写已具备 |
| 2.10 | 实现 QueryClassifier | P1 | 新建 `rag/QueryClassifier.java` | 2d | ✅ 已完成：查询类型归入 `RagQueryAnalysis`，由 QueryRewriter 统一输出 |
| 2.11 | 实现 HybridRetriever | P0 | 新建 `rag/HybridRetriever.java` | 3d | ✅ 已完成：Milvus 向量 + 全文检索 + RRF 融合已接入 |
| 2.12 | 实现 Bm25SearchService | P0 | 新建 `rag/Bm25SearchService.java` | 2d | ✅ 已完成：全文检索抽象已落地，支持 JPA 回退与 Elasticsearch 实现 |
| 2.13 | 实现 Reranker | P0 | 新建 `rag/Reranker.java` | 3d | ✅ 已完成：`RagReranker` 已支持混合结果重排 |
| 2.14 | 实现 ContextCompressor | P1 | 新建 `rag/ContextCompressor.java` | 2d | ✅ 已完成：`RagContextCompressor` 控制上下文长度 |
| 2.15 | 实现 CitationGenerator | P0 | 新建 `rag/CitationGenerator.java` | 2d | ✅ 已完成：`EnhancedRagPipeline` 生成引用编号和 `RagCitation` |
| 2.16 | 实现 EnhancedRagPipeline | P0 | 新建 `rag/EnhancedRagPipeline.java` | 3d | ✅ 已完成：查询分析、混合检索、重排、父上下文、压缩、引用、追踪串联 |
| 2.17 | 改造 VectorMemoryService | P0 | 改造 `service/VectorMemoryService.java` | 2d | ✅ 已完成：仅保留 Milvus 向量库路径，支持租户/用户过滤 |
| 2.18 | 改造 RagRetrievalService | P0 | 改造 `rag/RagRetrievalService.java` | 2d | ✅ 已完成：统一通过增强 RAG 管道构建上下文 |
| 2.19 | 单元测试 | P0 | `test/` | 5d | ✅ 已完成：分块、检索、重排、压缩、ES 客户端和 RAG 控制器测试已覆盖 |
| 2.20 | 集成测试 | P0 | `test/` | 4d | ✅ 已完成：增强 RAG 管道和检索服务聚焦测试通过 |

**Phase 2 总工期：约 47 个工作日**

### 5.7 讨论议题

#### 议题 1：分块策略选择

| 方案 | 描述 | 适用场景 |
|------|------|---------|
| A: 纯语义分块 | LLM 判断语义边界 | 质量要求高、资源充足 |
| B: 纯结构分块 | 按文档固有结构分块 | 结构化文档为主 |
| C: 结构优先+语义增强 | 先按结构，语义补充 | 混合场景 |

**建议：方案 C（结构优先+语义增强）**

#### 议题 2：层级分块是否启用

| 选项 | 存储成本 | 检索精度 | 实现复杂度 |
|------|---------|---------|-----------|
| 启用 | 翻倍 | 高 | 中 |
| 暂不启用 | 不变 | 中 | 低 |

**建议：Phase 2 暂不启用，Phase 3 再考虑**

#### 议题 3：重排序模型选择

| 选项 | 成本 | 精度 | 延迟 |
|------|------|------|------|
| BGE-Reranker (开源) | 低 | 高 | 中 |
| Cohere Rerank (商业) | 高 | 最高 | 低 |
| 本地轻量模型 | 低 | 中 | 高 |

**建议：Phase 2 用 BGE-Reranker，评估效果后再决定是否切换**

#### 议题 4：BM25 检索实现

| 选项 | 实现复杂度 | 依赖 |
|------|---------|------|
| MySQL 全文索引 | 低 | MySQL |
| Elasticsearch | 高 | ES 集群 |
| OpenSearch | 高 | OP 集群 |

**建议：先用 MySQL 全文索引，评估效果后再决定是否引入 ES**

---

## 六、Phase 3：增强推理引擎

### 6.1 为什么要增强推理引擎？

#### 6.1.1 从"线性 ReAct"到"Plan-Execute-Reflect"

```
【当前：线性 ReAct】

用户问：分析Q3销售，对比竞品，画图
          │
          ▼
┌─────────────────────────────────────┐
│  LLM: 思考... 调用工具A查销售       │
│  工具A失败                          │
│  LLM: 再试一次... 还是失败          │
│  LLM: ...达到最大次数...            │
│  输出：无法完成                     │
└─────────────────────────────────────┘
          │
          ▼
问题：❌ 不知道失败原因 ❌ 不会换策略 ❌ 没有规划

【增强后：Plan-Execute-Reflect】

用户问：分析Q3销售，对比竞品，画图
          │
          ▼
┌─────────────────────────────────────┐
│  1. Plan（规划阶段）                 │
│  任务拆解：                          │
│  - 任务1：查Q3销售数据（依赖：无）  │
│  - 任务2：查竞品数据（依赖：无）    │
│  - 任务3：画对比图（依赖：1,2）    │
│  执行顺序：A→B→C                    │
└─────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────┐
│  2. Execute（执行阶段）             │
│  并行执行：任务1 + 任务2            │
│  等待两者完成                        │
│  执行任务3                          │
└─────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────┐
│  3. Reflect（反思阶段）             │
│  工具失败？→ 反思原因 → 换策略      │
│  结果不完整？→ 补充检索              │
│  答案不确定？→ 标注置信度           │
└─────────────────────────────────────┘
          │
          ▼
      最终回答
```

#### 6.1.2 现有实现的不足

| # | 问题 | 影响 | 优先级 |
|---|------|------|--------|
| 1 | **无任务规划** | 复杂任务容易迷失方向 | P0 |
| 2 | **无自我反思** | 工具失败不会换策略 | P0 |
| 3 | **工具调用解析脆弱** | 正则解析不支持复杂参数 | P1 |
| 4 | **无错误恢复** | 连续失败导致任务失败 | P0 |
| 5 | **无并行执行** | 独立任务串行执行，浪费时间 | P1 |
| 6 | **无完整执行追踪** | 难以调试和优化 | P2 |

#### 6.1.3 业界最佳实践对比

```
┌─────────────────────────────────────────────────────────┐
│              推理模式对比                                 │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  【ReAct】当前方案                                      │
│  公式：Thought → Action → Observation → ... → Answer   │
│  优点：简单、可解释                                      │
│  缺点：复杂任务容易迷失、无规划、无反思                  │
│                                                          │
│  【Plan-and-Execute】规划优先                           │
│  公式：Plan → Execute → Answer                         │
│  优点：复杂任务不迷失、可并行执行                       │
│  缺点：执行时缺少灵活调整                               │
│                                                          │
│  【Reflexion】反思驱动                                  │
│  公式：Act → Observe → Reflect → ... → Answer         │
│  优点：能从错误中学习、持续改进                         │
│  缺点：需要额外的反思步骤                               │
│                                                          │
│  【LangChain Agent】组合模式 ✅推荐                      │
│  公式：Plan → Execute → Reflect → Adjust → ... → Answer│
│  优点：结合三者优点、灵活可配置                         │
│  缺点：实现复杂度高                                     │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 6.2 增强推理架构设计

#### 6.2.1 整体架构

```
┌─────────────────────────────────────────────────────────┐
│         Enhanced ReAct Engine (Plan-Execute-Reflect)      │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │ 1. Task Planner (任务规划器)                       │   │
│  │ ├── 任务分解：复杂问题 → 子任务列表               │   │
│  │ ├── 依赖分析：识别任务间的依赖关系                │   │
│  │ ├── 并行识别：找出可以并行执行的任务             │   │
│  │ └── 执行计划：生成可执行的计划图                  │   │
│  └─────────────────────────────────────────────────┘   │
│                          │                               │
│                          ▼                               │
│  ┌─────────────────────────────────────────────────┐   │
│  │ 2. Parallel Executor (并行执行器)                 │   │
│  │ ├── 任务队列管理                                 │   │
│  │ ├── 并行执行引擎（支持依赖调度）                 │   │
│  │ ├── 工具调用器（支持 JSON Function Calling）      │   │
│  │ └── 中间结果收集                                 │   │
│  └─────────────────────────────────────────────────┘   │
│                          │                               │
│                          ▼                               │
│  ┌─────────────────────────────────────────────────┐   │
│  │ 3. Self Reflector (自我反思器)                    │   │
│  │ ├── 失败反思：工具失败 → 分析原因 → 换策略       │   │
│  │ ├── 结果验证：LLM 判断结果是否足够回答问题       │   │
│  │ ├── 质量评估：置信度标注 + 不确定性说明          │   │
│  │ └── 补充检索：信息不足 → 触发补充检索           │   │
│  └─────────────────────────────────────────────────┘   │
│                          │                               │
│                          ▼                               │
│  ┌─────────────────────────────────────────────────┐   │
│  │ 4. Error Recovery (错误恢复器)                    │   │
│  │ ├── 重试机制：失败 → 等待 → 重试                 │   │
│  │ ├── 策略回退：策略A失败 → 尝试策略B             │   │
│  │ ├── 降级处理：复杂方案失败 → 简化方案           │   │
│  │ └── 优雅失败：无法恢复 → 提供清晰错误 + 建议     │   │
│  └─────────────────────────────────────────────────┘   │
│                          │                               │
│                          ▼                               │
│  ┌─────────────────────────────────────────────────┐   │
│  │ 5. Result Integrator (结果整合器)                │   │
│  │ ├── 多任务结果融合                               │   │
│  │ ├── 上下文连贯性检查                             │   │
│  │ ├── 置信度综合                                   │   │
│  │ └── 最终回答生成                                 │   │
│  └─────────────────────────────────────────────────┘   │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

#### 6.2.2 执行流程详解

```
用户问：分析Q3销售数据，对比竞品，画个图
              │
              ▼
┌─────────────────────────────────────────────────────────┐
│ Step 1: 任务规划 (Task Planner)                          │
├─────────────────────────────────────────────────────────┤
│                                                          │
│ LLM 输出任务计划：                                       │
│ - task_id: t1 → 查询Q3销售数据 (工具: executeSql)        │
│ - task_id: t2 → 查询竞品数据 (工具: searchKnowledge)     │
│ - task_id: t3 → 生成对比图表 (工具: generateChart)       │
│   依赖: t1, t2                                         │
│                                                          │
│ 执行顺序：                                               │
│ - t1 和 t2 可以并行执行（独立任务）                      │
│ - t3 必须等待 t1 和 t2 完成（依赖关系）                  │
│                                                          │
└─────────────────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────┐
│ Step 2: 并行执行 (Parallel Executor)                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│ 执行图：                                                 │
│         ┌─────────────────────────────┐                  │
│         │      并行执行              │                  │
│         │  ┌─────────┐  ┌─────────┐ │                  │
│         │  │  Task1  │  │  Task2  │ │                  │
│         │  │ 查询销售 │  │ 查询竞品 │ │                  │
│         │  └────┬────┘  └────┬────┘ │                  │
│         │       │             │       │                  │
│         └───────┼─────────────┼───────┘                  │
│                 │             │                          │
│                 ▼             ▼                          │
│           ┌─────────────────────────────┐                │
│           │        Task3              │                  │
│           │       生成图表             │                │
│           │     (等待依赖完成)        │                │
│           └─────────────────────────────┘                │
│                                                          │
└─────────────────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────┐
│ Step 3: 自我反思 (Self Reflector)                        │
├─────────────────────────────────────────────────────────┤
│                                                          │
│ 反思点1：Task1执行失败（SQL超时）                        │
│ LLM反思："SQL执行超时，换策略：先查汇总数据"             │
│                                                          │
│ 反思点2：Task3生成图表后                                 │
│ LLM反思："图表数据不全（缺少12月），补充检索"           │
│                                                          │
│ 反思点3：最终答案生成前                                  │
│ LLM反思："竞品数据只有上半年，置信度：中"               │
│                                                          │
└─────────────────────────────────────────────────────────┘
              │
              ▼
          最终回答
```

### 6.3 核心组件设计

#### 6.3.1 TaskPlanner（任务规划器）

```java
// com.ai.agent.react.planner.TaskPlanner
@Service
public class TaskPlanner {

    private final McpModelService modelService;

    private static final String PLANNING_PROMPT = """
        你是一个任务规划专家。请将用户请求分解为可执行的子任务。

        用户请求：%s

        要求：
        1. 识别需要执行的具体任务
        2. 识别每个任务需要使用的工具
        3. 识别任务间的依赖关系（哪些任务可以并行，哪些必须串行）
        4. 评估任务复杂度（简单/中等/复杂）

        输出格式（严格JSON）：
        {
            "task_count": 任务数量,
            "complexity": "simple|medium|complex",
            "tasks": [
                {
                    "task_id": "t1",
                    "description": "任务描述",
                    "tool": "工具名",
                    "parameters": {"key": "value"},
                    "dependencies": [],
                    "can_parallel_with": []
                }
            ],
            "execution_order": [
                {"phase": 1, "tasks": ["t1", "t2"], "can_parallel": true},
                {"phase": 2, "tasks": ["t3"], "can_parallel": false}
            ]
        }
        """;

    /**
     * 规划任务
     */
    public TaskPlan plan(String userQuery, List<ToolSpecification> availableTools) {
        String prompt = PLANNING_PROMPT.formatted(userQuery);
        String llmResponse = modelService.callModel(prompt);
        TaskPlan plan = parsePlan(llmResponse);
        plan = optimizeExecutionOrder(plan);
        validatePlan(plan);
        return plan;
    }

    /**
     * 识别复杂任务，决定是否需要规划
     */
    public boolean shouldPlan(String userQuery) {
        if (isSimpleQuery(userQuery)) {
            return false;
        }
        return containsKeywords(userQuery,
            "分析", "对比", "综合", "多个", "分别", "而且"
        );
    }

    private boolean isSimpleQuery(String query) {
        return containsKeywords(query, "是多少", "是什么", "查一下", "告诉我")
            && !containsKeywords(query, "对比", "分析", "多个");
    }
}

/**
 * 任务计划
 */
@Data
public class TaskPlan {
    private String planId;
    private String originalQuery;
    private int taskCount;
    private String complexity;
    private List<SubTask> tasks;
    private List<ExecutionPhase> executionOrder;
    private LocalDateTime createdAt;
}

/**
 * 子任务
 */
@Data
public class SubTask {
    private String taskId;
    private String description;
    private String toolName;
    private Map<String, Object> parameters;
    private List<String> dependencies;
    private List<String> canParallelWith;
    private String estimatedComplexity;
    private TaskStatus status;
    private String result;
    private String error;
    private int retryCount;
    private long executionTimeMs;
}
```

#### 6.3.2 ParallelExecutor（并行执行器）

```java
// com.ai.agent.react.executor.ParallelExecutor
@Service
public class ParallelExecutor {

    private final AgentToolInvoker toolInvoker;
    private final ExecutorService executorService;

    /**
     * 执行任务计划
     */
    public ExecutionResult execute(TaskPlan plan, List<ToolSpecification> toolSpecs,
                                   String modelId) {
        ExecutionResult result = new ExecutionResult();
        result.setPlanId(plan.getPlanId());
        Map<String, Object> sharedContext = new HashMap<>();

        // 按执行阶段执行
        for (ExecutionPhase phase : plan.getExecutionOrder()) {
            if (phase.isCanParallel()) {
                // 并行执行
                Map<String, SubTaskResult> phaseResults = executeParallel(
                    plan.getTasksByIds(phase.getTasks()),
                    toolSpecs, modelId, sharedContext
                );
                result.getTaskResults().putAll(phaseResults);
            } else {
                // 串行执行
                for (String taskId : phase.getTasks()) {
                    SubTaskResult taskResult = executeSingle(
                        plan.getTask(taskId), toolSpecs, modelId, sharedContext
                    );
                    result.getTaskResults().put(taskId, taskResult);
                    if (!taskResult.isSuccess() && !canContinue(plan, taskId)) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * 并行执行多个独立任务
     */
    private Map<String, SubTaskResult> executeParallel(
            List<SubTask> tasks,
            List<ToolSpecification> toolSpecs,
            String modelId,
            Map<String, Object> sharedContext) {

        Map<String, CompletableFuture<SubTaskResult>> futures = new HashMap<>();

        for (SubTask task : tasks) {
            CompletableFuture<SubTaskResult> future = CompletableFuture.supplyAsync(() ->
                executeSingle(task, toolSpecs, modelId, sharedContext),
                executorService
            );
            futures.put(task.getTaskId(), future);
        }

        Map<String, SubTaskResult> results = new HashMap<>();
        for (Map.Entry<String, CompletableFuture<SubTaskResult>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                results.put(entry.getKey(), SubTaskResult.failure(entry.getKey(), e.getMessage()));
            }
        }
        return results;
    }
}

/**
 * 执行结果
 */
@Data
public class ExecutionResult {
    private String planId;
    private Map<String, SubTaskResult> taskResults;
    private boolean isComplete;
    private String finalAnswer;
    private Double confidence;
    private List<String> warnings;
}
```

#### 6.3.3 SelfReflector（自我反思器）

```java
// com.ai.agent.react.reflector.SelfReflector
@Service
public class SelfReflector {

    private final McpModelService modelService;

    private static final String FAILURE_REFLECTION_PROMPT = """
        任务执行失败，需要反思和调整策略。

        任务信息：
        - 任务描述：%s
        - 使用工具：%s
        - 错误信息：%s
        - 已重试次数：%d

        请分析：
        1. 失败的可能原因是什么？
        2. 应该换什么策略？
        3. 需要修改哪些参数？

        输出格式（严格JSON）：
        {
            "analysis": "失败原因分析",
            "new_strategy": "新的执行策略",
            "suggested_parameters": {"参数修改建议"},
            "should_abandon": false
        }
        """;

    private static final String RESULT_VALIDATION_PROMPT = """
        请评估以下任务执行结果是否足够回答用户问题。

        用户问题：%s
        已执行的任务数：%d
        任务结果摘要：%s

        评估：
        1. 现有信息是否足够回答问题？
        2. 是否需要补充检索？
        3. 答案的置信度如何？

        输出格式（严格JSON）：
        {
            "is_sufficient": true/false,
            "confidence": 0.0-1.0,
            "missing_info": ["缺失的信息"],
            "suggested_actions": ["建议的补充行动"]
        }
        """;

    /**
     * 反思任务失败原因
     */
    public ReflectionResult reflectOnFailure(SubTask task, String error, int retryCount) {
        String prompt = FAILURE_REFLECTION_PROMPT.formatted(
            task.getDescription(), task.getToolName(), error, retryCount
        );
        String llmResponse = modelService.callModel(prompt);
        return parseReflectionResult(llmResponse);
    }

    /**
     * 验证执行结果是否足够
     */
    public ValidationResult validateResults(
            String userQuery,
            List<SubTask> completedTasks,
            Map<String, SubTaskResult> taskResults) {

        String resultSummary = summarizeResults(taskResults);
        String prompt = RESULT_VALIDATION_PROMPT.formatted(
            userQuery, completedTasks.size(), resultSummary
        );

        String llmResponse = modelService.callModel(prompt);
        ValidationResult result = parseValidationResult(llmResponse);

        if (!result.isSufficient()) {
            List<SubTask> additionalTasks = generateAdditionalTasks(
                userQuery, result.getMissingInfo()
            );
            result.setAdditionalTasks(additionalTasks);
        }

        return result;
    }

    /**
     * 评估最终答案的置信度
     */
    public ConfidenceAssessment assessConfidence(
            String userQuery,
            String answer,
            List<SubTaskResult> taskResults) {

        long failedCount = taskResults.stream().filter(r -> !r.isSuccess()).count();
        double baseConfidence = 1.0 - (failedCount * 0.2);

        String qualityPrompt = """
            请评估以下回答的质量和置信度。

            用户问题：%s
            回答内容：%s

            输出格式：
            {
                "confidence": 0.0-1.0,
                "uncertain_statements": ["不确定的陈述"],
                "suggestions": ["改进建议"]
            }
            """.formatted(userQuery, answer);

        String llmResponse = modelService.callModel(qualityPrompt);
        return parseConfidenceAssessment(llmResponse, baseConfidence);
    }
}
```

#### 6.3.4 ErrorRecovery（错误恢复器）

```java
// com.ai.agent.react.recovery.ErrorRecovery
@Service
public class ErrorRecovery {

    private static final int MAX_RETRY_PER_TASK = 3;

    // 常见错误及回退策略
    private static final Map<String, RecoveryStrategy> ERROR_STRATEGIES = Map.of(
        "timeout", new RecoveryStrategy(
            "查询超时",
            List.of(
                new RecoveryAction("reduce_query_scope", "缩小查询范围，添加LIMIT"),
                new RecoveryAction("use_cache", "尝试从缓存获取数据"),
                new RecoveryAction("simplify_query", "简化SQL语句")
            )
        ),
        "sql_syntax_error", new RecoveryStrategy(
            "SQL语法错误",
            List.of(
                new RecoveryAction("fix_syntax", "修正SQL语法"),
                new RecoveryAction("use_natural_language", "改用自然语言查询")
            )
        ),
        "no_data", new RecoveryStrategy(
            "数据不存在",
            List.of(
                new RecoveryAction("check_alternative_source", "检查其他数据源"),
                new RecoveryAction("relax_conditions", "放宽查询条件")
            )
        ),
        "rate_limit", new RecoveryStrategy(
            "API限流",
            List.of(
                new RecoveryAction("wait_and_retry", "等待后重试"),
                new RecoveryAction("use_cache", "使用缓存数据")
            )
        )
    );

    /**
     * 获取错误恢复策略
     */
    public RecoveryPlan getRecoveryPlan(String errorType, SubTask task, int currentRetryCount) {
        RecoveryStrategy strategy = ERROR_STRATEGIES.get(errorType);

        if (strategy == null) {
            return getGenericRecoveryPlan(task, currentRetryCount);
        }

        RecoveryAction action = strategy.getActions().stream()
            .filter(a -> isActionApplicable(a, task))
            .findFirst()
            .orElse(null);

        if (action == null) {
            return RecoveryPlan.abandon(task.getTaskId(), "无法找到合适的恢复策略");
        }

        return RecoveryPlan.builder()
            .taskId(task.getTaskId())
            .strategy(action.getActionType())
            .modifiedTask(modifyTask(task, action))
            .shouldRetry(currentRetryCount < MAX_RETRY_PER_TASK)
            .build();
    }

    /**
     * 判断是否应该放弃任务
     */
    public boolean shouldAbandon(SubTask task, int retryCount, String lastError) {
        if (retryCount >= MAX_RETRY_PER_TASK) {
            return true;
        }
        return lastError != null && (
            lastError.contains("权限不足") ||
            lastError.contains("数据不存在") ||
            lastError.contains("不支持")
        );
    }
}

/**
 * 恢复策略
 */
@Data
public class RecoveryStrategy {
    private String description;
    private List<RecoveryAction> actions;
}

/**
 * 恢复计划
 */
@Data
@Builder
public class RecoveryPlan {
    private String taskId;
    private String strategy;
    private SubTask modifiedTask;
    private boolean shouldRetry;
    private String abandonReason;
}
```

#### 6.3.5 FunctionCallParser（结构化工具调用解析器）

```java
// com.ai.agent.react.parser.FunctionCallParser
@Service
public class FunctionCallParser {

    private final ObjectMapper objectMapper;

    /**
     * 解析工具调用（支持多种格式）
     */
    public ParsedToolCall parse(String llmResponse) {
        // 1. 尝试 JSON 格式（OpenAI Function Calling）
        if (containsJsonFormat(llmResponse)) {
            return parseJsonFormat(llmResponse);
        }

        // 2. 尝试 [CALL:tool(args)] 格式（当前实现）
        if (containsLegacyFormat(llmResponse)) {
            return parseLegacyFormat(llmResponse);
        }

        // 3. 检查是否是最终答案
        if (isFinalAnswer(llmResponse)) {
            return ParsedToolCall.finalAnswer(extractAnswer(llmResponse));
        }

        return null;
    }

    /**
     * 解析 JSON 格式的工具调用
     */
    private ParsedToolCall parseJsonFormat(String response) {
        Pattern pattern = Pattern.compile(
            "\"function_call\"\\s*:\\s*\\{[^}]+\\}",
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String json = matcher.group();
            FunctionCall fc = objectMapper.readValue(json, FunctionCall.class);

            return ParsedToolCall.builder()
                .isToolCall(true)
                .toolName(fc.getName())
                .arguments(fc.getArguments())
                .rawJson(json)
                .build();
        }
        return null;
    }

    /**
     * 解析传统 [CALL:tool(args)] 格式
     */
    private ParsedToolCall parseLegacyFormat(String response) {
        Pattern pattern = Pattern.compile(
            "\\[CALL[：:]?\\s*(\\w+)\\s*\\(([\\s\\S]*?)\\)\\]?",
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String toolName = matcher.group(1);
            String argsStr = matcher.group(2);
            Map<String, Object> args = parseArguments(argsStr);

            return ParsedToolCall.builder()
                .isToolCall(true)
                .toolName(toolName)
                .arguments(args)
                .rawText(response.substring(matcher.start(), matcher.end()))
                .build();
        }
        return null;
    }

    /**
     * 智能解析参数（支持多种格式）
     */
    private Map<String, Object> parseArguments(String argsStr) {
        Map<String, Object> args = new HashMap<>();

        // 尝试 JSON 格式
        if (argsStr.trim().startsWith("{")) {
            try {
                return objectMapper.readValue(argsStr, Map.class);
            } catch (Exception e) { }
        }

        // 尝试 key=value 格式
        if (argsStr.contains("=")) {
            for (String pair : argsStr.split(",")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    args.put(kv[0].trim(), kv[1].trim().replaceAll("^\"|\"$", ""));
                }
            }
            return args;
        }

        // 尝试位置参数格式
        if (!argsStr.trim().isEmpty()) {
            args.put("_positional", argsStr.trim());
        }

        return args;
    }
}

/**
 * 解析后的工具调用
 */
@Data
@Builder
public class ParsedToolCall {
    private boolean isToolCall;
    private String toolName;
    private Map<String, Object> arguments;
    private String rawText;
    private String rawJson;
}
```

#### 6.3.6 EnhancedReActEngine（增强推理引擎主控）

```java
// com.ai.agent.react.EnhancedReActEngine
@Service
public class EnhancedReActEngine {

    private final TaskPlanner taskPlanner;
    private final ParallelExecutor executor;
    private final SelfReflector reflector;
    private final ErrorRecovery errorRecovery;
    private final FunctionCallParser parser;
    private final ResultIntegrator integrator;

    private static final int MAX_PLAN_ATTEMPTS = 2;

    /**
     * 执行增强推理
     */
    public EngineResult execute(String userQuery,
                               List<ToolSpecification> toolSpecs,
                               String modelId,
                               ExecutionContext context) {

        EngineResult result = new EngineResult();

        // Step 1: 判断是否需要规划
        if (!taskPlanner.shouldPlan(userQuery)) {
            return executeSimpleReAct(userQuery, toolSpecs, modelId, context);
        }

        // Step 2: 任务规划
        TaskPlan plan = null;
        for (int attempt = 0; attempt < MAX_PLAN_ATTEMPTS; attempt++) {
            try {
                plan = taskPlanner.plan(userQuery, toolSpecs);
                result.setPlan(plan);
                break;
            } catch (Exception e) {
                if (attempt == MAX_PLAN_ATTEMPTS - 1) {
                    log.warn("Task planning failed, falling back to simple ReAct", e);
                    return executeSimpleReAct(userQuery, toolSpecs, modelId, context);
                }
            }
        }

        // Step 3: 执行计划
        ExecutionResult executionResult = executePlan(plan, toolSpecs, modelId, context);
        result.setExecutionResult(executionResult);

        // Step 4: 反思和调整
        result = reflectAndAdjust(result, userQuery, toolSpecs, modelId, context);

        // Step 5: 整合结果
        String finalAnswer = integrator.integrate(result);
        result.setFinalAnswer(finalAnswer);

        return result;
    }

    /**
     * 执行计划（带反思循环）
     */
    private ExecutionResult executePlan(TaskPlan plan,
                                      List<ToolSpecification> toolSpecs,
                                      String modelId,
                                      ExecutionContext context) {

        int reflectionRounds = 0;
        ExecutionResult currentResult;

        do {
            currentResult = executor.execute(plan, toolSpecs, modelId);

            ValidationResult validation = reflector.validateResults(
                plan.getOriginalQuery(),
                currentResult.getCompletedTasks(),
                currentResult.getTaskResults()
            );

            if (validation.isSufficient()) {
                return currentResult;
            }

            if (!validation.getAdditionalTasks().isEmpty()) {
                List<SubTask> additionalTasks = validation.getAdditionalTasks();
                plan = extendPlan(plan, additionalTasks);
            }

            reflectionRounds++;
        } while (reflectionRounds < 3 && !currentResult.isComplete());

        return currentResult;
    }

    /**
     * 反思和调整
     */
    private EngineResult reflectAndAdjust(EngineResult result,
                                         String userQuery,
                                         List<ToolSpecification> toolSpecs,
                                         String modelId,
                                         ExecutionContext context) {

        ConfidenceAssessment confidence = reflector.assessConfidence(
            userQuery,
            result.getFinalAnswer(),
            result.getExecutionResult().getTaskResults()
        );

        result.setConfidence(confidence.getOverallConfidence());

        if (confidence.getConfidence() < 0.7) {
            result.addWarning("答案置信度较低：" +
                String.join("；", confidence.getUncertainStatements()));
        }

        List<SubTask> failedTasks = result.getExecutionResult().getFailedTasks();
        if (!failedTasks.isEmpty()) {
            String warning = "以下任务失败：" + failedTasks.stream()
                .map(SubTask::getDescription)
                .collect(Collectors.joining("；"));
            result.addWarning(warning);
        }

        return result;
    }
}

/**
 * 引擎执行结果
 */
@Data
public class EngineResult {
    private TaskPlan plan;
    private ExecutionResult executionResult;
    private String finalAnswer;
    private Double confidence;
    private List<String> warnings;
    private Map<String, Object> metadata;
}
```

### 6.4 与现有代码的集成

#### 6.4.1 集成策略

```
┌─────────────────────────────────────────────────────────┐
│              渐进式集成策略                               │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Phase 3.1: 保留现有 ReAct，添加规划层                   │
│  ├── 新增 TaskPlanner                                  │
│  ├── 简单查询 → 传统 ReAct                             │
│  ├── 复杂查询 → TaskPlanner → 增强执行                 │
│  └── 风险：低（完全向后兼容）                           │
│                                                          │
│  Phase 3.2: 增强错误处理                                │
│  ├── 新增 ErrorRecovery                                │
│  ├── 工具失败 → 反思 → 换策略                          │
│  └── 风险：中（需要修改 ReActStepHandler）              │
│                                                          │
│  Phase 3.3: 支持并行执行                                │
│  ├── 新增 ParallelExecutor                            │
│  ├── 独立任务并行执行                                   │
│  └── 风险：中（需要修改执行流程）                       │
│                                                          │
│  Phase 3.4: 结构化工具调用                             │
│  ├── 新增 FunctionCallParser                          │
│  ├── 支持 JSON Function Calling                       │
│  └── 风险：中（需要模型配合）                          │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

#### 6.4.2 代码改造点

| 文件 | 改造内容 | 风险 |
|------|---------|------|
| `ReActAgent.java` | 添加 EnhancedReActEngine，根据复杂度选择执行器 | 低 |
| `ReActLoopRunner.java` | 重构为可配置的策略模式 | 中 |
| `ReActStepHandler.java` | 集成 ErrorRecovery | 中 |
| `ReActResponseParser.java` | 重构为 FunctionCallParser | 中 |
| 新增 `TaskPlanner.java` | 任务规划器 | 低 |
| 新增 `ParallelExecutor.java` | 并行执行器 | 中 |
| 新增 `SelfReflector.java` | 自我反思器 | 低 |
| 新增 `ErrorRecovery.java` | 错误恢复器 | 低 |
| 新增 `EnhancedReActEngine.java` | 增强引擎主控 | 低 |

### 6.5 Phase 3 实施任务清单

| # | 任务 | 优先级 | 涉及文件 | 工作量 | 说明 |
|---|------|--------|---------|--------|------|
| 3.1 | 设计 TaskPlan 数据模型 | P0 | `agent/ExecutionPlan.java`、`ExecutionPlanStep.java`、`ExecutionPhase.java` | 1d | ✅ 已完成：以轻量 `ExecutionPlan` 作为当前计划模型，避免额外 DTO 层膨胀 |
| 3.2 | 设计 ExecutionResult 数据模型 | P0 | `ReActExecutionResult.java`、`ParallelPlanExecutionResult.java`、`ParallelPlanStepResult.java` | 0.5d | ✅ 已完成：覆盖 ReAct 结果、并行预检聚合结果和单步骤结果 |
| 3.3 | 实现 TaskPlanner | P0 | `agent/TaskPlanner.java` | 4d | ✅ 已完成：根据任务复杂度、文件/技能/Agent 上下文、RAG 命中和关键词生成确定性计划 |
| 3.4 | 实现 FunctionCallParser | P0 | `agent/ReActResponseParser.java` | 3d | ✅ 已完成：支持 JSON、`function_call`、`tool_calls`、对象参数和数组参数解析 |
| 3.5 | 实现 ParallelExecutor | P1 | `agent/ParallelPlanExecutor.java`、`ParallelTaskExecutor.java` | 4d | ✅ 已完成：在 ReAct 前并行执行安全只读预检步骤，并传播 MDC 与认证上下文 |
| 3.6 | 实现 ErrorRecovery | P0 | `agent/ErrorRecoveryAdvisor.java`、`ReActRecoveryTracker.java` | 3d | ✅ 已完成：识别未知工具、参数不足、SQL、超时、无数据和通用工具失败，并限制恢复次数 |
| 3.7 | 实现 SelfReflector | P0 | `agent/SelfReflector.java` | 3d | ✅ 已完成：支持工具失败反思、恢复耗尽降级反思和最终答案质量自检 |
| 3.8 | 实现 ResultIntegrator | P1 | `agent/ResultIntegrator.java` | 2d | ✅ 已完成：多专家/多任务结果整合组件已具备，ReAct 单 Agent 结果由循环处理器直接汇总 |
| 3.9 | 实现 EnhancedReActEngine | P0 | `agent/ReActAgent.java` | 4d | ✅ 已完成：增强引擎职责合并到 ReActAgent 门面，统一调度快速路径、规划、并行预检、ReAct 循环和元数据输出 |
| 3.10 | 改造 ReActAgent | P0 | `agent/ReActAgent.java` | 2d | ✅ 已完成：集成复杂度判断、快速路径、执行计划、并行预检、RAG 和记忆上下文 |
| 3.11 | 改造 ReActLoopRunner | P1 | `agent/ReActLoopRunner.java` | 2d | ✅ 已完成：循环职责拆分到模型调用、同步/流式步骤处理器、工作记忆和结构化日志 |
| 3.12 | 改造 ReActStepHandler | P0 | `agent/ReActStepHandler.java` | 2d | ✅ 已完成：工具观察统一追加错误恢复建议，并返回可恢复决策上下文 |
| 3.13 | 改造 ReActResponseParser | P0 | `agent/ReActResponseParser.java` | 2d | ✅ 已完成：结构化工具调用解析已替代脆弱正则解析 |
| 3.14 | 添加配置开关 | P1 | `agent/AgentReasoningProperties.java`、`application.yml` | 1d | ✅ 已完成：支持快速路径、规划、并行预检、反思和工作记忆开关 |
| 3.15 | 单元测试 | P0 | `test/java/com/ai/agent/` | 4d | ✅ 已完成：覆盖规划、复杂度分类、并行预检、错误恢复、反思、解析和快速路径 |
| 3.16 | 集成测试 | P0 | `ReActAgentFastPathTest`、`ReActSynchronousStepProcessorTest`、`ReActStreamingStepProcessorTest` | 3d | ✅ 已完成：覆盖同步/流式入口、计划可视化、并行预检和降级回答 |

**Phase 3 总工期：约 43 个工作日**

**当前实现说明**：文档早期设计中的 `EnhancedReActEngine`、`FunctionCallParser`、`ParallelExecutor`
没有按示例包名新增空壳类，而是分别落在 `ReActAgent`、`ReActResponseParser`、`ParallelPlanExecutor`
等现有主干组件中。这样可以减少转发层和重复抽象，保留 Plan-Execute-Reflect 能力，同时让现有调用链更短。

### 6.6 核心设计决策（已确定）

#### 决策 1：推理策略选择

```
┌─────────────────────────────────────────────────────────┐
│            渐进式推理策略（核心设计理念）                    │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  设计原则：简单问题简单解决，复杂问题深度处理              │
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Tier 1: 直接执行（零规划）                       │   │
│  │ ├── 适用：简单查询、单一工具调用                 │   │
│  │ ├── 延迟：最低（<1秒）                         │   │
│  │ ├── Token：最少                                 │   │
│  │ └── 示例："今天销售额多少？"                   │   │
│  └─────────────────────────────────────────────────┘   │
│                          │                               │
│                          ▼                               │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Tier 2: 传统 ReAct（轻量规划）                   │   │
│  │ ├── 适用：多步骤但线性依赖的任务                 │   │
│  │ ├── 延迟：中（2-5秒）                           │   │
│  │ ├── Token：中等                                 │   │
│  │ └── 示例："分析Q3销售数据"                     │   │
│  └─────────────────────────────────────────────────┘   │
│                          │                               │
│                          ▼                               │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Tier 3: 增强推理（完整规划）                     │   │
│  │ ├── 适用：复杂多步骤、有并行机会的任务          │   │
│  │ ├── 延迟：较高（5-15秒）                       │   │
│  │ ├── Token：较高                                 │   │
│  │ └── 示例："分析Q3销售，对比竞品，画图"         │   │
│  └─────────────────────────────────────────────────┘   │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

#### 决策 2：问题复杂度识别

```java
/**
 * 问题复杂度分类器
 * 目标：用最少Token判断是否需要增强推理
 */
@Service
public class QueryComplexityClassifier {

    // 简单查询关键词（命中任一即判定为简单）
    private static final Set<String> SIMPLE_KEYWORDS = Set.of(
        "多少", "是什么", "查一下", "告诉我", "看看",
        "有没有", "在哪里", "多少钱", "怎么样"
    );

    // 复杂查询关键词（命中任一即判定为复杂）
    private static final Set<String> COMPLEX_KEYWORDS = Set.of(
        "分析", "对比", "综合", "多个", "分别", "而且",
        "为什么", "原因", "建议", "预测", "评估"
    );

    // 复杂句式模式
    private static final List<Pattern> COMPLEX_PATTERNS = List.of(
        Pattern.compile(".{0,10}和.{0,10}对比"),
        Pattern.compile(".{0,10}以及.{0,10}"),
        Pattern.compile(".{0,10}，然后.{0,10}"),
        Pattern.compile(".{0,10}的同时.{0,10}")
    );

    /**
     * 判断查询复杂度
     */
    public ComplexityTier classify(String query) {
        // Tier 1: 明确简单
        if (isSimpleQuery(query)) {
            return ComplexityTier.DIRECT;
        }

        // Tier 3: 明确复杂
        if (isComplexQuery(query)) {
            return ComplexityTier.ENHANCED;
        }

        // Tier 2: 中等复杂，走传统 ReAct
        return ComplexityTier.REACT;
    }

    private boolean isSimpleQuery(String query) {
        return SIMPLE_KEYWORDS.stream().anyMatch(query::contains)
            && !isComplexQuery(query)
            && !containsComplexPattern(query);
    }

    private boolean isComplexQuery(String query) {
        return COMPLEX_KEYWORDS.stream().anyMatch(query::contains)
            || containsComplexPattern(query);
    }

    private boolean containsComplexPattern(String query) {
        return COMPLEX_PATTERNS.stream()
            .anyMatch(p -> p.matcher(query).find());
    }
}

public enum ComplexityTier {
    DIRECT,    // 直接执行
    REACT,     // 传统 ReAct
    ENHANCED   // 增强推理
}
```

#### 决策 3：错误恢复策略

```
┌─────────────────────────────────────────────────────────┐
│            智能错误恢复策略                               │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  核心原则：能省则省，但不让错误浪费机会                  │
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │ 错误类型 → 重试策略 → 反思时机                    │   │
│  ├─────────────────────────────────────────────────┤   │
│  │ 超时    → 重试2次   → 3次后反思                  │   │
│  │ 语法错误 → 立即反思  → 不重试（重试也无用）       │   │
│  │ 无数据   → 反思参数  → 检查条件是否正确           │   │
│  │ 限流    → 等待重试  → 3次后反思                  │   │
│  │ 权限    → 不重试    → 直接告知用户                │   │
│  └─────────────────────────────────────────────────┘   │
│                                                          │
│  最大重试次数：3次（配置可调）                         │
│  最大反思次数：2次（防止无限反思）                     │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

```java
/**
 * 错误恢复策略
 */
@Service
public class ErrorRecoveryStrategy {

    private static final int MAX_RETRY = 3;
    private static final int MAX_REFLECTION = 2;

    // 错误类型 → 重试策略
    private static final Map<String, RetryStrategy> ERROR_RETRY_MAP = Map.of(
        "timeout", new RetryStrategy(2, 1000),      // 超时：重试2次，间隔1秒
        "sql_syntax_error", new RetryStrategy(0, 0), // 语法错误：不重试
        "no_data", new RetryStrategy(0, 0),          // 无数据：不重试
        "rate_limit", new RetryStrategy(2, 2000),    // 限流：重试2次，间隔2秒
        "permission_denied", new RetryStrategy(0, 0) // 权限：不重试
    );

    /**
     * 决定下一步行动
     */
    public RecoveryAction decide(String errorType, int retryCount, int reflectionCount) {
        RetryStrategy strategy = ERROR_RETRY_MAP.getOrDefault(
            errorType, new RetryStrategy(1, 1000));

        // 是否应该重试
        if (retryCount < strategy.maxRetries()) {
            return RecoveryAction.RETRY;
        }

        // 是否应该反思
        if (reflectionCount < MAX_REFLECTION) {
            return RecoveryAction.REFLECT;
        }

        // 放弃，返回错误
        return RecoveryAction.ABANDON;
    }

    /**
     * 是否应该放弃任务
     */
    public boolean shouldAbandon(String errorType) {
        // 权限类错误直接放弃
        if (errorType.contains("permission") || errorType.contains("权限")) {
            return true;
        }
        // 数据不存在可以放弃（换个方法也没用）
        if (errorType.contains("not found") || errorType.contains("不存在")) {
            return true;
        }
        return false;
    }
}
```

#### 决策 4：并行执行策略

```
┌─────────────────────────────────────────────────────────┐
│            谨慎并行策略                                   │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  可以并行的场景：                                       │
│  ├── 独立的知识检索（知识库A、知识库B）               │
│  ├── 独立的统计计算（增长率、占比）                     │
│  ├── 独立的文件读取（文件A、文件B）                   │
│  └── 独立的API调用（天气API、股票API）                 │
│                                                          │
│  不可并行的场景：                                       │
│  ├── 有依赖关系的任务（A的结果是B的输入）             │
│  ├── 需要共享状态的任务                                 │
│  ├── 数据库写入类任务                                   │
│  └── 需要顺序执行的操作                                 │
│                                                          │
│  最大并发数：3（可配置）                               │
│  单任务超时：30秒                                       │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

```java
/**
 * 并行执行管理器
 */
@Service
public class ParallelExecutionManager {

    @Value("${app.agent.max-parallel-tasks:3}")
    private int maxParallelTasks;

    @Value("${app.agent.task-timeout-seconds:30}")
    private long taskTimeoutSeconds;

    /**
     * 判断任务是否可并行
     */
    public boolean canParallelize(SubTask task, List<SubTask> pendingTasks) {
        // 检查是否有依赖
        if (task.getDependencies() != null && !task.getDependencies().isEmpty()) {
            return false;
        }

        // 检查并发数限制
        return getCurrentParallelCount() < maxParallelTasks;
    }

    /**
     * 执行并行任务组
     */
    public List<SubTaskResult> executeParallel(List<SubTask> tasks) {
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(tasks.size(), maxParallelTasks));

        List<CompletableFuture<SubTaskResult>> futures = tasks.stream()
            .map(task -> CompletableFuture.supplyAsync(
                () -> executeTask(task), executor))
            .toList();

        // 等待所有任务完成（带超时）
        return futures.stream()
            .map(f -> {
                try {
                    return f.get(taskTimeoutSeconds, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return SubTaskResult.failure(null, "任务超时或失败: " + e.getMessage());
                }
            })
            .toList();
    }
}
```

#### 决策 5：Token 成本控制

```
┌─────────────────────────────────────────────────────────┐
│            Token 成本控制策略                             │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  核心原则：能省则省，不牺牲用户体验                      │
│                                                          │
│  1️⃣ 推理层级控制                                      │
│  ├── 简单问题：不走规划，直接执行                       │
│  ├── 中等问题：传统ReAct，不额外反思                   │
│  └── 复杂问题：完整推理，必要时才反思                   │
│                                                          │
│  2️⃣ Prompt 压缩                                        │
│  ├── 工具描述：只传递当前可能用到的工具                 │
│  ├── 上下文：超过阈值自动截断                           │
│  └── 历史消息：只保留最近N轮                           │
│                                                          │
│  3️⃣ LLM 调用最小化                                    │
│  ├── 规划Prompt：精简版（<500字）                      │
│  ├── 反思Prompt：精简版（<300字）                      │
│  └── 合并操作：能用一次LLM调用的不用两次               │
│                                                          │
│  4️⃣ 缓存复用                                           │
│  ├── 相同查询：缓存结果                                 │
│  ├── 工具Schema：缓存解析结果                          │
│  └── 用户画像：复用而非重新提取                         │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

```java
/**
 * Token 成本控制器
 */
@Service
public class TokenBudgetController {

    @Value("${app.agent.max-context-tokens:8000}")
    private int maxContextTokens;

    @Value("${app.agent.simple-query-threshold:500}")
    private int simpleQueryThreshold;  // 超过这个长度的query不算"简单"

    /**
     * 估算当前上下文 Token 数
     */
    public int estimateContextTokens(List<ChatMessage> messages) {
        return messages.stream()
            .mapToInt(msg -> msg.text().length() / 4)  // 粗略估算
            .sum();
    }

    /**
     * 决定推理深度
     */
    public ReasoningDepth decideReasoningDepth(String query, int currentTokens) {
        // 太长或太短都不算简单
        if (query.length() > simpleQueryThreshold) {
            return ReasoningDepth.ENHANCED;
        }

        // 检查是否包含复杂关键词
        if (containsComplexKeywords(query)) {
            return ReasoningDepth.ENHANCED;
        }

        // 上下文快满了，减少推理深度
        if (currentTokens > maxContextTokens * 0.8) {
            return ReasoningDepth.REACT;
        }

        return ReasoningDepth.DIRECT;
    }

    /**
     * 压缩历史消息
     */
    public List<ChatMessage> compressHistory(List<ChatMessage> messages) {
        int currentTokens = estimateContextTokens(messages);

        if (currentTokens <= maxContextTokens) {
            return messages;
        }

        // 保留系统消息和最近N轮
        List<ChatMessage> compressed = new ArrayList<>();
        compressed.add(messages.get(0)); // 系统消息

        // 从后往前保留，直到达到阈值
        int tokens = 0;
        for (int i = messages.size() - 1; i >= 1 && tokens < maxContextTokens * 0.6; i--) {
            compressed.add(0, messages.get(i));
            tokens += messages.get(i).text().length() / 4;
        }

        return compressed;
    }
}

public enum ReasoningDepth {
    DIRECT,    // 直接执行，不规划
    REACT,     // 传统ReAct，轻量推理
    ENHANCED   // 完整推理，规划+反思
}
```

---

## 七、Phase 4：Orchestrator 编排层

### 7.1 核心设计理念

```
┌─────────────────────────────────────────────────────────┐
│        Phase 4 核心设计理念                                 │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  🎯 用户能自己创建 Agent                                 │
│  ├── 动态注册，无需重启系统                             │
│  ├── 自定义 Agent 的能力和 Prompt                       │
│  └── 类似你的 SkillManager，但更强大                    │
│                                                          │
│  🔗 Agent 分工合作，不是各自独立                        │
│  ├── 任务分解 → 分配 → 协作 → 整合                     │
│  ├── Agent 间可以共享数据和状态                         │
│  └── 类似"团队协作"，不是"单兵作战"                   │
│                                                          │
│  🧠 理解复杂用户意图                                    │
│  ├── LLM 驱动的意图理解和任务规划                       │
│  ├── 规则兜底，处理简单场景                            │
│  └── 智能路由，选择最合适的 Agent                       │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 7.2 整体架构

```
┌─────────────────────────────────────────────────────────┐
│              Orchestrator 编排层架构                       │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │          OrchestratorAgent (总调度)                 │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌───────────┐│   │
│  │  │ Intent      │ │ Task       │ │ Result    ││   │
│  │  │ Analyzer    │ │ Planner    │ │ Integrator││   │
│  │  │ 意图理解    │ │ 任务规划    │ │ 结果整合  ││   │
│  │  └─────────────┘ └─────────────┘ └───────────┘│   │
│  └─────────────────────────────────────────────────┘   │
│                          │                               │
│                          ▼                               │
│  ┌─────────────────────────────────────────────────┐   │
│  │              SpecialistRegistry                    │   │
│  │              (专家注册中心)                       │   │
│  │                                                  │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐    │   │
│  │  │ System   │ │ Custom   │ │ Dynamic  │    │   │
│  │  │ Specialists│ │ Specialists│ │ Specialists│    │   │
│  │  │ 系统专家  │ │ 自定义专家 │ │ 动态专家  │    │   │
│  │  └──────────┘ └──────────┘ └──────────┘    │   │
│  └─────────────────────────────────────────────────┘   │
│                          │                               │
│          ┌───────────────┼───────────────┐             │
│          ▼               ▼               ▼             │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐   │
│  │  DataAnalyst │ │  Knowledge   │ │   Report    │   │
│  │  Specialist  │ │  Expert     │ │   Expert    │   │
│  │  数据分析专家 │ │  知识专家    │ │  报告专家   │   │
│  └──────────────┘ └──────────────┘ └──────────────┘   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐   │
│  │  SQL Expert  │ │  Chart      │ │  Custom     │   │
│  │  SQL专家     │ │  Expert     │ │  Agent #N   │   │
│  └──────────────┘ └──────────────┘ └──────────────┘   │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 7.3 工作流程

```
用户问：分析Q3销售数据，对比竞品，给出建议

══════════════════════════════════════════════════════

🎯 Step 1: 意图理解 (IntentAnalyzer)
────────────────────────────────────────────────────
LLM 分析用户意图：

"用户想要：
1. 分析Q3销售数据 → 需要 DataAnalyst
2. 对比竞品信息 → 需要 KnowledgeExpert
3. 给出建议 → 需要 ReportExpert
4. 复杂度：高（多步骤，需要规划）"

══════════════════════════════════════════════════════

📋 Step 2: 任务规划 (TaskPlanner)
────────────────────────────────────────────────────
分解为可执行任务：

Task 1: 查询Q3销售数据
  → Specialist: DataAnalyst
  → 依赖: 无，可并行

Task 2: 查询竞品信息
  → Specialist: KnowledgeExpert
  → 依赖: 无，可并行

Task 3: 对比分析销售与竞品
  → Specialist: DataAnalyst
  → 依赖: Task 1, Task 2

Task 4: 生成建议报告
  → Specialist: ReportExpert
  → 依赖: Task 3

执行计划：
阶段1（并行）: Task 1 + Task 2
阶段2（串行）: Task 3 → Task 4

══════════════════════════════════════════════════════

🔗 Step 3: 协作执行 (Specialists)
────────────────────────────────────────────────────
阶段1（并行）:

Task 1 执行中...                    Task 2 执行中...
┌────────────────────┐            ┌────────────────────┐
│ DataAnalyst        │            │ KnowledgeExpert    │
│ 查询销售数据...     │            │ 查询竞品信息...     │
│ 输出: 销售数据JSON │◄──────────►│ 输出: 竞品信息JSON │
│                    │  共享上下文 │                    │
└────────────────────┘            └────────────────────┘

阶段2（串行）:

Task 3 执行中...
┌────────────────────┐
│ DataAnalyst        │
│ 输入: Task1+Task2结果 │
│ 执行: 对比分析      │
│ 输出: 对比分析结果  │
└────────────────────┘
        ↓
Task 4 执行中...
┌────────────────────┐
│ ReportExpert       │
│ 输入: Task3结果    │
│ 执行: 生成建议报告 │
│ 输出: 完整报告    │
└────────────────────┘

══════════════════════════════════════════════════════

📦 Step 4: 结果整合 (ResultIntegrator)
────────────────────────────────────────────────────
收集各方结果：
- Task 1: Q3销售额1200万，增长15%
- Task 2: 竞品平均1000万
- Task 3: 高于竞品20%，主要增长来自天猫
- Task 4: 建议优化京东渠道、提升客单价

整合成最终回答，输出给用户

══════════════════════════════════════════════════════
```

### 7.4 核心组件设计

#### 7.4.1 SpecialistRegistry（专家注册中心）

```java
// com.ai.agent.orchestrator.registry.SpecialistRegistry
@Service
public class SpecialistRegistry {

    private final Map<String, Specialist> specialists = new ConcurrentHashMap<>();
    private final Map<String, SpecialistConfig> customSpecialists = new HashMap<>();

    /**
     * 注册系统专家（启动时自动注册）
     */
    @PostConstruct
    public void registerSystemSpecialists() {
        register(new DataAnalystSpecialist());
        register(new KnowledgeExpertSpecialist());
        register(new ChartExpertSpecialist());
        register(new SQLExpertSpecialist());
        register(new ReportExpertSpecialist());
        register(new FileExpertSpecialist());
    }

    /**
     * 注册自定义专家（用户创建）
     */
    public void registerCustomSpecialist(String userId, SpecialistConfig config) {
        String specialistId = generateSpecialistId(userId, config.getName());
        Specialist specialist = createSpecialistFromConfig(config);
        specialists.put(specialistId, specialist);
        customSpecialists.put(specialistId, config);

        log.info("Registered custom specialist: {} for user: {}", specialistId, userId);
    }

    /**
     * 注销专家
     */
    public void unregister(String specialistId) {
        specialists.remove(specialistId);
        customSpecialists.remove(specialistId);

        log.info("Unregistered specialist: {}", specialistId);
    }

    /**
     * 查找能处理任务的专家
     */
    public List<Specialist> findSpecialistsForTask(String taskDescription) {
        return specialists.values().stream()
            .filter(s -> s.canHandle(taskDescription))
            .sorted((a, b) -> {
                // 系统专家优先于自定义专家
                boolean aIsCustom = customSpecialists.containsKey(a.getId());
                boolean bIsCustom = customSpecialists.containsKey(b.getId());
                if (aIsCustom && !bIsCustom) return 1;
                if (!aIsCustom && bIsCustom) return -1;
                return 0;
            })
            .toList();
    }

    /**
     * 根据能力查找专家
     */
    public Optional<Specialist> findByCapability(String capability) {
        return specialists.values().stream()
            .filter(s -> s.getCapability().equals(capability))
            .findFirst();
    }

    /**
     * 获取所有专家
     */
    public List<Specialist> getAllSpecialists() {
        return new ArrayList<>(specialists.values());
    }

    /**
     * 获取用户的自定义专家
     */
    public List<SpecialistConfig> getUserSpecialists(String userId) {
        return customSpecialists.values().stream()
            .filter(c -> c.getUserId().equals(userId))
            .toList();
    }
}

/**
 * 专家配置（用户创建专家时使用）
 */
@Data
public class SpecialistConfig {

    private String userId;
    private String name;                    // 专家名称
    private String description;             // 专家描述
    private String capability;              // 能力标识

    // Prompt 配置
    private String systemPrompt;           // 系统提示词
    private String taskPromptTemplate;     // 任务提示模板

    // 工具配置
    private List<String> allowedTools;    // 允许使用的工具
    private List<String> disabledTools;     // 禁止使用的工具

    // 能力配置
    private Integer maxIterations;         // 最大迭代次数
    private Integer timeoutSeconds;         // 超时时间
    private String reasoningMode;          // 推理模式

    // 元数据
    private String icon;                   // 图标
    private String color;                  // 颜色
    private List<String> tags;            // 标签
}
```

#### 7.4.2 IntentAnalyzer（意图理解器）

```java
// com.ai.agent.orchestrator.IntentAnalyzer
@Service
public class IntentAnalyzer {

    private final McpModelService modelService;

    private static final String INTENT_ANALYSIS_PROMPT = """
        请分析以下用户请求的意图。

        用户请求：%s

        分析维度：
        1. 意图类型：查询/分析/生成/对比/推荐/其他
        2. 需要的能力组合
        3. 复杂度评估：简单/中等/复杂
        4. 是否需要多专家协作
        5. 关键实体和参数

        输出格式（严格JSON）：
        {
            "intent_type": "查询|分析|生成|对比|推荐|其他",
            "capabilities_needed": ["data_analysis", "knowledge_retrieval"],
            "complexity": "simple|medium|complex",
            "needs_collaboration": true/false,
            "entities": {"公司": "XX电商", "时间": "Q3"},
            "key_parameters": {"指标": "销售额", "维度": "月度"},
            "suggested_specialists": ["DataAnalyst", "KnowledgeExpert"]
        }
        """;

    /**
     * 分析用户意图
     */
    public IntentAnalysis analyze(String userQuery, String context) {
        // 1. 快速规则匹配（简单场景）
        IntentAnalysis quickMatch = tryQuickMatch(userQuery);
        if (quickMatch != null) {
            return quickMatch;
        }

        // 2. LLM 深度分析（复杂场景）
        return llmAnalyze(userQuery, context);
    }

    /**
     * 快速规则匹配（处理简单场景）
     */
    private IntentAnalysis tryQuickMatch(String userQuery) {
        // 简单查询
        if (userQuery.matches(".*(多少|是什么|查一下|看看).*")
            && !userQuery.contains("对比")
            && !userQuery.contains("分析")) {
            return IntentAnalysis.builder()
                .intentType(IntentType.QUERY)
                .complexity(Complexity.SIMPLE)
                .needsCollaboration(false)
                .suggestedSpecialists(List.of("DataAnalyst"))
                .build();
        }

        // 图表生成
        if (userQuery.contains("图") || userQuery.contains("图表")) {
            return IntentAnalysis.builder()
                .intentType(IntentType.GENERATION)
                .complexity(Complexity.MEDIUM)
                .needsCollaboration(false)
                .suggestedSpecialists(List.of("ChartExpert"))
                .build();
        }

        return null;
    }

    /**
     * LLM 深度分析
     */
    private IntentAnalysis llmAnalyze(String userQuery, String context) {
        String prompt = INTENT_ANALYSIS_PROMPT.formatted(userQuery);
        String response = modelService.callModel(prompt);

        try {
            return objectMapper.readValue(response, IntentAnalysis.class);
        } catch (Exception e) {
            log.warn("Failed to parse intent analysis, using fallback", e);
            return IntentAnalysis.builder()
                .intentType(IntentType.OTHER)
                .complexity(Complexity.MEDIUM)
                .needsCollaboration(true)
                .suggestedSpecialists(List.of("DataAnalyst"))
                .build();
        }
    }

    /**
     * 判断是否需要多专家协作
     */
    public boolean needsCollaboration(IntentAnalysis analysis) {
        // 明确需要协作
        if (analysis.isNeedsCollaboration()) {
            return true;
        }

        // 多能力组合
        if (analysis.getCapabilitiesNeeded().size() > 1) {
            return true;
        }

        // 复杂任务
        return analysis.getComplexity() == Complexity.COMPLEX;
    }
}

/**
 * 意图分析结果
 */
@Data
@Builder
public class IntentAnalysis {
    private IntentType intentType;
    private List<String> capabilitiesNeeded;
    private Complexity complexity;
    private boolean needsCollaboration;
    private Map<String, String> entities;
    private Map<String, String> keyParameters;
    private List<String> suggestedSpecialists;
}

public enum IntentType {
    QUERY,        // 查询
    ANALYSIS,     // 分析
    GENERATION,   // 生成
    COMPARISON,   // 对比
    RECOMMENDATION, // 推荐
    OTHER         // 其他
}

public enum Complexity {
    SIMPLE,   // 简单
    MEDIUM,   // 中等
    COMPLEX   // 复杂
}
```

#### 7.4.3 OrchestratorTaskPlanner（编排级任务规划器）

```java
// com.ai.agent.orchestrator.OrchestratorTaskPlanner
@Service
public class OrchestratorTaskPlanner {

    private final SpecialistRegistry specialistRegistry;
    private final McpModelService modelService;

    private static final String TASK_PLANNING_PROMPT = """
        请将用户请求分解为可执行的子任务。

        用户请求：%s

        意图分析结果：
        - 意图类型：%s
        - 需要的能力：%s
        - 复杂度：%s

        可用的专家：
        %s

        要求：
        1. 每个子任务分配给最合适的专家
        2. 识别任务间的依赖关系
        3. 识别可并行的任务
        4. 考虑任务结果的共享

        输出格式（严格JSON）：
        {
            "tasks": [
                {
                    "task_id": "t1",
                    "description": "任务描述",
                    "specialist": "专家名称",
                    "input_from": [],  // 依赖的task_id
                    "output_to": [],   // 输出给哪些task
                    "can_parallel_with": [],  // 可并行的task_id
                    "expected_output": "预期输出描述"
                }
            ],
            "execution_phases": [
                {"phase": 1, "tasks": ["t1", "t2"], "parallel": true},
                {"phase": 2, "tasks": ["t3"], "parallel": false}
            ],
            "shared_context_keys": ["销售数据", "竞品信息"]  // 专家间共享的数据
        }
        """;

    /**
     * 规划任务
     */
    public OrchestrationPlan plan(String userQuery, IntentAnalysis intentAnalysis) {
        // 1. 获取可用专家
        List<String> availableSpecialists = specialistRegistry.getAllSpecialists().stream()
            .map(Specialist::getName)
            .toList();

        // 2. 构建专家列表描述
        String specialistsDesc = availableSpecialists.stream()
            .map(s -> "- " + s)
            .collect(Collectors.joining("\n"));

        // 3. LLM 规划
        String prompt = TASK_PLANNING_PROMPT.formatted(
            userQuery,
            intentAnalysis.getIntentType(),
            intentAnalysis.getCapabilitiesNeeded(),
            intentAnalysis.getComplexity(),
            specialistsDesc
        );

        String response = modelService.callModel(prompt);

        // 4. 解析执行计划
        return parsePlan(response, intentAnalysis);
    }

    /**
     * 简单任务直接分配
     */
    public OrchestrationPlan planSimple(IntentAnalysis intentAnalysis) {
        String specialistName = intentAnalysis.getSuggestedSpecialists().get(0);

        OrchestrationTask task = OrchestrationTask.builder()
            .taskId("t1")
            .description("执行" + specialistName + "任务")
            .specialistName(specialistName)
            .build();

        return OrchestrationPlan.builder()
            .tasks(List.of(task))
            .executionPhases(List.of(
                ExecutionPhase.builder()
                    .phase(1)
                    .tasks(List.of("t1"))
                    .isParallel(false)
                    .build()
            ))
            .sharedContextKeys(List.of())
            .build();
    }

    private OrchestrationPlan parsePlan(String response, IntentAnalysis intentAnalysis) {
        try {
            return objectMapper.readValue(response, OrchestrationPlan.class);
        } catch (Exception e) {
            log.warn("Failed to parse task plan, using simple fallback", e);
            // 回退到简单分配
            return planSimple(intentAnalysis);
        }
    }
}

/**
 * 编排执行计划
 */
@Data
@Builder
public class OrchestrationPlan {
    private String planId;
    private String originalQuery;
    private List<OrchestrationTask> tasks;
    private List<ExecutionPhase> executionPhases;
    private List<String> sharedContextKeys;
    private LocalDateTime createdAt;
}

/**
 * 编排任务
 */
@Data
@Builder
public class OrchestrationTask {
    private String taskId;
    private String description;
    private String specialistName;
    private List<String> inputFrom;       // 依赖的task_id
    private List<String> outputTo;         // 输出给哪些task
    private List<String> canParallelWith;  // 可并行的task_id
    private String expectedOutput;
    private TaskStatus status;
    private Object result;
    private String error;
}

/**
 * 执行阶段
 */
@Data
@Builder
public class ExecutionPhase {
    private int phase;
    private List<String> tasks;
    private boolean isParallel;
}
```

#### 7.4.4 Specialist（专家接口）

```java
// com.ai.agent.orchestrator.specialist.Specialist
/**
 * 专家接口
 */
public interface Specialist {

    // 专家ID（唯一标识）
    String getId();

    // 专家名称（展示用）
    String getName();

    // 能力标识
    String getCapability();

    // 能力描述（用于意图理解）
    String getDescription();

    // 是否能处理这个任务
    boolean canHandle(String taskDescription);

    // 执行任务（使用增强推理引擎）
    SpecialistResult execute(SpecialistTask task);

    // 获取专家类型
    SpecialistType getType();

    // 获取优先级（数字越小优先级越高）
    default int getPriority() {
        return 100;
    }
}

public enum SpecialistType {
    SYSTEM,    // 系统专家（预置）
    CUSTOM,    // 自定义专家（用户创建）
    DYNAMIC    // 动态专家（按需创建）
}

/**
 * 专家任务
 */
@Data
public class SpecialistTask {
    private String taskId;
    private String description;
    private Map<String, Object> parameters;
    private Map<String, Object> sharedContext;  // 共享上下文（来自其他专家的结果）
    private SpecialistConfig config;              // 专家配置（自定义专家）
    private ExecutionContext executionContext;   // 执行上下文
}

/**
 * 专家执行结果
 */
@Data
@Builder
public class SpecialistResult {
    private String taskId;
    private String specialistId;
    private boolean success;
    private String result;
    private String error;
    private long executionTimeMs;
    private Map<String, Object> outputContext;  // 输出给其他专家的上下文
    private List<String> nextSuggestedTasks;     // 建议的后续任务
}
```

#### 7.4.5 DataAnalystSpecialist（数据分析专家）

```java
// com.ai.agent.orchestrator.specialist.DataAnalystSpecialist
@Component
public class DataAnalystSpecialist implements Specialist {

    private final EnhancedReActEngine reActEngine;
    private final AgentToolInvoker toolInvoker;

    @Override
    public String getId() {
        return "system_data_analyst";
    }

    @Override
    public String getName() {
        return "数据分析专家";
    }

    @Override
    public String getCapability() {
        return "data_analysis";
    }

    @Override
    public String getDescription() {
        return "擅长数据分析、销售统计、趋势计算、对比分析、数据可视化";
    }

    @Override
    public boolean canHandle(String taskDescription) {
        return containsKeywords(taskDescription,
            "销售", "数据", "统计", "增长", "下降",
            "分析", "对比", "趋势", "占比", "金额", "数量"
        );
    }

    @Override
    public int getPriority() {
        return 10;  // 系统专家，高优先级
    }

    @Override
    public SpecialistResult execute(SpecialistTask task) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 构建执行上下文
            ExecutionContext context = buildContext(task);

            // 2. 使用增强推理引擎执行
            EngineResult result = reActEngine.execute(
                task.getDescription(),
                getAvailableTools(),
                getModelId(),
                context
            );

            // 3. 构建输出上下文（给其他专家用）
            Map<String, Object> outputContext = new HashMap<>();
            outputContext.put("analysis_result", result.getFinalAnswer());
            outputContext.put("confidence", result.getConfidence());
            outputContext.put("task_results", result.getExecutionResult());

            return SpecialistResult.builder()
                .taskId(task.getTaskId())
                .specialistId(getId())
                .success(true)
                .result(result.getFinalAnswer())
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .outputContext(outputContext)
                .build();

        } catch (Exception e) {
            log.error("DataAnalyst execution failed", e);
            return SpecialistResult.builder()
                .taskId(task.getTaskId())
                .specialistId(getId())
                .success(false)
                .error(e.getMessage())
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    private ExecutionContext buildContext(SpecialistTask task) {
        ExecutionContext context = task.getExecutionContext();

        // 注入共享上下文（来自其他专家的结果）
        if (task.getSharedContext() != null) {
            String sharedInfo = task.getSharedContext().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));

            if (!sharedInfo.isEmpty()) {
                context.setAdditionalContext("【其他专家提供的信息】\n" + sharedInfo);
            }
        }

        return context;
    }
}
```

#### 7.4.6 CollaborationManager（协作管理器）

```java
// com.ai.agent.orchestrator.CollaborationManager
/**
 * 专家间协作管理器
 */
@Service
public class CollaborationManager {

    private final SpecialistRegistry specialistRegistry;

    /**
     * 管理共享上下文
     */
    public Map<String, Object> manageSharedContext(
            List<OrchestrationTask> tasks,
            List<SpecialistResult> results) {

        Map<String, Object> sharedContext = new HashMap<>();

        // 构建 taskId -> result 的映射
        Map<String, SpecialistResult> resultMap = results.stream()
            .collect(Collectors.toMap(SpecialistResult::getTaskId, r -> r));

        // 收集每个任务的输出上下文
        for (OrchestrationTask task : tasks) {
            SpecialistResult result = resultMap.get(task.getTaskId());
            if (result != null && result.getOutputContext() != null) {
                sharedContext.putAll(result.getOutputContext());
            }
        }

        return sharedContext;
    }

    /**
     * 为任务注入共享上下文
     */
    public SpecialistTask injectSharedContext(
            OrchestrationTask task,
            Map<String, Object> sharedContext) {

        SpecialistTask enrichedTask = SpecialistTask.builder()
            .taskId(task.getTaskId())
            .description(task.getDescription())
            .parameters(task.getParameters())
            .sharedContext(new HashMap<>())
            .build();

        // 只注入当前任务需要的上下文（根据 inputFrom）
        for (String dependencyId : task.getInputFrom()) {
            Object context = sharedContext.get(dependencyId);
            if (context != null) {
                enrichedTask.getSharedContext().put(dependencyId, context);
            }
        }

        return enrichedTask;
    }

    /**
     * 检测任务间的数据依赖
     */
    public List<String> detectDataDependencies(
            OrchestrationTask task,
            Map<String, SpecialistResult> completedResults) {

        List<String> dependencies = new ArrayList<>();

        // 检查任务描述中的引用
        for (String completedTaskId : completedResults.keySet()) {
            if (task.getDescription().contains(completedTaskId)) {
                dependencies.add(completedTaskId);
            }
        }

        // 显式依赖
        if (task.getInputFrom() != null) {
            dependencies.addAll(task.getInputFrom());
        }

        return dependencies;
    }
}
```

#### 7.4.7 OrchestratorAgent（编排器主类）

```java
// com.ai.agent.orchestrator.OrchestratorAgent
@Service
public class OrchestratorAgent {

    private final IntentAnalyzer intentAnalyzer;
    private final OrchestratorTaskPlanner taskPlanner;
    private final SpecialistRegistry specialistRegistry;
    private final CollaborationManager collaborationManager;
    private final ResultIntegrator resultIntegrator;

    /**
     * 执行用户请求
     */
    public OrchestratorResult execute(String userQuery,
                                     String userId,
                                     String tenantId) {
        long startTime = System.currentTimeMillis();

        OrchestratorResult result = OrchestratorResult.builder()
            .originalQuery(userQuery)
            .startTime(startTime)
            .build();

        try {
            // Step 1: 意图理解
            IntentAnalysis intentAnalysis = intentAnalyzer.analyze(userQuery, null);
            result.setIntentAnalysis(intentAnalysis);
            log.info("Intent analyzed: type={}, complexity={}, collaboration={}",
                intentAnalysis.getIntentType(),
                intentAnalysis.getComplexity(),
                intentAnalysis.isNeedsCollaboration());

            // Step 2: 任务规划
            OrchestrationPlan plan;
            if (!intentAnalysis.isNeedsCollaboration() &&
                intentAnalysis.getComplexity() == Complexity.SIMPLE) {
                // 简单任务，直接分配
                plan = taskPlanner.planSimple(intentAnalysis);
            } else {
                // 复杂任务，完整规划
                plan = taskPlanner.plan(userQuery, intentAnalysis);
            }
            result.setPlan(plan);
            log.info("Plan created: {} tasks in {} phases",
                plan.getTasks().size(),
                plan.getExecutionPhases().size());

            // Step 3: 执行计划
            ExecutionResult executionResult = executePlan(plan, userId, tenantId);
            result.setExecutionResult(executionResult);

            // Step 4: 结果整合
            String finalAnswer = resultIntegrator.integrate(
                userQuery, plan, executionResult);
            result.setFinalAnswer(finalAnswer);

            result.setSuccess(true);
            result.setEndTime(System.currentTimeMillis());

            return result;

        } catch (Exception e) {
            log.error("Orchestrator execution failed", e);
            result.setSuccess(false);
            result.setError(e.getMessage());
            result.setFinalAnswer("抱歉，处理您的请求时遇到问题：" + e.getMessage());
            result.setEndTime(System.currentTimeMillis());
            return result;
        }
    }

    /**
     * 执行编排计划
     */
    private ExecutionResult executePlan(OrchestrationPlan plan,
                                       String userId,
                                       String tenantId) {

        ExecutionResult execResult = ExecutionResult.builder()
            .planId(plan.getPlanId())
            .taskResults(new HashMap<>())
            .build();

        // 维护共享上下文
        Map<String, Object> sharedContext = new HashMap<>();

        // 按阶段执行
        for (ExecutionPhase phase : plan.getExecutionPhases()) {
            List<OrchestrationTask> phaseTasks = plan.getTasksByIds(phase.getTasks());

            if (phase.isParallel()) {
                // 并行执行
                executeTasksInParallel(phaseTasks, sharedContext, userId, tenantId, execResult);
            } else {
                // 串行执行
                executeTasksSerially(phaseTasks, sharedContext, userId, tenantId, execResult);
            }

            // 更新共享上下文
            collaborationManager.manageSharedContext(
                phaseTasks,
                new ArrayList<>(execResult.getTaskResults().values()));
        }

        return execResult;
    }

    /**
     * 并行执行任务
     */
    private void executeTasksInParallel(List<OrchestrationTask> tasks,
                                       Map<String, Object> sharedContext,
                                       String userId,
                                       String tenantId,
                                       ExecutionResult execResult) {
        // ... 并行执行逻辑
    }

    /**
     * 串行执行任务
     */
    private void executeTasksSerially(List<OrchestrationTask> tasks,
                                    Map<String, Object> sharedContext,
                                    String userId,
                                    String tenantId,
                                    ExecutionResult execResult) {
        for (OrchestrationTask task : tasks) {
            // 注入共享上下文
            SpecialistTask specialistTask =
                collaborationManager.injectSharedContext(task, sharedContext);

            // 获取专家
            Specialist specialist = specialistRegistry.findByName(task.getSpecialistName())
                .orElseThrow(() -> new IllegalStateException(
                    "Specialist not found: " + task.getSpecialistName()));

            // 执行
            SpecialistResult specialistResult = specialist.execute(specialistTask);

            // 保存结果
            execResult.getTaskResults().put(task.getTaskId(), specialistResult);

            // 更新共享上下文
            if (specialistResult.getOutputContext() != null) {
                sharedContext.put(task.getTaskId(), specialistResult.getOutputContext());
            }
        }
    }
}

/**
 * 编排结果
 */
@Data
@Builder
public class OrchestratorResult {
    private String originalQuery;
    private IntentAnalysis intentAnalysis;
    private OrchestrationPlan plan;
    private ExecutionResult executionResult;
    private String finalAnswer;
    private boolean success;
    private String error;
    private long startTime;
    private long endTime;
}
```

### 7.5 用户自定义 Agent 的 API

```java
// com.ai.agent.controller.CustomAgentController
@RestController
@RequestMapping("/api/agents")
public class CustomAgentController {

    private final SpecialistRegistry specialistRegistry;
    private final SpecialistFactory specialistFactory;

    /**
     * 创建自定义 Agent
     */
    @PostMapping("/custom")
    public ApiResponse<SpecialistConfig> createCustomAgent(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody CreateAgentRequest request) {

        // 1. 验证配置
        validateConfig(request);

        // 2. 生成专家ID
        String specialistId = generateSpecialistId(userId, request.getName());

        // 3. 创建配置
        SpecialistConfig config = SpecialistConfig.builder()
            .userId(userId)
            .name(request.getName())
            .description(request.getDescription())
            .capability(request.getCapability())
            .systemPrompt(request.getSystemPrompt())
            .allowedTools(request.getAllowedTools())
            .build();

        // 4. 注册专家
        specialistRegistry.registerCustomSpecialist(userId, config);

        // 5. 创建专家实例
        Specialist specialist = specialistFactory.createFromConfig(config);
        specialistRegistry.register(specialist);

        return ApiResponse.success(config);
    }

    /**
     * 获取用户的自定义 Agent 列表
     */
    @GetMapping("/custom")
    public ApiResponse<List<SpecialistConfig>> getUserAgents(
            @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(
            specialistRegistry.getUserSpecialists(userId));
    }

    /**
     * 更新自定义 Agent
     */
    @PutMapping("/custom/{agentId}")
    public ApiResponse<SpecialistConfig> updateCustomAgent(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String agentId,
            @RequestBody UpdateAgentRequest request) {
        // ... 更新逻辑
    }

    /**
     * 删除自定义 Agent
     */
    @DeleteMapping("/custom/{agentId}")
    public ApiResponse<Void> deleteCustomAgent(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String agentId) {
        specialistRegistry.unregister(agentId);
        return ApiResponse.success(null);
    }

    /**
     * 测试自定义 Agent
     */
    @PostMapping("/custom/{agentId}/test")
    public ApiResponse<SpecialistResult> testCustomAgent(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String agentId,
            @RequestBody TestAgentRequest request) {

        Specialist specialist = specialistRegistry.findById(agentId)
            .orElseThrow(() -> new NotFoundException("Agent not found"));

        SpecialistTask task = SpecialistTask.builder()
            .taskId("test")
            .description(request.getTaskDescription())
            .build();

        SpecialistResult result = specialist.execute(task);
        return ApiResponse.success(result);
    }
}

/**
 * 创建 Agent 请求
 */
@Data
public class CreateAgentRequest {
    @NotBlank
    private String name;                    // Agent 名称

    @NotBlank
    private String description;              // Agent 描述

    @NotBlank
    private String capability;              // 能力标识

    private String systemPrompt;            // 系统提示词

    private List<String> allowedTools;      // 允许的工具

    private Integer maxIterations;          // 最大迭代次数
}
```

### 7.6 协作数据流设计

```
┌─────────────────────────────────────────────────────────┐
│              Agent 间协作数据流                            │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  场景：分析Q3销售 + 对比竞品 → 生成报告                  │
│                                                          │
│  ┌─────────────┐                                        │
│  │ DataAnalyst │                                        │
│  │ 查询销售数据 │                                        │
│  └──────┬──────┘                                        │
│         │                                                 │
│         │ shared_context:                                │
│         │ {                                             │
│         │   "sales_data": {...},                       │
│         │   "growth_rate": "15%"                       │
│         │ }                                             │
│         ▼                                                 │
│  ┌─────────────┐                                        │
│  │KnowledgeExpert│                                       │
│  │ 查询竞品信息 │                                        │
│  └──────┬──────┘                                        │
│         │                                                 │
│         │ shared_context:                                │
│         │ {                                             │
│         │   "competitor_data": {...},                  │
│         │   "market_share": {...}                       │
│         │ }                                             │
│         ▼                                                 │
│  ┌─────────────┐                                        │
│  │ DataAnalyst │                                        │
│  │ 对比分析    │ ← 输入: 销售数据 + 竞品数据            │
│  └──────┬──────┘                                        │
│         │                                                 │
│         │ shared_context:                                │
│         │ {                                             │
│         │   "comparison_result": "高于竞品20%"          │
│         │ }                                             │
│         ▼                                                 │
│  ┌─────────────┐                                        │
│  │ ReportExpert │                                        │
│  │ 生成报告    │ ← 输入: 对比分析结果                     │
│  └─────────────┘                                        │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 7.7 Phase 4 实施任务清单

| # | 任务 | 优先级 | 涉及文件 | 工作量 | 说明 |
|---|------|--------|---------|--------|------|
| 4.1 | 设计 Specialist 接口 | P0 | `agent/AgentSpecialist.java`、`AgentExecutionRequest.java`、`SpecialistTask.java`、`SpecialistResult.java` | 2d | ✅ 已完成：统一专家执行契约、任务上下文和执行结果模型 |
| 4.2 | 实现 SpecialistRegistry | P0 | `agent/AgentSpecialistRegistry.java` | 3d | ✅ 已完成：按 `AgentType` 注册系统专家，支持运行时可用性判断和回退到 ReAct |
| 4.3 | 实现 IntentAnalyzer | P0 | `agent/IntentAnalyzer.java` | 4d | ✅ 已完成：规则优先、可选 LLM 增强的意图识别，输出偏好专家类型和置信度 |
| 4.4 | 实现 OrchestratorTaskPlanner | P0 | `agent/OrchestratorTaskPlanner.java` | 4d | ✅ 已完成：规则计划 + 可选 LLM 规划，支持多阶段、并行任务、依赖和共享上下文键 |
| 4.5 | 实现 CollaborationManager | P0 | `agent/CollaborationManager.java` | 2d | ✅ 已完成：共享上下文合并、依赖注入、依赖阻塞检查、协作质量摘要 |
| 4.6 | 实现 ResultIntegrator | P0 | `agent/ResultIntegrator.java` | 3d | ✅ 已完成：整合专家输出、共享上下文兜底、风险提示和质量摘要 |
| 4.7 | 实现 DataAnalystSpecialist | P0 | `agent/DataAgentSpecialist.java` | 3d | ✅ 已完成：数据源预览、任务上下文/记忆注入、模型分析回答 |
| 4.8 | 实现 KnowledgeExpertSpecialist | P0 | `agent/KnowledgeExpertSpecialist.java` | 3d | ✅ 已完成：知识库检索、引用元数据、基于知识上下文生成回答 |
| 4.9 | 实现 ChartExpertSpecialist | P1 | `agent/ChartExpertSpecialist.java` | 2d | ✅ 已完成：图表类型识别、上下文合并、图表工具执行和图表元数据 |
| 4.10 | 实现 ReportExpertSpecialist | P1 | `agent/ReportExpertSpecialist.java` | 3d | ✅ 已完成：报告 Prompt、上下文合并、Markdown 报告生成和摘要元数据 |
| 4.11 | 实现 OrchestratorAgent | P0 | `orchestrator/` | 5d | ✅ 已完成：编排主类已接入意图分析、任务规划、共享上下文和系统专家回退 |
| 4.12 | 实现 CustomAgentController | P0 | `controller/` | 3d | ✅ 已完成：新增 `/api/v1/my/agents` 用户自定义 Agent API |
| 4.13 | 实现 SpecialistFactory | P0 | `orchestrator/` | 2d | ✅ 已完成：统一专家解析、请求构建、模型默认值注入 |
| 4.14 | 改造 MultiAgentRuntimeService | P0 | `service/` | 2d | ✅ 已完成：配置化 Agent 执行统一走 SpecialistFactory |
| 4.15 | 单元测试 | P0 | `test/` | 4d | ✅ 已完成：覆盖工厂、服务、控制器和编排器关键路径 |
| 4.16 | 集成测试 | P0 | `test/` | 4d | ✅ 已完成：覆盖自定义 Agent 从同步分析入口到专家执行的端到端链路 |

**Phase 4 总工期：约 52 个工作日**

**当前实现说明**：为减少包层级和迁移成本，Phase 4 组件统一放在 `com.ai.agent`
下，而不是早期示例中的 `com.ai.agent.orchestrator.*` 包。自定义 Agent 持久化仍由
`AgentProfile`/`AgentProfileService` 管理，系统专家由 `AgentSpecialistRegistry`
注册，二者在 `OrchestratorAgent` 中统一参与路由和执行。

### 7.8 核心设计决策（已确定）

#### 决策 1：协作模式

```
┌─────────────────────────────────────────────────────────┐
│              Agent 协作模式                               │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ✅ 分工合作，不是各自独立                              │
│                                                          │
│  ├── 任务分解：Orchestrator 将复杂任务分解为子任务      │
│  ├── 能力匹配：每个子任务分配给最合适的 Agent           │
│  ├── 数据共享：Agent 间通过 shared_context 传递数据      │
│  ├── 依赖管理：Orchestrator 管理任务间的依赖关系        │
│  └── 结果整合：最终由 Orchestrator 整合各方输出          │
│                                                          │
│  共享上下文示例：                                       │
│  Task1 输出 → { "sales_data": {...} }                  │
│  Task2 输入 → { "sales_data": {...}, "competitor_data": {...} } │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

#### 决策 2：用户自定义 Agent

```
┌─────────────────────────────────────────────────────────┐
│              用户自定义 Agent 设计                         │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ✅ 用户能自己创建 Agent                                 │
│                                                          │
│  支持配置：                                             │
│  ├── Agent 名称和描述                                   │
│  ├── 系统提示词                                         │
│  ├── 能力标识                                           │
│  ├── 允许使用的工具列表                                 │
│  ├── 最大迭代次数                                       │
│  └── 超时时间                                           │
│                                                          │
│  注册机制：                                             │
│  ├── 动态注册，无需重启系统                            │
│  ├── 持久化到数据库（MySQL）                          │
│  ├── 用户间隔离（多租户）                               │
│  └── 支持测试和调试                                     │
│                                                          │
│  系统 Agent vs 自定义 Agent：                           │
│  ├── 系统 Agent：预置，高优先级，不可删除               │
│  └── 自定义 Agent：用户创建，可编辑，可删除             │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

#### 决策 3：意图理解策略

```
┌─────────────────────────────────────────────────────────┐
│              意图理解策略                                 │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ✅ 理解复杂用户意图                                     │
│                                                          │
│  简单场景 → 规则匹配（快速、低成本）                   │
│  ├── "销售额多少？" → 直接查数据库                    │
│  └── 耗时：<100ms                                      │
│                                                          │
│  复杂场景 → LLM 分析（精准、灵活）                    │
│  ├── "分析Q3销售，对比竞品，给出建议"                │
│  └── 耗时：500-1000ms                                 │
│                                                          │
│  判断逻辑：                                             │
│  ├── 包含"分析"+"对比" → LLM                         │
│  ├── 包含"生成"+"报告" → LLM                         │
│  └── 其他 → 规则匹配                                   │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 八、Phase 5：可观测性与安全（简化版）

### 8.1 设计理念

```
┌─────────────────────────────────────────────────────────┐
│         Phase 5 设计理念                                    │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  核心原则：做有价值的，放弃形式主义                      │
│                                                          │
│  ✅ 做：                                                 │
│  ├── 结构化日志（方便排查问题）                        │
│  ├── 用户反馈收集（质量评估靠用户）                    │
│  └── SQL注入防护（已有，继续保持）                      │
│                                                          │
│  ❌ 不做：                                               │
│  ├── 复杂追踪界面（不需要可视化）                      │
│  ├── 复杂输入审核（内部用户风险低）                    │
│  └── 高级安全防护（按需再加）                          │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 8.2 增强日志记录

```java
// com.ai.logging.StructuredLogger
/**
 * 结构化日志
 */
@Service
@Slf4j
public class StructuredLogger {

    private final ObjectMapper objectMapper;

    /**
     * 记录 Agent 执行步骤
     */
    public void logAgentStep(String sessionId, String step, Map<String, Object> data) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("timestamp", LocalDateTime.now().toString());
        logEntry.put("type", "AGENT_STEP");
        logEntry.put("session_id", sessionId);
        logEntry.put("step", step);
        logEntry.put("data", data);

        try {
            log.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            log.error("Failed to write structured log", e);
        }
    }

    /**
     * 记录工具调用
     */
    public void logToolCall(String sessionId, String toolName,
                           Map<String, Object> params, Object result,
                           long executionTimeMs, boolean success) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("timestamp", LocalDateTime.now().toString());
        logEntry.put("type", "TOOL_CALL");
        logEntry.put("session_id", sessionId);
        logEntry.put("tool", toolName);
        logEntry.put("params", params);
        logEntry.put("success", success);
        logEntry.put("execution_time_ms", executionTimeMs);

        if (!success) {
            logEntry.put("result", result);
        }

        try {
            log.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            log.error("Failed to write structured log", e);
        }
    }

    /**
     * 记录 LLM 调用
     */
    public void logLlmCall(String sessionId, String model,
                          int inputTokens, int outputTokens,
                          long latencyMs, double cost) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("timestamp", LocalDateTime.now().toString());
        logEntry.put("type", "LLM_CALL");
        logEntry.put("session_id", sessionId);
        logEntry.put("model", model);
        logEntry.put("input_tokens", inputTokens);
        logEntry.put("output_tokens", outputTokens);
        logEntry.put("latency_ms", latencyMs);
        logEntry.put("cost", cost);

        try {
            log.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            log.error("Failed to write structured log", e);
        }
    }
}
```

### 8.3 用户反馈收集

```java
// com.ai.feedback.FeedbackService
/**
 * 用户反馈服务
 */
@Service
public class FeedbackService {

    private final ConversationSessionRepository sessionRepository;

    /**
     * 收集用户反馈
     */
    public void recordFeedback(String sessionId, String messageId, FeedbackType type, String comment) {
        ConversationSession session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new NotFoundException("Session not found"));

        Feedback feedback = Feedback.builder()
            .id(UUID.randomUUID().toString())
            .sessionId(sessionId)
            .messageId(messageId)
            .type(type)
            .comment(comment)
            .createdAt(LocalDateTime.now())
            .build();

        session.getFeedbacks().add(feedback);
        sessionRepository.save(session);
    }

    /**
     * 统计满意度
     */
    public FeedbackStats getStats(String tenantId, LocalDateTime startDate, LocalDateTime endDate) {
        List<ConversationSession> sessions = sessionRepository
            .findByTenantIdAndCreatedAtBetween(tenantId, startDate, endDate);

        long totalPositive = 0;
        long totalNegative = 0;
        long totalMessages = 0;

        for (ConversationSession session : sessions) {
            for (Feedback feedback : session.getFeedbacks()) {
                totalMessages++;
                if (feedback.getType() == FeedbackType.POSITIVE) {
                    totalPositive++;
                } else if (feedback.getType() == FeedbackType.NEGATIVE) {
                    totalNegative++;
                }
            }
        }

        return FeedbackStats.builder()
            .totalFeedbacks(totalMessages)
            .positiveCount(totalPositive)
            .negativeCount(totalNegative)
            .positiveRate(totalMessages > 0 ? (double) totalPositive / totalMessages : 0)
            .negativeRate(totalMessages > 0 ? (double) totalNegative / totalMessages : 0)
            .build();
    }

    /**
     * 获取低质量回答（用于分析改进）
     */
    public List<FeedbackWithMessage> getNegativeFeedbacks(String tenantId, int limit) {
        List<ConversationSession> sessions = sessionRepository.findByTenantId(tenantId);

        List<FeedbackWithMessage> negativeFeedbacks = new ArrayList<>();

        for (ConversationSession session : sessions) {
            for (Feedback feedback : session.getFeedbacks()) {
                if (feedback.getType() == FeedbackType.NEGATIVE) {
                    ConversationMessage message = session.getMessages().stream()
                        .filter(m -> m.getId().equals(feedback.getMessageId()))
                        .findFirst()
                        .orElse(null);

                    if (message != null) {
                        negativeFeedbacks.add(new FeedbackWithMessage(feedback, message));
                    }
                }
            }
        }

        return negativeFeedbacks.stream()
            .sorted(Comparator.comparing(FeedbackWithMessage::getCreatedAt).reversed())
            .limit(limit)
            .toList();
    }
}

@Data
@Builder
public class Feedback {
    private String id;
    private String sessionId;
    private String messageId;
    private FeedbackType type;
    private String comment;
    private LocalDateTime createdAt;
}

public enum FeedbackType {
    POSITIVE,  // 👍 满意
    NEGATIVE,  // 👎 不满意
    NEUTRAL    // 中立
}

@Data
public class FeedbackWithMessage {
    private Feedback feedback;
    private ConversationMessage message;
}

@Data
@Builder
public class FeedbackStats {
    private long totalFeedbacks;
    private long positiveCount;
    private long negativeCount;
    private double positiveRate;
    private double negativeRate;
}
```

### 8.4 API 接口

```java
// com.ai.feedback.FeedbackController
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 提交反馈
     */
    @PostMapping
    public ApiResponse<Void> submitFeedback(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestHeader("X-Message-Id") String messageId,
            @RequestBody FeedbackRequest request) {

        feedbackService.recordFeedback(
            sessionId,
            messageId,
            request.getType(),
            request.getComment()
        );

        return ApiResponse.success(null);
    }

    /**
     * 获取满意度统计
     */
    @GetMapping("/stats")
    public ApiResponse<FeedbackStats> getStats(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        FeedbackStats stats = feedbackService.getStats(tenantId, startDate, LocalDateTime.now());

        return ApiResponse.success(stats);
    }

    /**
     * 获取低质量回答（研发排查用）
     */
    @GetMapping("/negative")
    public ApiResponse<List<FeedbackWithMessage>> getNegativeFeedbacks(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(defaultValue = "20") int limit) {

        List<FeedbackWithMessage> feedbacks = feedbackService.getNegativeFeedbacks(tenantId, limit);

        return ApiResponse.success(feedbacks);
    }
}

/**
 * 反馈请求
 */
@Data
public class FeedbackRequest {
    @NotNull
    private FeedbackType type;

    private String comment;
}
```

### 8.5 现有安全机制（继续保持）

```
┌─────────────────────────────────────────────────────────┐
│         已有的安全机制                                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ✅ SQL 注入防护                                        │
│  ├── 白名单 SQL 验证                                    │
│  ├── 参数化查询                                         │
│  └── 数据范围限制                                        │
│                                                          │
│  ✅ 权限控制                                            │
│  ├── JWT 认证                                           │
│  ├── 多租户隔离                                         │
│  └── 数据源权限                                          │
│                                                          │
│  ✅ 审计日志                                            │
│  ├── AuditLogService                                    │
│  └── TokenUsageRecorder                                 │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 8.6 Phase 5 实施任务清单

| # | 任务 | 优先级 | 涉及文件 | 工作量 | 说明 |
|---|------|--------|---------|--------|------|
| 5.1 | 实现结构化日志 | P0 | 新建 `logging/StructuredLogger.java` | 2d | ✅ 已完成：统一 JSON 事件日志，支持 Agent、工具、LLM、反馈、轨迹事件，并自动携带请求链路上下文 |
| 5.2 | 改造现有日志为结构化 | P0 | 改造相关Service | 1d | ✅ 已完成：ReAct 步骤、工具调用、LLM 调用、反馈保存、轨迹保存已接入 |
| 5.3 | 实现FeedbackService | P0 | 新建 `feedback/FeedbackService.java` | 2d | ✅ 已完成：支持反馈收集、覆盖更新、统计摘要和负向反馈洞察 |
| 5.4 | 实现FeedbackController | P0 | 新建 `feedback/FeedbackController.java` | 1d | ✅ 已完成：开放提交、列表、摘要、洞察、Dashboard API |
| 5.5 | 前端集成反馈按钮 | P1 | 前端改造 | 2d | ✅ 已完成：对话页支持正向/负向反馈，管理端反馈页支持筛选和轨迹查看 |
| 5.6 | 代码审查 | P0 | - | 1d | ✅ 已完成：补充聚焦测试，编译、Checkstyle、diff 检查通过 |

**Phase 5 总工期：约 9 个工作日**（简化版）

---

## 九、技术决策

### 9.1 关键技术选型

#### 9.1.1 存储层选型

```
┌─────────────────────────────────────────────────────────┐
│         存储架构说明                                       │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  MySQL（关系型、结构化）：                             │
│  ├── 会话、消息                                       │
│  ├── 用户、租户                                       │
│  ├── Agent配置、工具配置                             │
│  ├── 记忆摘要（不是向量）                             │
│  └── 业务统计数据                                     │
│                                                          │
│  Redis（缓存）：                                       │
│  ├── 工作记忆                                         │
│  └── 会话缓存                                         │
│                                                          │
│  Elasticsearch（全文 + 向量）：                         │
│  ├── 知识库文档                                       │
│  ├── BM25 全文检索                                   │
│  └── 配合 Milvus 做混合检索                          │
│                                                          │
│  Milvus（向量）：                                     │
│  ├── 长期记忆向量                                     │
│  ├── RAG 向量检索                                     │
│  └── 知识库向量                                       │
│                                                          │
│  MinIO（文件）：                                      │
│  └── 上传的文件                                       │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

| 决策点 | 选项 | 选择 | 理由 |
|--------|------|------|------|
| 结构化存储 | MySQL / PostgreSQL | MySQL | 保持现有基础设施 |
| 缓存 | Redis | Redis | 保持现有基础设施 |
| 全文检索 | MySQL全文 / ES | **ES** | 企业级全文搜索更强 |
| 向量存储 | ES向量 / Milvus | **ES + Milvus** | 企业级，向量性能最优 |
| 文件存储 | 本地 / MinIO | MinIO | 保持现有基础设施 |

#### 9.1.2 AI层选型

| 决策点 | 选项 | 选择 | 理由 |
|--------|------|------|------|
| Embedding模型 | AllMiniLmL6V2 / BGE-zh | **BGE-zh** | 中文效果好，专为中文训练 |
| LLM 主力 | 通义千问 / 智谱 / DeepSeek | **智谱GLM** | 中文理解强，稳定性好 |
| LLM 备选 | DeepSeek | **DeepSeek** | 性价比高，复杂推理能力强 |
| Reranker | 无 / 开源 / 商业 | **BGE-Reranker** | 开源免费，可私有部署 |
| 分块策略 | 固定 / 语义 / 层级 | 组合模式 | 不同文档类型用不同策略 |
| 检索策略 | 向量 / BM25 / 混合 | 混合检索 | ES全文 + Milvus向量 + RRF融合 |

#### 9.1.3 应用层选型

| 决策点 | 选项 | 选择 | 理由 |
|--------|------|------|------|
| 推理模式 | ReAct / Plan-Execute / Reflexion | **Plan-Execute-Reflect** | 结合三者优点，简单问题简单处理 |
| 模型路由 | 规则 / LLM判断 | LLM判断 | 更智能的任务分配 |
| 异步处理 | CompletableFuture | CompletableFuture | JDK原生，无需额外依赖 |
| 配置管理 | yml / 配置中心 | yml | 先用简单的，后续有需求再加 |

#### 9.1.4 可观测性选型

| 决策点 | 选项 | 选择 | 理由 |
|--------|------|------|------|
| 日志 | 普通日志 / JSON结构化 | **JSON结构化** | 方便查询和分析 |
| 监控 | 无 / Micrometer | **Prometheus + Grafana** | 简单粒度，支持扩展 |
| 追踪界面 | 做 / 不做 | **不做** | 用结构化日志替代 |
| 质量评估 | 自动 / 用户反馈 | **用户反馈** | 简单有效 |
| 输入审核 | 做 / 不做 | **不做** | 内部用户风险低 |

#### 9.1.5 最终技术栈

```
┌─────────────────────────────────────────────────────────┐
│              最终技术栈                                       │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  基础设施：                                             │
│  ├── Spring Boot 3.2 + Java 17                     │
│  ├── MySQL + Redis + MinIO                            │
│  ├── Elasticsearch + Milvus                            │
│  └── React + Vite                                      │
│                                                          │
│  AI 能力：                                             │
│  ├── Embedding：BGE-zh（中文）                        │
│  ├── LLM：智谱GLM（主力）+ DeepSeek（备选）         │
│  ├── Reranker：BGE-Reranker                           │
│  └── LangChain4j                                       │
│                                                          │
│  可观测性：                                             │
│  ├── 日志：JSON结构化                                  │
│  └── 监控：Prometheus + Grafana                       │
│                                                          │
│  安全保障：                                             │
│  ├── JWT + Spring Security                             │
│  ├── SQL注入防护                                       │
│  └── 多租户隔离                                        │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 9.2 性能目标

| 指标 | 当前 | Phase 1 目标 | 最终目标 |
|------|------|-------------|---------|
| 记忆聚合延迟 | N/A | < 200ms | < 200ms |
| RAG 端到端延迟 | < 2s | < 2s | < 2s |
| 复杂任务总延迟 | < 30s | < 20s | < 15s |
| Token 成本 | 基准 | -20% | -40% |
| ES 检索延迟 | N/A | < 100ms | < 100ms |
| Milvus 向量检索延迟 | N/A | < 50ms | < 50ms |

### 9.3 基础设施规划

```
┌─────────────────────────────────────────────────────────┐
│              基础设施规划                                    │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Phase 1-2（初期）：                                   │
│  ├── ES 单节点 / 3节点集群                            │
│  ├── Milvus 单节点                                    │
│  └── 满足中小企业规模（<100万向量）                     │
│                                                          │
│  Phase 3+（扩展）：                                    │
│  ├── ES 3节点集群                                    │
│  ├── Milvus 分布式集群                                │
│  └── 支持中大型企业（100万~1000万向量）                │
│                                                          │
│  运维建议：                                             │
│  ├── ES + Milvus 都有 Docker 部署方案                │
│  ├── 推荐使用云服务商托管（阿里云/腾讯云）             │
│  └── 降低运维成本，专注业务开发                       │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 十、风险与挑战

### 10.1 技术风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| LLM 摘要质量不稳定 | 高 | 中 | 多模型投票 / 人工抽检 / 可配置压缩策略 |
| 向量检索精度不足 | 高 | 低 | 混合检索 + 重排序 + 用户反馈闭环 |
| Phase 1 改动影响现有功能 | 中 | 中 | 渐进式改造，功能开关控制 |

### 10.2 实施风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| Phase 1 工期超预期 | 中 | 高 | 拆分为多个小迭代，每个可独立上线 |
| ES + Milvus 运维成本 | 中 | 中 | 使用云托管服务降低运维压力 |
| LLM 摘要质量不稳定 | 高 | 中 | 多模型投票 / 人工抽检 / 可配置压缩策略 |
| 向量检索精度不足 | 高 | 低 | 混合检索 + 重排序 + 用户反馈闭环 |
| 多方依赖 | 中 | 低 | 提前做好联调环境 |

---

## 十一、项目结构

```
com.ai/
├── agent/
│   ├── orchestrator/                    # 🆕 Orchestrator 编排层
│   │   ├── OrchestratorAgent.java        # 编排主类
│   │   ├── IntentAnalyzer.java           # 意图理解
│   │   ├── TaskPlanner.java             # 任务规划
│   │   ├── ResultIntegrator.java       # 结果整合
│   │   └── CollaborationManager.java    # 协作管理
│   │
│   ├── specialist/                       # 🆕 专家 Agent 集合
│   │   ├── Specialist.java              # 专家接口
│   │   ├── SpecialistRegistry.java      # 专家注册中心
│   │   ├── DataAnalystSpecialist.java   # 数据分析专家
│   │   ├── KnowledgeExpertSpecialist.java # 知识检索专家
│   │   ├── ChartExpertSpecialist.java   # 图表专家
│   │   ├── SQLExpertSpecialist.java     # SQL专家
│   │   └── ReportExpertSpecialist.java  # 报告专家
│   │
│   ├── react/                          # 🔄 增强版 ReAct
│   │   ├── EnhancedReActEngine.java     # 增强推理引擎
│   │   ├── TaskPlanner.java           # 任务分解
│   │   ├── ParallelExecutor.java       # 并行执行
│   │   ├── SelfReflector.java         # 自我反思
│   │   ├── ErrorRecovery.java          # 错误恢复
│   │   └── FunctionCallParser.java     # 函数调用解析
│   │
│   ├── AgentTools.java                  # 🔄 支持 JSON Function Calling
│   └── MultiAgentRuntimeService.java   # 🔄 升级为编排器

├── memory/                              # 🆕 三层记忆体系
│   ├── MemoryManager.java              # 统一记忆管理器
│   ├── WorkingMemory.java               # 工作记忆 (Redis)
│   ├── ShortTermMemory.java            # 短期记忆 (MySQL)
│   ├── LongTermMemory.java             # 长期记忆 (Milvus)
│   ├── MemoryCompressor.java           # LLM 摘要压缩
│   ├── MemoryWorthinessEvaluator.java  # 记忆价值评估
│   └── dto/
│       ├── MemoryEntry.java            # 记忆条目
│       ├── MemoryContext.java          # 记忆上下文
│       └── UserProfile.java            # 用户画像

├── rag/                                 # 🔄 深度 RAG
│   ├── RagPipeline.java                # RAG 主管道
│   ├── QueryRewriter.java             # 查询改写
│   ├── QueryClassifier.java           # 查询分类
│   ├── HybridRetriever.java           # 混合检索 (ES + Milvus)
│   ├── Reranker.java                  # Cross-Encoder 重排
│   ├── CitationGenerator.java         # 引用溯源
│   └── dto/
│       ├── RetrievalResult.java        # 检索结果
│       └── Citation.java               # 引用信息

├── vector/                             # 🔄 智能分块
│   ├── SmartTextChunker.java         # 智能分块器
│   ├── SemanticChunker.java          # 语义分块
│   ├── HierarchicalChunker.java      # 层级分块
│   ├── DocumentStructureParser.java   # 文档结构解析
│   └── dto/
│       └── ChunkMetadata.java         # 分块元数据

├── search/                             # 🆕 Elasticsearch 集成
│   ├── ElasticsearchService.java      # ES 服务
│   ├── DocumentIndexer.java          # 文档索引
│   └── SearchQueryBuilder.java       # 搜索查询构建

├── embedding/                          # 🆕 Embedding 服务
│   ├── EmbeddingService.java         # Embedding 服务
│   └── BgeZhEmbedding.java          # BGE-zh 中文嵌入

├── monitoring/                         # 🆕 监控服务
│   ├── MetricsCollector.java         # 指标收集
│   ├── MetricsController.java       # 指标 API
│   └── dto/
│       └── MetricsData.java         # 指标数据

├── feedback/                           # 🆕 用户反馈
│   ├── FeedbackService.java          # 反馈服务
│   ├── FeedbackController.java       # 反馈 API
│   └── dto/
│       └── Feedback.java             # 反馈数据

├── logging/                           # 🆕 结构化日志
│   └── StructuredLogger.java         # 结构化日志

└── skill/                             # 🔄 技能系统
    ├── SkillManager.java            # 技能管理
    └── DynamicSkill.java           # 动态技能
```

---

## 十二、讨论议题汇总

| # | 议题 | 选项 | 状态 | 结论 |
|---|------|------|------|------|
| 1 | 压缩触发策略 | A:固定轮数 B:Token阈值 C:语义检测 D:混合模式 | ✅ 已确定 | D: 混合模式 |
| 2 | 短期记忆保留时长 | 7天/30天/90天 | ✅ 已确定 | 30天（标准） |
| 3 | 摘要压缩粒度 | 每轮/批量/渐进 | ✅ 已确定 | 渐进压缩 |
| 4 | 分块策略优先级 | 语义/层级/元数据 | ✅ 已确定 | 语义分块 (P0) |
| 5 | Orchestrator 实现方式 | 纯LLM/纯规则/LLM+规则 | ✅ 已确定 | LLM+规则混合 |
| 6 | 存储选型 | ES vs ES+Milvus | ✅ 已确定 | ES + Milvus 组合 |
| 7 | Embedding模型 | AllMiniLm / BGE-zh | ✅ 已确定 | BGE-zh（中文） |
| 8 | 监控粒度 | 简单/详细 | ✅ 已确定 | 简单粒度，支持扩展 |

---

## 十三、实施路线图

```
┌─────────────────────────────────────────────────────────┐
│              实施路线图                                       │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Phase 1: 记忆体系升级                                 │
│  ├── 时间：~28天                                       │
│  ├── 核心：三层记忆 + 摘要压缩                        │
│  └── 里程碑：Agent能记住用户偏好和历史                  │
│                                                          │
│  Phase 2: 智能分块 + RAG                             │
│  ├── 时间：~47天                                       │
│  ├── 核心：ES全文 + Milvus向量 + Reranker           │
│  └── 里程碑：RAG检索质量显著提升                      │
│                                                          │
│  Phase 3: 增强推理引擎                                 │
│  ├── 时间：~43天                                       │
│  ├── 核心：Plan-Execute-Reflect                      │
│  └── 里程碑：复杂任务处理能力增强                      │
│                                                          │
│  Phase 4: Orchestrator 编排层                         │
│  ├── 时间：~52天                                       │
│  ├── 核心：多专家协作 + 用户自定义Agent               │
│  └── 里程碑：真正的多Agent协作                       │
│                                                          │
│  Phase 5: 可观测性与安全                               │
│  ├── 时间：~9天                                       │
│  ├── 核心：结构化日志 + 用户反馈                     │
│  └── 里程碑：基础运维能力建立                         │
│                                                          │
├─────────────────────────────────────────────────────────┤
│  总工期：~179天（约9个月）                              │
└─────────────────────────────────────────────────────────┘
```

---

## 十四、验收标准

### Phase 1 验收标准

```
功能验收：
☑ 三层记忆正常工作（工作/短期/长期）
☑ 对话摘要压缩功能正常
☑ 用户画像提取功能正常
☑ 记忆上下文正确注入Prompt

性能验收：
☑ 记忆聚合通过 MemoryManager 统一读取，并支持配额、保留、衰减治理
☑ 记忆存储增长可控（摘要压缩、价值判断、配额和保留策略已接入）

质量验收：
☑ Agent能记住用户说过的关键信息
☑ 跨会话记忆可用
```

Phase 1 验收证据：
- `MemoryManagerTest`、`MemoryCrossSessionIntegrationTest` 验证记忆捕获、检索和跨会话上下文。
- `MemoryDecaySchedulerTest`、`MemoryGovernanceServiceTest`、`MemoryQuotaServiceTest` 验证记忆治理、衰减和配额。
- `DeterministicMemoryCompressorTest`、`ModelBackedMemoryCompressorTest`、`MemoryWorthinessEvaluatorTest` 验证压缩和值得记忆判断。
- `UserProfileMemoryServiceTest`、`UserProfileMemoryRefreshServiceTest` 验证用户画像提取和刷新。
- 2026-05-19 已执行聚焦测试：
  `mvn -q -Dtest=MemoryManagerTest,MemoryCrossSessionIntegrationTest,MemoryDecaySchedulerTest,MemoryGovernanceServiceTest,MemoryWorthinessEvaluatorTest test`

### Phase 2 验收标准

```
功能验收：
☑ ES全文检索正常工作
☑ Milvus向量检索正常工作
☑ 混合检索（RRF）正常工作
☑ Reranker重排正常工作
☑ 引用溯源功能正常

性能验收：
☑ 支持 JPA 全文回退和 Elasticsearch 全文实现，便于本地与生产环境分层验证
☑ Milvus 向量库路径已成为唯一向量检索路径，内存向量路径已移除
☑ RAG 管道支持查询重写、混合检索、重排、父上下文和上下文压缩

质量验收：
☑ 检索结果相关性通过 RRF 融合和 Reranker 提升
☑ 引用编号、来源、分数、章节路径和片段可返回前端
```

Phase 2 验收证据：
- `TextChunkerTest`、`SmartTextChunkerTest`、`SemanticChunkerTest`、`HierarchicalChunkerTest` 验证结构化分块。
- `HybridRetrieverTest`、`RagRetrievalServiceTest`、`EnhancedRagPipelineTest` 验证增强 RAG 主链路。
- `RagQueryRewriterTest`、`RagRerankerTest`、`RrfFusionRankerTest` 验证查询改写、重排和融合。
- `ElasticsearchFullTextClientTest`、`ElasticsearchFullTextRetrieverTest`、`JpaFullTextRetrieverTest` 验证全文检索实现与回退。
- `RagParentContextResolverTest`、`RagContextCompressorTest`、`RagQualityEvaluationServiceTest` 验证父上下文、压缩和质量评估。
- 2026-05-19 已执行聚焦测试：
  `mvn -q -Dtest=EnhancedRagPipelineTest,HybridRetrieverTest,RagRetrievalServiceTest,RagQueryRewriterTest,RagRerankerTest,TextChunkerTest,SmartTextChunkerTest,SemanticChunkerTest,HierarchicalChunkerTest test`

### Phase 3 验收标准

```
功能验收：
☑ 简单问题走快速路径（直接执行）
☑ 复杂问题走增强推理（规划+反思）
☑ 错误恢复机制正常
☑ 并行执行正常

性能验收：
☑ 简单问题进入 fast_path，不触发 ReAct 循环
☑ 复杂问题进入 reasoning，并输出执行计划、并行预检、迭代次数和反思步骤

质量验收：
☑ 错误恢复次数按错误类型隔离，不跨请求泄漏
☑ 反思机制能向模型追加调整建议，并在恢复耗尽后生成降级回答
```

Phase 3 验收证据：
- `TaskPlannerTest`、`TaskComplexityClassifierTest` 验证快速路径和规划决策。
- `ParallelPlanExecutorTest`、`ParallelTaskExecutorTest` 验证安全并行预检和上下文传播。
- `ErrorRecoveryAdvisorTest`、`ReActRecoveryTrackerTest`、`SelfReflectorTest` 验证错误恢复和反思。
- `ReActResponseParserTest` 验证结构化工具调用解析。
- `ReActAgentFastPathTest`、`ReActSynchronousStepProcessorTest`、`ReActStreamingStepProcessorTest` 验证同步/流式主链路。
- 2026-05-19 已执行聚焦测试：
  `mvn -q -Dtest=TaskPlannerTest,TaskComplexityClassifierTest,ParallelPlanExecutorTest,ParallelTaskExecutorTest,ErrorRecoveryAdvisorTest,SelfReflectorTest,ReActFastPathDeciderTest,ReActFastAnswerServiceTest,ReActRecoveryTrackerTest,ReActSynchronousStepProcessorTest,ReActStreamingStepProcessorTest,ReActAgentFastPathTest,ReActResponseParserTest,ResultIntegratorTest test`

### Phase 4 验收标准

```
功能验收：
☑ 内置专家注册中心可解析 DATA / KNOWLEDGE / CHART / REPORT / CHAT / REACT
☑ Orchestrator 可做意图识别、任务规划、专家选择和 ReAct 回退
☑ 多阶段任务支持并行执行、依赖阻塞和共享上下文注入
☑ 用户自定义 Agent 支持创建、查询、更新、启停、试运行和删除

性能验收：
☑ 可并行阶段通过 ParallelTaskExecutor 执行，并继承请求 MDC 与认证上下文
☑ 单专家任务可直接路由，不强制进入多专家流程

质量验收：
☑ 编排结果携带 collaborationSummary、integrationSummary、执行计划和任务轨迹
☑ 依赖失败时下游任务会跳过并给出编排提示，避免基于缺失结果继续生成
```

Phase 4 验收证据：
- `AgentSpecialistRegistryTest`、`SpecialistFactoryTest` 验证专家注册、解析和请求构建。
- `IntentAnalyzerTest`、`OrchestratorTaskPlannerTest` 验证意图识别和编排计划。
- `CollaborationManagerTest`、`ResultIntegratorTest` 验证共享上下文、依赖阻塞、质量摘要和结果整合。
- `DataAgentSpecialistTest`、`KnowledgeExpertSpecialistTest`、`ChartExpertSpecialistTest`、`ReportExpertSpecialistTest` 验证核心系统专家。
- `OrchestratorAgentTest`、`MultiAgentRuntimeServiceTest`、`CustomAgentRuntimeIntegrationTest`、`CustomAgentControllerTest` 验证多 Agent 主链路和用户自定义 Agent API。
- 2026-05-19 已执行聚焦测试：
  `mvn -q -Dtest=AgentSpecialistRegistryTest,IntentAnalyzerTest,OrchestratorTaskPlannerTest,CollaborationManagerTest,ResultIntegratorTest,DataAgentSpecialistTest,KnowledgeExpertSpecialistTest,ChartExpertSpecialistTest,ReportExpertSpecialistTest,SpecialistFactoryTest,OrchestratorAgentTest,MultiAgentRuntimeServiceTest,CustomAgentRuntimeIntegrationTest,CustomAgentControllerTest test`

---

## 十五、依赖项清单

```
┌─────────────────────────────────────────────────────────┐
│              新增依赖项                                       │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  基础设施：                                             │
│  ├── elasticsearch: 8.x                                │
│  ├── milvus-sdk-java: 2.x                             │
│  └── bge-reranker (自定义)                            │
│                                                          │
│  监控：                                                 │
│  ├── micrometer-registry-prometheus                   │
│  └── logback-encoder (JSON日志)                      │
│                                                          │
│  Embedding：                                           │
│  ├── BGE-zh embedding model                           │
│  └── sentence-transformers (本地运行)                   │
│                                                          │
│  工具：                                                 │
│  └── CompletableFuture (JDK内置，无需额外依赖)         │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 十六、状态与变更记录

| 版本 | 日期 | 作者 | 变更内容 |
|------|------|------|---------|
| v0.1 | 2026-05-12 | AI Assistant | 初始草案 |
| v1.0 | 2026-05-12 | AI Assistant | 完整版，包含所有 Phase |
| v2.0 | 2026-05-12 | AI Assistant | 细化 Phase 1 记忆体系，增加存储策略、代码示例 |
| v2.1 | 2026-05-12 | AI Assistant | 细化 Phase 2 智能分块+RAG |
| v2.2 | 2026-05-12 | AI Assistant | 细化 Phase 3 增强推理引擎，增加设计决策 |
| v2.3 | 2026-05-12 | AI Assistant | 细化 Phase 4 Orchestrator编排层 |
| v2.4 | 2026-05-12 | AI Assistant | 简化 Phase 5 可观测性，增加技术选型 |
| v3.0 | 2026-05-12 | AI Assistant | 确定技术选型：ES+Milvus+BGE-zh+智谱+DeepSeek |
| v3.1 | 2026-05-19 | AI Assistant | 完成 Phase 5 可观测性闭环：结构化日志、反馈、执行轨迹和验证 |
| v3.2 | 2026-05-19 | AI Assistant | 增强请求链路关联：X-Request-Id、MDC 用户租户上下文和结构化日志透传 |
| v3.3 | 2026-05-19 | AI Assistant | 增强异步链路上下文传播：线程池、SSE、Agent 并行任务统一传递 MDC 和认证上下文 |
| v3.4 | 2026-05-19 | AI Assistant | 对齐 Phase 1/2 实施状态：记忆体系和增强 RAG 主体能力已完成并通过聚焦测试 |
| v3.5 | 2026-05-19 | AI Assistant | 对齐 Phase 3 实施状态：增强推理、错误恢复、并行预检、结构化工具调用和计划可视化已完成并通过聚焦测试 |
| v3.6 | 2026-05-19 | AI Assistant | 对齐 Phase 4 实施状态：系统专家、编排计划、共享上下文、自定义 Agent 和多 Agent 主链路已完成并通过聚焦测试 |

---

## 十七、下一步行动

```
优先事项：
□ 1. 评审并确认 ARCHITECTURE_PLAN.md 文档
□ 2. Phase 1 详细设计和任务分解
□ 3. 开发环境搭建（ES + Milvus + BGE-zh）
□ 4. Phase 1 第一个 Sprint 启动

后续事项：
□ 5. Phase 2 技术预研（ES + Milvus 集成）
□ 6. Phase 3 设计评审
□ 7. Phase 4 设计评审
□ 8. Phase 5 设计评审

资源需求：
□ 后端开发：2-3人
□ 前端开发：1人
□ 测试：1人
□ 运维支持：1人（ES + Milvus 部署）
```

---

*文档结束*
