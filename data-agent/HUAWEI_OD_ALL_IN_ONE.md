# 成都华为 OD Java 后端 3 年面试一体化复习稿

这份只放你面成都华为 OD 需要看的内容。不要再分散看其它文档。  
目标画像：3 年 Java 后端，基础扎实，能独立负责模块，能处理线上问题，机试代码能稳定写出来。

阅读方法：

1. 先看第 14 章和第 15 章的“理解记忆版”，把脑子里的模型建起来。
2. 算法不熟时，先看第 17 章的“核心题超细讲解版”，再看第 18 章的“剩余题逐题讲明白”。
3. 再回头看第 5 章基础八股和第 8 章算法模板。
4. 最后用第 13 章做面试前速扫。

不要把这份文档当成死背材料。你真正要练的是：看到问题能先说一句人话，再展开原理，再落到项目。

## 0. 你这次面试的准备重点

华为 OD 面试最看重三件事：

1. 机试/手撕代码：字符串、数组、排序、栈、双指针、树、DFS/BFS、简单 DP。
2. Java 基础：集合、并发、JVM、MySQL、Redis、Spring。
3. 项目深挖：你做了什么，为什么这么做，出问题怎么排查，最后效果如何。

你的主线排序：

1. 商品库数据拉取系统：主项目，最适合讲稳定性、数据量、JVM、并发、监控。
2. 酒店收益策略预测系统：第二项目，适合讲调度、线程池、限流、吞吐。
3. Data Agent：加分项，不要作为主项目开场。

一句话定位：

> 我不是只会调接口的 CRUD 后端，我做过真实业务链路、千万级数据处理、JVM 调优、并发调度和线上问题排查。

### 0.1 算法优先阅读顺序

你先别平均刷题，按下面顺序来。这个顺序是按“华为 OD 机试常见 + HR 明确提醒 + 技术面手撕概率”排的。

第一轮：必须先看，优先手写到熟。第 8.0.3 已经按下面 1-15 的顺序重排，正文直接顺着看即可。

1. ACM 输入输出模板  
   先看第 7 章。华为 OD 机试常常要自己读输入，别因为输入输出挂题。

2. 最长无重复子串  
   关键词：字符串、滑动窗口。  
   先看第 8.0.3 A，再看第 17.1。

3. 反转单词  
   关键词：字符串处理、空格处理。  
   先看第 8.2，再看第 18.2。

4. 有效括号  
   关键词：栈、括号匹配。  
   先看第 8.4，再看第 18.3。

5. 两数之和  
   关键词：HashMap、查找配对数。  
   先看第 8.0.3 L，再看第 8.7。

6. 三数之和  
   关键词：排序、双指针、去重。  
   先看第 8.0.3 B，再看第 17.2。

7. 移动零  
   关键词：数组、快慢指针、原地。  
   先看第 8.0.3 M，再看第 18.7。

8. 合并两个有序数组  
   关键词：数组、双指针、从后往前。  
   先看第 8.12，再看第 18.9。

9. 快速排序  
   关键词：排序、partition。  
   先看第 8.0.3 C，再看第 17.3。

10. 归并排序  
    关键词：排序、分治、稳定。  
    先看第 8.0.3 D，再看第 17.4。

11. 二叉树剪枝  
    关键词：二叉树、后序遍历。  
    先看第 8.0.3 E，再看第 17.5。

12. 二叉树层序遍历  
    关键词：BFS、队列、按层。  
    先看第 8.0.3 F，再看第 17.6。

13. 每日温度  
    关键词：单调栈、右边第一个更大。  
    先看第 8.0.3 G，再看第 17.7。

14. 反转链表  
    关键词：链表、三指针。  
    先看第 8.0.3 H，再看第 17.8。

15. 岛屿数量  
    关键词：DFS、连通块。  
    先看第 8.0.3 I，再看第 17.9。

第二轮：第一轮稳了再看，用来提高 OD 机试覆盖面。

16. 字符串相加  
    关键词：字符串、大数、进位。  
    看第 8.5 和第 18.4。

17. 删除有序数组重复项  
    关键词：有序数组、快慢指针。  
    看第 8.11 和第 18.8。

18. 下一个更大元素  
    关键词：单调栈。  
    看第 8.28 和第 18.17。

19. 循环数组下一个更大元素  
    关键词：单调栈、循环数组。  
    看第 8.29 和第 18.18。

20. 最大子数组和  
    关键词：动态规划、连续子数组。  
    先看第 8.0.3 J，再看第 17.10。

21. 爬楼梯  
    关键词：动态规划、方法数。  
    看第 8.38 和第 18.23。

22. 最长递增子序列  
    关键词：动态规划、子序列。  
    看第 8.40 和第 18.24。

23. 合并区间  
    关键词：排序、区间。  
    看第 8.14 和第 18.11。

24. 盛最多水的容器  
    关键词：双指针、短板。  
    看第 8.13 和第 18.10。

25. 环形链表  
    关键词：快慢指针。  
    看第 8.33 和第 18.20。

26. 删除倒数第 N 个节点  
    关键词：链表、快慢指针、dummy。  
    看第 8.34 和第 18.21。

27. 01 背包  
    关键词：动态规划、选择/不选择。  
    看第 17.11。

你时间不够时，只看第一轮 15 道。第一轮比第二轮重要得多。

## 1. 自我介绍

### 30 秒版本

面试官您好，我叫张原铭，本科软件工程，有 3 年左右 Java 后端开发经验。之前主要做同程酒店业务的商品库数据拉取、检索数据供给和收益策略预测系统，技术栈包括 Spring Boot、MyBatis、MySQL、Redis、多线程、Kafka、JVM 调优等。我参与过千万级数据拉取链路、红包匹配结果集优化、Full GC/OOM 排查，以及多机任务调度和高并发预测调用。整体上我比较偏工程落地型后端，关注稳定性、性能和线上问题定位。

### 1 分钟版本

面试官您好，我叫张原铭，本科软件工程，目前有 3 年左右 Java 后端开发经验。工作主要在酒店业务方向，做过商品库数据拉取、酒店检索数据供给和收益策略预测系统。

商品库系统主要负责库存、价格、房型、产品、促销等 300 多个维度的数据拉取、PB 格式化和下游推送。我参与了拉取主流程、文件校验、红包匹配优化、监控告警和 JVM 调优。其中红包匹配优化把业务增长后 12 亿级匹配结果降到 1400 万左右；JVM 调优处理过 Full GC 几秒一次和 OOM 问题。

收益预测系统主要用于策略上线前评估，通过固定样本、动态样本和实时请求模拟用户访问，对比新旧策略指标。我负责多机任务抢占调度、线程池并发、双环境调用、令牌桶限流和 DeepSeek 报告集成，把端到端耗时从 20 分钟降到 10 分钟。

我平时比较关注 Java 基础、并发、MySQL、Redis、JVM 和线上排查，最近也在学习 AI 后端方向，做了一个 Data Agent 个人项目作为技术扩展。

注意：不要一上来强调“AI 写代码”。AI 项目放最后，当学习能力加分项。

## 2. 项目一：商品库数据拉取系统

### 2.1 一句话介绍

这是酒店检索的数据供给系统，负责把酒店库存、价格、房型、产品、促销等 300 多个维度从多个数据源拉取出来，转换成 PB 文件并推送给下游检索服务。

### 2.2 两分钟讲法

这个项目主要解决酒店检索的数据供给问题。酒店检索依赖很多维度的数据，比如库存、价格、房型、产品、促销、红包规则等，维度多、数据量大、下游依赖强，所以系统要求在固定时间内完成拉取、转换、校验和推送。

整体链路是：先按维度或分片拆分任务，多进程并行拉取上游数据；拉取后做数据清洗和 PB 格式化；再做文件完整性校验和推送；如果某个维度失败，会有重试、异常文件清理和告警。

我主要做了三块。第一是拉取主流程和文件推送稳定性，处理过多进程并行下机器宕机导致残留文件的问题。我通过记录分片、机器、时间、状态，拉取后做批量校验，清理失败维度的本地和网盘文件，并发邮件告警，避免下游误读半成品文件。

第二是红包匹配结果集优化。原来业务增长后匹配结果到了 12 亿级，DB 和下游消费压力都很大。我们回到业务规则，把只圈选星级红包视为默认红包，减少无意义明细展开，再配合过期红包清理，最终结果集降到 1400 万左右。

第三是 JVM 调优。拉取服务出现过 Full GC 几秒一次甚至 OOM。我先通过 GC 日志、监控和堆 dump 分析对象占用，发现部分维度中间数据暂存过多。最后通过分批处理、减少中间集合、精简对象结构，再配合 JVM 参数和 GC 策略调整，让服务稳定下来。

### 2.3 高频追问

Q：300 多个维度怎么保证 1 小时内完成？

答：

核心不是单线程顺序跑，而是把 300 多个维度拆成任务，按维度/分片做并行处理。不同机器或进程处理不同维度，进程内也可以根据维度特点做线程级并发。每个维度有状态记录，失败可以重试。对于耗时特别长的维度，会单独分析 SQL、分页方式和对象结构，避免它拖慢整体闭环。

Q：多进程并行怎么避免重复、漏处理和脏文件？

答：

我主要从任务状态和后置校验两块保证。任务层面记录维度、分片、机器、时间和状态，异常时可以重新分配。文件层面不直接相信“进程执行过”，而是在拉取后统一校验完整性。如果某个维度失败，就清理它对应的本地和网盘文件，并告警。这样可以避免机器挂起后残留半成品文件被下游消费。

Q：红包匹配 12 亿降到 1400 万，是不是把数据删了？

答：

不是简单删除有效数据，而是减少冗余展开。原逻辑把一些默认规则也展开成大量明细结果，业务上其实没有必要。我们和业务确认后，把只圈选星级的红包视为默认红包，只保留真正有差异化圈选的明细，再清理过期红包。所以优化的是存储和计算表达方式，不是牺牲业务准确性。

Q：Full GC 调优你具体怎么做？

答：

我会按“现象、定位、优化、验证”讲。现象是 Full GC 几秒一次，甚至 OOM。定位时先看 GC 日志和老年代曲线，再分析堆 dump 里的大对象和引用链。发现主要是拉取过程中中间集合过大，对象生命周期长，容易进入老年代。优化时先改代码：分批处理、减少全量暂存、精简对象字段；然后再调堆大小、新生代比例和 GC 策略。最后通过监控验证 Full GC 频率和任务稳定性。

Q：为什么不直接加机器？

答：

加机器只能缓解压力，但解决不了根因。比如红包匹配 12 亿结果，如果只是加机器，DB 存储、网络传输和下游消费还是很重。我们先从业务规则和数据结构上减少无效结果，再配合资源优化，这样收益更稳定。

## 3. 项目二：酒店收益策略预测系统

### 3.1 一句话介绍

这是策略上线前的影响评估系统，通过模拟用户请求，对比在线环境和预览环境的核心指标差异，再自动生成分析报告。

### 3.2 一分钟讲法

收益策略直接上线会有风险，所以系统会在策略提交后自动触发预测，构造固定模板、动态模板和实时请求三类样本，同时调用在线环境和预览环境，比较新旧策略指标差异。

我主要负责多机任务抢占调度和高并发调用。调度上用数据库 CAS 乐观锁和机器 IP 绑定实现轻量抢任务，配合状态机、30 秒轮询、时间窗口和异常回滚。执行上用 32 线程固定线程池做任务级并发，单个任务里在线和预览环境并行调用，每个环境独立令牌桶限流 500 QPS，并设置超时控制，避免压垮下游。最后把端到端耗时从 20 分钟降到 10 分钟。

### 3.3 高频追问

Q：为什么用数据库 CAS，不用分布式锁或调度框架？

答：

这个场景任务频率不算特别高，任务状态本身就在数据库里。用 DB CAS 可以把抢占、状态流转、机器绑定和异常恢复放在一张任务表里，简单、可观察、维护成本低。抢任务时通过状态、更新时间或版本号做条件 update，只有一台机器能更新成功。如果任务超时，可以基于状态和更新时间回滚或重新抢占。

Q：怎么防止下游服务被打爆？

答：

并发提升不是无限加线程。我这里分两层控制：线程池控制本系统并发，令牌桶控制对每个环境的 QPS，再加上超时时间和异常处理。在线环境和预览环境分别限流，避免其中一个环境慢或异常影响整体。

Q：线程池参数怎么定？

答：

我会根据任务类型来定。这个场景主要是远程调用，偏 IO 型，所以线程数可以比 CPU 核数高一些。但不能只看本机吞吐，还要看下游承载能力。最后选择 32 线程固定线程池，再用每环境 500 QPS 的令牌桶做保护，通过压测和线上监控看吞吐、错误率和耗时。

Q：DeepSeek 报告怎么保证稳定？

答：

我没有让模型自由发挥，而是把 27 个核心指标整理成固定结构，配合固定 Prompt 和 HTML 模板。模型负责解释差异、总结风险和给出建议，但输入指标和输出格式都尽量结构化。这样报告稳定性会比纯自然语言 prompt 好很多。

## 4. Data Agent 加分项

### 4.1 怎么讲

Data Agent 是我在 Java 后端基础上扩展 AI 工程化能力的个人项目，用 Spring Boot、LangChain4j、Milvus、DeepSeek 实现 Agent、RAG、工具调用和执行追踪。

一句话：

> 它不是简单调用模型接口，而是做了一个可调用工具、可检索知识库、可追踪执行过程的数据分析 Agent 后端。

### 4.2 高频追问

Q：这个项目是不是 AI 写的？

答：

我确实使用 Claude Code、Cursor 这类工具提高编码效率，但项目目标、模块拆分、技术选型、问题定位和代码 review 是我主导的。AI 对我来说是开发辅助，不是替代我理解系统。具体到 ReAct 循环、RAG 检索、模型配置、工具边界和执行追踪，我能讲清楚实现链路。

Q：和普通 ChatGPT 套壳有什么区别？

答：

普通套壳通常是用户问题直接发给模型。这个项目在模型前后加了工程化能力：前面有文件解析、知识库检索、记忆上下文；执行中有工具调用，比如查文件、查知识库、查数据库、计算；后面有执行轨迹、质量反馈和审计。它更像一个可控的数据分析 Agent 后端。

Q：ReAct 是什么？

答：

ReAct 可以理解为 Reasoning + Acting，让模型不是直接回答，而是在“思考、调用工具、观察结果、继续推理”之间循环。工程上关键不是写一个 prompt，而是要控制循环上限、工具解析、异常恢复、日志追踪和权限边界。

Q：RAG 是什么？

答：

RAG 是检索增强生成。先把文档解析、分块、向量化后写入向量库；用户提问时先检索相关片段，再把片段作为上下文给模型。这样可以减少幻觉，也方便追踪答案依据。

## 5. Java 基础八股

### 5.1 面向对象

Q：面向对象三大特性？

答：

封装、继承、多态。封装是隐藏内部实现，对外暴露稳定接口；继承是复用父类能力；多态是同一个接口在不同实现上有不同行为。业务开发里我更倾向接口 + 组合，继承不要滥用。

Q：JDK 动态代理和 CGLIB 区别？

答：

JDK 动态代理基于接口生成代理类；CGLIB 基于继承生成子类代理，所以不能代理 final 类和 final 方法。Spring AOP 如果目标类有接口可以用 JDK 代理，没有接口时通常用 CGLIB。

### 5.2 String

Q：String 为什么不可变？

答：

String 类不可变带来线程安全、常量池复用和 hashCode 稳定。String 经常作为 HashMap 的 key，如果可变会导致 hash 位置失效；也常作为路径、URL、参数，不可变更安全。

Q：String、StringBuilder、StringBuffer 区别？

答：

String 不可变，少量拼接或常量适合用。StringBuilder 可变、非线程安全，单线程大量拼接性能好。StringBuffer 方法加 synchronized，线程安全但性能较低。

### 5.3 HashMap

Q：HashMap 底层原理？

答：

HashMap 底层是数组 + 链表 + 红黑树。put 时先计算 key 的 hash 并扰动，再通过 `(n - 1) & hash` 定位桶。桶为空直接放；key 相同覆盖；发生冲突就链表追加，链表过长并且数组容量足够时转红黑树。元素数量超过阈值会扩容。

必须记住：

- 默认负载因子 0.75。
- 容量是 2 的幂，方便位运算定位和扩容迁移。
- 链表长度到 8 且数组容量至少 64 才树化。
- HashMap 线程不安全，并发用 ConcurrentHashMap。

Q：ConcurrentHashMap 怎么保证线程安全？

答：

Java 8 主要是 CAS + synchronized。读操作大多无锁，写操作对桶头节点加锁，锁粒度比 Hashtable 小很多。扩容时多个线程可以协助迁移。

Q：ArrayList 和 LinkedList 区别？

答：

ArrayList 底层是动态数组，按下标查询 O(1)，尾部追加快，扩容时需要数组拷贝。LinkedList 是双向链表，随机访问 O(n)，已定位节点后的插入删除方便。但实际业务里 ArrayList 更常用，因为缓存局部性好。

Q：CopyOnWriteArrayList 适合什么场景？

答：

写时复制，读不加锁，写时复制新数组。适合读多写少、数据量不大的场景，比如配置列表、监听器列表。不适合频繁写和大集合。

### 5.4 JVM

Q：JVM 内存区域？

答：

堆、方法区/元空间、虚拟机栈、本地方法栈、程序计数器。堆存对象，是 GC 重点；元空间存类元信息；栈存方法调用栈帧和局部变量；程序计数器记录线程执行位置。

Q：类加载过程？

答：

加载、验证、准备、解析、初始化。准备阶段给静态变量分配内存并赋默认值，初始化阶段才执行静态变量赋值和 static 代码块。

Q：双亲委派模型？

答：

类加载器收到加载请求后，先交给父加载器，父加载器找不到才自己加载。好处是避免核心类被篡改和重复加载，比如用户自己写 `java.lang.String` 不会替换 JDK 的 String。

Q：GC 怎么判断对象可回收？

答：

主流 JVM 用可达性分析。从 GC Roots 出发，能到达的对象存活，不能到达的对象可回收。GC Roots 包括栈中的引用、静态变量、常量、JNI 引用等。

Q：怎么排查 OOM？

答：

先看日志和监控，确认是堆、栈、元空间还是直接内存。堆 OOM 就看 GC 日志，确认 GC 后内存是否下降，再 dump 堆，用 MAT/VisualVM 分析大对象、对象数量和引用链。然后找代码原因，比如缓存没释放、集合过大、一次性加载太多、线程池队列堆积。优先改代码，JVM 参数调整只是辅助。

### 5.5 并发

Q：线程池核心参数？

答：

`corePoolSize`、`maximumPoolSize`、`keepAliveTime`、`workQueue`、`threadFactory`、`RejectedExecutionHandler`。

Q：线程池执行流程？

答：

任务进来，如果线程数小于 corePoolSize，创建核心线程；否则放入队列；队列满且线程数小于 maximumPoolSize，创建非核心线程；还放不下就执行拒绝策略。

Q：拒绝策略？

答：

AbortPolicy 抛异常；CallerRunsPolicy 由提交任务的线程执行，能形成反压；DiscardPolicy 直接丢弃；DiscardOldestPolicy 丢弃队列最老任务。

Q：volatile 能保证什么？

答：

保证可见性和一定有序性，不保证复合操作原子性。比如 `count++` 即使用 volatile 也不是线程安全的。

Q：synchronized 和 ReentrantLock 区别？

答：

synchronized 是 JVM 内置锁，使用简单，自动释放锁；ReentrantLock 是显式锁，需要手动 unlock，但支持公平锁、tryLock、可中断和多个 Condition。

Q：ThreadLocal 原理和风险？

答：

每个线程有自己的 ThreadLocalMap，key 是 ThreadLocal 弱引用，value 是实际值。线程池场景线程会复用，用完不 remove 可能导致内存泄漏或数据串用，所以要在 finally 里 remove。

Q：CAS 是什么？

答：

CAS 是比较并交换，是乐观锁思想。更新前先比较内存值是否还是预期值，如果是才更新。问题是可能有 ABA，可以用版本号解决。

### 5.6 Spring

Q：IOC 和 AOP？

答：

IOC 是控制反转，对象创建和依赖关系交给 Spring 容器管理。AOP 是面向切面，通过代理在方法前后增强逻辑，常见场景是事务、日志、权限、监控。

Q：Spring Bean 生命周期？

答：

实例化、属性填充、Aware 回调、BeanPostProcessor 前置处理、初始化方法、BeanPostProcessor 后置处理、放入单例池、销毁。

Q：Spring Boot 自动装配？

答：

Spring Boot 通过 `@SpringBootApplication` 开启自动配置，本质包含 `@EnableAutoConfiguration`。它会根据 classpath、配置项和条件注解判断是否创建对应 Bean。

Q：事务什么时候失效？

答：

同类内部方法调用绕过代理；方法不是 public；异常被 catch 没抛出；默认只回滚 RuntimeException；数据库引擎不支持事务；对象不是 Spring 管理的 Bean。

### 5.7 MySQL

Q：为什么索引用 B+ 树？

答：

B+ 树高度低，磁盘 IO 少；非叶子节点只存 key，可以放更多索引项；叶子节点有序链表，适合范围查询。相比哈希索引，B+ 树支持范围和排序。

Q：聚簇索引和二级索引？

答：

InnoDB 主键索引是聚簇索引，叶子节点存整行数据。二级索引叶子节点存主键值，如果查询字段不在二级索引里，需要回表查主键索引。

Q：覆盖索引？

答：

查询需要的字段都在索引里，不需要回表。

Q：索引失效常见原因？

答：

对索引列使用函数；like 前面带 `%`；联合索引不满足最左前缀；隐式类型转换；OR 使用不当；区分度太低优化器不走索引。

Q：事务隔离级别？

答：

读未提交、读已提交、可重复读、串行化。InnoDB 默认可重复读。MVCC 通过 undo log 和 read view 实现快照读，减少读写阻塞。

Q：慢 SQL 怎么排查？

答：

先看慢 SQL 日志定位 SQL，再用 explain 看 type、key、rows、Extra。重点看是否全表扫描、索引失效、回表太多、filesort、temporary、分页太深。优化可以加/改联合索引、减少返回列、改分页方式、拆 SQL、归档历史数据。

### 5.8 Redis

Q：Redis 常见数据类型？

答：

String 用于缓存、计数器、锁；Hash 用于对象缓存；List 用于队列；Set 用于去重和交并差；ZSet 用于排行榜和延时队列。

Q：缓存穿透、击穿、雪崩？

答：

穿透是查不存在的数据，每次都打 DB，解决方式是缓存空值、布隆过滤器、参数校验。击穿是热点 key 过期，大量请求同时打 DB，解决方式是互斥锁、逻辑过期、热点不过期。雪崩是大量 key 同时过期或 Redis 故障，解决方式是过期时间随机、多级缓存、限流降级、集群高可用。

Q：Redis 分布式锁？

答：

基本做法是 `SET key value NX EX seconds`，value 用唯一标识。释放时用 Lua 脚本先比较 value 再删除，避免删掉别人的锁。业务执行时间可能超过过期时间时，需要续期。

### 5.9 Kafka

Q：Kafka 为什么吞吐高？

答：

顺序写磁盘、page cache、批量发送、零拷贝、分区并行。

Q：如何保证消息不丢？

答：

生产端设置 `acks=all`、开启重试和幂等；Broker 设置副本和最小同步副本；消费端处理完业务后再提交 offset。业务还要做好幂等，因为可能重复消费。

Q：Kafka 如何保证顺序？

答：

Kafka 只能保证同一分区内有序。需要有序的业务 key 要发到同一个分区，消费端也要按分区串行或按 key 串行处理。

## 6. Linux 和线上排查

Q：常用 Linux 命令？

答：

- `top`：看 CPU、内存整体情况。
- `ps -ef | grep java`：看 Java 进程。
- `netstat` / `ss`：看端口和连接。
- `df -h`：看磁盘。
- `tail -f`：实时看日志。
- `grep` / `awk`：过滤日志。
- `jps`、`jstat`、`jmap`、`jstack`：Java 排查。

Q：线上接口变慢怎么排查？

答：

先看是不是整体慢还是单接口慢，再看应用日志、接口耗时、线程池、GC、CPU、内存、数据库慢 SQL、Redis/Kafka 下游耗时。如果是 Java 应用，可以看 GC 日志、jstack 线程栈、jstat 内存变化。如果是数据库问题，用慢日志和 explain 定位。

Q：CPU 飙高怎么排查 Java 进程？

答：

先 `top` 找到高 CPU 进程，再用 `top -Hp pid` 找高 CPU 线程，把线程 id 转 16 进制，然后用 `jstack pid` 查对应线程栈，看是死循环、锁竞争、GC 线程还是业务代码热点。

## 7. 华为 OD 机试：输入输出模板

OD 机试经常是 ACM 模式，不是 LeetCode 方法签名。你要会自己读输入。

### 7.1 多行输入，每行两个数

```java
import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] arr = line.split("\\s+");
            int a = Integer.parseInt(arr[0]);
            int b = Integer.parseInt(arr[1]);
            System.out.println(a + b);
        }
    }
}
```

### 7.2 第一行 n，第二行数组

```java
BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
int n = Integer.parseInt(br.readLine().trim());
String[] arr = br.readLine().trim().split("\\s+");
int[] nums = new int[n];
for (int i = 0; i < n; i++) {
    nums[i] = Integer.parseInt(arr[i]);
}
```

### 7.3 逗号分隔数组

```java
String[] arr = br.readLine().trim().split(",");
int[] nums = new int[arr.length];
for (int i = 0; i < arr.length; i++) {
    nums[i] = Integer.parseInt(arr[i].trim());
}
```

### 7.4 二维矩阵

```java
String[] nm = br.readLine().trim().split("\\s+");
int m = Integer.parseInt(nm[0]);
int n = Integer.parseInt(nm[1]);
int[][] grid = new int[m][n];
for (int i = 0; i < m; i++) {
    String[] row = br.readLine().trim().split("\\s+");
    for (int j = 0; j < n; j++) {
        grid[i][j] = Integer.parseInt(row[j]);
    }
}
```

## 8. 算法题：逐题讲解 + Java 模板

先说清楚：算法不要从代码开始背。你要先会“识别题型”，再会“解释变量”，最后才是写代码。

这一章的阅读方式：

1. 先看下面的“题型识别地图”，知道什么题用什么套路。
2. 再看每道题的“怎么想到”，不要直接跳代码。
3. 最后遮住代码，自己手写一遍。

### 8.0 算法题型识别地图

| 题目特征 | 优先想到 | 你脑子里的第一句话 |
| --- | --- | --- |
| 找某个数是否出现过、两数之和、去重、计数 | HashMap / HashSet | 我需要快速判断“之前见没见过”。 |
| 有序数组、两边往中间靠、两数求和 | 左右双指针 | 有序意味着我移动一边能让结果变大或变小。 |
| 原地删除、移动零、保留相对顺序 | 快慢指针 | fast 负责扫描，slow 负责写答案。 |
| 连续子串/子数组，最长/最短 | 滑动窗口 | right 扩张，left 收缩，窗口始终表达当前候选答案。 |
| 括号匹配、最近未匹配 | 栈 | 后出现的左括号要先匹配。 |
| 右边第一个更大/更小 | 单调栈 | 栈里放还没等到答案的下标。 |
| 树的删除、依赖左右子树结果 | 后序遍历 | 先让左右子树返回结果，再决定当前节点。 |
| 树按层、矩阵最短路 | BFS 队列 | 队列一层层扩散，size 固定当前层。 |
| 连通块、岛屿、路径搜索 | DFS | 一条路走到底，边走边标记访问。 |
| 最优值、方法数、选择/不选择 | 动态规划 | 定义 dp 含义，再找从哪里转移过来。 |

面试时，你可以先说这个：

> 我先看题目特征。这个题要求的是连续子串的最长长度，所以我优先考虑滑动窗口；如果是右边第一个更大，我会考虑单调栈；如果是树节点删除，一般要后序遍历。

### 8.0.1 真正写代码前，你要问自己的 5 个问题

1. 输入为空怎么办？
2. 有没有重复元素？
3. 是否要求原地？
4. 是否要求保持相对顺序？
5. 返回的是值、下标、长度，还是修改后的结构？

比如“移动零”：

- 输入是数组。
- 有很多 0。
- 要原地。
- 非零元素相对顺序不能变。
- 返回 void。

所以不能排序，不能新开数组，应该用快慢指针。

### 8.0.2 算法面试表达模板

你不要上来就闷头写。按这个说：

> 暴力做法是……复杂度是……  
> 这里可以优化，因为题目有……特征。  
> 我准备用……  
> 变量含义是……  
> 最后复杂度是……

例子，最长无重复子串：

> 暴力可以枚举所有子串再判断重复，但复杂度太高。这个题要求连续子串，而且是最长不重复，所以我用滑动窗口。left 表示窗口左边界，right 表示右边界，Map 记录字符上次出现位置。遇到窗口内重复字符时，left 跳到上次出现位置后一位。

### 8.0.3 第一轮算法主线：按这个顺序看，讲解和代码放一起

这一节就是你的算法主阅读区，已经按第一轮 15 道的顺序排好。你就从 1 往 15 顺着看，不要跳。

#### 1. ACM 输入输出模板

为什么第一个看它：

华为 OD 机试经常不是 LeetCode 那种只写方法，而是要你写完整 `Main` 类。如果输入输出写错，算法会也没用。

最常用模板：多行输入，每行按空格分隔。

```java
import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line;

        // 有些 OD 题会有多行输入，所以用 while 读到 EOF
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // 空格分隔数字
            String[] parts = line.split("\\s+");

            // 后面按题目要求解析 parts
            // int a = Integer.parseInt(parts[0]);
            // int b = Integer.parseInt(parts[1]);
        }
    }
}
```

第一行 n，第二行数组：

```java
BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
int n = Integer.parseInt(br.readLine().trim());
String[] parts = br.readLine().trim().split("\\s+");
int[] nums = new int[n];
for (int i = 0; i < n; i++) {
    nums[i] = Integer.parseInt(parts[i]);
}
```

二维矩阵：

```java
BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
String[] mn = br.readLine().trim().split("\\s+");
int m = Integer.parseInt(mn[0]);
int n = Integer.parseInt(mn[1]);
int[][] grid = new int[m][n];

for (int i = 0; i < m; i++) {
    String[] row = br.readLine().trim().split("\\s+");
    for (int j = 0; j < n; j++) {
        grid[i][j] = Integer.parseInt(row[j]);
    }
}
```

面试/机试记忆点：

> 空格用 `split("\\s+")`，逗号用 `split(",")`。大量输入优先用 `BufferedReader`。

#### 2. 最长无重复子串

题目关键词：字符串、连续子串、最长、不重复。  
选择方法：滑动窗口。

怎么想到：

只要看到“连续子串 + 最长/最短”，先想滑动窗口。这个题窗口里要保持没有重复字符。

变量含义：

- `left`：窗口左边界。
- `right`：窗口右边界，也是当前遍历位置。
- `last`：每个字符上一次出现的位置。
- `ans`：目前最长窗口长度。

例子 `abba`：

```text
right=0 看到 a，窗口 a，ans=1
right=1 看到 b，窗口 ab，ans=2
right=2 又看到 b，上一个 b 在 1，left 跳到 2，窗口 b
right=3 看到 a，上一个 a 在 0，但 0 已经在窗口外，不影响
```

代码：

```java
public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> last = new HashMap<>();
    int left = 0;
    int ans = 0;

    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);

        // 只有重复字符在当前窗口里，才需要移动 left
        if (last.containsKey(c) && last.get(c) >= left) {
            left = last.get(c) + 1;
        }

        last.put(c, right);
        ans = Math.max(ans, right - left + 1);
    }

    return ans;
}
```

面试说法：

> 我用滑动窗口维护一个无重复区间。right 向右扩张，Map 记录字符上次出现位置；如果当前字符在窗口内重复，就把 left 跳到上次位置后一位。left 和 right 都只向右走，所以时间复杂度 O(n)。

易错点：

`last.get(c) >= left` 不能少，否则 left 可能倒退。

#### 3. 反转单词

题目关键词：字符串处理、空格处理、单词顺序反转。  
选择方法：切分单词 + 倒序拼接。

怎么想到：

反转的是“单词顺序”，不是字符顺序。所以先把字符串处理成单词数组。

例子：

```text
"  hello   world  "
trim -> "hello   world"
split("\\s+") -> ["hello", "world"]
倒序拼接 -> "world hello"
```

代码：

```java
public String reverseWords(String s) {
    String[] words = s.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();

    for (int i = words.length - 1; i >= 0; i--) {
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(words[i]);
    }

    return sb.toString();
}
```

面试说法：

> 这题的单位是单词，所以我先 trim 去掉首尾空格，再用连续空白切成单词数组，最后从后往前拼接。这样能自然处理多个空格。

易错点：

不要用 `split(" ")`，多个空格会切出空字符串。

#### 4. 有效括号

题目关键词：括号匹配、最近匹配。  
选择方法：栈。

怎么想到：

括号匹配是后进先出。最后出现的左括号，必须最先被右括号匹配。

代码：

```java
public boolean isValid(String s) {
    Deque<Character> stack = new ArrayDeque<>();

    for (char c : s.toCharArray()) {
        if (c == '(') {
            stack.push(')');
        } else if (c == '[') {
            stack.push(']');
        } else if (c == '{') {
            stack.push('}');
        } else if (stack.isEmpty() || stack.pop() != c) {
            return false;
        }
    }

    return stack.isEmpty();
}
```

面试说法：

> 括号匹配符合后进先出，所以我用栈。遇到左括号就把它期待的右括号入栈，遇到右括号就必须和栈顶一致。最后栈为空才合法。

易错点：

遍历中没失败不代表合法，最后还要看栈是否为空。

#### 5. 两数之和

题目关键词：找另一个数、target、下标。  
选择方法：HashMap。

怎么想到：

暴力是每个数都去后面找另一个数。优化点是把“找另一个数”变成 O(1)。

代码：

```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> map = new HashMap<>();

    for (int i = 0; i < nums.length; i++) {
        int need = target - nums[i];

        // 先查再放，避免同一个元素用两次
        if (map.containsKey(need)) {
            return new int[] {map.get(need), i};
        }

        map.put(nums[i], i);
    }

    return new int[] {-1, -1};
}
```

面试说法：

> 我用 HashMap 记录已经遍历过的数字和下标。遍历当前数时，直接查 target - 当前数 是否出现过，这样把查找从 O(n) 降到 O(1)。

易错点：

一定要先查再放。

#### 6. 三数之和

题目关键词：三元组、和为 0、不重复。  
选择方法：排序 + 固定一个数 + 双指针。

怎么想到：

先固定一个数，问题就变成后面区间找两个数。排序后，可以用双指针，同时方便去重。

代码：

```java
public List<List<Integer>> threeSum(int[] nums) {
    Arrays.sort(nums);
    List<List<Integer>> ans = new ArrayList<>();

    for (int i = 0; i < nums.length - 2; i++) {
        if (i > 0 && nums[i] == nums[i - 1]) {
            continue;
        }

        int left = i + 1;
        int right = nums.length - 1;

        while (left < right) {
            int sum = nums[i] + nums[left] + nums[right];

            if (sum == 0) {
                ans.add(Arrays.asList(nums[i], nums[left], nums[right]));

                while (left < right && nums[left] == nums[left + 1]) {
                    left++;
                }
                while (left < right && nums[right] == nums[right - 1]) {
                    right--;
                }

                left++;
                right--;
            } else if (sum < 0) {
                left++;
            } else {
                right--;
            }
        }
    }

    return ans;
}
```

面试说法：

> 我先排序，固定一个数，然后在后面的有序区间里用双指针找另外两个数。sum 小了移动 left，sum 大了移动 right。为了不重复，固定数要去重，找到答案后 left 和 right 也要跳过重复值。

易错点：

固定数去重、left/right 去重都要写。

#### 7. 移动零

题目关键词：数组、原地、保持非零相对顺序。  
选择方法：快慢指针。

怎么想到：

不能排序，因为要保持非零元素原顺序。slow 左边维护已经处理好的非零区，fast 负责扫描。

代码：

```java
public void moveZeroes(int[] nums) {
    int slow = 0;

    for (int fast = 0; fast < nums.length; fast++) {
        if (nums[fast] != 0) {
            int temp = nums[slow];
            nums[slow] = nums[fast];
            nums[fast] = temp;
            slow++;
        }
    }
}
```

面试说法：

> 我用 fast 扫描数组，slow 记录下一个非零元素应该写入的位置。遇到非零就和 slow 交换，slow 左边始终是保持原相对顺序的非零元素。

易错点：

不要新开数组，不要排序。

#### 8. 合并两个有序数组

题目关键词：两个有序数组、nums1 后面有空位。  
选择方法：从后往前双指针。

怎么想到：

从前往后会覆盖 nums1 还没处理的有效元素，所以从后往前填。

代码：

```java
public void merge(int[] nums1, int m, int[] nums2, int n) {
    int i = m - 1;
    int j = n - 1;
    int k = m + n - 1;

    while (j >= 0) {
        if (i >= 0 && nums1[i] > nums2[j]) {
            nums1[k--] = nums1[i--];
        } else {
            nums1[k--] = nums2[j--];
        }
    }
}
```

面试说法：

> 我从后往前合并，每次把 nums1 和 nums2 当前较大的数放到 nums1 的末尾。这样不会覆盖 nums1 前面还没处理的有效元素。

易错点：

循环条件是 `j >= 0`。nums2 放完后，nums1 剩下的本来就在正确位置。

#### 9. 快速排序

题目关键词：排序、partition。  
选择方法：快排。

怎么想到：

快排的核心是 partition。每次让一个 pivot 回到最终位置，然后递归排左右。

代码：

```java
public void quickSort(int[] nums) {
    if (nums == null || nums.length < 2) {
        return;
    }
    quickSort(nums, 0, nums.length - 1);
}

private void quickSort(int[] nums, int left, int right) {
    if (left >= right) {
        return;
    }

    int pivotIndex = partition(nums, left, right);
    quickSort(nums, left, pivotIndex - 1);
    quickSort(nums, pivotIndex + 1, right);
}

private int partition(int[] nums, int left, int right) {
    int pivot = nums[right];
    int less = left;

    for (int i = left; i < right; i++) {
        if (nums[i] <= pivot) {
            swap(nums, less, i);
            less++;
        }
    }

    swap(nums, less, right);
    return less;
}

private void swap(int[] nums, int i, int j) {
    int temp = nums[i];
    nums[i] = nums[j];
    nums[j] = temp;
}
```

面试说法：

> 快排核心是 partition。每次选一个 pivot，把小于等于 pivot 的放左边，大于 pivot 的放右边，最后 pivot 的最终位置就确定了。然后递归处理左右区间。

易错点：

partition 只扫到 `i < right`，最后要把 pivot 换到 less。

#### 10. 归并排序

题目关键词：排序、分治、稳定。  
选择方法：归并排序。

怎么想到：

先拆到单个元素，再合并两个有序区间。合并时相等先放左边，所以稳定。

代码：

```java
public void mergeSort(int[] nums) {
    if (nums == null || nums.length < 2) {
        return;
    }
    int[] temp = new int[nums.length];
    mergeSort(nums, 0, nums.length - 1, temp);
}

private void mergeSort(int[] nums, int left, int right, int[] temp) {
    if (left >= right) {
        return;
    }

    int mid = left + (right - left) / 2;
    mergeSort(nums, left, mid, temp);
    mergeSort(nums, mid + 1, right, temp);
    merge(nums, left, mid, right, temp);
}

private void merge(int[] nums, int left, int mid, int right, int[] temp) {
    int i = left;
    int j = mid + 1;
    int k = left;

    while (i <= mid && j <= right) {
        if (nums[i] <= nums[j]) {
            temp[k++] = nums[i++];
        } else {
            temp[k++] = nums[j++];
        }
    }

    while (i <= mid) {
        temp[k++] = nums[i++];
    }
    while (j <= right) {
        temp[k++] = nums[j++];
    }

    for (int p = left; p <= right; p++) {
        nums[p] = temp[p];
    }
}
```

面试说法：

> 归并排序是分治思想，先递归拆分，再合并两个有序区间。每层合并总共 O(n)，层数 O(log n)，所以时间 O(n log n)。需要 O(n) 额外空间，但稳定。

易错点：

merge 后要拷回原数组。

#### 11. 二叉树剪枝

题目关键词：二叉树、删除、依赖子树结果。  
选择方法：后序遍历。

怎么想到：

当前节点能不能删，取决于左右子树剪完后是否为空，所以必须后序。

代码：

```java
public TreeNode pruneTree(TreeNode root) {
    if (root == null) {
        return null;
    }

    root.left = pruneTree(root.left);
    root.right = pruneTree(root.right);

    if (root.val == 0 && root.left == null && root.right == null) {
        return null;
    }

    return root;
}
```

面试说法：

> 这题必须后序遍历，因为当前节点是否删除，取决于左右子树剪完之后是否为空。我让递归函数返回剪枝后的根节点，左右子树先递归剪完，再判断当前节点。

易错点：

递归结果要重新赋给 `root.left` 和 `root.right`。

#### 12. 二叉树层序遍历

题目关键词：二叉树、按层。  
选择方法：BFS 队列。

怎么想到：

按层遍历就是一层层出队。每层开始固定 size，因为处理当前层时会把下一层加入队列。

代码：

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> ans = new ArrayList<>();
    if (root == null) {
        return ans;
    }

    Queue<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);

    while (!queue.isEmpty()) {
        int size = queue.size();
        List<Integer> level = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            level.add(node.val);

            if (node.left != null) {
                queue.offer(node.left);
            }
            if (node.right != null) {
                queue.offer(node.right);
            }
        }

        ans.add(level);
    }

    return ans;
}
```

面试说法：

> 层序遍历用队列。每轮开始先固定当前队列 size，这个 size 就是当前层节点数。只处理这 size 个节点，同时把它们的孩子加入队列，下一轮自然就是下一层。

易错点：

size 必须先固定。

#### 13. 每日温度

题目关键词：右边第一个更大、距离。  
选择方法：单调栈。

怎么想到：

栈里放还没等到更高温度的日期下标。当前温度更高时，栈顶就能结算。

代码：

```java
public int[] dailyTemperatures(int[] temperatures) {
    int[] ans = new int[temperatures.length];
    Deque<Integer> stack = new ArrayDeque<>();

    for (int i = 0; i < temperatures.length; i++) {
        while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()]) {
            int prev = stack.pop();
            ans[prev] = i - prev;
        }
        stack.push(i);
    }

    return ans;
}
```

面试说法：

> 这题是找右边第一个更大元素，而且要计算距离，所以我用单调栈存下标。当前温度比栈顶温度高时，栈顶那天的答案就是当前下标差。

易错点：

栈里存下标，while 不是 if。

#### 14. 反转链表

题目关键词：链表、指针反向。  
选择方法：prev、cur、next 三指针。

怎么想到：

改 `cur.next` 前必须先保存 `next`，不然后面的链表会丢。

代码：

```java
public ListNode reverseList(ListNode head) {
    ListNode prev = null;
    ListNode cur = head;

    while (cur != null) {
        ListNode next = cur.next;
        cur.next = prev;
        prev = cur;
        cur = next;
    }

    return prev;
}
```

面试说法：

> 我用 prev、cur、next 三个指针。每次先保存 cur.next，防止断链；然后把 cur.next 指向 prev，再移动 prev 和 cur。最后 prev 就是反转后的头节点。

易错点：

最后返回 prev，不是 cur。

#### 15. 岛屿数量

题目关键词：二维矩阵、连通块。  
选择方法：DFS。

怎么想到：

遇到一个没访问过的 1，就是一座新岛。然后 DFS 把这座岛连着的所有 1 都标记掉。

代码：

```java
public int numIslands(char[][] grid) {
    int m = grid.length;
    int n = grid[0].length;
    int ans = 0;

    for (int i = 0; i < m; i++) {
        for (int j = 0; j < n; j++) {
            if (grid[i][j] == '1') {
                ans++;
                dfs(grid, i, j);
            }
        }
    }

    return ans;
}

private void dfs(char[][] grid, int i, int j) {
    if (i < 0 || i >= grid.length || j < 0 || j >= grid[0].length || grid[i][j] != '1') {
        return;
    }

    grid[i][j] = '0';

    dfs(grid, i + 1, j);
    dfs(grid, i - 1, j);
    dfs(grid, i, j + 1);
    dfs(grid, i, j - 1);
}
```

面试说法：

> 我遍历矩阵，遇到一个未访问的 1，就说明发现一座新岛，答案加一。然后 DFS 把这座岛上下左右连通的所有 1 都标记成 0，避免后面重复计数。

易错点：

必须标记访问，否则会重复计数甚至死递归。

### 8.1 反转字符串

题意：

原地反转字符数组，比如：

```text
['h','e','l','l','o'] -> ['o','l','l','e','h']
```

怎么想到：

题目说“原地”，说明不能新建一个数组。  
反转的本质是首尾交换：

```text
第 1 个 <-> 最后 1 个
第 2 个 <-> 倒数第 2 个
```

所以用左右双指针。

变量含义：

- `left`：左边还没交换的位置。
- `right`：右边还没交换的位置。

手动走例子：

```text
hello
left=h, right=o，交换 -> oellh
left=e, right=l，交换 -> olleh
left 到中间，结束
```

```java
public void reverseString(char[] s) {
    int left = 0;
    int right = s.length - 1;
    while (left < right) {
        char temp = s[left];
        s[left] = s[right];
        s[right] = temp;
        left++;
        right--;
    }
}
```

面试这样说：

> 因为题目要求原地反转，我用左右指针从两端向中间交换。每次交换后 left++、right--，直到两个指针相遇。每个字符最多交换一次，所以时间 O(n)，空间 O(1)。

易错点：循环条件是 `left < right`。  
复杂度：时间 O(n)，空间 O(1)。

### 8.2 反转单词

题意：

把单词顺序反过来，并去掉多余空格：

```text
"  hello   world  " -> "world hello"
```

怎么想到：

这题反转的是“单词顺序”，不是字符。  
所以先把字符串变成单词数组，再倒序拼接。

为什么要 `trim()`：

去掉首尾空格，不然切分时容易出现空字符串。

为什么用 `split("\\s+")`：

因为中间可能有多个空格。`\\s+` 表示一个或多个空白字符。

手动走例子：

```text
"  hello   world  "
trim -> "hello   world"
split("\\s+") -> ["hello", "world"]
倒序拼接 -> "world hello"
```

```java
public String reverseWords(String s) {
    String[] words = s.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = words.length - 1; i >= 0; i--) {
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(words[i]);
    }
    return sb.toString();
}
```

面试这样说：

> 这题的单位是单词，所以我先 trim 去掉首尾空格，再用连续空白切成单词数组，最后从后往前拼接。这样能自然处理多个空格。

易错点：不要用 `split(" ")`，多个空格会出问题。  
复杂度：时间 O(n)，空间 O(n)。

### 8.3 最长无重复子串

题意：

找一个“连续子串”，要求里面没有重复字符，返回最长长度。

例子：

```text
"abcabcbb" -> 3，对应 "abc"
"abba" -> 2，对应 "ab" 或 "ba"
```

怎么想到：

关键词是：

- 连续子串。
- 最长。
- 不重复。

连续子串的最长/最短问题，优先想滑动窗口。

窗口是什么：

窗口 `[left, right]` 表示当前正在维护的一段“不重复子串”。

right 做什么：

right 每次向右走，把新字符加入窗口。

left 做什么：

如果新字符在当前窗口里重复了，left 要跳过这个重复字符上次出现的位置。

为什么不是 left++：

如果重复字符离 left 很远，只 left++ 可能还没跳过重复位置，会继续重复。直接跳到 `上次位置 + 1` 更准确。

为什么要判断 `last.get(c) >= left`：

因为上次出现的位置可能已经在窗口外了。窗口外的重复不影响当前窗口。

手动走 `abba`：

```text
right=0, a，窗口 a，ans=1
right=1, b，窗口 ab，ans=2
right=2, b，上次 b 在 1，left 跳到 2，窗口 b
right=3, a，上次 a 在 0，但 0 < left，说明不在窗口里，不用管，窗口 ba
答案 2
```

变量含义：

- `left`：窗口左边界。
- `right`：窗口右边界。
- `last`：字符上一次出现的位置。
- `ans`：目前最大窗口长度。


```java
public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> last = new HashMap<>();
    int left = 0;
    int ans = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (last.containsKey(c) && last.get(c) >= left) {
            left = last.get(c) + 1;
        }
        last.put(c, right);
        ans = Math.max(ans, right - left + 1);
    }
    return ans;
}
```

面试这样说：

> 这题是连续子串的最长问题，我用滑动窗口。Map 记录每个字符上次出现的位置。right 向右扩张，如果当前字符在窗口内出现过，就把 left 跳到上次位置后一位。left 和 right 都只向右走，所以时间复杂度是 O(n)。

易错点：必须判断 `last.get(c) >= left`，否则 left 会倒退。  
复杂度：时间 O(n)，空间 O(n)。

### 8.4 有效括号

题意：判断括号字符串是否合法。

思路：栈。遇到左括号，把期望的右括号入栈；遇到右括号，必须匹配栈顶。

```java
public boolean isValid(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        if (c == '(') {
            stack.push(')');
        } else if (c == '[') {
            stack.push(']');
        } else if (c == '{') {
            stack.push('}');
        } else if (stack.isEmpty() || stack.pop() != c) {
            return false;
        }
    }
    return stack.isEmpty();
}
```

易错点：最后要判断栈为空。  
复杂度：时间 O(n)，空间 O(n)。

### 8.5 字符串相加

题意：两个非负整数字符串相加，不能转 int/long。

思路：从末尾模拟竖式加法，维护进位 carry。

```java
public String addStrings(String num1, String num2) {
    StringBuilder sb = new StringBuilder();
    int i = num1.length() - 1;
    int j = num2.length() - 1;
    int carry = 0;
    while (i >= 0 || j >= 0 || carry != 0) {
        int x = i >= 0 ? num1.charAt(i--) - '0' : 0;
        int y = j >= 0 ? num2.charAt(j--) - '0' : 0;
        int sum = x + y + carry;
        sb.append(sum % 10);
        carry = sum / 10;
    }
    return sb.reverse().toString();
}
```

易错点：循环条件要包含 `carry != 0`。  
复杂度：时间 O(max(m,n))，空间 O(max(m,n))。

### 8.6 最小覆盖子串

题意：s 中找最短子串，包含 t 的全部字符。

思路：滑动窗口。右指针扩张直到满足条件，左指针收缩优化答案。

```java
public String minWindow(String s, String t) {
    if (s.length() < t.length()) {
        return "";
    }
    Map<Character, Integer> need = new HashMap<>();
    for (char c : t.toCharArray()) {
        need.put(c, need.getOrDefault(c, 0) + 1);
    }
    Map<Character, Integer> window = new HashMap<>();
    int valid = 0;
    int left = 0;
    int start = 0;
    int len = Integer.MAX_VALUE;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (need.containsKey(c)) {
            window.put(c, window.getOrDefault(c, 0) + 1);
            if (window.get(c).equals(need.get(c))) {
                valid++;
            }
        }
        while (valid == need.size()) {
            if (right - left + 1 < len) {
                start = left;
                len = right - left + 1;
            }
            char d = s.charAt(left++);
            if (need.containsKey(d)) {
                if (window.get(d).equals(need.get(d))) {
                    valid--;
                }
                window.put(d, window.get(d) - 1);
            }
        }
    }
    return len == Integer.MAX_VALUE ? "" : s.substring(start, start + len);
}
```

易错点：`valid` 统计满足数量要求的字符种类数，不是字符总数。  
复杂度：时间 O(n)，空间 O(字符集大小)。

### 8.7 两数之和

题意：数组中找两个数，使和等于 target。

思路：HashMap 存已经遍历过的值和下标，当前值 x 只需要查 target - x。

```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> map = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int need = target - nums[i];
        if (map.containsKey(need)) {
            return new int[] {map.get(need), i};
        }
        map.put(nums[i], i);
    }
    return new int[] {-1, -1};
}
```

易错点：先查再放，避免同一个元素用两次。  
复杂度：时间 O(n)，空间 O(n)。

### 8.8 有序数组两数之和

题意：升序数组中找两个数和等于 target。

思路：双指针。和小了左指针右移，和大了右指针左移。

```java
public int[] twoSumSorted(int[] nums, int target) {
    int left = 0;
    int right = nums.length - 1;
    while (left < right) {
        int sum = nums[left] + nums[right];
        if (sum == target) {
            return new int[] {left, right};
        } else if (sum < target) {
            left++;
        } else {
            right--;
        }
    }
    return new int[] {-1, -1};
}
```

易错点：只有有序数组才能这么做。  
复杂度：时间 O(n)，空间 O(1)。

### 8.9 三数之和

题意：找所有不重复三元组，使和为 0。

思路：先排序，固定一个数，剩下两个数用双指针。重点是去重。

```java
public List<List<Integer>> threeSum(int[] nums) {
    Arrays.sort(nums);
    List<List<Integer>> ans = new ArrayList<>();
    for (int i = 0; i < nums.length - 2; i++) {
        if (i > 0 && nums[i] == nums[i - 1]) {
            continue;
        }
        int left = i + 1;
        int right = nums.length - 1;
        while (left < right) {
            int sum = nums[i] + nums[left] + nums[right];
            if (sum == 0) {
                ans.add(Arrays.asList(nums[i], nums[left], nums[right]));
                while (left < right && nums[left] == nums[left + 1]) {
                    left++;
                }
                while (left < right && nums[right] == nums[right - 1]) {
                    right--;
                }
                left++;
                right--;
            } else if (sum < 0) {
                left++;
            } else {
                right--;
            }
        }
    }
    return ans;
}
```

易错点：固定数去重，命中答案后 left/right 也要去重。  
复杂度：时间 O(n²)，空间 O(1) 不算结果。

### 8.10 移动零

题意：把 0 移到末尾，保持非 0 相对顺序。

思路：slow 表示下一个非 0 应放的位置，fast 扫描数组。

```java
public void moveZeroes(int[] nums) {
    int slow = 0;
    for (int fast = 0; fast < nums.length; fast++) {
        if (nums[fast] != 0) {
            int temp = nums[slow];
            nums[slow] = nums[fast];
            nums[fast] = temp;
            slow++;
        }
    }
}
```

易错点：不能排序，因为要保持相对顺序。  
复杂度：时间 O(n)，空间 O(1)。

### 8.11 删除有序数组重复项

题意：升序数组原地去重，返回新长度。

思路：slow 是写入位置，fast 扫描新值。

```java
public int removeDuplicates(int[] nums) {
    if (nums.length == 0) {
        return 0;
    }
    int slow = 1;
    for (int fast = 1; fast < nums.length; fast++) {
        if (nums[fast] != nums[fast - 1]) {
            nums[slow++] = nums[fast];
        }
    }
    return slow;
}
```

易错点：空数组返回 0，slow 初始为 1。  
复杂度：时间 O(n)，空间 O(1)。

### 8.12 合并两个有序数组

题意：nums1 后面有空间，把 nums2 合并进去。

思路：从后往前填，避免覆盖 nums1 未处理元素。

```java
public void merge(int[] nums1, int m, int[] nums2, int n) {
    int i = m - 1;
    int j = n - 1;
    int k = m + n - 1;
    while (j >= 0) {
        if (i >= 0 && nums1[i] > nums2[j]) {
            nums1[k--] = nums1[i--];
        } else {
            nums1[k--] = nums2[j--];
        }
    }
}
```

易错点：循环条件只要 `j >= 0`。  
复杂度：时间 O(m+n)，空间 O(1)。

### 8.13 盛最多水的容器

题意：找两根柱子组成最大面积。

思路：双指针从两端开始，每次移动较短边，因为面积由短板决定。

```java
public int maxArea(int[] height) {
    int left = 0;
    int right = height.length - 1;
    int ans = 0;
    while (left < right) {
        int area = Math.min(height[left], height[right]) * (right - left);
        ans = Math.max(ans, area);
        if (height[left] < height[right]) {
            left++;
        } else {
            right--;
        }
    }
    return ans;
}
```

易错点：移动短板，不是长板。  
复杂度：时间 O(n)，空间 O(1)。

### 8.14 合并区间

题意：合并重叠区间。

思路：按左端点排序，维护当前区间 cur，能合并就扩右边界，不能合并就加入答案。

```java
public int[][] mergeIntervals(int[][] intervals) {
    if (intervals.length == 0) {
        return new int[0][2];
    }
    Arrays.sort(intervals, Comparator.comparingInt(a -> a[0]));
    List<int[]> ans = new ArrayList<>();
    int[] cur = intervals[0];
    for (int i = 1; i < intervals.length; i++) {
        if (intervals[i][0] <= cur[1]) {
            cur[1] = Math.max(cur[1], intervals[i][1]);
        } else {
            ans.add(cur);
            cur = intervals[i];
        }
    }
    ans.add(cur);
    return ans.toArray(new int[ans.size()][]);
}
```

易错点：最后一个 cur 要加入答案。  
复杂度：时间 O(n log n)，空间 O(n)。

### 8.15 冒泡排序

思路：相邻元素比较，大的往后冒。每轮确定一个最大值。

```java
public void bubbleSort(int[] nums) {
    for (int i = 0; i < nums.length - 1; i++) {
        boolean swapped = false;
        for (int j = 0; j < nums.length - 1 - i; j++) {
            if (nums[j] > nums[j + 1]) {
                swap(nums, j, j + 1);
                swapped = true;
            }
        }
        if (!swapped) {
            break;
        }
    }
}
```

复杂度：平均/最坏 O(n²)，最好 O(n)，稳定。

### 8.16 快速排序

思路：选 pivot，partition 后 pivot 左边都小于等于它，右边都大于它，再递归左右。

```java
public void quickSort(int[] nums) {
    if (nums == null || nums.length < 2) {
        return;
    }
    quickSort(nums, 0, nums.length - 1);
}

private void quickSort(int[] nums, int left, int right) {
    if (left >= right) {
        return;
    }
    int pivot = partition(nums, left, right);
    quickSort(nums, left, pivot - 1);
    quickSort(nums, pivot + 1, right);
}

private int partition(int[] nums, int left, int right) {
    int pivot = nums[right];
    int less = left;
    for (int i = left; i < right; i++) {
        if (nums[i] <= pivot) {
            swap(nums, less, i);
            less++;
        }
    }
    swap(nums, less, right);
    return less;
}

private void swap(int[] nums, int i, int j) {
    int temp = nums[i];
    nums[i] = nums[j];
    nums[j] = temp;
}
```

易错点：partition 循环到 `i < right`，最后把 pivot 换到 less。  
复杂度：平均 O(n log n)，最坏 O(n²)，不稳定。

### 8.17 随机快排

思路：partition 前随机选 pivot，降低有序数组退化概率。

```java
private void quickSortRandom(int[] nums, int left, int right) {
    if (left >= right) {
        return;
    }
    int randomIndex = left + new Random().nextInt(right - left + 1);
    swap(nums, randomIndex, right);
    int pivot = partition(nums, left, right);
    quickSortRandom(nums, left, pivot - 1);
    quickSortRandom(nums, pivot + 1, right);
}
```

复杂度：期望 O(n log n)，最坏仍可能 O(n²)。

### 8.18 归并排序

思路：分治。先拆成小段，再合并两个有序段。

```java
public void mergeSort(int[] nums) {
    int[] temp = new int[nums.length];
    mergeSort(nums, 0, nums.length - 1, temp);
}

private void mergeSort(int[] nums, int left, int right, int[] temp) {
    if (left >= right) {
        return;
    }
    int mid = left + (right - left) / 2;
    mergeSort(nums, left, mid, temp);
    mergeSort(nums, mid + 1, right, temp);
    merge(nums, left, mid, right, temp);
}

private void merge(int[] nums, int left, int mid, int right, int[] temp) {
    int i = left;
    int j = mid + 1;
    int k = left;
    while (i <= mid && j <= right) {
        if (nums[i] <= nums[j]) {
            temp[k++] = nums[i++];
        } else {
            temp[k++] = nums[j++];
        }
    }
    while (i <= mid) {
        temp[k++] = nums[i++];
    }
    while (j <= right) {
        temp[k++] = nums[j++];
    }
    for (int p = left; p <= right; p++) {
        nums[p] = temp[p];
    }
}
```

易错点：merge 后要拷回 nums。  
复杂度：时间 O(n log n)，空间 O(n)，稳定。

### 8.19 第 K 大元素

题意：找数组中第 K 大。

思路：第 K 大等价于升序下标 `n - k`。用快排 partition，每次只去目标下标所在的一边。

```java
public int findKthLargest(int[] nums, int k) {
    int target = nums.length - k;
    int left = 0;
    int right = nums.length - 1;
    while (left <= right) {
        int p = partition(nums, left, right);
        if (p == target) {
            return nums[p];
        } else if (p < target) {
            left = p + 1;
        } else {
            right = p - 1;
        }
    }
    return -1;
}
```

易错点：第 K 大是下标 `n-k`，不是 `k-1`。  
复杂度：平均 O(n)，最坏 O(n²)。

### 8.20 二叉树节点定义

```java
class TreeNode {
    int val;
    TreeNode left;
    TreeNode right;

    TreeNode(int val) {
        this.val = val;
    }
}
```

### 8.21 二叉树剪枝

题意：删除所有不包含 1 的子树。

思路：后序遍历。先剪左右子树，再判断当前节点是否是值为 0 的叶子。

```java
public TreeNode pruneTree(TreeNode root) {
    if (root == null) {
        return null;
    }
    root.left = pruneTree(root.left);
    root.right = pruneTree(root.right);
    if (root.val == 0 && root.left == null && root.right == null) {
        return null;
    }
    return root;
}
```

易错点：必须后序，因为当前节点是否删除依赖左右子树剪完后的结果。  
复杂度：时间 O(n)，空间 O(h)。

### 8.22 删除目标叶子节点

思路：和剪枝一样，后序遍历。删除后新产生的 target 叶子也会被处理。

```java
public TreeNode removeLeafNodes(TreeNode root, int target) {
    if (root == null) {
        return null;
    }
    root.left = removeLeafNodes(root.left, target);
    root.right = removeLeafNodes(root.right, target);
    if (root.val == target && root.left == null && root.right == null) {
        return null;
    }
    return root;
}
```

复杂度：时间 O(n)，空间 O(h)。

### 8.23 二叉树最大深度

思路：最大深度 = 左右子树最大深度的较大值 + 1。

```java
public int maxDepth(TreeNode root) {
    if (root == null) {
        return 0;
    }
    return Math.max(maxDepth(root.left), maxDepth(root.right)) + 1;
}
```

复杂度：时间 O(n)，空间 O(h)。

### 8.24 对称二叉树

思路：比较两棵子树是否镜像，左的左对右的右，左的右对右的左。

```java
public boolean isSymmetric(TreeNode root) {
    if (root == null) {
        return true;
    }
    return mirror(root.left, root.right);
}

private boolean mirror(TreeNode a, TreeNode b) {
    if (a == null || b == null) {
        return a == b;
    }
    return a.val == b.val && mirror(a.left, b.right) && mirror(a.right, b.left);
}
```

复杂度：时间 O(n)，空间 O(h)。

### 8.25 二叉树层序遍历

思路：BFS 队列。每层开始记录队列 size，这个 size 就是当前层节点数。

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> ans = new ArrayList<>();
    if (root == null) {
        return ans;
    }
    Queue<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);
    while (!queue.isEmpty()) {
        int size = queue.size();
        List<Integer> level = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            level.add(node.val);
            if (node.left != null) {
                queue.offer(node.left);
            }
            if (node.right != null) {
                queue.offer(node.right);
            }
        }
        ans.add(level);
    }
    return ans;
}
```

易错点：size 要在每层开始固定。  
复杂度：时间 O(n)，空间 O(n)。

### 8.26 最近公共祖先

思路：递归找 p 和 q。左右都找到，当前节点就是祖先；只找到一边就向上返回那一边。

```java
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    if (root == null || root == p || root == q) {
        return root;
    }
    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);
    if (left != null && right != null) {
        return root;
    }
    return left != null ? left : right;
}
```

复杂度：时间 O(n)，空间 O(h)。

### 8.27 每日温度

题意：每一天还要等几天才有更高温度。

思路：单调栈，栈里存还没找到更高温度的下标。当前温度比栈顶高时，栈顶答案就确定。

```java
public int[] dailyTemperatures(int[] temperatures) {
    int[] ans = new int[temperatures.length];
    Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < temperatures.length; i++) {
        while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()]) {
            int prev = stack.pop();
            ans[prev] = i - prev;
        }
        stack.push(i);
    }
    return ans;
}
```

易错点：栈里存下标，while 不是 if。  
复杂度：时间 O(n)，空间 O(n)。

### 8.28 下一个更大元素

思路：单调栈。当前元素比栈顶大时，栈顶答案就是当前元素。

```java
public int[] nextGreaterElement(int[] nums) {
    int[] ans = new int[nums.length];
    Arrays.fill(ans, -1);
    Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < nums.length; i++) {
        while (!stack.isEmpty() && nums[i] > nums[stack.peek()]) {
            ans[stack.pop()] = nums[i];
        }
        stack.push(i);
    }
    return ans;
}
```

复杂度：时间 O(n)，空间 O(n)。

### 8.29 循环数组下一个更大元素

思路：遍历两遍，用 `i % n` 模拟循环。第二遍只帮第一遍找答案，不再入栈。

```java
public int[] nextGreaterElements(int[] nums) {
    int n = nums.length;
    int[] ans = new int[n];
    Arrays.fill(ans, -1);
    Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < 2 * n; i++) {
        int idx = i % n;
        while (!stack.isEmpty() && nums[idx] > nums[stack.peek()]) {
            ans[stack.pop()] = nums[idx];
        }
        if (i < n) {
            stack.push(idx);
        }
    }
    return ans;
}
```

复杂度：时间 O(n)，空间 O(n)。

### 8.30 接雨水

题意：计算柱子能接多少雨水。

思路：每个位置水量取决于左右最高柱子的较小值。双指针维护 leftMax/rightMax，每次移动较小的一侧。

```java
public int trap(int[] height) {
    int left = 0;
    int right = height.length - 1;
    int leftMax = 0;
    int rightMax = 0;
    int ans = 0;
    while (left < right) {
        leftMax = Math.max(leftMax, height[left]);
        rightMax = Math.max(rightMax, height[right]);
        if (leftMax < rightMax) {
            ans += leftMax - height[left];
            left++;
        } else {
            ans += rightMax - height[right];
            right--;
        }
    }
    return ans;
}
```

易错点：先更新 leftMax/rightMax，再计算水量。  
复杂度：时间 O(n)，空间 O(1)。

### 8.31 链表节点定义

```java
class ListNode {
    int val;
    ListNode next;

    ListNode(int val) {
        this.val = val;
    }
}
```

### 8.32 反转链表

思路：prev、cur、next 三指针。每次把 cur.next 指向 prev。

```java
public ListNode reverseList(ListNode head) {
    ListNode prev = null;
    ListNode cur = head;
    while (cur != null) {
        ListNode next = cur.next;
        cur.next = prev;
        prev = cur;
        cur = next;
    }
    return prev;
}
```

易错点：先保存 next，否则会丢链。  
复杂度：时间 O(n)，空间 O(1)。

### 8.33 环形链表

思路：快慢指针。slow 一步，fast 两步；有环一定相遇，无环 fast 到 null。

```java
public boolean hasCycle(ListNode head) {
    ListNode slow = head;
    ListNode fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) {
            return true;
        }
    }
    return false;
}
```

复杂度：时间 O(n)，空间 O(1)。

### 8.34 删除倒数第 N 个节点

思路：dummy + 快慢指针。fast 先走 n 步，然后一起走，slow 停在待删节点前一个。

```java
public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode dummy = new ListNode(0);
    dummy.next = head;
    ListNode fast = dummy;
    ListNode slow = dummy;
    for (int i = 0; i < n; i++) {
        fast = fast.next;
    }
    while (fast.next != null) {
        fast = fast.next;
        slow = slow.next;
    }
    slow.next = slow.next.next;
    return dummy.next;
}
```

易错点：用 dummy 处理删除头节点。  
复杂度：时间 O(n)，空间 O(1)。

### 8.35 合并两个有序链表

思路：dummy + cur，每次接较小节点。

```java
public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
    ListNode dummy = new ListNode(0);
    ListNode cur = dummy;
    while (list1 != null && list2 != null) {
        if (list1.val <= list2.val) {
            cur.next = list1;
            list1 = list1.next;
        } else {
            cur.next = list2;
            list2 = list2.next;
        }
        cur = cur.next;
    }
    cur.next = list1 != null ? list1 : list2;
    return dummy.next;
}
```

复杂度：时间 O(m+n)，空间 O(1)。

### 8.36 岛屿数量

题意：二维网格里，1 是陆地，0 是水，求岛屿数量。

思路：遍历网格，遇到 1 就答案 +1，并 DFS 把整座岛标记成 0。

```java
public int numIslands(char[][] grid) {
    int m = grid.length;
    int n = grid[0].length;
    int ans = 0;
    for (int i = 0; i < m; i++) {
        for (int j = 0; j < n; j++) {
            if (grid[i][j] == '1') {
                ans++;
                dfs(grid, i, j);
            }
        }
    }
    return ans;
}

private void dfs(char[][] grid, int i, int j) {
    if (i < 0 || i >= grid.length || j < 0 || j >= grid[0].length || grid[i][j] != '1') {
        return;
    }
    grid[i][j] = '0';
    dfs(grid, i + 1, j);
    dfs(grid, i - 1, j);
    dfs(grid, i, j + 1);
    dfs(grid, i, j - 1);
}
```

复杂度：时间 O(mn)，空间 O(mn) 最坏递归栈。

### 8.37 矩阵 BFS 最短路框架

思路：队列按层扩展，step 表示步数。

```java
private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

public int bfs(int[][] grid, int sx, int sy) {
    int m = grid.length;
    int n = grid[0].length;
    boolean[][] visited = new boolean[m][n];
    Queue<int[]> queue = new ArrayDeque<>();
    queue.offer(new int[] {sx, sy});
    visited[sx][sy] = true;
    int step = 0;
    while (!queue.isEmpty()) {
        int size = queue.size();
        for (int k = 0; k < size; k++) {
            int[] cur = queue.poll();
            int x = cur[0];
            int y = cur[1];
            if (isTarget(grid, x, y)) {
                return step;
            }
            for (int[] dir : DIRS) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                if (nx < 0 || nx >= m || ny < 0 || ny >= n || visited[nx][ny]) {
                    continue;
                }
                visited[nx][ny] = true;
                queue.offer(new int[] {nx, ny});
            }
        }
        step++;
    }
    return -1;
}

private boolean isTarget(int[][] grid, int x, int y) {
    return false;
}
```

### 8.38 爬楼梯

题意：每次爬 1 或 2 阶，n 阶有多少种爬法。

思路：`dp[i] = dp[i-1] + dp[i-2]`。

```java
public int climbStairs(int n) {
    if (n <= 2) {
        return n;
    }
    int a = 1;
    int b = 2;
    for (int i = 3; i <= n; i++) {
        int c = a + b;
        a = b;
        b = c;
    }
    return b;
}
```

复杂度：时间 O(n)，空间 O(1)。

### 8.39 最大子数组和

题意：找连续子数组的最大和。

思路：当前最大和要么从当前数重新开始，要么接在前面后面。

```java
public int maxSubArray(int[] nums) {
    int cur = nums[0];
    int ans = nums[0];
    for (int i = 1; i < nums.length; i++) {
        cur = Math.max(nums[i], cur + nums[i]);
        ans = Math.max(ans, cur);
    }
    return ans;
}
```

复杂度：时间 O(n)，空间 O(1)。

### 8.40 最长递增子序列

思路：`dp[i]` 表示以 nums[i] 结尾的最长递增子序列长度。

```java
public int lengthOfLIS(int[] nums) {
    int n = nums.length;
    int[] dp = new int[n];
    Arrays.fill(dp, 1);
    int ans = 1;
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < i; j++) {
            if (nums[j] < nums[i]) {
                dp[i] = Math.max(dp[i], dp[j] + 1);
            }
        }
        ans = Math.max(ans, dp[i]);
    }
    return ans;
}
```

复杂度：时间 O(n²)，空间 O(n)。

### 8.41 01 背包

思路：每个物品只能选一次，容量倒序遍历。

```java
public int knapsack(int[] weights, int[] values, int capacity) {
    int[] dp = new int[capacity + 1];
    for (int i = 0; i < weights.length; i++) {
        for (int c = capacity; c >= weights[i]; c--) {
            dp[c] = Math.max(dp[c], dp[c - weights[i]] + values[i]);
        }
    }
    return dp[capacity];
}
```

易错点：01 背包容量必须倒序，防止一个物品被重复选。  
复杂度：时间 O(n * capacity)，空间 O(capacity)。

## 9. 算法现场万能话术

滑动窗口：

> 我用两个指针维护窗口，右指针扩张，窗口不满足条件时左指针收缩。每个元素最多进出窗口一次，所以通常是 O(n)。

双指针：

> 如果数组有序，可以从两端向中间移动；如果是原地覆盖问题，一个指针扫描，一个指针记录写入位置。

单调栈：

> 单调栈适合找右边或左边第一个更大/更小元素。栈里通常存下标，当前元素能让栈顶结算答案时就出栈。

树递归：

> 树题先想当前节点要返回什么。如果当前节点依赖左右子树结果，一般用后序遍历。

动态规划：

> 先定义 dp 含义，再找状态转移，最后确定初始化和遍历顺序。

## 10. 3 年级别常见综合追问

Q：你觉得自己和 1 年开发有什么区别？

答：

我觉得主要在三点。第一是做需求时不只完成接口，会考虑数据量、异常、幂等、监控和上线风险。第二是遇到线上问题能自己定位，比如 GC、OOM、慢 SQL、文件残留这类问题，不是只看业务日志。第三是对业务链路理解更完整，比如商品库数据不是拉出来就完了，还要考虑下游检索消费、文件完整性和告警闭环。

Q：如果让你独立负责一个模块，你怎么做？

答：

我会先确认业务目标和上下游依赖，再拆接口、数据模型和异常流程。开发时会重点考虑幂等、重试、超时、日志、监控和数据校验。上线前会准备回滚方案和核心指标观察点。上线后看日志、告警和业务结果，确认模块稳定。

Q：如何保证代码质量？

答：

我会从几方面做：第一，编码时保持方法职责清晰，不把所有逻辑堆在 Controller 或一个大方法里；第二，关键逻辑加单元测试或至少本地构造边界数据验证；第三，涉及数据变更的逻辑考虑幂等和事务；第四，上线前关注日志和监控，方便出问题能定位。

Q：抗压和加班怎么看？

答：

互联网业务高峰或线上问题需要响应，我可以接受。我的看法是，短期为了上线或故障处理加班没问题，但长期还是要通过工具、监控、自动化和流程优化减少无效加班。遇到问题我会先把事情推进，而不是只抱怨压力。

## 11. 你要避免的表达

不要说：

- “这个是 AI 帮我写的，我不太清楚。”
- “大数据我掌握 Kafka/Flink/HBase 很熟。”如果没深做，不要说太满。
- “JVM 参数调了一下就好了。”要强调先定位再优化。
- “线程开多点就快了。”要强调限流、超时和下游保护。
- “我只想做 AI。”OD Java 岗更看重工程交付。

可以说：

- “这个组件我在线上没有做过深度调优，但我理解它的核心原理是……”
- “这个问题我会先从日志、监控、链路和数据状态排查。”
- “我当时不是直接加机器，而是先分析业务和数据结构，减少无效计算。”

## 12. 一周复习计划

第 1 天：Java 集合 + 字符串/数组算法

- HashMap、ConcurrentHashMap、ArrayList。
- 最长无重复子串、两数之和、三数之和、移动零、合并数组。

第 2 天：JVM + 排序

- JVM 内存、GC、类加载、OOM 排查。
- 快排、随机快排、归并、第 K 大。

第 3 天：并发 + 栈/队列

- 线程池、锁、volatile、CAS、ThreadLocal。
- 有效括号、每日温度、下一个更大元素、接雨水。

第 4 天：MySQL + Redis

- 索引、事务、MVCC、慢 SQL。
- 缓存穿透/击穿/雪崩、分布式锁。

第 5 天：项目深挖

- 商品库数据拉取 2 分钟讲法。
- JVM 调优追问。
- 红包匹配优化追问。
- 收益预测调度和限流追问。

第 6 天：DFS/BFS + DP

- 岛屿数量、矩阵 BFS、层序遍历。
- 爬楼梯、最大子数组和、LIS、01 背包。

第 7 天：模拟面试

- 自我介绍 3 遍。
- 主项目讲 3 遍。
- 手写 5 道算法。
- 复盘答不顺的基础题。

## 13. 面试前最后一小时

只看这些：

1. 自我介绍 1 分钟。
2. 商品库数据拉取 2 分钟。
3. Full GC/OOM 排查。
4. 线程池执行流程。
5. HashMap put 流程。
6. MySQL 索引和慢 SQL。
7. Redis 三大缓存问题。
8. 快排、三数之和、最长无重复子串、二叉树层序、每日温度。

最后一句提醒：

> 成都华为 OD Java 后端，不要把自己包装成“AI 方向选手”，要包装成“Java 基础扎实、项目真实、能抗线上问题、算法能写”的 3 年后端。

## 14. 八股理解记忆版：把知识点变成脑子里的模型

这一章不是补充更多知识，而是告诉你“怎么理解、怎么记、面试时怎么说”。先把这一章看熟，再去看第 5 章的标准答案。

### 14.1 面试回答万能结构

任何八股题，都按这个顺序答：

1. 一句话定义：先让面试官知道你不是懵的。
2. 核心机制：讲它怎么工作的。
3. 为什么这么设计：讲好处和代价。
4. 项目落点：我在哪里用过、踩过什么坑。

例子：HashMap

> HashMap 本质是一个用 hash 快速定位的 key-value 容器。它底层用数组做桶，冲突时用链表或红黑树解决。容量设计成 2 的幂，是为了用位运算快速定位桶，也让扩容迁移更简单。项目里我们经常用它做临时映射，但并发场景不能直接用 HashMap，要用 ConcurrentHashMap 或加同步控制。

你要练到每个知识点都能这样说。

### 14.2 HashMap：把它想成“快递柜”

记忆画面：

HashMap 像一排快递柜。

- 数组：一排柜子。
- hash：快递单号。
- `(n - 1) & hash`：算这个快递应该放哪个柜子。
- 链表：同一个柜子里放了多个包裹，只能一个个找。
- 红黑树：柜子里包裹太多了，改成有序货架，提高查找效率。
- 扩容：柜子不够用了，换一排更大的柜子，重新分配包裹。

面试第一句话：

> HashMap 的核心是用 hash 把 key 映射到数组下标，冲突时用链表或红黑树处理，所以平均情况下 put/get 是 O(1)。

为什么容量是 2 的幂：

你可以这样记：  
取模像“算除法”，位运算像“看尾号”，更快。容量是 2 的幂时，`hash % n` 可以等价成 `(n - 1) & hash`。

扩容怎么记：

容量翻倍后，一个节点的新位置只有两种可能：

- 留在原位置。
- 移到原位置 + oldCap。

所以 Java 8 扩容不需要重新算完整 hash 分布。

树化怎么记：

链表长度到 8 只是“柜子太挤”的信号，但如果柜子总数还太少，优先扩容；只有数组容量至少 64，才说明不是柜子太少，而是这个柜子冲突真的严重，才树化。

项目落点：

> 我会注意 HashMap 不适合并发写。比如多线程聚合结果，如果多个线程同时 put，可能出现数据覆盖或结构异常。并发场景我会用 ConcurrentHashMap，或者让每个线程先用局部 Map，最后单线程合并。

最容易被追问：

Q：为什么 HashMap 线程不安全？

你这样答：

> 因为 put、扩容、链表/树结构调整都不是原子操作。多个线程同时修改同一个桶，可能互相覆盖，也可能在扩容时看到不一致状态。所以 HashMap 适合单线程或外部同步场景，并发容器要用 ConcurrentHashMap。

### 14.3 ConcurrentHashMap：把它想成“分柜台办业务”

记忆画面：

Hashtable 像整个营业厅只有一把大锁，一个人办业务时所有人都等。  
ConcurrentHashMap 像每个柜台一把锁，不同柜台可以同时办。

面试第一句话：

> ConcurrentHashMap 的核心是降低锁粒度。Java 8 里读操作大多无锁，写操作主要通过 CAS 和 synchronized 锁住桶头节点。

怎么记 CAS + synchronized：

- CAS：没人抢时，直接用无锁方式改。
- synchronized：发生冲突了，就锁当前桶，不锁整个 Map。

项目落点：

> 如果是多线程任务统计维度状态、缓存任务结果，我会优先考虑 ConcurrentHashMap。但如果 value 是 List 这种复合结构，也要注意 List 本身是不是线程安全，不能只看 Map 线程安全。

### 14.4 ArrayList 和 LinkedList：别只背“增删改查”

记忆画面：

- ArrayList 像电影院座位，知道座位号能直接过去，但中间插入一个人要让后面一排人挪位置。
- LinkedList 像一群人手拉手，找到某个人要从头走过去，但找到之后插入比较方便。

面试第一句话：

> ArrayList 底层是数组，随机访问快；LinkedList 底层是双向链表，随机访问慢。实际业务里 ArrayList 更常用，因为内存连续、CPU 缓存友好。

高级一点的理解：

很多人背“LinkedList 插入删除快”，但这是有前提的：已经定位到节点。如果你还要先按下标找节点，那查找已经 O(n) 了，不一定快。

项目落点：

> 大部分业务查询结果、批量处理列表我会用 ArrayList。除非明确需要频繁在已知节点前后插入删除，否则不会默认选 LinkedList。

### 14.5 String：不可变是为了“安全复用”

记忆画面：

String 像身份证号，很多地方都拿它做身份识别。如果它能被人随便改，所有依赖它的地方都会乱。

面试第一句话：

> String 不可变主要是为了线程安全、常量池复用和 hash 稳定。

为什么 hash 稳定重要：

HashMap 里 key 的位置依赖 hash。如果 String 做 key 后内容变了，hash 也变了，这个 key 就可能再也找不回来。

StringBuilder 怎么记：

- String：定稿文件，每改一次都生成新文件。
- StringBuilder：草稿纸，可以反复改。
- StringBuffer：带锁的草稿纸，多人写安全但慢。

项目落点：

> 循环里大量拼接字符串我会用 StringBuilder，比如拼 SQL 片段、日志摘要、文件内容片段。普通少量拼接编译器会优化，不需要过度处理。

### 14.6 JVM 内存：把 JVM 想成“工厂”

记忆画面：

JVM 像一个工厂：

- 堆：仓库，放生产出来的对象。
- 栈：每个工人的工作台，放当前方法的局部变量和调用记录。
- 方法区/元空间：工厂图纸室，放类信息。
- 程序计数器：每个工人的当前操作步骤。
- 本地方法栈：调用外部/native 工具时用的工作台。

面试第一句话：

> JVM 运行时内存主要分堆、方法区、虚拟机栈、本地方法栈和程序计数器。线上 OOM 排查时，要先判断是哪块内存出问题。

怎么记不同 OOM：

- Java heap space：仓库爆了，对象太多或没释放。
- StackOverflowError：方法调用太深，常见递归没出口。
- Metaspace：类元信息太多，可能动态生成类或类加载器泄漏。

项目落点：

> 我在商品库拉取里遇到的是堆压力，主要因为中间集合和对象暂存太多。处理时不是只调大堆，而是先分析对象占用，减少全量暂存和中间对象。

### 14.7 GC：把它想成“仓库清理”

记忆画面：

GC 像仓库管理员清理不用的物品。判断一个东西能不能扔，不是看它旧不旧，而是看有没有人还能找到它。

面试第一句话：

> JVM 主流垃圾回收通过可达性分析判断对象是否存活，从 GC Roots 出发能到达的对象保留，到达不了的对象可以回收。

GC Roots 怎么记：

“当前还在用的入口”就是 Roots：

- 栈里的局部变量引用。
- 静态变量引用。
- 常量引用。
- JNI 引用。

Full GC 频繁怎么理解：

Young GC 清年轻代，Full GC 通常会涉及老年代。Full GC 频繁说明老年代压力大，可能是对象活得太久、晋升太快、内存泄漏、一次性加载太多。

面试说法：

> Full GC 频繁我不会第一反应就是调参数。我会先看 GC 后内存是否下降。如果下降明显，可能是内存压力或参数问题；如果下降不明显，要怀疑对象被长期引用，可能有泄漏或缓存没释放。

项目落点：

> 商品库拉取里部分维度数据在内存里暂存太久，经历多次 Young GC 后进入老年代，导致 Full GC 频繁。优化重点是减少对象存活时间和中间集合大小。

### 14.8 类加载和双亲委派：把它想成“逐级审批”

记忆画面：

你要加载一个类，先问上级部门有没有标准版本。上级没有，自己才加载。

面试第一句话：

> 双亲委派是类加载器收到加载请求后，先委托父加载器加载，父加载器找不到再自己加载。

为什么这样设计：

防止核心类被篡改。比如你自己写一个 `java.lang.String`，如果没有双亲委派，可能覆盖 JDK 的 String，整个系统就危险了。

类加载过程记忆：

“加载验证准备解析初始化”可以记成：  
先把人招进来，查证件，发工牌，安排座位，正式上岗。

- 加载：读 class。
- 验证：检查安全。
- 准备：静态变量分配内存并赋默认值。
- 解析：符号引用转直接引用。
- 初始化：执行静态赋值和 static 块。

### 14.9 线程池：把它想成“餐厅接单”

记忆画面：

线程池像餐厅：

- corePoolSize：固定厨师。
- workQueue：等餐队列。
- maximumPoolSize：临时厨师上限。
- keepAliveTime：临时厨师闲多久下班。
- rejectedExecutionHandler：爆单后的处理方式。

面试第一句话：

> 线程池是为了复用线程、控制并发和管理任务队列，避免无限创建线程把系统打垮。

执行流程怎么记：

先叫固定厨师；固定厨师忙不过来，订单进队列；队列满了，再叫临时厨师；临时厨师也满了，就拒单。

拒绝策略怎么理解：

- AbortPolicy：直接说“不接了”，抛异常。
- CallerRunsPolicy：谁下单谁自己做，形成反压。
- DiscardPolicy：悄悄丢掉。
- DiscardOldestPolicy：丢掉排最久的订单。

项目落点：

> 收益预测里我用固定线程池提升远程调用吞吐，但没有无限加线程，而是配合令牌桶和超时保护下游。线程池解决本系统并发，限流解决下游承载。

### 14.10 volatile、synchronized、ReentrantLock：三句话区分

记忆画面：

- volatile：公告栏，改了大家马上能看到。
- synchronized：房间钥匙，一次只能一个人进。
- ReentrantLock：高级门禁，可以尝试进、排队、公平排队、等多个条件。

volatile 面试第一句话：

> volatile 保证可见性和有序性，但不保证复合操作的原子性。

为什么 `count++` 不安全：

`count++` 不是一步，是读、加、写三步。volatile 只能保证别人看得见最新值，但不能保证这三步不被打断。

synchronized 面试第一句话：

> synchronized 是 JVM 内置锁，可以保证同一时刻只有一个线程进入临界区，并且会自动释放锁。

ReentrantLock 面试第一句话：

> ReentrantLock 是 JUC 的显式锁，能力比 synchronized 更丰富，比如 tryLock、可中断、公平锁和多个 Condition。

项目落点：

> 简单同步我优先用 synchronized，可读性好；需要超时获取锁、可中断或多个条件队列时，才考虑 ReentrantLock。

### 14.11 ThreadLocal：把它想成“每个线程自己的口袋”

记忆画面：

每个线程都有自己的口袋，ThreadLocal 变量放在各自口袋里，互不影响。

面试第一句话：

> ThreadLocal 用来保存线程本地变量，每个线程都有自己的副本，常用于保存用户上下文、traceId、事务上下文等。

为什么会内存泄漏：

线程池里的线程不会执行完就销毁，它会反复复用。如果 ThreadLocal 的 value 用完不 remove，就可能一直挂在线程上。

项目落点：

> Web 请求或线程池场景用 ThreadLocal，一定要在 finally 里 remove，避免用户上下文串到下一个请求。

### 14.12 MySQL 索引：把 B+ 树想成“图书目录”

记忆画面：

B+ 树像图书馆多级目录：

- 根节点：总目录。
- 中间节点：分区目录。
- 叶子节点：真正书的位置。
- 叶子链表：书按顺序排好，方便范围查。

面试第一句话：

> InnoDB 索引用 B+ 树，是因为它高度低、磁盘 IO 少，并且叶子节点有序，适合范围查询。

聚簇索引怎么记：

主键索引叶子节点放的是“整本书”。  
二级索引叶子节点放的是“主键书签”。  
如果通过二级索引查不到所有字段，就要拿主键书签再去主键索引找整行，这就是回表。

覆盖索引怎么记：

你要的信息目录页上都有，就不用去书架拿整本书。

索引失效怎么记：

面试里不要散背，记一句：

> 让索引列变形、让查询不符合索引顺序、或者让优化器觉得走索引不划算，都可能导致索引失效。

展开：

- 函数/表达式：索引列变形。
- like `%xx`：不知道从哪开始找。
- 联合索引不满足最左前缀：目录顺序用错。
- 隐式类型转换：列被转换。
- 区分度低：走索引还不如全表扫。

项目落点：

> 慢 SQL 我会先 explain，看 type、key、rows、Extra，再判断是索引缺失、索引顺序不合适、回表多，还是分页太深。

### 14.13 MVCC：把它想成“拍快照”

记忆画面：

你看数据时，数据库给你拍了一张快照。别人后面改了，不影响你这张快照里的视图。

面试第一句话：

> MVCC 是多版本并发控制，通过 undo log 和 Read View，让读操作不用阻塞写操作。

可重复读怎么理解：

同一个事务里多次快照读，看到的是同一份 Read View，所以结果一致。

当前读和快照读：

- 普通 select：快照读，看历史版本。
- update/delete/select for update：当前读，要读最新数据并加锁。

项目落点：

> 如果是任务抢占这种场景，不能只靠普通快照读判断状态，最终要用 update where status/version 这种当前写操作保证只有一个机器抢成功。

### 14.14 Redis 缓存三兄弟：穿透、击穿、雪崩

记忆画面：

- 穿透：查一个根本不存在的人，门卫也没有记录，每次都跑到档案室。
- 击穿：一个超级热门人的档案刚好过期，所有人瞬间冲进档案室。
- 雪崩：一大片档案同时过期，所有请求都冲进档案室。

面试第一句话：

> 这三个问题本质都是缓存没有挡住请求，导致压力打到数据库，只是原因不同。

怎么区分：

- 穿透：key 不存在。
- 击穿：一个热点 key 失效。
- 雪崩：大量 key 同时失效或 Redis 故障。

解决怎么记：

- 穿透：不存在也记一下，或者布隆过滤器挡住。
- 击穿：热点 key 加保护，互斥锁或逻辑过期。
- 雪崩：别一起过期，加随机时间，多级缓存，限流降级。

项目落点：

> 商品库静态缓存里，如果是热点配置或规则类数据，我会避免大量 key 同时过期，过期时间加随机值，必要时加本地缓存或降级策略。

### 14.15 Redis 分布式锁：记住“加锁要唯一，解锁要验身份”

面试第一句话：

> Redis 分布式锁常用 `SET key value NX EX seconds`，NX 保证不存在才设置，EX 保证异常时能自动过期。

关键理解：

value 必须是唯一标识。解锁时不能直接 delete，要用 Lua 脚本先判断 value 是不是自己的，再删除。

记忆画面：

锁像酒店房卡。你退房时要确认这张房卡是你的房间，不能随便把别人房间退了。

项目落点：

> 如果业务执行时间可能超过锁过期时间，要考虑续期，否则锁过期后别人拿到锁，原线程执行完再 delete，可能删掉别人的锁。

### 14.16 Kafka：把它想成“分区日志文件”

记忆画面：

Kafka 不是普通队列，更像按分区追加写的日志本。生产者往后追加，消费者记住自己读到哪一行 offset。

面试第一句话：

> Kafka 吞吐高主要靠顺序写磁盘、page cache、批量发送、零拷贝和分区并行。

顺序怎么保证：

只保证同一个分区内有序。如果同一订单的消息要有序，就要用订单 id 作为 key 发到同一分区。

不丢怎么讲：

生产端 acks=all + 重试 + 幂等，Broker 有副本和 ISR，消费端处理完业务再提交 offset。

重复消费怎么讲：

Kafka 更常见的工程思路是“至少一次 + 业务幂等”，不要轻易承诺绝对不重复。

### 14.17 Spring：IOC 是“对象交给容器”，AOP 是“统一加外挂”

IOC 记忆画面：

以前你自己 new 对象，像自己招人。IOC 是把招人和分配协作交给 Spring 人事部。

面试第一句话：

> IOC 是控制反转，对象创建和依赖管理交给 Spring 容器，业务代码只关注使用。

AOP 记忆画面：

AOP 像给方法统一加外挂：方法前后自动加日志、事务、权限，不改业务方法本身。

面试第一句话：

> AOP 通过代理在方法前后增强逻辑，典型应用是事务、日志、权限和监控。

事务失效怎么理解：

Spring 事务靠代理。如果调用没有经过代理，事务就不会生效。

最常见：

- 同类内部方法调用。
- 方法不是 public。
- 异常被 catch。
- 抛的是受检异常但没配置 rollbackFor。

项目落点：

> 如果一个方法需要事务，我会注意它是不是通过 Spring Bean 被外部调用，而不是同类内部直接 this 调用。

### 14.18 Linux 排查：记住“先定位层，再定位点”

面试第一句话：

> 线上问题我会先判断是应用、JVM、数据库、缓存、网络还是下游依赖，再进入具体工具排查。

接口慢的排查顺序：

1. 看监控：是整体慢还是单接口慢。
2. 看应用日志：有没有异常、超时、重试。
3. 看 JVM：GC、线程池、CPU、内存。
4. 看 DB：慢 SQL、连接池。
5. 看 Redis/Kafka/下游：调用耗时。

CPU 高怎么记：

找进程 -> 找线程 -> 转 16 进制 -> 查 jstack。

面试说法：

> 我会先 top 找高 CPU 进程，再 top -Hp 找线程，把线程 id 转成 16 进制，用 jstack 查对应线程栈，看是业务死循环、锁竞争还是 GC 线程。

### 14.19 八股面试记忆钩子总表

这张表用来面试前快速过脑子。你不需要逐字背，重点背“第一句话”和“记忆钩子”。

| 知识点 | 面试第一句话 | 记忆钩子 | 项目落点 |
| --- | --- | --- | --- |
| OOP | 面向对象核心是封装、继承、多态。 | 封装像黑盒，继承像复用家族能力，多态像同一个遥控器控制不同设备。 | Service 暴露稳定接口，Agent/Specialist 用接口扩展。 |
| 工厂模式 | 工厂模式把对象创建逻辑集中起来。 | 调用方点菜，厨房决定具体怎么做。 | 根据类型创建不同处理器/Agent。 |
| 代理模式 | 代理是在不改业务代码的情况下增强逻辑。 | 业务方法外面套一层安检。 | Spring AOP、事务、日志、权限。 |
| String | String 不可变是为了安全复用和 hash 稳定。 | 字符串像身份证号，不能随便改。 | String 做 Map key、参数、路径更安全。 |
| HashMap | HashMap 用 hash 快速定位数组桶，冲突用链表/红黑树。 | 快递柜：hash 算柜号，冲突就同柜多包裹。 | 单线程映射可以用，并发写不能直接用。 |
| ConcurrentHashMap | 它通过 CAS + 桶级锁降低锁粒度。 | Hashtable 锁整个营业厅，CHM 锁单个柜台。 | 多线程任务状态、缓存结果。 |
| ArrayList | ArrayList 是动态数组，查询快，插入删除可能搬数据。 | 电影院座位，知道座位号直接找。 | 批量数据、查询结果默认优先用。 |
| LinkedList | LinkedList 是双向链表，随机访问慢。 | 手拉手队伍，找人要从头走。 | 不要只背“增删快”，定位节点也要成本。 |
| JVM 内存 | JVM 内存分堆、栈、元空间等，排查先判断哪块出问题。 | 工厂：堆是仓库，栈是工作台，元空间是图纸室。 | OOM 排查先分类型。 |
| GC | GC 用可达性分析判断对象是否还能被找到。 | 仓库清理，不看旧不旧，看还有没人用。 | Full GC 看回收后是否下降。 |
| 双亲委派 | 类加载先问父加载器，父找不到自己再加载。 | 逐级审批，标准件先找总部。 | 防止核心类被篡改。 |
| 线程池 | 线程池复用线程并控制并发。 | 餐厅接单：固定厨师、队列、临时厨师、拒单。 | 预测系统用线程池提吞吐。 |
| volatile | volatile 保证可见性和有序性，不保证原子性。 | 公告栏，大家能看到最新通知，但不能防多人同时改。 | 状态标记可以用，计数不能只靠它。 |
| synchronized | synchronized 是 JVM 内置互斥锁。 | 房间钥匙，一次只进一个人。 | 简单临界区同步。 |
| ReentrantLock | 显式锁，能力更丰富。 | 高级门禁，能 try、能中断、能公平排队。 | 复杂锁控制时考虑。 |
| ThreadLocal | 每个线程保存自己的变量副本。 | 每个线程自己的口袋。 | traceId/userContext 用完 remove。 |
| CAS | CAS 是比较并交换的乐观锁。 | 改之前先确认东西没被别人动过。 | DB CAS 抢任务、Atomic 类。 |
| B+ 树索引 | B+ 树高度低，叶子有序，适合磁盘和范围查询。 | 图书馆多级目录。 | 慢 SQL explain 看索引。 |
| 聚簇索引 | 主键索引叶子节点存整行。 | 主键目录直接放整本书。 | 二级索引可能回表。 |
| 覆盖索引 | 查询字段都在索引里，不需要回表。 | 目录页信息够了，不用取书。 | 减少随机 IO。 |
| MVCC | MVCC 用多版本让读写少互相阻塞。 | 读数据时拍快照。 | 普通 select 是快照读。 |
| Redis 穿透 | 查不存在的数据，缓存挡不住。 | 查空气，每次都打 DB。 | 空值缓存、布隆过滤器。 |
| Redis 击穿 | 热点 key 过期，大量请求打 DB。 | 明星档案过期，大家一起冲档案室。 | 互斥锁、逻辑过期。 |
| Redis 雪崩 | 大量 key 同时失效或 Redis 故障。 | 一大片档案同时没了。 | 随机过期、限流降级。 |
| 分布式锁 | 加锁要唯一，解锁要验身份。 | 酒店房卡，退房要确认是自己的房。 | SET NX EX + Lua 删除。 |
| Kafka | Kafka 是按分区追加写的日志。 | 每个分区一本日志，消费者记 offset。 | 消息驱动、削峰、解耦。 |
| Spring IOC | 对象创建和依赖交给容器。 | Spring 人事部帮你招人配人。 | Bean 管理、依赖注入。 |
| Spring AOP | 通过代理统一增强方法。 | 给方法外面套日志/事务外挂。 | 事务、日志、权限。 |
| 事务失效 | Spring 事务靠代理，没走代理就失效。 | 没经过收费站，就不会打票。 | 同类内部调用、异常被 catch。 |
| Linux 排查 | 先定位层，再定位点。 | 应用/JVM/DB/缓存/下游逐层排除。 | CPU 高用 top + jstack。 |

## 15. 算法理解记忆版：看到题就知道用什么套路

这一章解决“我看得懂代码，但面试不知道怎么想到”的问题。你要背的不是代码，而是题型信号。

### 15.1 算法题现场步骤

面试时按这个节奏：

1. 复述题意：确认输入输出。
2. 说暴力解：让面试官知道你能先解决。
3. 找优化点：用哈希、双指针、栈、队列、递归、DP。
4. 写代码。
5. 用例子跑一遍。
6. 说复杂度。

现场开口模板：

> 我先确认一下题意。这个题输入是……输出是……如果用暴力可以……复杂度是……这里可以优化，因为……所以我准备用……

### 15.2 哈希表题：看到“找另一个、去重、计数”就想 HashMap/HashSet

题型信号：

- 找两个数相加等于 target。
- 判断是否出现过。
- 统计字符频次。
- 去重。
- 需要 O(1) 查找。

核心记忆：

HashMap 是“我见过什么”。  
遍历到当前元素时，问一句：我之前见过能和它配对的东西吗？

两数之和的思考：

暴力是每个数去后面找另一个数。优化就是把“找”变成 O(1)。所以边遍历边把历史数字放 Map。

面试第一句话：

> 这题的关键是快速判断某个值之前是否出现过，所以我用 HashMap 存已经遍历过的数字和下标。

易错点记忆：

先查再放，避免自己和自己配对。

### 15.3 双指针题：看到“有序、原地、两端、快慢”就想双指针

双指针分三类。

第一类：左右指针

题型信号：

- 数组有序。
- 找两个数。
- 容器面积。
- 回文判断。

记忆：

左右指针像两个人从两头往中间夹。  
如果当前结果太小，就移动能让它变大的那边；太大，就移动能让它变小的那边。

有序两数之和：

> 数组有序，当前和小了说明需要更大的数，所以 left++；当前和大了说明需要更小的数，所以 right--。

盛水容器：

> 面积由短板决定，移动长板没有意义，所以每次移动短板。

第二类：快慢指针

题型信号：

- 原地删除。
- 移动零。
- 链表中点。
- 链表是否有环。

记忆：

fast 负责探索未知，slow 负责维护结果。

移动零：

> fast 扫描所有元素，slow 指向下一个非 0 应该放的位置。

第三类：链表快慢

记忆：

跑步追人。有环的话，跑得快的人一定会追上跑得慢的人。

### 15.4 滑动窗口：看到“连续子串/子数组 + 最长/最短”就想窗口

题型信号：

- 连续子串。
- 连续子数组。
- 最长不重复。
- 最短覆盖。
- 满足某个条件的最长/最短区间。

核心记忆：

窗口像一个可伸缩的框：

- right 负责把更多元素框进来。
- left 负责把不需要的元素丢出去。

最长题：

通常是“右边扩，违规了左边缩”。

最短题：

通常是“右边扩到满足条件，再左边缩到不能再缩”。

最长无重复子串：

> 窗口里不能有重复字符。right 遇到重复字符时，left 跳到上次重复位置后面。

最小覆盖子串：

> right 先扩到字符数量满足 t，再移动 left 尝试缩短答案。

易错点：

窗口题最怕 left 往回退。所有更新 left 的地方都要保证 left 只向右走。

### 15.5 栈题：看到“最近匹配、括号、下一个更大”就想栈

普通栈题信号：

- 括号匹配。
- 路径简化。
- 表达式。
- 最近的未匹配元素。

记忆：

栈是“后来的先处理”。括号匹配就是最后出现的左括号要最先匹配。

有效括号：

> 左括号入栈，右括号必须匹配栈顶。最后栈空才合法。

单调栈题信号：

- 右边第一个更大。
- 右边第一个更小。
- 每日温度。
- 柱状图、接雨水相关。

记忆：

单调栈是“排队等答案”。  
栈里放还没找到答案的人。当前元素一来，如果能让栈顶找到答案，就弹出结算。

每日温度：

> 栈里存还没等到升温的日期。当前温度更高时，栈顶日期就知道还要等几天。

易错点：

栈里经常存下标，不存值。因为答案通常要计算距离或回填数组。

### 15.6 排序题：快排记“分地盘”，归并记“先拆后合”

快排记忆：

选一个 pivot，它像分界线。partition 后，左边都不大于它，右边都大于它。pivot 的位置就确定了，再递归左右。

面试第一句话：

> 快排核心是 partition，每次确定一个基准值的最终位置，再递归处理左右区间。

快排易错：

- partition 不要扫到 pivot 自己。
- 最后要把 pivot 换到 less 位置。
- 最坏 O(n²)，可以随机 pivot 优化。

归并记忆：

归并像整理两叠已经排好序的牌。先把大数组拆成单张牌，再两两合并。

面试第一句话：

> 归并排序是分治，先递归拆分，再合并两个有序区间。

怎么区分快排和归并：

- 快排：先分区，后递归；原地，不稳定。
- 归并：先递归，后合并；需要额外数组，稳定。

第 K 大：

不要完整排序。第 K 大只关心一个位置，用快排 partition 每次排除一半。

记忆：

第 K 大 = 升序下标 `n - k`。

### 15.7 二叉树题：先问“当前节点要返回什么”

树题不要背代码，先想递归定义。

三种常见返回值：

1. 返回节点：剪枝、删除节点、最近公共祖先。
2. 返回数字：深度、高度、路径和。
3. 返回 boolean：是否对称、是否平衡、是否存在路径。

遍历顺序怎么选：

- 先用当前节点再处理子树：前序。
- 要按大小顺序：中序，通常是二叉搜索树。
- 当前节点依赖子树结果：后序。
- 按层：BFS。

二叉树剪枝为什么后序：

> 当前节点能不能删，取决于左右子树剪完后是不是为空，所以必须先处理左右子树。

最大深度：

> 当前树的最大深度就是左右子树最大深度的较大值 + 1。

层序遍历：

> 队列里每一轮的 size 就是当前层节点数，处理完这 size 个节点就进入下一层。

最近公共祖先：

> 左右子树都找到目标，当前节点就是公共祖先；只一边找到，就把那边结果往上返回。

### 15.8 DFS/BFS：一个往深挖，一个按层扩

DFS 记忆：

像走迷宫，一条路走到底，走不通再回退。

题型信号：

- 岛屿数量。
- 连通块。
- 路径搜索。
- 树的递归。

岛屿数量：

> 遇到一块陆地，答案 +1，然后 DFS 把和它连着的陆地全淹掉，避免重复计数。

BFS 记忆：

像水波纹一层层扩散。最适合求最短步数。

题型信号：

- 最短路径。
- 最少步数。
- 层序遍历。
- 从一个点向四周扩散。

BFS 易错：

- visited 入队时就标记，不要出队才标记，否则可能重复入队。
- 每层开始记录 size，用于 step++。

### 15.9 动态规划：先别慌，按四句话走

DP 题看起来吓人，但面试基础 DP 可以按四句话：

1. dp[i] 表示什么？
2. dp[i] 从哪里来？
3. 初始值是什么？
4. 遍历顺序是什么？

爬楼梯：

- dp[i]：爬到第 i 阶的方法数。
- 来源：从 i-1 走一步，或从 i-2 走两步。
- 转移：`dp[i] = dp[i-1] + dp[i-2]`。

最大子数组和：

- cur：以当前位置结尾的最大连续和。
- 要么接上前面，要么从当前重新开始。
- 转移：`cur = max(nums[i], cur + nums[i])`。

LIS：

- dp[i]：以 nums[i] 结尾的最长递增子序列长度。
- 看前面所有比 nums[i] 小的 nums[j]。
- 转移：`dp[i] = max(dp[i], dp[j] + 1)`。

01 背包：

- dp[c]：容量为 c 时的最大价值。
- 每个物品只能选一次，所以容量倒序遍历。

记忆点：

> 01 背包倒序，完全背包正序。倒序是为了防止同一个物品被重复用。

### 15.10 面试里算法不会时怎么救

不要沉默。按这个顺序救：

1. 先说暴力解。
2. 分析瓶颈。
3. 尝试套题型。
4. 写出部分正确代码。

例子：

> 我先给一个暴力思路，两层循环可以解决，但复杂度是 O(n²)。这个题看起来是在找之前是否出现过某个值，所以可以用 HashMap 把查找降到 O(1)。

如果真的不会最优：

> 我现在能先写出一个正确的暴力版本，然后再看能不能用哈希或双指针优化。面试里我会优先保证正确性。

这句话比硬编一个错的高级算法更好。

### 15.11 算法逐题记忆钩子总表

这张表是把第 8 章的代码压缩成“面试时怎么想到”。每道题至少背住第一句话和记忆点。

| 题目 | 看到题怎么想 | 面试第一句话 | 易错记忆 |
| --- | --- | --- | --- |
| 反转字符串 | 原地、左右交换。 | 这题用左右双指针，从两端向中间交换。 | `left < right`，不需要新数组。 |
| 反转单词 | 反转的是单词顺序，不是字符。 | 先去首尾空格，再按连续空格切分，倒序拼接。 | 用 `split("\\s+")`。 |
| 最长无重复子串 | 连续子串 + 最长 + 不重复。 | 用滑动窗口，右指针扩张，重复时左边界跳过上次位置。 | left 只能右移，不能倒退。 |
| 有效括号 | 最近匹配，后进先出。 | 左括号入栈期望的右括号，右括号必须匹配栈顶。 | 最后栈必须为空。 |
| 字符串相加 | 大数不能转 int。 | 从末尾模拟竖式加法，用 carry 保存进位。 | 循环条件带 `carry != 0`。 |
| 最小覆盖子串 | 连续子串 + 最短 + 覆盖字符。 | 右指针扩到满足条件，左指针收缩更新最短答案。 | valid 是满足的字符种类数。 |
| 两数之和 | 找配对值。 | 遍历当前数时，用 HashMap 查之前是否出现过 target - x。 | 先查再放。 |
| 有序两数之和 | 有序数组找两数。 | 左右指针，和小左移，和大右移。 | 只有有序才能这样。 |
| 三数之和 | 三元组去重。 | 排序后固定一个数，剩下两个数双指针。 | 固定数和左右指针都要去重。 |
| 移动零 | 原地、保持顺序。 | fast 扫描，slow 记录下一个非零写入位置。 | 不能排序。 |
| 删除有序数组重复项 | 有序 + 原地去重。 | slow 写新值，fast 扫描，和前一个不同才写。 | 空数组；slow 初始为 1。 |
| 合并有序数组 | nums1 后面有空位。 | 从后往前填，避免覆盖 nums1 有效元素。 | 循环只要 `j >= 0`。 |
| 盛最多水容器 | 两端选柱子最大面积。 | 面积由短板决定，每次移动短板。 | 不是移动长板。 |
| 合并区间 | 区间重叠。 | 先按左端点排序，维护当前区间，能合并就扩右边界。 | 最后 cur 别忘加。 |
| 冒泡排序 | 基础相邻交换。 | 每轮把未排序部分最大值冒到最后。 | 内层边界减 i。 |
| 快速排序 | 分治 + partition。 | partition 确定 pivot 最终位置，再递归左右。 | partition 不扫 pivot 自己。 |
| 随机快排 | 避免有序退化。 | partition 前随机选 pivot 换到末尾。 | 最坏仍可能 O(n²)。 |
| 归并排序 | 先拆后合。 | 递归拆分，合并两个有序区间。 | merge 后拷回原数组。 |
| 第 K 大 | 不必完整排序。 | 第 K 大等于升序下标 n-k，用 partition 只找一边。 | 不是 k-1。 |
| 二叉树剪枝 | 删除依赖子树结果。 | 后序遍历，先剪左右，再判断当前节点。 | 不能先删当前。 |
| 删除目标叶子 | 删除后可能产生新叶子。 | 还是后序，左右处理完再判断当前是否 target 叶子。 | 新产生的叶子也要删。 |
| 最大深度 | 树高。 | 当前深度等于左右最大深度 + 1。 | 空树是 0。 |
| 对称二叉树 | 镜像比较。 | 左树的左对右树的右，左树的右对右树的左。 | 不是同方向比。 |
| 层序遍历 | 按层。 | BFS 队列，每层开始固定 size。 | size 不能动态变。 |
| 最近公共祖先 | 两边找目标。 | 左右都找到当前就是祖先，只一边找到就向上返回那边。 | 普通二叉树不能用大小关系。 |
| 每日温度 | 右边第一个更大。 | 单调栈存未找到升温的下标，当前温度更高就结算。 | 栈存下标，while 不是 if。 |
| 下一个更大元素 | 右边第一个更大。 | 当前元素让栈顶找到答案就弹出。 | 默认答案 -1。 |
| 循环下一个更大 | 数组能绕一圈。 | 遍历两遍，用 i%n 模拟循环，第二遍不入栈。 | 防止重复入栈。 |
| 接雨水 | 每格水量由左右最高较小值决定。 | 双指针维护 leftMax/rightMax，移动较小侧。 | 先更新 max 再算水。 |
| 反转链表 | 指针反向。 | prev、cur、next 三指针，逐个反转 next。 | 先保存 next，最后返回 prev。 |
| 环形链表 | 快慢指针追及。 | slow 一步 fast 两步，有环一定相遇。 | while 判断 fast 和 fast.next。 |
| 删除倒数 N | 倒数位置。 | dummy + 快慢指针，fast 先走 n 步。 | 删除头节点靠 dummy。 |
| 合并有序链表 | 两个有序流合并。 | dummy + cur，每次接较小节点。 | 最后接剩余链表。 |
| 岛屿数量 | 连通块计数。 | 遇到 1 答案加一，DFS 把整座岛淹掉。 | 标记访问，避免重复数。 |
| 矩阵 BFS | 最短步数。 | 队列按层扩散，step 每层加一。 | 入队时就 visited。 |
| 爬楼梯 | 到当前来自前一步或前两步。 | dp[i] = dp[i-1] + dp[i-2]。 | n<=2 直接返回 n。 |
| 最大子数组和 | 连续子数组最大和。 | 当前位置要么接前面，要么从自己重新开始。 | 全负数也要正确。 |
| LIS | 以 i 结尾的最长递增。 | dp[i] 看前面所有比 nums[i] 小的位置。 | 是子序列，不要求连续。 |
| 01 背包 | 每个物品只能选一次。 | dp[c] 表示容量 c 最大价值，容量倒序遍历。 | 倒序防止重复选同一物品。 |

## 16. 把项目讲出理解：别像背简历

项目题的核心不是“我用了什么技术”，而是“我为什么这么做”。

### 16.1 项目回答四段式

每个项目都按这四段：

1. 背景：为什么要做。
2. 难点：哪里容易出问题。
3. 方案：你怎么解决。
4. 结果：带来了什么效果。

商品库例子：

> 背景是酒店检索需要稳定的数据供给。难点是维度多、数据量大、多进程并行容易有残留文件，下游又不能消费脏数据。我的方案是任务状态记录 + 后置完整性校验 + 失败文件清理 + 告警。结果是避免了机器异常时半成品文件被下游误读。

### 16.2 JVM 调优不要讲成“调参数”

错误讲法：

> 我调了堆参数和 GC 参数，后来好了。

正确讲法：

> 我先确认现象是 Full GC 频繁和 OOM，再通过 GC 日志看老年代回收效果，通过堆 dump 看大对象和引用链。发现主要是中间集合暂存太多，对象生命周期过长。优化时先改代码，减少全量暂存和对象体积，再配合 JVM 参数调整。参数是辅助，减少无效对象和缩短对象生命周期才是根本。

记忆点：

> JVM 调优顺序：先定位对象，再改代码，最后调参数。

### 16.3 线程池项目不要讲成“开 32 个线程”

错误讲法：

> 我开了 32 个线程，所以速度快了。

正确讲法：

> 这个任务主要是远程调用，偏 IO 型，适合适当提高并发。但并发不能无限放大，所以我用固定线程池控制本系统并发，再用每环境独立令牌桶控制对下游的 QPS，并设置超时和异常处理。这样既提高吞吐，又保护下游。

记忆点：

> 线程池提吞吐，限流保下游，超时防拖死。

### 16.4 红包匹配优化不要讲成“删数据”

错误讲法：

> 我们把 12 亿数据删到 1400 万。

正确讲法：

> 不是删除有效数据，而是把原来大量冗余的明细展开改成规则表达。默认红包不需要展开到每个明细，只有差异化圈选才保留明细。这样减少的是无效存储和计算，不影响业务覆盖。

记忆点：

> 优化不是少算业务，而是少算冗余。

### 16.5 Data Agent 不要讲成“AI 很厉害”

错误讲法：

> 我做了一个 AI Agent，可以自动分析。

正确讲法：

> 我更关注的是 AI 后端工程化：模型不能直接碰真实系统，所以我做了工具白名单、SQL 只读限制、RAG 上下文、执行轨迹和质量反馈。重点是让模型可控、可追踪，而不是自由发挥。

记忆点：

> AI 项目讲“可控、可追踪、有边界”，不要讲玄学。

## 17. 算法超细讲解版：从题目一步步想到代码

这一章只讲最容易在华为 OD 技术面和机试里出现的核心题。你先把这些讲明白，再去背第 8 章的完整模板。

看每道题时记住三件事：

1. 这题的“关键词”是什么。
2. 我为什么选择这个算法。
3. 代码里的每个变量到底代表什么。

### 17.1 最长无重复子串：滑动窗口到底怎么滑

题目意思：

给你一个字符串，找一个“连续”的子串，要求里面没有重复字符，并返回最长长度。

比如：

```text
s = "abcabcbb"
答案是 3，因为 "abc" 最长。
```

关键词：

- 连续子串。
- 最长。
- 不重复。

看到这三个词，就先想滑动窗口。

为什么不用暴力：

暴力做法是枚举每个起点和终点，再判断中间有没有重复。这样至少 O(n²)，还要判断重复，面试里不优雅。

滑动窗口怎么理解：

你可以把窗口想成一个框，框住当前正在尝试的无重复子串。

```text
a b c a b c b b
^
left/right 一开始都在左边
```

right 负责向右扩，把字符放进窗口。  
left 负责在窗口不合法时往右收。

最关键的问题：

遇到重复字符，left 移到哪里？

例子：`abba`

```text
下标: 0 1 2 3
字符: a b b a
```

走一遍：

1. right=0，看到 a，窗口是 `a`，ans=1。
2. right=1，看到 b，窗口是 `ab`，ans=2。
3. right=2，又看到 b。上一个 b 在下标 1，所以 left 要跳到 2，窗口变成 `b`。
4. right=3，看到 a。a 上次在下标 0，但 0 已经在窗口外面了，所以 left 不能回到 1，left 还是 2。

为什么要判断 `last.get(c) >= left`：

因为只处理“窗口里面的重复”。如果重复字符上次出现的位置已经在 left 左边，说明它不影响当前窗口。

变量含义：

```text
left：当前无重复窗口的左边界。
right：当前遍历到的位置，也是窗口右边界。
last：每个字符上一次出现的位置。
ans：目前见过的最大窗口长度。
```

代码：

```java
public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> last = new HashMap<>();
    int left = 0;
    int ans = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (last.containsKey(c) && last.get(c) >= left) {
            left = last.get(c) + 1;
        }
        last.put(c, right);
        ans = Math.max(ans, right - left + 1);
    }
    return ans;
}
```

你面试时这样讲：

> 我用滑动窗口维护一个不包含重复字符的区间。right 每次向右扩展，如果当前字符在窗口里出现过，就把 left 跳到它上一次出现位置的后一位。每个字符最多被 right 扫一次，left 也只向右走，所以时间复杂度是 O(n)。

最容易错：

- left 不能往回退。
- 长度是 `right - left + 1`。

### 17.2 三数之和：为什么要排序，怎么去重

题目意思：

找数组里所有不重复的三元组，让三个数相加等于 0。

比如：

```text
nums = [-1,0,1,2,-1,-4]
答案 = [[-1,-1,2],[-1,0,1]]
```

关键词：

- 三个数。
- 和为 0。
- 不重复。

为什么先排序：

排序有两个好处：

1. 可以用双指针，不用三层循环。
2. 重复数字挨在一起，方便去重。

排序后：

```text
[-4, -1, -1, 0, 1, 2]
```

思路拆开：

先固定第一个数 `nums[i]`。  
问题变成：在 i 后面找两个数，让它们的和等于 `-nums[i]`。

这就是“有序数组两数之和”，可以用 left/right 双指针。

为什么 sum 小了 left++：

数组已经有序。sum 小了，需要更大的数，所以 left 右移。  
sum 大了，需要更小的数，所以 right 左移。

去重为什么这么写：

固定 i 的去重：

```java
if (i > 0 && nums[i] == nums[i - 1]) continue;
```

意思是：如果当前固定数和上一个固定数一样，那么它会产生重复三元组，跳过。

命中答案后的 left/right 去重：

```java
while (left < right && nums[left] == nums[left + 1]) left++;
while (left < right && nums[right] == nums[right - 1]) right--;
```

意思是：当前这个 left/right 值已经用过了，后面相同的值会产生重复答案，要跳过。

变量含义：

```text
i：固定的第一个数。
left：第二个数，从 i+1 开始。
right：第三个数，从数组末尾开始。
sum：三数之和。
ans：答案列表。
```

代码：

```java
public List<List<Integer>> threeSum(int[] nums) {
    Arrays.sort(nums);
    List<List<Integer>> ans = new ArrayList<>();
    for (int i = 0; i < nums.length - 2; i++) {
        if (i > 0 && nums[i] == nums[i - 1]) {
            continue;
        }
        int left = i + 1;
        int right = nums.length - 1;
        while (left < right) {
            int sum = nums[i] + nums[left] + nums[right];
            if (sum == 0) {
                ans.add(Arrays.asList(nums[i], nums[left], nums[right]));
                while (left < right && nums[left] == nums[left + 1]) {
                    left++;
                }
                while (left < right && nums[right] == nums[right - 1]) {
                    right--;
                }
                left++;
                right--;
            } else if (sum < 0) {
                left++;
            } else {
                right--;
            }
        }
    }
    return ans;
}
```

你面试时这样讲：

> 我先排序，然后固定一个数，把三数之和转成后半部分的两数之和。因为数组有序，所以可以用双指针根据 sum 大小移动 left 或 right。为了避免重复，固定数要去重，找到答案后 left 和 right 也要跳过重复值。

最容易错：

- 忘记排序。
- 只给 i 去重，忘记 left/right 去重。
- 找到答案后忘记 `left++` 和 `right--`。

### 17.3 快速排序：partition 到底在干嘛

题目意思：

把数组排成升序。

快排核心不是递归，而是 partition。

partition 的目标：

选一个基准值 pivot，把数组分成三块：

```text
[ <= pivot 的区域 ][ > pivot 的区域 ][ pivot ]
```

最后把 pivot 放到中间正确位置。

用例子走一遍：

```text
nums = [3, 1, 5, 2]
pivot = 2
left = 0, right = 3
less = 0
```

`less` 的含义：

> less 指向“小于等于 pivot 区域”的下一个可写位置。

遍历 i：

```text
i=0, nums[i]=3 > 2，不动
数组: [3, 1, 5, 2], less=0

i=1, nums[i]=1 <= 2，把 1 换到 less 位置
数组: [1, 3, 5, 2], less=1

i=2, nums[i]=5 > 2，不动
数组: [1, 3, 5, 2], less=1
```

遍历结束，把 pivot 和 less 位置交换：

```text
[1, 2, 5, 3]
    ^
  pivot 已经在最终位置
```

接下来递归排左边 `[1]` 和右边 `[5,3]`。

代码：

```java
public void quickSort(int[] nums) {
    if (nums == null || nums.length < 2) {
        return;
    }
    quickSort(nums, 0, nums.length - 1);
}

private void quickSort(int[] nums, int left, int right) {
    if (left >= right) {
        return;
    }
    int pivot = partition(nums, left, right);
    quickSort(nums, left, pivot - 1);
    quickSort(nums, pivot + 1, right);
}

private int partition(int[] nums, int left, int right) {
    int pivot = nums[right];
    int less = left;
    for (int i = left; i < right; i++) {
        if (nums[i] <= pivot) {
            swap(nums, less, i);
            less++;
        }
    }
    swap(nums, less, right);
    return less;
}

private void swap(int[] nums, int i, int j) {
    int temp = nums[i];
    nums[i] = nums[j];
    nums[j] = temp;
}
```

为什么快排平均 O(n log n)：

如果每次 pivot 都把数组差不多分成两半，递归层数是 log n，每层 partition 总共扫 n 个元素，所以是 O(n log n)。

为什么最坏 O(n²)：

如果每次 pivot 都选到最大或最小，数组每次只少一个元素，递归层数变成 n，总复杂度退化成 O(n²)。

怎么优化：

随机选 pivot，降低每次都选到极端值的概率。

面试时这样讲：

> 快排的核心是 partition。每次选一个 pivot，把小于等于 pivot 的元素放左边，大于 pivot 的放右边，这样 pivot 的最终位置就确定了。然后递归处理左右区间。平均复杂度 O(n log n)，但如果 pivot 选得很差会退化到 O(n²)，所以可以随机 pivot 优化。

最容易错：

- for 循环条件是 `i < right`，不要扫 pivot。
- partition 最后必须把 pivot 换到 less。
- 递归边界是 `left >= right`。

### 17.4 归并排序：为什么它稳定

题目意思：

实现排序。

归并排序的核心：

先拆，再合。

```text
[5,2,3,1]
拆成 [5,2] 和 [3,1]
再拆成 [5] [2] [3] [1]
合并 [2,5] 和 [1,3]
最后合并 [1,2,3,5]
```

为什么合并两个有序数组容易：

两个数组都已经有序，只要比较两个数组当前最小值，把更小的放进临时数组。

变量含义：

```text
i：左半部分当前指针。
j：右半部分当前指针。
k：临时数组当前写入位置。
temp：临时数组。
```

为什么稳定：

合并时如果 `nums[i] <= nums[j]`，相等时优先放左边元素。这样原来在前面的相等元素仍然在前面。

代码：

```java
public void mergeSort(int[] nums) {
    int[] temp = new int[nums.length];
    mergeSort(nums, 0, nums.length - 1, temp);
}

private void mergeSort(int[] nums, int left, int right, int[] temp) {
    if (left >= right) {
        return;
    }
    int mid = left + (right - left) / 2;
    mergeSort(nums, left, mid, temp);
    mergeSort(nums, mid + 1, right, temp);
    merge(nums, left, mid, right, temp);
}

private void merge(int[] nums, int left, int mid, int right, int[] temp) {
    int i = left;
    int j = mid + 1;
    int k = left;
    while (i <= mid && j <= right) {
        if (nums[i] <= nums[j]) {
            temp[k++] = nums[i++];
        } else {
            temp[k++] = nums[j++];
        }
    }
    while (i <= mid) {
        temp[k++] = nums[i++];
    }
    while (j <= right) {
        temp[k++] = nums[j++];
    }
    for (int p = left; p <= right; p++) {
        nums[p] = temp[p];
    }
}
```

面试时这样讲：

> 归并排序是分治思想。先把数组递归拆到单个元素，再合并两个有序区间。合并时用双指针，每层合并总共 O(n)，递归层数是 log n，所以时间复杂度是 O(n log n)。它需要 O(n) 临时数组，但排序是稳定的。

最容易错：

- merge 后忘记把 temp 拷回 nums。
- mid 写成 `(left + right) / 2` 一般也能跑，但更推荐 `left + (right - left) / 2`。
- 递归边界是 `left >= right`。

### 17.5 二叉树剪枝：为什么一定是后序

题目意思：

给一棵只有 0 和 1 的树，删除所有“不包含 1”的子树。

关键理解：

一个节点该不该删，不只看它自己，还要看它的左右子树有没有 1。

例子：

```text
      1
     / \
    0   0
   / \
  0   1
```

左边的 0 不能删，因为它下面有一个 1。  
右边的 0 要删，因为它自己是 0，下面也没有 1。

为什么不能前序：

如果你先看当前节点，看到 0 就删，会误删“下面其实有 1”的子树。

所以必须后序：

```text
先处理左子树
再处理右子树
最后判断当前节点
```

返回值含义：

`pruneTree(root)` 返回“剪完之后的新 root”。  
如果这棵子树应该被删，就返回 null。

代码：

```java
public TreeNode pruneTree(TreeNode root) {
    if (root == null) {
        return null;
    }
    root.left = pruneTree(root.left);
    root.right = pruneTree(root.right);
    if (root.val == 0 && root.left == null && root.right == null) {
        return null;
    }
    return root;
}
```

面试时这样讲：

> 这题必须用后序遍历，因为当前节点是否删除依赖左右子树剪完后的结果。我让递归函数返回剪枝后的根节点，先递归处理左右子树，再判断当前节点是不是值为 0 且左右都为空，如果是就返回 null。

最容易错：

- 先判断当前节点。
- 忘记把递归结果重新赋给 `root.left` 和 `root.right`。

### 17.6 二叉树层序遍历：size 为什么要先固定

题目意思：

按层输出二叉树。

比如：

```text
      3
     / \
    9  20
       / \
      15  7

输出:
[[3], [9,20], [15,7]]
```

为什么用队列：

层序遍历是“先进先出”。先看到的节点先处理，所以用队列。

为什么要记录 size：

队列里一开始放当前层节点。处理当前层时，会把下一层节点加入队列。  
如果你直接用 `queue.size()` 作为循环条件，它会变，因为你在循环里不断加新节点。

所以每层开始先固定：

```java
int size = queue.size();
```

这表示“当前层有多少个节点”。

代码：

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> ans = new ArrayList<>();
    if (root == null) {
        return ans;
    }
    Queue<TreeNode> queue = new ArrayDeque<>();
    queue.offer(root);
    while (!queue.isEmpty()) {
        int size = queue.size();
        List<Integer> level = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            level.add(node.val);
            if (node.left != null) {
                queue.offer(node.left);
            }
            if (node.right != null) {
                queue.offer(node.right);
            }
        }
        ans.add(level);
    }
    return ans;
}
```

面试时这样讲：

> 层序遍历用 BFS 队列。每一轮开始时，队列里的元素数量就是当前层节点数，我先固定 size，然后只处理这 size 个节点，同时把它们的孩子加入队列。处理完这一轮，就进入下一层。

最容易错：

- root 为空没处理。
- size 没固定。

### 17.7 每日温度：单调栈为什么存下标

题目意思：

对每一天，求还要等几天才会出现更高温度。

比如：

```text
temperatures = [73,74,75,71,69,72,76,73]
答案 = [1,1,4,2,1,1,0,0]
```

关键词：

- 右边第一个更大。
- 求距离。

看到“右边第一个更大”，想单调栈。

栈里放什么：

放下标，不放温度。

为什么：

答案要的是“几天后”，也就是下标差 `i - prev`。如果只存温度，就算不出距离。

栈里维护什么：

栈里放的是还没找到更高温度的日期下标。  
这些下标对应的温度从栈底到栈顶大致是递减的。

手动走一点：

```text
[73,74,75,71,69,72,76,73]

i=0, 73 入栈: [0]
i=1, 74 > 73，0 出栈，ans[0]=1，1 入栈
i=2, 75 > 74，1 出栈，ans[1]=1，2 入栈
i=3, 71 不大于 75，3 入栈
i=4, 69 不大于 71，4 入栈
i=5, 72 > 69，4 出栈 ans[4]=1
     72 > 71，3 出栈 ans[3]=2
     72 不大于 75，5 入栈
```

代码：

```java
public int[] dailyTemperatures(int[] temperatures) {
    int[] ans = new int[temperatures.length];
    Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < temperatures.length; i++) {
        while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()]) {
            int prev = stack.pop();
            ans[prev] = i - prev;
        }
        stack.push(i);
    }
    return ans;
}
```

面试时这样讲：

> 这题是在找每个元素右边第一个更大的元素，而且要算距离，所以我用单调栈存下标。遍历到当前温度时，如果它比栈顶温度高，说明栈顶那天等到了更高温度，就弹出并计算下标差。每个下标最多入栈出栈一次，所以是 O(n)。

最容易错：

- 用 if 而不是 while。当前温度可能让多个历史日期同时结算。
- 栈里存温度而不是下标。

### 17.8 反转链表：三根指针到底怎么动

题目意思：

```text
1 -> 2 -> 3 -> null
变成
3 -> 2 -> 1 -> null
```

关键问题：

如果你直接写：

```java
cur.next = prev;
```

那原来 cur 后面的链表就断了，找不到了。

所以必须先保存 next：

```java
ListNode next = cur.next;
```

三个变量含义：

```text
prev：已经反转好的前半段头节点。
cur：当前正在处理的节点。
next：cur 原来的下一个节点，防止断链。
```

走一遍：

初始：

```text
prev = null
cur = 1 -> 2 -> 3
```

处理 1：

```text
next = 2
1.next = null
prev = 1
cur = 2
```

处理 2：

```text
next = 3
2.next = 1
prev = 2
cur = 3
```

处理 3：

```text
next = null
3.next = 2
prev = 3
cur = null
```

结束返回 prev。

代码：

```java
public ListNode reverseList(ListNode head) {
    ListNode prev = null;
    ListNode cur = head;
    while (cur != null) {
        ListNode next = cur.next;
        cur.next = prev;
        prev = cur;
        cur = next;
    }
    return prev;
}
```

面试时这样讲：

> 我用 prev、cur、next 三个指针。cur 是当前节点，prev 是已经反转好的前半部分。每次先保存 cur.next，避免断链，然后把 cur.next 指向 prev，再整体向后移动。最后 cur 为空时，prev 就是新头节点。

最容易错：

- 忘记保存 next。
- 返回 cur，而不是 prev。

### 17.9 岛屿数量：为什么 DFS 后要改成 0

题目意思：

二维网格里，`1` 表示陆地，`0` 表示水。上下左右相连的陆地算一座岛。求岛屿数量。

例子：

```text
1 1 0
1 0 0
0 0 1

有 2 座岛
```

思路：

遍历每个格子。遇到一个 `1`，说明发现了一座新岛，答案加 1。  
然后从这个格子开始 DFS，把和它连着的所有 `1` 都标记为 `0`。

为什么要改成 0：

表示这块陆地已经访问过了。否则后面遍历到同一座岛的其它陆地时，会重复计数。

DFS 在做什么：

从当前位置向四个方向扩散：

```text
上、下、左、右
```

遇到越界、水、访问过，就返回。

代码：

```java
public int numIslands(char[][] grid) {
    int m = grid.length;
    int n = grid[0].length;
    int ans = 0;
    for (int i = 0; i < m; i++) {
        for (int j = 0; j < n; j++) {
            if (grid[i][j] == '1') {
                ans++;
                dfs(grid, i, j);
            }
        }
    }
    return ans;
}

private void dfs(char[][] grid, int i, int j) {
    if (i < 0 || i >= grid.length || j < 0 || j >= grid[0].length || grid[i][j] != '1') {
        return;
    }
    grid[i][j] = '0';
    dfs(grid, i + 1, j);
    dfs(grid, i - 1, j);
    dfs(grid, i, j + 1);
    dfs(grid, i, j - 1);
}
```

面试时这样讲：

> 我遍历整个矩阵，每遇到一个未访问的陆地 1，就说明发现一座新岛，答案加一。然后用 DFS 把这座岛连通的所有陆地都标记成 0，避免后续重复计数。

最容易错：

- 忘记标记访问，导致重复计数或死递归。
- 只搜两个方向，漏掉上下左右。

### 17.10 最大子数组和：为什么负数前缀要丢掉

题目意思：

找一个连续子数组，让它的和最大。

例子：

```text
nums = [-2,1,-3,4,-1,2,1,-5,4]
答案 = 6，对应 [4,-1,2,1]
```

核心问题：

走到当前位置时，要不要接上前面的连续和？

如果前面的和是负数，它只会拖累当前数，不如从当前数重新开始。

变量含义：

```text
cur：以当前位置结尾的最大连续子数组和。
ans：全局最大连续子数组和。
```

为什么 `cur = max(nums[i], cur + nums[i])`：

以 i 结尾的子数组只有两种选择：

1. 只要 nums[i]，从这里重新开始。
2. 接在前面的子数组后面，也就是 cur + nums[i]。

取更大那个。

代码：

```java
public int maxSubArray(int[] nums) {
    int cur = nums[0];
    int ans = nums[0];
    for (int i = 1; i < nums.length; i++) {
        cur = Math.max(nums[i], cur + nums[i]);
        ans = Math.max(ans, cur);
    }
    return ans;
}
```

面试时这样讲：

> 我定义 cur 表示以当前位置结尾的最大子数组和。对于 nums[i]，要么接在前面的连续数组后面，要么从自己重新开始，所以转移是 max(nums[i], cur + nums[i])。ans 记录全局最大值。

最容易错：

- ans 初始化为 0。这样全负数会错。必须初始化为 nums[0]。

### 17.11 01 背包：为什么容量要倒序

题目意思：

有 n 个物品，每个物品有重量和价值，每个物品只能选一次。背包容量有限，求最大价值。

核心变量：

```text
dp[c]：容量为 c 时，当前能得到的最大价值。
```

对一个物品：

如果选它：

```text
价值 = dp[c - weight] + value
```

如果不选它：

```text
价值 = dp[c]
```

所以：

```java
dp[c] = Math.max(dp[c], dp[c - weight] + value);
```

为什么容量要倒序：

因为每个物品只能选一次。

如果正序遍历：

```text
dp[2] 更新后，dp[4] 可能立刻用刚更新的 dp[2]
等于同一个物品被选了两次
```

倒序遍历可以保证 `dp[c - weight]` 还是上一轮物品的结果，不会重复使用当前物品。

代码：

```java
public int knapsack(int[] weights, int[] values, int capacity) {
    int[] dp = new int[capacity + 1];
    for (int i = 0; i < weights.length; i++) {
        for (int c = capacity; c >= weights[i]; c--) {
            dp[c] = Math.max(dp[c], dp[c - weights[i]] + values[i]);
        }
    }
    return dp[capacity];
}
```

面试时这样讲：

> 我定义 dp[c] 表示容量为 c 时的最大价值。遍历每个物品时，对于每个容量 c，有选和不选两种情况。因为 01 背包每个物品只能选一次，所以容量必须倒序遍历，避免当前物品被重复使用。

最容易错：

- 容量正序遍历。
- dp 数组长度应该是 `capacity + 1`。

### 17.12 ACM 输入输出：为什么你本地会写，机试却挂

华为 OD 机试经常不是给你方法，而是要你写完整 `Main` 类。

你要特别注意三件事：

1. 输入可能有多行。
2. 数字可能用空格或逗号分隔。
3. 输出格式要完全一致。

最稳模板：

```java
import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            // 按题目解析 parts
        }
    }
}
```

为什么推荐 BufferedReader：

Scanner 写起来简单，但大量输入时可能慢。BufferedReader 更稳。

如果是逗号：

```java
String[] parts = line.split(",");
```

如果是空格：

```java
String[] parts = line.split("\\s+");
```

机试前你至少要手写三遍：

- 读一行数组。
- 读 n + 下一行数组。
- 读 m*n 矩阵。

## 18. 剩余算法逐题讲明白：补齐所有常见模板

第 17 章讲的是最核心、最容易卡的题。这一章把剩余题也讲清楚。完整代码在第 8 章，这里重点讲“为什么”。

### 18.1 反转字符串：为什么是两头换

关键词：

- 原地。
- 反转。
- 字符数组。

你要想到：

反转就是第一个和最后一个换，第二个和倒数第二个换。这个过程天然是左右双指针。

变量：

- `left`：左边还没交换的位置。
- `right`：右边还没交换的位置。

例子：

```text
hello
left=h, right=o -> oellh
left=e, right=l -> olleh
left 到中间，结束
```

面试说法：

> 因为要求原地反转，我用左右指针从两端向中间交换。每个字符最多交换一次，所以时间 O(n)，空间 O(1)。

易错点：

`left < right` 就够了，相等时没必要交换。

### 18.2 反转单词：先切成单词再倒序

关键词：

- 单词顺序反转。
- 多余空格。

你要想到：

反转的单位是“单词”，不是字符。所以先把字符串规整成单词数组，再倒序拼。

为什么用 `trim()`：

去掉首尾空格，避免首尾产生空单词。

为什么用 `split("\\s+")`：

`\\s+` 表示一个或多个空白字符。这样多个空格、tab 都能处理。

例子：

```text
"  hello   world  "
trim 后: "hello   world"
split 后: ["hello", "world"]
倒序拼: "world hello"
```

面试说法：

> 这题要反转单词顺序并处理多余空格。我先 trim 去首尾空格，再用连续空白切分成单词数组，最后倒序拼接。

易错点：

不要用 `split(" ")`。

### 18.3 有效括号：为什么栈最自然

关键词：

- 括号匹配。
- 最近的左括号要最先匹配。

你要想到：

这是后进先出。最后出现的左括号，必须最先被右括号匹配，所以用栈。

为什么推荐“左括号入对应右括号”：

比如遇到 `(`，压入 `)`。后面遇到的右括号只需要和栈顶直接比较，不用再写一堆映射判断。

例子：

```text
"([])"
遇到 (: 期待 )，栈 [)]
遇到 [: 期待 ]，栈 ], )
遇到 ]: 匹配栈顶
遇到 ): 匹配栈顶
栈空，合法
```

面试说法：

> 括号匹配符合后进先出，所以我用栈。遇到左括号就把它期待的右括号入栈，遇到右括号就必须和栈顶一致。最后栈为空才合法。

易错点：

遍历中没报错不代表合法，最后还要看栈是否为空。

### 18.4 字符串相加：把它当小学竖式

关键词：

- 大数字符串。
- 不能转整数。
- 加法。

你要想到：

小学加法从个位开始，也就是字符串末尾开始。

变量：

- `i`：num1 当前位。
- `j`：num2 当前位。
- `carry`：进位。
- `sb`：从低位到高位收集答案，所以最后要 reverse。

例子：

```text
999 + 1
9+1=10，写 0，进 1
9+0+1=10，写 0，进 1
9+0+1=10，写 0，进 1
最后 carry=1，写 1
反转得到 1000
```

面试说法：

> 我从两个字符串末尾开始模拟竖式加法，每次取一位相加并处理 carry。因为结果是从低位往高位追加，最后需要反转。

易错点：

循环条件必须包含 `carry != 0`。

### 18.5 最小覆盖子串：为什么它比最长无重复难

关键词：

- 最短。
- 覆盖 t 的全部字符。
- 连续子串。

你要想到：

还是滑动窗口，但这次不是“窗口不能重复”，而是“窗口要满足 t 的字符数量”。

为什么需要两个 Map：

- `need`：目标要求，t 里每个字符需要几个。
- `window`：当前窗口里每个字符有几个。

`valid` 到底是什么：

`valid` 不是总字符数量。  
它表示“有多少种字符已经达到 need 要求”。

例子：

```text
t = "AABC"
need:
A -> 2
B -> 1
C -> 1

只有当窗口里 A 有 2 个时，A 这一种字符才算 valid。
```

为什么先扩再缩：

你得先让窗口满足条件，才有资格谈“最短”。满足后，再移动 left 尽量缩短。

面试说法：

> 这是最短覆盖问题。我用滑动窗口，right 负责扩张直到窗口包含 t 的所有字符，满足后移动 left 收缩窗口，并在每次满足时更新最短答案。

易错点：

- `valid == need.size()` 才说明所有字符种类都满足。
- 收缩时，如果某字符本来刚好满足，移出去会导致 valid--。

### 18.6 有序数组两数之和：为什么能排除一边

关键词：

- 有序。
- 两个数。
- target。

你要想到：

只要数组有序，就能用左右指针。

为什么 sum 小移动 left：

当前 `nums[left] + nums[right]` 太小，right 已经是右边最大了。想变大，只能让 left 右移。

为什么 sum 大移动 right：

当前和太大，left 已经是左边最小了。想变小，只能让 right 左移。

面试说法：

> 因为数组有序，我用左右指针。当前和小于 target，说明需要更大的数，所以 left++；当前和大于 target，说明需要更小的数，所以 right--。

易错点：

没排序的数组不能直接这么做。

### 18.7 移动零：slow 左边永远是答案区

关键词：

- 原地。
- 保持非零顺序。
- 把 0 放最后。

你要想到：

不要排序。排序会打乱顺序。  
用 fast 扫描，slow 维护答案区。

不变量：

> slow 左边的区域，永远是已经处理好的非零元素。

例子：

```text
[0,1,0,3,12]
slow=0
fast=0 是 0，不动
fast=1 是 1，和 slow 交换 -> [1,0,0,3,12], slow=1
fast=2 是 0，不动
fast=3 是 3，和 slow 交换 -> [1,3,0,0,12], slow=2
fast=4 是 12，交换 -> [1,3,12,0,0]
```

面试说法：

> 我用 slow 指向下一个非零元素应该放的位置，fast 扫描数组。fast 遇到非零就和 slow 交换，并移动 slow。这样 slow 左边始终是保持原相对顺序的非零元素。

易错点：

不要新开数组，题目一般要求原地。

### 18.8 删除有序数组重复项：为什么只和前一个比

关键词：

- 有序。
- 原地去重。

你要想到：

有序意味着重复元素一定挨在一起。所以只需要判断当前元素和前一个元素是否不同。

变量：

- `fast`：扫描位置。
- `slow`：下一个新元素要写入的位置。

例子：

```text
[1,1,2,2,3]
slow=1
fast=1，nums[1]==nums[0]，跳过
fast=2，2 != 1，写到 slow=1 -> [1,2,...], slow=2
fast=3，2 == 2，跳过
fast=4，3 != 2，写到 slow=2 -> [1,2,3]
```

面试说法：

> 因为数组有序，重复元素一定相邻。我用 fast 扫描，slow 表示去重数组的写入位置。当前元素和前一个不同，才写到 slow。

易错点：

slow 初始是 1，因为第一个元素一定保留。

### 18.9 合并两个有序数组：为什么从后往前

关键词：

- nums1 后面有空位。
- 合并到 nums1。
- 有序。

你要想到：

如果从前往后，会覆盖 nums1 还没处理的有效元素。  
从后往前填，就不会覆盖。

变量：

- `i`：nums1 有效部分末尾。
- `j`：nums2 末尾。
- `k`：nums1 总末尾，当前写入位置。

例子：

```text
nums1 = [1,2,3,0,0,0], m=3
nums2 = [2,5,6], n=3

比较 3 和 6，6 放最后
比较 3 和 5，5 放倒数第二
比较 3 和 2，3 放倒数第三
继续...
```

面试说法：

> 我从后往前合并，每次把 nums1 和 nums2 当前较大的数放到 nums1 的末尾。这样不会覆盖 nums1 前面还没处理的有效元素。

易错点：

循环条件是 `j >= 0`。如果 nums2 已经放完，nums1 剩下的本来就在正确位置。

### 18.10 盛最多水的容器：为什么移动短板

关键词：

- 两根线。
- 最大面积。
- 左右边界。

你要想到：

面积 = 宽度 * 较短高度。

为什么移动短板：

宽度每次都会变小。  
如果你移动长板，短板没变，宽度还变小，面积不可能更好。  
只有移动短板，才可能遇到更高的短板，弥补宽度减少。

面试说法：

> 容器面积由短板决定。左右指针从两端开始，每次计算面积后移动较短的一边，因为移动长边不会提高短板高度，只会让宽度变小。

易错点：

不是谁大移动谁，而是谁小移动谁。

### 18.11 合并区间：排序后重叠区间会挨着

关键词：

- 区间。
- 合并重叠。

你要想到：

先按左端点排序。排序后，能和当前区间重叠的区间一定会连续出现。

变量：

- `cur`：当前正在合并的区间。
- `ans`：已经确定不会再扩展的区间。

判断能不能合并：

```text
next.start <= cur.end
```

能合并时：

```text
cur.end = max(cur.end, next.end)
```

面试说法：

> 我先按左端点排序，然后维护当前区间 cur。遍历下一个区间时，如果它的左端点小于等于 cur 的右端点，说明有重叠，就更新右端点；否则 cur 已经结束，加入答案。

易错点：

循环结束后，最后一个 cur 还没加入答案，别忘了 add。

### 18.12 第 K 大元素：为什么不必完整排序

关键词：

- 第 K 大。
- 只要一个元素。

你要想到：

不需要排完整个数组。只要找到它最终排序后的位置。

升序下标：

```text
第 1 大 -> 下标 n-1
第 2 大 -> 下标 n-2
第 K 大 -> 下标 n-k
```

为什么用 partition：

partition 一次可以确定 pivot 的最终下标。  
如果 pivot 下标正好是 `n-k`，直接返回。  
如果小了，就去右边找；大了，就去左边找。

面试说法：

> 第 K 大等价于升序排序后下标 n-k 的元素。我用快排 partition，每次确定一个 pivot 的最终位置，然后只递归或循环进入目标下标所在的一边，不需要完整排序。

易错点：

第 K 大不是下标 `k - 1`。

### 18.13 删除目标叶子节点：为什么删除后还要继续判断父节点

关键词：

- 删除叶子。
- 删除后可能产生新叶子。

你要想到：

还是后序。因为一个父节点原来不是叶子，但它的孩子被删后，可能变成新的叶子。

例子：

```text
    1
   /
  2
 /
2
target=2
```

先删最下面的 2。  
上面的 2 变成叶子，也要删。

面试说法：

> 这题也要后序遍历。先处理左右子树，删除所有目标叶子后，再判断当前节点是否变成了新的目标叶子。

易错点：

如果前序判断，会漏掉删除后新产生的叶子。

### 18.14 最大深度：递归函数的意义要清楚

关键词：

- 树的深度。

你要想到：

定义函数：`maxDepth(root)` 返回以 root 为根的树的最大深度。

那答案自然是：

```text
max(左子树深度, 右子树深度) + 1
```

面试说法：

> 我定义递归函数返回当前子树的最大深度。空树深度是 0，非空树深度等于左右子树最大深度的较大值再加当前节点这一层。

易错点：

单节点深度是 1，不是 0。

### 18.15 对称二叉树：不是左右一样，是镜像一样

关键词：

- 对称。
- 镜像。

你要想到：

对称不是左子树和右子树“同方向相同”，而是镜像相同。

比较规则：

```text
左树的左孩子 vs 右树的右孩子
左树的右孩子 vs 右树的左孩子
```

面试说法：

> 我写一个 mirror 函数判断两棵树是否互为镜像。两个节点都为空时对称，一个为空一个不为空时不对称；值相等后，还要递归比较外侧和内侧节点。

易错点：

不要写成 `mirror(a.left, b.left)` 和 `mirror(a.right, b.right)`。

### 18.16 最近公共祖先：递归返回“找到了谁”

关键词：

- 两个节点。
- 最近公共祖先。
- 普通二叉树。

你要想到：

递归函数的含义：

> 在当前子树里找 p 或 q，找到了就返回找到的节点，找不到返回 null。

三种情况：

1. 左右都返回非空：p、q 分别在两边，当前节点就是祖先。
2. 只有左边非空：两个目标都在左边，或者只找到一个，向上返回左边。
3. 只有右边非空：同理。

面试说法：

> 对普通二叉树不能用大小关系。我用递归在左右子树查找 p 和 q。如果左右都找到了，说明当前节点是最近公共祖先；如果只一边找到了，就把那边结果向上返回。

易错点：

这是普通二叉树，不是二叉搜索树。

### 18.17 下一个更大元素：和每日温度其实同一道题

关键词：

- 右边第一个更大。

你要想到：

单调栈。  
栈里都是“还没找到下一个更大元素”的下标。

当前元素来了：

如果它比栈顶大，栈顶的答案就是它。

面试说法：

> 这题和每日温度一样，都是找右边第一个更大元素。我用单调栈保存还没找到答案的下标，当前元素比栈顶大时就弹栈并填答案。

易错点：

没有找到更大元素的默认是 -1。

### 18.18 循环数组下一个更大元素：为什么遍历两遍

关键词：

- 循环数组。
- 下一个更大。

你要想到：

普通下一个更大只看右边。循环数组里，末尾后面还能接开头，所以要模拟两遍。

为什么第二遍不入栈：

第一遍已经把每个下标都放进栈了。第二遍只是给这些下标找答案，如果再入栈，会重复处理。

面试说法：

> 我遍历 2n 次，用 i % n 模拟循环数组。前 n 次正常入栈，后 n 次只用于帮助栈里的元素寻找下一个更大值，不再入栈。

易错点：

第二遍不要 push。

### 18.19 接雨水：左右最大值的较小者决定水量

关键词：

- 柱子。
- 接水。
- 左右边界。

你要想到：

每个位置能接多少水，取决于左边最高柱子和右边最高柱子的较小值。

公式：

```text
water[i] = min(leftMax, rightMax) - height[i]
```

为什么双指针可行：

如果 `leftMax < rightMax`，说明左边当前能接多少水已经由 leftMax 决定了，因为右边有更高的边界兜底。  
所以可以结算 left，然后 left++。

面试说法：

> 每个位置能接的水由左右最高柱子的较小值决定。我用双指针维护 leftMax 和 rightMax，每次移动较小的一边，因为那一边的水量已经可以确定。

易错点：

先更新 max，再算水量。

### 18.20 环形链表：为什么快指针一定追上慢指针

关键词：

- 链表是否有环。

你要想到：

快慢指针。

为什么一定相遇：

如果有环，slow 进入环后，fast 也在环里。fast 每次比 slow 多走一步，相当于每轮距离缩小 1，最终一定追上。

面试说法：

> 我用快慢指针。slow 每次走一步，fast 每次走两步。如果链表有环，fast 会在环里追上 slow；如果没环，fast 会先走到 null。

易错点：

循环条件必须是 `fast != null && fast.next != null`。

### 18.21 删除倒数第 N 个节点：让 fast 先跑 N 步

关键词：

- 倒数第 N 个。
- 一趟扫描。

你要想到：

快慢指针保持 N 的距离。

为什么用 dummy：

如果删除的是头节点，没有 dummy 会很麻烦。有 dummy 后，删除任何节点都变成“删除 slow.next”。

过程：

1. fast 先走 n 步。
2. fast 和 slow 一起走。
3. fast 到最后时，slow 在待删除节点前一个。

面试说法：

> 我用 dummy 节点统一处理删除头节点的情况。fast 先走 n 步，然后 slow 和 fast 一起走，直到 fast 到链表末尾，此时 slow 正好在待删除节点前一个。

易错点：

循环条件用 `fast.next != null`，这样 slow 停在前驱节点。

### 18.22 合并两个有序链表：dummy 是为了少写特判

关键词：

- 两个有序链表。
- 合并。

你要想到：

像合并两个有序数组，但链表靠指针接。

为什么用 dummy：

没有 dummy，你要单独处理新链表头节点。  
有 dummy 后，所有节点都接到 `cur.next`，最后返回 `dummy.next`。

面试说法：

> 我用 dummy 节点和 cur 指针，每次比较两个链表当前节点，把较小的接到 cur 后面。某个链表为空后，直接接上另一个链表剩余部分。

易错点：

最后别忘了接剩余链表。

### 18.23 爬楼梯：最简单的 DP 入门

关键词：

- 每次 1 或 2 步。
- 求方法数。

你要想到：

到第 n 阶，最后一步只有两种可能：

1. 从 n-1 走 1 步。
2. 从 n-2 走 2 步。

所以：

```text
f(n) = f(n-1) + f(n-2)
```

面试说法：

> 我定义 dp[i] 表示爬到第 i 阶的方法数。最后一步要么从 i-1 走一步，要么从 i-2 走两步，所以 dp[i] = dp[i-1] + dp[i-2]。

易错点：

n=1 返回 1，n=2 返回 2。

### 18.24 最长递增子序列：为什么 dp[i] 是“以 i 结尾”

关键词：

- 子序列。
- 递增。
- 最长。

你要想到：

子序列不要求连续，所以滑动窗口不合适。  
基础做法用 DP。

为什么定义“以 i 结尾”：

如果只说前 i 个元素的 LIS 长度，不容易转移。  
定义成“以 nums[i] 结尾”，就可以看前面哪些数能接到 nums[i] 前面。

转移：

如果 `nums[j] < nums[i]`，那么 nums[i] 可以接在以 j 结尾的递增子序列后面：

```text
dp[i] = max(dp[i], dp[j] + 1)
```

面试说法：

> 我定义 dp[i] 表示以 nums[i] 结尾的最长递增子序列长度。对每个 i，遍历前面的 j，如果 nums[j] < nums[i]，就可以把 nums[i] 接到 j 后面，更新 dp[i]。

易错点：

子序列不要求连续；每个 dp[i] 初始是 1，因为自己单独就是长度 1。
