# 注解提示词与 Flux 流式接口
用户授权：“进行改，流式也一起改，我是为了学习”。以此作为本轮实现授权，不重复请求确认。
- 同步/流式 AgentChatAssistant 使用 @SystemMessage + @V 注入每次请求已组装的提示词；窗口只预填历史，不手动注入第二份 SystemMessage。
- 接入官方 langchain4j-reactor 1.20.2-beta30。助手返回 Flux<AiServiceStreamingEvent>，消费 PartialResponseEvent 与 FinalResponseEvent，区分工具旁白和最终保存文本。
- 受治理模型增加 Reactive Streams 适配；保留现有模型记账和取消，完整工具响应先预检，禁止 CompleteToolCall 事件绕过整批审批。
- Controller 返回 Flux<ServerSentEvent<String>>。MVC 原生订阅并写出 SSE；保持 Spring MVC/Security/JPA，不宣称整个系统迁成非阻塞 WebFlux。
- Flux.defer 每次订阅独立生命周期，捕获请求身份，启动放入现有有界线程池；背压缓冲有界，断订阅/绝对超时/缓冲溢出取消 Run，正常终态与审批暂停不误取消。
- 配置独立有界 MVC 流写入池及 MVC 超时，避免默认执行器和默认 30 秒超时截断流。
- 无仓库测试类、无提交、无外部业务写操作；保留现有工作区改动。
