package com.ai.agent.react;

import com.ai.memory.MemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.ai.agent.AgentReasoningProperties;

/**
 * ReAct 循环执行期间的工作记忆管理。
 *
 * <p>在循环开始、每轮迭代和完成时将执行状态写入 {@link MemoryManager}，
 * 供后续分析和上下文恢复使用。出现记忆层异常时优雅降级，不影响主流程。</p>
 *
 * @author data-agent
 */
@Component
public class ReActWorkingMemoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReActWorkingMemoryService.class);

    private final MemoryManager memoryManager;
    private final AgentReasoningProperties properties;

    /**
     * 构造工作记忆服务。
     *
     * @param memoryManager 记忆管理器
     * @param properties 推理配置
     */
    public ReActWorkingMemoryService(MemoryManager memoryManager, AgentReasoningProperties properties) {
        this.memoryManager = memoryManager;
        this.properties = properties;
    }

    /**
     * 记录 ReAct 循环开始。
     *
     * <p>空白 sessionId 时跳过，避免无效写入。</p>
     *
     * @param sessionId 会话编号
     * @param question 用户问题
     */
    public void recordStart(String sessionId, String question) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        try {
            String content = "执行状态: STARTED\n问题: " + (question == null ? "" : question);
            memoryManager.saveWorkingMemory(sessionId, content);
        } catch (Exception e) {
            LOGGER.warn("记录 ReAct 开始状态失败: {}", e.getMessage());
        }
    }

    /**
     * 记录单轮迭代状态。
     *
     * @param sessionId 会话编号
     * @param question 用户问题
     * @param iteration 当前迭代编号
     * @param modelOutput 模型输出
     * @param stepResult 循环步骤结果
     * @param partialAnswer 当前部分答案
     */
    public void recordIteration(String sessionId, String question, int iteration,
            String modelOutput, ReActLoopStepResult stepResult, String partialAnswer) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        try {
            StringBuilder content = new StringBuilder();
            content.append("迭代: ").append(iteration).append('\n');
            content.append("最近模型输出: ").append(modelOutput == null ? "" : modelOutput).append('\n');
            content.append("当前答案: ").append(partialAnswer == null ? "" : partialAnswer);
            memoryManager.saveWorkingMemory(sessionId, content.toString());
        } catch (Exception e) {
            LOGGER.warn("记录 ReAct 迭代状态失败: {}", e.getMessage());
        }
    }

    /**
     * 记录 ReAct 循环完成。
     *
     * <p>即使记忆层抛出异常也不会中断调用方流程。</p>
     *
     * @param sessionId 会话编号
     * @param question 用户问题
     * @param iterations 总迭代次数
     * @param answer 最终答案
     */
    public void recordFailure(String sessionId, String question, int iterations) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        try {
            String content = "执行状态: FAILED\n迭代次数: " + iterations + "\n问题: " + (question == null ? "" : question);
            memoryManager.saveWorkingMemory(sessionId, content);
        } catch (Exception e) {
            LOGGER.warn("记录 ReAct 失败状态时异常: {}", e.getMessage());
        }
    }

    public void recordCompletion(String sessionId, String question, int iterations, String answer) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        try {
            StringBuilder content = new StringBuilder();
            content.append("执行状态: COMPLETED\n");
            content.append("总迭代次数: ").append(iterations).append('\n');
            content.append("最终答案: ").append(answer == null ? "" : answer);
            memoryManager.saveWorkingMemory(sessionId, content.toString());
        } catch (Exception e) {
            LOGGER.warn("记录 ReAct 完成状态失败，不影响主流程: {}", e.getMessage());
        }
    }
}
