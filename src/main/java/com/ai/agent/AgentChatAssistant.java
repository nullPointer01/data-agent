package com.ai.agent;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * 聊天与工具对话共用的声明式业务接口，实现由 LangChain4j AiServices 创建。
 * 每次执行绑定独立消息窗口、受治理模型和获准工具；审批持久化仍归 Harness。
 */
public interface AgentChatAssistant {

    /** 根据已装配的系统指令、历史和当前输入返回完整回答。 */
    @SystemMessage("{{systemPrompt}}")
    Result<String> chat(@V("systemPrompt") String systemPrompt, @UserMessage String message);

    /** 官方 Reactor 适配返回事件 Flux，明确区分每轮文本片段与最终回答。 */
    @SystemMessage("{{systemPrompt}}")
    Flux<AiServiceStreamingEvent> stream(@V("systemPrompt") String systemPrompt, @UserMessage String message);
}
