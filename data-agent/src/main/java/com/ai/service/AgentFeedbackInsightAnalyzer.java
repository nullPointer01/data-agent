package com.ai.service;

import com.ai.agent.dto.AgentFeedbackInsightResponse;
import com.ai.agent.dto.AgentFeedbackIssueResponse;
import com.ai.agent.dto.AgentFeedbackResponse;
import com.ai.model.AgentFeedback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Agent 负向反馈质量洞察分析器。
 *
 * @author data-agent
 */
@Component
public class AgentFeedbackInsightAnalyzer {

    private static final String ISSUE_OTHER = "OTHER";
    private final AgentFeedbackInsightProperties properties;

    public AgentFeedbackInsightAnalyzer(AgentFeedbackInsightProperties properties) {
        this.properties = properties;
    }

    /**
     * 基于最近负向反馈样本生成质量洞察。
     *
     * @param negativeFeedbacks 最近负向反馈样本
     * @param negativeCount 当前租户负向反馈总数
     * @return 质量洞察
     */
    public AgentFeedbackInsightResponse analyze(List<AgentFeedback> negativeFeedbacks, long negativeCount) {
        List<AgentFeedback> safeFeedbacks = negativeFeedbacks == null ? List.of() : negativeFeedbacks;
        Map<String, List<AgentFeedback>> issueGroups = groupIssues(safeFeedbacks);
        List<AgentFeedbackIssueResponse> issues = properties.issueRules().stream()
                .map(rule -> buildIssueResponse(rule, issueGroups.getOrDefault(rule.type(), List.of()),
                        safeFeedbacks.size()))
                .filter(issue -> issue.count() > 0L)
                .toList();
        List<AgentFeedback> otherFeedbacks = issueGroups.getOrDefault(ISSUE_OTHER, List.of());
        if (!otherFeedbacks.isEmpty()) {
            issues = appendOtherIssue(issues, otherFeedbacks, safeFeedbacks.size());
        }
        return new AgentFeedbackInsightResponse(true, safeFeedbacks.size(), negativeCount, issues,
                buildGlobalRecommendations(negativeCount, issues));
    }

    private Map<String, List<AgentFeedback>> groupIssues(List<AgentFeedback> feedbacks) {
        java.util.Map<String, java.util.List<AgentFeedback>> groups = new java.util.LinkedHashMap<>();
        for (AgentFeedback feedback : feedbacks) {
            String issueType = classifyIssue(feedback);
            groups.computeIfAbsent(issueType, key -> new java.util.ArrayList<>()).add(feedback);
        }
        return groups;
    }

    private String classifyIssue(AgentFeedback feedback) {
        String text = buildClassifyText(feedback);
        for (AgentFeedbackInsightProperties.IssueRuleProperties rule : properties.issueRules()) {
            if (rule.matches(text)) {
                return rule.type();
            }
        }
        return ISSUE_OTHER;
    }

    private String buildClassifyText(AgentFeedback feedback) {
        return String.join(" ",
                nullToEmpty(feedback.getComment()),
                nullToEmpty(feedback.getQuestion()),
                nullToEmpty(feedback.getAnswer())).toLowerCase(Locale.ROOT);
    }

    private AgentFeedbackIssueResponse buildIssueResponse(AgentFeedbackInsightProperties.IssueRuleProperties rule,
            List<AgentFeedback> feedbacks,
            int sampleSize) {
        return new AgentFeedbackIssueResponse(rule.type(), rule.name(), feedbacks.size(),
                calculateIssueRatio(feedbacks.size(), sampleSize), rule.recommendation(),
                feedbacks.stream()
                        .limit(properties.issueSampleLimit())
                        .map(AgentFeedbackResponse::from)
                        .toList());
    }

    private List<AgentFeedbackIssueResponse> appendOtherIssue(List<AgentFeedbackIssueResponse> issues,
            List<AgentFeedback> feedbacks,
            int sampleSize) {
        java.util.List<AgentFeedbackIssueResponse> nextIssues = new java.util.ArrayList<>(issues);
        nextIssues.add(new AgentFeedbackIssueResponse(ISSUE_OTHER, "其他问题", feedbacks.size(),
                calculateIssueRatio(feedbacks.size(), sampleSize),
                properties.otherRecommendation(),
                feedbacks.stream()
                        .limit(properties.issueSampleLimit())
                        .map(AgentFeedbackResponse::from)
                        .toList()));
        return nextIssues;
    }

    private List<String> buildGlobalRecommendations(long negativeCount, List<AgentFeedbackIssueResponse> issues) {
        java.util.List<String> recommendations = new java.util.ArrayList<>();
        recommendations.add(negativeCount > 0L ? properties.highNegativeRecommendation()
                : properties.healthyRecommendation());
        issues.stream()
                .limit(3L)
                .map(AgentFeedbackIssueResponse::recommendation)
                .forEach(recommendations::add);
        return recommendations;
    }

    private double calculateIssueRatio(long count, int sampleSize) {
        if (sampleSize <= 0) {
            return 0D;
        }
        return (double) count / sampleSize;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

}
