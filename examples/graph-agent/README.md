# 图式 Agent 教学示例

这里保存一个不参与 Data Agent 运行时和 Maven 构建的最小状态图示例，用来说明
State、Node、条件边和人工审批中断。生产链路仍以
`AgentRunCoordinator -> ConfigurableAgentExecutor -> ReActLoopRunner` 为准。

示例没有第三方依赖，但使用了 Java 17 的 `record`，请确认 `java` 和 `javac` 均为 JDK 17 后再单独编译运行：

```bash
cd examples/graph-agent
javac -d /tmp/data-agent-graph-demo src/main/java/com/ai/graphdemo/*.java
java -cp /tmp/data-agent-graph-demo com.ai.graphdemo.ReActGraphDemo
```
