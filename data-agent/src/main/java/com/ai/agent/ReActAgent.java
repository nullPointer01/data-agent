package com.ai.agent;

import com.ai.mcp.MCPModelService;
import com.ai.mcp.TokenMonitor;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.model.ConversationSession;
import com.ai.service.SessionManager;
import com.ai.service.VectorMemoryService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ReActAgent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);
    private static final int MAX_ITERATIONS = 8;
    private static final String SYSTEM_PROMPT = """
            你是一个强大的数据分析智能体(Agent)，拥有多种工具来辅助完成用户的数据分析任务。

            ## 工作模式 (ReAct: 思考-行动-观察)

            对于每个用户请求，你需要:
            1. **思考(Thought)**: 分析用户意图，规划解决步骤
            2. **行动(Action)**: 选择并调用合适的工具
            3. **观察(Observation)**: 分析工具返回的结果
            4. 重复上述步骤直到获得足够信息
            5. **最终回答**: 整合所有信息，给出清晰专业的回答

            ## 核心原则
            - 绝不编造数据。有数据则基于数据分析；无数据则请求用户提供
            - 需要计算时使用 calculate 工具，不要心算
            - 对文件数据先用 analyzeFileData 获取概览，再深入分析
            - 搜索相关知识时可以同时使用 searchMemory 和 searchKnowledge
            - 每次只调用一个工具
            - 回答应结构清晰、专业简洁，可使用 Markdown 格式

            ## 输出格式
            思考和工具调用时，先写出你的思考过程，然后调用工具:
            [思考] 用户想要...，我需要先...
            [CALL:工具名(参数)]

            最终回答时，直接给出完整答案，不要包含工具调用语法。
            """;

    private final AgentTools agentTools;
    private final MCPModelService mcpModelService;
    private final TokenMonitor tokenMonitor;
    private final SessionManager sessionManager;
    private final VectorMemoryService vectorMemoryService;

    public ReActAgent(AgentTools agentTools, MCPModelService mcpModelService,
                      TokenMonitor tokenMonitor, SessionManager sessionManager,
                      VectorMemoryService vectorMemoryService) {
        this.agentTools = agentTools;
        this.mcpModelService = mcpModelService;
        this.tokenMonitor = tokenMonitor;
        this.sessionManager = sessionManager;
        this.vectorMemoryService = vectorMemoryService;
    }

    public AnalysisResponse execute(AnalysisRequest request, String fileContent) {
        String modelId = request.hasModel() ? request.getModelId() : null;

        ConversationSession session = null;
        if (request.hasSession()) {
            session = sessionManager.getSession(request.getSessionId());
        }

        String userQuery = request.getQuestion();
        if (fileContent != null && !fileContent.isEmpty()) {
            userQuery += "\n\n[用户已上传文件数据，请使用 analyzeFileData 或 getFileContent 工具获取数据]";
        }

        List<ToolSpecification> toolSpecs = buildToolSpecifications();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));

        if (session != null && !session.getHistory().isEmpty()) {
            String historyContext = session.buildContextPrompt(userQuery);
            messages.add(UserMessage.from(historyContext));
        } else {
            messages.add(UserMessage.from(userQuery));
        }

        StringBuilder finalAnswer = new StringBuilder();
        List<AnalysisResponse.ThinkingStep> thinkingSteps = new ArrayList<>();
        int iterations = 0;

        while (iterations < MAX_ITERATIONS) {
            iterations++;
            log.info("ReAct iteration {}/{} for query: {}", iterations, MAX_ITERATIONS,
                    userQuery.substring(0, Math.min(50, userQuery.length())));

            String llmResponse = callLlmWithTools(messages, toolSpecs, modelId);

            if (llmResponse == null || llmResponse.isEmpty()) {
                finalAnswer.append("模型调用失败，请稍后重试");
                thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "error", "模型调用失败"));
                break;
            }

            log.info("ReAct LLM response (first 200): {}", llmResponse.substring(0, Math.min(200, llmResponse.length())));

            String thinking = extractThinking(llmResponse);
            if (thinking != null) {
                thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "thinking", thinking));
            }

            String toolCallResult = tryExecuteToolCall(llmResponse);

            if (toolCallResult == null) {
                String cleanResponse = cleanToolCallSyntax(llmResponse);
                cleanResponse = cleanThinkingSyntax(cleanResponse);
                finalAnswer.append(cleanResponse);
                thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "answer", "生成最终回答"));
                break;
            }

            if (toolCallResult.equals("__MEMORY_INDEXED__")) {
                messages.add(AiMessage.from(llmResponse));
                messages.add(UserMessage.from("记忆已保存。请继续回答用户的问题。"));
                iterations--;
                continue;
            }

            AnalysisResponse.ThinkingStep toolStep = new AnalysisResponse.ThinkingStep(iterations, "tool_call", "调用工具");
            toolStep.setToolName(extractToolName(llmResponse));
            String truncatedResult = toolCallResult.length() > 300
                    ? toolCallResult.substring(0, 300) + "..."
                    : toolCallResult;
            toolStep.setToolResult(truncatedResult);
            thinkingSteps.add(toolStep);

            messages.add(AiMessage.from(llmResponse));
            messages.add(UserMessage.from("工具执行结果:\n" + toolCallResult + "\n\n请根据结果继续分析或给出最终回答。如果已有足够信息，请直接给出最终回答（使用Markdown格式），不要再调用工具。"));

            if (isFinalAnswer(toolCallResult)) {
                messages.add(AiMessage.from("基于以上工具返回的结果，我来整合总结："));
                String summaryResponse = callLlmWithTools(messages, toolSpecs, modelId);
                if (summaryResponse != null) {
                    finalAnswer.append(cleanThinkingSyntax(cleanToolCallSyntax(summaryResponse)));
                } else {
                    finalAnswer.append(toolCallResult);
                }
                thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "answer", "整合结果生成最终回答"));
                break;
            }
        }

        if (iterations >= MAX_ITERATIONS && finalAnswer.isEmpty()) {
            finalAnswer.append("分析步骤较多，以下是目前已获得的分析结果:\n");
            for (ChatMessage msg : messages) {
                if (msg instanceof AiMessage) {
                    String text = ((AiMessage) msg).text();
                    if (text != null && !text.isEmpty()) {
                        String clean = cleanThinkingSyntax(cleanToolCallSyntax(text));
                        if (!clean.isEmpty()) {
                            finalAnswer.append(clean).append("\n");
                        }
                    }
                }
            }
            thinkingSteps.add(new AnalysisResponse.ThinkingStep(iterations, "max_iterations", "达到最大迭代次数，整合已有结果"));
        }

        String result = finalAnswer.toString().trim();

        if (session != null) {
            session.addUserMessage(request.getQuestion());
            session.addAssistantMessage(result);
            try {
                sessionManager.saveMessage(session.getSessionId(), "user", request.getQuestion(), null, null,
                        tokenMonitor.estimateTokens(request.getQuestion()));
                sessionManager.saveMessage(session.getSessionId(), "assistant", result, "react-agent", modelId,
                        tokenMonitor.estimateTokens(result));
            } catch (Exception e) {
                log.warn("Failed to persist messages", e);
            }
        }

        try {
            vectorMemoryService.indexConversation(
                    session != null ? session.getSessionId() : "anonymous",
                    request.getQuestion(),
                    result.length() > 500 ? result.substring(0, 500) : result);
        } catch (Exception e) {
            log.warn("Failed to index conversation to vector memory", e);
        }

        AnalysisResponse response = AnalysisResponse.ok(result);
        response.setSkillUsed("react-agent");
        response.setThinkingSteps(thinkingSteps);
        if (session != null) response.setSessionId(session.getSessionId());
        return response;
    }

    private String callLlmWithTools(List<ChatMessage> messages, List<ToolSpecification> toolSpecs, String modelId) {
        try {
            String toolsPrompt = buildToolsPrompt(toolSpecs);
            List<ChatMessage> enhancedMessages = new ArrayList<>(messages);
            if (!enhancedMessages.isEmpty() && enhancedMessages.get(0) instanceof SystemMessage) {
                String originalSystem = ((SystemMessage) enhancedMessages.get(0)).text();
                enhancedMessages.set(0, SystemMessage.from(originalSystem + "\n\n" + toolsPrompt));
            } else {
                enhancedMessages.add(0, SystemMessage.from(toolsPrompt));
            }

            String prompt = buildPromptFromMessages(enhancedMessages);
            return mcpModelService.callModel(prompt, modelId);
        } catch (Exception e) {
            log.error("LLM call failed in ReAct loop", e);
            return null;
        }
    }

    private String buildToolsPrompt(List<ToolSpecification> toolSpecs) {
        StringBuilder sb = new StringBuilder("\n\n# 可用工具\n");
        for (ToolSpecification spec : toolSpecs) {
            sb.append("- ").append(spec.name()).append(": ");
            if (spec.description() != null) sb.append(spec.description());
            sb.append("\n");
        }
        sb.append("\n调用工具格式: [CALL:工具名(参数)]\n");
        sb.append("例如: [CALL:listAvailableSkills()]\n");
        sb.append("例如: [CALL:useSkill(\"销售分析\", \"分析销售趋势\")]\n");
        sb.append("例如: [CALL:getFileContent(\"file-123\")]\n");
        sb.append("不调用工具时直接回答。\n");
        return sb.toString();
    }

    private String buildPromptFromMessages(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            if (msg instanceof SystemMessage) {
                sb.append("[系统] ").append(((SystemMessage) msg).text()).append("\n\n");
            } else if (msg instanceof UserMessage) {
                sb.append("[用户] ").append(((UserMessage) msg).singleText()).append("\n\n");
            } else if (msg instanceof AiMessage) {
                sb.append("[助手] ").append(((AiMessage) msg).text()).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String extractThinking(String llmResponse) {
        if (llmResponse == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\[思考]\\s*(.+?)(?=\\[CALL|$)", java.util.regex.Pattern.DOTALL)
                .matcher(llmResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        matcher = java.util.regex.Pattern
                .compile("(?:Thought|思考)[：:]\\s*(.+?)(?=\\[CALL|Action|$)", java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(llmResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String extractToolName(String llmResponse) {
        if (llmResponse == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\[CALL[：:]?\\s*(\\w+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(llmResponse);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    private String cleanThinkingSyntax(String response) {
        if (response == null) return "";
        return response
                .replaceAll("\\[思考]\\s*.*?(?=\\n|$)", "")
                .replaceAll("(?:Thought|思考)[：:]\\s*.*?(?=\\n|$)", "")
                .trim();
    }

    private String tryExecuteToolCall(String llmResponse) {
        if (llmResponse == null) return null;

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\[CALL[：:]?\\s*(\\w+)\\s*\\((.*?)\\)\\]?", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                .matcher(llmResponse);

        if (!matcher.find()) return null;

        String toolName = matcher.group(1);
        String argsStr = matcher.group(2) != null ? matcher.group(2) : "";

        log.info("Tool call: {}({})", toolName, argsStr);

        String[] args = parseArgs(argsStr);

        try {
            String result = switch (toolName) {
                case "listAvailableSkills" -> agentTools.listAvailableSkills();
                case "useSkill" -> args.length >= 2 ? agentTools.useSkill(args[0], args[1]) : "参数不足: 需要skillName和query";
                case "getFileContent" -> args.length >= 1 ? agentTools.getFileContent(args[0]) : "参数不足: 需要fileId";
                case "listFiles" -> agentTools.listFiles();
                case "getConversationHistory" -> args.length >= 1 ? agentTools.getConversationHistory(args[0]) : "参数不足: 需要sessionId";
                case "askUserForInfo" -> args.length >= 1 ? agentTools.askUserForInfo(args[0]) : "参数不足: 需要message";
                case "searchMemory" -> args.length >= 1 ? agentTools.searchMemory(args[0]) : "参数不足: 需要query";
                case "calculate" -> args.length >= 1 ? agentTools.calculate(args[0]) : "参数不足: 需要expression";
                case "getCurrentTime" -> agentTools.getCurrentTime();
                case "analyzeFileData" -> args.length >= 1 ? agentTools.analyzeFileData(args[0]) : "参数不足: 需要fileId";
                case "searchKnowledge" -> args.length >= 1 ? agentTools.searchKnowledge(args[0]) : "参数不足: 需要query";
                case "getMemoryStats" -> agentTools.getMemoryStats();
                default -> "未知工具: " + toolName;
            };
            return result;
        } catch (Exception e) {
            log.warn("Tool execution failed: {} - {}", toolName, e.getMessage());
            return "工具执行失败: " + e.getMessage();
        }
    }

    private String[] parseArgs(String argsStr) {
        if (argsStr == null || argsStr.trim().isEmpty()) return new String[0];

        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : argsStr.toCharArray()) {
            if (c == '"' || c == '\'') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                args.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            args.add(current.toString().trim());
        }

        return args.toArray(new String[0]);
    }

    private boolean isFinalAnswer(String toolResult) {
        if (toolResult == null) return true;
        return toolResult.startsWith("需要用户输入:") ||
               toolResult.contains("不存在") ||
               toolResult.contains("没有已上传") ||
               toolResult.contains("没有可用的");
    }

    private String cleanToolCallSyntax(String response) {
        if (response == null) return "";
        return response.replaceAll("\\[CALL:\\w+\\(.*?\\)\\]", "").trim();
    }

    private List<ToolSpecification> buildToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        specs.add(ToolSpecification.builder()
                .name("listAvailableSkills")
                .description("列出所有可用的数据分析技能(Skill)")
                .build());
        specs.add(ToolSpecification.builder()
                .name("useSkill")
                .description("使用指定技能分析问题。参数: skillName, query")
                .build());
        specs.add(ToolSpecification.builder()
                .name("getFileContent")
                .description("获取已上传文件的内容。参数: fileId")
                .build());
        specs.add(ToolSpecification.builder()
                .name("listFiles")
                .description("列出所有已上传的文件")
                .build());
        specs.add(ToolSpecification.builder()
                .name("getConversationHistory")
                .description("获取当前会话的对话历史。参数: sessionId")
                .build());
        specs.add(ToolSpecification.builder()
                .name("askUserForInfo")
                .description("向用户请求更多信息或数据。参数: message")
                .build());
        specs.add(ToolSpecification.builder()
                .name("searchMemory")
                .description("搜索历史对话和文件中的相关信息。参数: query")
                .build());
        specs.add(ToolSpecification.builder()
                .name("calculate")
                .description("执行数学计算表达式，支持加减乘除括号等。参数: expression(如 '(100+200)*0.8')")
                .build());
        specs.add(ToolSpecification.builder()
                .name("getCurrentTime")
                .description("获取当前日期和时间")
                .build());
        specs.add(ToolSpecification.builder()
                .name("analyzeFileData")
                .description("对文件数据进行统计分析(行数、列数、均值、最大最小值等)。参数: fileId")
                .build());
        specs.add(ToolSpecification.builder()
                .name("searchKnowledge")
                .description("搜索知识库中的专业知识和文档内容。参数: query")
                .build());
        specs.add(ToolSpecification.builder()
                .name("getMemoryStats")
                .description("查看向量记忆库的状态和已索引数据统计")
                .build());
        return specs;
    }
}
