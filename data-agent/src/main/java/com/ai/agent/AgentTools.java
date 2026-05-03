package com.ai.agent;

import com.ai.service.FileProcessingService;
import com.ai.service.SessionManager;
import com.ai.service.VectorMemoryService;
import com.ai.skill.Skill;
import com.ai.skill.SkillManager;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AgentTools {

    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);

    private final SkillManager skillManager;
    private final FileProcessingService fileProcessingService;
    private final SessionManager sessionManager;
    private final VectorMemoryService vectorMemoryService;

    public AgentTools(SkillManager skillManager, FileProcessingService fileProcessingService,
                      SessionManager sessionManager, VectorMemoryService vectorMemoryService) {
        this.skillManager = skillManager;
        this.fileProcessingService = fileProcessingService;
        this.sessionManager = sessionManager;
        this.vectorMemoryService = vectorMemoryService;
    }

    @Tool("列出所有可用的数据分析技能(Skill)，返回技能名称和描述")
    public String listAvailableSkills() {
        List<Skill> skills = skillManager.getAllSkills();
        if (skills.isEmpty()) return "当前没有可用的技能";
        return skills.stream()
                .map(s -> "- " + s.getName() + ": " + s.getDescription())
                .collect(Collectors.joining("\n"));
    }

    @Tool("使用指定技能分析问题。参数: skillName(技能名称), query(分析问题)")
    public String useSkill(String skillName, String query) {
        log.info("Agent using skill: {} for query: {}", skillName, query);
        String result = skillManager.processWithSkillByName(skillName, query, null);
        if (result == null) return "技能 '" + skillName + "' 不存在或执行失败，可用技能: " + listAvailableSkills();
        return result;
    }

    @Tool("获取已上传文件的内容。参数: fileId(文件ID)")
    public String getFileContent(String fileId) {
        String content = fileProcessingService.getFileContent(fileId);
        if (content == null) return "文件不存在或内容为空";
        if (content.length() > 3000) {
            return content.substring(0, 3000) + "\n...[文件已截断，共" + content.length() + "字符]";
        }
        return content;
    }

    @Tool("列出所有已上传的文件，返回文件ID、名称和类型")
    public String listFiles() {
        var result = fileProcessingService.listFiles();
        @SuppressWarnings("unchecked")
        List<?> files = (List<?>) result.get("files");
        if (files == null || files.isEmpty()) return "没有已上传的文件，请先上传文件";
        return files.stream()
                .map(f -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) f;
                    return "- " + map.get("fileId") + " | " + map.get("filename") + " | " + map.get("contentType");
                })
                .collect(Collectors.joining("\n"));
    }

    @Tool("获取当前会话的对话历史摘要")
    public String getConversationHistory(String sessionId) {
        var session = sessionManager.getSession(sessionId);
        if (session == null || session.getHistory().isEmpty()) return "没有对话历史";
        return session.buildContextPrompt("");
    }

    @Tool("当无法完成用户请求时，向用户请求更多信息或数据。参数: message(请求信息)")
    public String askUserForInfo(String message) {
        return "需要用户输入: " + message;
    }

    @Tool("搜索相关的历史对话、文件内容和知识，实现长期记忆。参数: query(搜索关键词)")
    public String searchMemory(String query) {
        log.info("Agent searching memory for: {}", query);
        String result = vectorMemoryService.searchRelevant(query, 5);
        if (result == null || result.isEmpty()) {
            return "没有找到相关的历史记忆";
        }
        return result;
    }

    @Tool("执行数学计算表达式。支持加减乘除、括号、求幂等。参数: expression(数学表达式，如 '(100+200)*0.8')")
    public String calculate(String expression) {
        log.info("Agent calculating: {}", expression);
        try {
            String sanitized = expression.replaceAll("[^0-9+\\-*/().,%^ ]", "");
            if (sanitized.isEmpty()) return "无效的数学表达式";

            sanitized = sanitized.replace("^", "**");
            sanitized = sanitized.replace("%", "/100.0*");

            double result = evaluateExpression(sanitized);
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return expression + " = " + String.format("%.0f", result);
            }
            return expression + " = " + String.format("%.4f", result);
        } catch (Exception e) {
            return "计算失败: " + e.getMessage() + "。请检查表达式格式。";
        }
    }

    private double evaluateExpression(String rawExpr) {
        final String expr = rawExpr.trim();
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) { nextChar(); return true; }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < expr.length()) throw new RuntimeException("Unexpected: " + (char) ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if (eat('*')) {
                        if (ch == '*') { nextChar(); x = Math.pow(x, parseFactor()); }
                        else x *= parseFactor();
                    } else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(expr.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Unexpected: " + (char) ch);
                }
                return x;
            }
        }.parse();
    }

    @Tool("获取当前时间和日期信息。无需参数。")
    public String getCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)");
        return "当前时间: " + now.format(formatter);
    }

    @Tool("对文件数据进行统计分析摘要(行数、列数、数值列的均值/最大/最小/求和)。参数: fileId(文件ID)")
    public String analyzeFileData(String fileId) {
        log.info("Agent analyzing file data: {}", fileId);
        String content = fileProcessingService.getFileContent(fileId);
        if (content == null || content.isEmpty()) return "文件不存在或内容为空";

        String[] lines = content.split("\n");
        if (lines.length == 0) return "文件内容为空";

        StringBuilder sb = new StringBuilder();
        sb.append("## 数据概览\n");
        sb.append("- 总行数: ").append(lines.length).append("\n");

        String[] headers = lines[0].split("\t");
        sb.append("- 列数: ").append(headers.length).append("\n");
        sb.append("- 列名: ").append(String.join(", ", headers)).append("\n\n");

        if (lines.length > 1) {
            sb.append("## 数值列统计\n");
            for (int col = 0; col < headers.length; col++) {
                double sum = 0;
                double min = Double.MAX_VALUE;
                double max = Double.MIN_VALUE;
                int numericCount = 0;

                for (int row = 1; row < lines.length; row++) {
                    String[] cells = lines[row].split("\t");
                    if (col < cells.length) {
                        try {
                            double val = Double.parseDouble(cells[col].trim());
                            sum += val;
                            min = Math.min(min, val);
                            max = Math.max(max, val);
                            numericCount++;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                if (numericCount > 0) {
                    sb.append(String.format("**%s**: 有效数据%d条, 求和=%.2f, 均值=%.2f, 最小=%.2f, 最大=%.2f\n",
                            headers[col], numericCount, sum, sum / numericCount, min, max));
                }
            }

            sb.append("\n## 前5行样例\n");
            int sampleRows = Math.min(6, lines.length);
            for (int i = 0; i < sampleRows; i++) {
                sb.append(lines[i]).append("\n");
            }
        }

        return sb.toString();
    }

    @Tool("搜索知识库中的专业知识和文档。与 searchMemory 不同，此工具专注于搜索已索引的知识文档。参数: query(搜索内容)")
    public String searchKnowledge(String query) {
        log.info("Agent searching knowledge base for: {}", query);
        try {
            var matches = vectorMemoryService.searchMatches(query, 5, 0.5);
            var knowledgeMatches = matches.stream()
                    .filter(m -> {
                        String type = m.embedded().metadata().getString("type");
                        return "knowledge".equals(type) || "file".equals(type);
                    })
                    .toList();

            if (knowledgeMatches.isEmpty()) {
                return "知识库中没有找到相关内容。建议用户上传相关文档或数据。";
            }

            return knowledgeMatches.stream()
                    .map(match -> {
                        String type = match.embedded().metadata().getString("type");
                        double score = match.score();
                        return "[" + type + " | 相关度:" + String.format("%.2f", score) + "] " + match.embedded().text();
                    })
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.warn("Knowledge search failed: {}", e.getMessage());
            return "知识库搜索失败: " + e.getMessage();
        }
    }

    @Tool("查看向量记忆库的状态统计，了解已索引的数据量和类型分布。无需参数。")
    public String getMemoryStats() {
        int total = vectorMemoryService.getIndexedCount();
        Map<String, Long> byType = vectorMemoryService.getIndexedCountByType();
        boolean usingMilvus = vectorMemoryService.isUsingMilvus();

        StringBuilder sb = new StringBuilder();
        sb.append("向量记忆库状态:\n");
        sb.append("- 存储后端: ").append(usingMilvus ? "Milvus" : "内存(本地持久化)").append("\n");
        sb.append("- 已索引条目总数: ").append(total).append("\n");
        if (!byType.isEmpty()) {
            sb.append("- 按类型分布:\n");
            byType.forEach((type, count) ->
                    sb.append("  - ").append(type).append(": ").append(count).append("条\n"));
        }
        return sb.toString();
    }
}
