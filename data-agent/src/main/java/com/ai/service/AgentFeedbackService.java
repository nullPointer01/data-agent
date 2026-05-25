package com.ai.service;

import com.ai.agent.dto.AgentFeedbackInsightResponse;
import com.ai.agent.dto.AgentFeedbackDashboardResponse;
import com.ai.agent.dto.AgentFeedbackListResponse;
import com.ai.agent.dto.AgentFeedbackMutationResponse;
import com.ai.agent.dto.AgentFeedbackRequest;
import com.ai.agent.dto.AgentFeedbackResponse;
import com.ai.agent.dto.AgentFeedbackSummaryResponse;
import com.ai.logging.StructuredLogger;
import com.ai.model.AgentFeedback;
import com.ai.repository.AgentFeedbackRepository;
import com.ai.security.SecurityContextHelper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 回答反馈服务。
 *
 * @author data-agent
 */
@Service
public class AgentFeedbackService {

    private static final int DEFAULT_PAGE = 0;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 200;
    private static final int MAX_QUESTION_LENGTH = 1024;
    private static final int MAX_ANSWER_LENGTH = 2048;
    private static final int MAX_COMMENT_LENGTH = 1024;
    private static final String RATING_UP = "UP";
    private static final String RATING_DOWN = "DOWN";
    private static final String METRIC_AGENT_FEEDBACK_TOTAL = "data_agent_feedback_total";
    private static final String TAG_RATING = "rating";
    private static final String KEY_FEEDBACK_ID = "feedbackId";
    private static final String KEY_TENANT_ID = "tenantId";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_SESSION_ID = "sessionId";
    private static final String KEY_TRACE_ID = "traceId";
    private static final String KEY_RATING = "rating";
    private static final String KEY_QUESTION_LENGTH = "questionLength";
    private static final String KEY_ANSWER_LENGTH = "answerLength";
    private static final String KEY_COMMENT_LENGTH = "commentLength";

    private final AgentFeedbackRepository feedbackRepository;
    private final SecurityContextHelper securityContextHelper;
    private final MeterRegistry meterRegistry;
    private final AgentFeedbackInsightAnalyzer feedbackInsightAnalyzer;
    private final AgentFeedbackInsightProperties feedbackInsightProperties;
    private final StructuredLogger structuredLogger;

    public AgentFeedbackService(AgentFeedbackRepository feedbackRepository,
            SecurityContextHelper securityContextHelper,
            MeterRegistry meterRegistry,
            AgentFeedbackInsightAnalyzer feedbackInsightAnalyzer,
            AgentFeedbackInsightProperties feedbackInsightProperties,
            StructuredLogger structuredLogger) {
        this.feedbackRepository = feedbackRepository;
        this.securityContextHelper = securityContextHelper;
        this.meterRegistry = meterRegistry;
        this.feedbackInsightAnalyzer = feedbackInsightAnalyzer;
        this.feedbackInsightProperties = feedbackInsightProperties;
        this.structuredLogger = structuredLogger;
    }

    /**
     * 保存当前用户对 Agent 回答的反馈。
     *
     * @param request 反馈请求
     * @return 保存结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentFeedbackMutationResponse saveCurrentUserFeedback(AgentFeedbackRequest request) {
        validate(request);
        String tenantId = securityContextHelper.getCurrentTenantId();
        String userId = securityContextHelper.getCurrentUserId();
        AgentFeedback feedback = resolveFeedback(request, userId, tenantId);
        feedback.setTenantId(tenantId);
        feedback.setUserId(userId);
        feedback.setSessionId(blankToNull(request.sessionId()));
        feedback.setTraceId(blankToNull(request.traceId()));
        String normalizedRating = normalizeRating(request.rating());
        feedback.setRating(normalizedRating);
        feedback.setQuestion(truncate(request.question(), MAX_QUESTION_LENGTH));
        feedback.setAnswer(truncate(request.answer(), MAX_ANSWER_LENGTH));
        feedback.setComment(truncate(request.comment(), MAX_COMMENT_LENGTH));
        feedbackRepository.save(feedback);
        meterRegistry.counter(METRIC_AGENT_FEEDBACK_TOTAL, TAG_RATING, normalizedRating).increment();
        structuredLogger.logEvent(StructuredLogger.TYPE_AGENT_FEEDBACK, buildFeedbackLogPayload(feedback));
        return AgentFeedbackMutationResponse.saved(feedback.getFeedbackId());
    }

    /**
     * 查询当前租户下的反馈列表。
     *
     * @param limit 返回条数
     * @return 反馈列表
     */
    @Transactional(readOnly = true)
    public AgentFeedbackListResponse listCurrentTenantFeedbacks(int limit) {
        return listCurrentTenantFeedbacks(limit, null, null);
    }

    /**
     * 查询当前租户下的反馈列表。
     *
     * @param limit 返回条数
     * @param traceId 可选执行轨迹编号
     * @param rating 可选评分
     * @return 反馈列表
     */
    @Transactional(readOnly = true)
    public AgentFeedbackListResponse listCurrentTenantFeedbacks(int limit, String traceId, String rating) {
        String tenantId = securityContextHelper.getCurrentTenantId();
        PageRequest page = PageRequest.of(DEFAULT_PAGE, Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT)));
        String normalizedRating = normalizeOptionalRating(rating);
        List<AgentFeedback> feedbacks = findFeedbacks(tenantId, traceId, normalizedRating, page);
        return new AgentFeedbackListResponse(true, feedbacks.stream()
                .map(AgentFeedbackResponse::from)
                .toList());
    }

    /**
     * 查询反馈管理台所需的聚合数据。
     *
     * @param limit 反馈列表返回条数
     * @param traceId 可选执行轨迹编号
     * @param rating 可选评分
     * @return 反馈管理台数据
     */
    @Transactional(readOnly = true)
    public AgentFeedbackDashboardResponse getCurrentTenantDashboard(int limit, String traceId, String rating) {
        AgentFeedbackSummaryResponse summary = summarizeCurrentTenantFeedbacks();
        AgentFeedbackInsightResponse insights = analyzeCurrentTenantFeedbacks();
        AgentFeedbackListResponse feedbacks = listCurrentTenantFeedbacks(limit, traceId, rating);
        return new AgentFeedbackDashboardResponse(true, summary, insights, feedbacks);
    }

    /**
     * 汇总当前租户的 Agent 回答质量反馈。
     *
     * @return 反馈质量摘要
     */
    @Transactional(readOnly = true)
    public AgentFeedbackSummaryResponse summarizeCurrentTenantFeedbacks() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        long totalCount = feedbackRepository.countByTenantId(tenantId);
        long upCount = feedbackRepository.countByTenantIdAndRating(tenantId, RATING_UP);
        long downCount = feedbackRepository.countByTenantIdAndRating(tenantId, RATING_DOWN);
        AgentFeedbackResponse latestNegative = feedbackRepository
                .findFirstByTenantIdAndRatingOrderByCreatedAtDesc(tenantId, RATING_DOWN)
                .map(AgentFeedbackResponse::from)
                .orElse(null);
        return new AgentFeedbackSummaryResponse(true, totalCount, upCount, downCount,
                calculatePositiveRate(totalCount, upCount), latestNegative);
    }

    /**
     * 分析当前租户最近负向反馈并生成可执行的质量洞察。
     *
     * @return 反馈质量洞察
     */
    @Transactional(readOnly = true)
    public AgentFeedbackInsightResponse analyzeCurrentTenantFeedbacks() {
        String tenantId = securityContextHelper.getCurrentTenantId();
        long negativeCount = feedbackRepository.countByTenantIdAndRating(tenantId, RATING_DOWN);
        PageRequest page = PageRequest.of(DEFAULT_PAGE, feedbackInsightProperties.analyzeSampleLimit());
        List<AgentFeedback> negativeFeedbacks = feedbackRepository
                .findByTenantIdAndRatingOrderByCreatedAtDesc(tenantId, RATING_DOWN, page);
        return feedbackInsightAnalyzer.analyze(negativeFeedbacks, negativeCount);
    }

    private AgentFeedback resolveFeedback(AgentFeedbackRequest request, String userId, String tenantId) {
        if (StringUtils.hasText(request.traceId())) {
            return feedbackRepository.findByTraceIdAndUserIdAndTenantId(request.traceId(), userId, tenantId)
                    .orElseGet(this::newFeedback);
        }
        return newFeedback();
    }

    private List<AgentFeedback> findFeedbacks(String tenantId, String traceId, String rating, PageRequest page) {
        if (StringUtils.hasText(traceId) && StringUtils.hasText(rating)) {
            return feedbackRepository.findByTenantIdAndTraceIdAndRatingOrderByCreatedAtDesc(tenantId, traceId,
                    rating, page);
        }
        if (StringUtils.hasText(traceId)) {
            return feedbackRepository.findByTenantIdAndTraceIdOrderByCreatedAtDesc(tenantId, traceId, page);
        }
        if (StringUtils.hasText(rating)) {
            return feedbackRepository.findByTenantIdAndRatingOrderByCreatedAtDesc(tenantId, rating, page);
        }
        return feedbackRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, page);
    }

    private AgentFeedback newFeedback() {
        AgentFeedback feedback = new AgentFeedback();
        feedback.setFeedbackId(UUID.randomUUID().toString());
        return feedback;
    }

    private void validate(AgentFeedbackRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("反馈请求不能为空");
        }
        normalizeRating(request.rating());
        if (!StringUtils.hasText(request.traceId()) && !StringUtils.hasText(request.sessionId())) {
            throw new IllegalArgumentException("反馈必须关联会话或执行轨迹");
        }
    }

    private String normalizeRating(String rating) {
        if (!StringUtils.hasText(rating)) {
            throw new IllegalArgumentException("反馈评分不能为空");
        }
        String normalizedRating = rating.trim().toUpperCase(Locale.ROOT);
        if (!RATING_UP.equals(normalizedRating) && !RATING_DOWN.equals(normalizedRating)) {
            throw new IllegalArgumentException("反馈评分只支持 UP 或 DOWN");
        }
        return normalizedRating;
    }

    private String normalizeOptionalRating(String rating) {
        if (!StringUtils.hasText(rating) || "ALL".equalsIgnoreCase(rating.trim())) {
            return null;
        }
        return normalizeRating(rating);
    }

    private Map<String, Object> buildFeedbackLogPayload(AgentFeedback feedback) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(KEY_FEEDBACK_ID, feedback.getFeedbackId());
        payload.put(KEY_TENANT_ID, feedback.getTenantId());
        payload.put(KEY_USER_ID, feedback.getUserId());
        payload.put(KEY_SESSION_ID, feedback.getSessionId());
        payload.put(KEY_TRACE_ID, feedback.getTraceId());
        payload.put(KEY_RATING, feedback.getRating());
        payload.put(KEY_QUESTION_LENGTH, lengthOf(feedback.getQuestion()));
        payload.put(KEY_ANSWER_LENGTH, lengthOf(feedback.getAnswer()));
        payload.put(KEY_COMMENT_LENGTH, lengthOf(feedback.getComment()));
        return payload;
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private double calculatePositiveRate(long totalCount, long upCount) {
        if (totalCount <= 0L) {
            return 0D;
        }
        return (double) upCount / totalCount;
    }
}
