package com.ai.agent.outcome;

import com.ai.agent.outcome.AgentCriterionEvaluation.CriterionDecision;
import com.ai.agent.outcome.AgentOutcomeEvaluator.OutcomeEvidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 只依赖稳定运行证据的确定性任务结果评估器。
 *
 * @author data-agent
 */
@Component
public class DeterministicOutcomeEvaluator implements AgentOutcomeEvaluator {

    public static final String EVALUATOR_ID = "deterministic-outcome";
    public static final String EVALUATOR_VERSION = "1.0";

    private final ObjectMapper objectMapper;

    public DeterministicOutcomeEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentOutcomeEvaluation evaluate(AgentTaskContract taskContract, OutcomeEvidence evidence) {
        if (taskContract == null) {
            return new AgentOutcomeEvaluation(AgentOutcomeStatus.NOT_EVALUATED, EVALUATOR_ID,
                    EVALUATOR_VERSION, "TASK_CONTRACT_MISSING", List.of());
        }
        if (evidence == null) {
            return unavailable(taskContract, "RUN_EVIDENCE_MISSING");
        }
        if (evidence.runStatus() == null || !evidence.runStatus().isTerminal()) {
            return unavailable(taskContract, "RUN_NOT_TERMINAL");
        }
        List<AgentCriterionEvaluation> results = taskContract.criteria().stream()
                .map(criterion -> evaluateCriterion(criterion, evidence))
                .toList();
        AgentOutcomeStatus status = aggregate(results);
        String reasonCode = switch (status) {
            case ACHIEVED -> "ALL_REQUIRED_CRITERIA_PASSED";
            case NOT_ACHIEVED -> "REQUIRED_CRITERION_FAILED";
            case NOT_EVALUATED -> "REQUIRED_CRITERION_UNAVAILABLE";
        };
        return new AgentOutcomeEvaluation(status, EVALUATOR_ID, EVALUATOR_VERSION, reasonCode, results);
    }

    private AgentCriterionEvaluation evaluateCriterion(AgentSuccessCriterion criterion,
            OutcomeEvidence evidence) {
        return switch (criterion.type()) {
            case ANSWER_CONTAINS -> evaluateAnswerContains(criterion, evidence);
            case JSON_FIELD_EQUALS -> evaluateJsonField(criterion, evidence);
            case TOOL_CALLED -> evaluateSetContains(criterion, evidence.calledTools(),
                    evidence.toolEvidenceComplete(), "tool");
            case APPROVAL_STATUS -> evaluateSetContains(criterion, evidence.approvalStatuses(),
                    evidence.approvalEvidenceComplete(), "approval");
            case RUN_STATUS -> evaluateRunStatus(criterion, evidence);
        };
    }

    private AgentCriterionEvaluation evaluateAnswerContains(AgentSuccessCriterion criterion,
            OutcomeEvidence evidence) {
        if (!StringUtils.hasText(evidence.finalAnswer())) {
            return result(criterion, CriterionDecision.NOT_EVALUATED, "ANSWER_MISSING", List.of());
        }
        boolean matched = evidence.finalAnswer().contains(criterion.expectedValue());
        return result(criterion, matched ? CriterionDecision.PASSED : CriterionDecision.FAILED,
                matched ? "ANSWER_CONTAINS_EXPECTED" : "ANSWER_MISSING_EXPECTED",
                List.of("answer"));
    }

    private AgentCriterionEvaluation evaluateJsonField(AgentSuccessCriterion criterion,
            OutcomeEvidence evidence) {
        if (!StringUtils.hasText(evidence.finalAnswer())) {
            return result(criterion, CriterionDecision.NOT_EVALUATED, "ANSWER_MISSING", List.of());
        }
        try {
            JsonNode rule = objectMapper.readTree(criterion.expectedValue());
            String pointer = rule == null ? null : rule.path("path").asText(null);
            JsonNode expected = rule == null ? null : rule.get("value");
            if (!StringUtils.hasText(pointer) || !pointer.startsWith("/") || expected == null) {
                return result(criterion, CriterionDecision.NOT_EVALUATED,
                        "INVALID_JSON_FIELD_RULE", List.of());
            }
            JsonNode answer = objectMapper.readTree(evidence.finalAnswer());
            JsonNode actual = answer == null ? null : answer.at(pointer);
            boolean matched = actual != null && !actual.isMissingNode() && actual.equals(expected);
            return result(criterion, matched ? CriterionDecision.PASSED : CriterionDecision.FAILED,
                    matched ? "JSON_FIELD_MATCHED" : "JSON_FIELD_MISMATCH",
                    List.of("answer#" + pointer));
        } catch (Exception ignored) {
            return result(criterion, CriterionDecision.NOT_EVALUATED,
                    "JSON_EVIDENCE_UNPARSABLE", List.of());
        }
    }

    private AgentCriterionEvaluation evaluateSetContains(AgentSuccessCriterion criterion,
            java.util.Set<String> values,
            boolean evidenceComplete,
            String referencePrefix) {
        boolean matched = values.contains(criterion.expectedValue());
        if (!matched && !evidenceComplete) {
            return result(criterion, CriterionDecision.NOT_EVALUATED,
                    "EVIDENCE_INCOMPLETE", List.of());
        }
        return result(criterion, matched ? CriterionDecision.PASSED : CriterionDecision.FAILED,
                matched ? "EXPECTED_EVIDENCE_FOUND" : "EXPECTED_EVIDENCE_NOT_FOUND",
                matched ? List.of(referencePrefix + ":" + criterion.expectedValue()) : List.of());
    }

    private AgentCriterionEvaluation evaluateRunStatus(AgentSuccessCriterion criterion,
            OutcomeEvidence evidence) {
        if (evidence.runStatus() == null) {
            return result(criterion, CriterionDecision.NOT_EVALUATED, "RUN_STATUS_MISSING", List.of());
        }
        boolean matched = evidence.runStatus().name().equals(criterion.expectedValue());
        return result(criterion, matched ? CriterionDecision.PASSED : CriterionDecision.FAILED,
                matched ? "RUN_STATUS_MATCHED" : "RUN_STATUS_MISMATCH", List.of("run.status"));
    }

    private AgentCriterionEvaluation result(AgentSuccessCriterion criterion,
            CriterionDecision decision,
            String reasonCode,
            List<String> evidenceReferences) {
        return new AgentCriterionEvaluation(
                criterion.criterionId(),
                criterion.type(),
                Boolean.TRUE.equals(criterion.required()),
                decision,
                EVALUATOR_VERSION,
                reasonCode,
                evidenceReferences);
    }

    private AgentOutcomeEvaluation unavailable(AgentTaskContract taskContract, String reasonCode) {
        List<AgentCriterionEvaluation> results = new ArrayList<>();
        for (AgentSuccessCriterion criterion : taskContract.criteria()) {
            results.add(result(criterion, CriterionDecision.NOT_EVALUATED, reasonCode, List.of()));
        }
        return new AgentOutcomeEvaluation(AgentOutcomeStatus.NOT_EVALUATED, EVALUATOR_ID,
                EVALUATOR_VERSION, reasonCode, results);
    }

    private AgentOutcomeStatus aggregate(List<AgentCriterionEvaluation> results) {
        List<AgentCriterionEvaluation> decisive = results.stream()
                .filter(AgentCriterionEvaluation::required)
                .toList();
        if (decisive.isEmpty()) {
            decisive = results;
        }
        if (decisive.stream().anyMatch(item -> item.decision() == CriterionDecision.FAILED)) {
            return AgentOutcomeStatus.NOT_ACHIEVED;
        }
        if (decisive.stream().anyMatch(item -> item.decision() == CriterionDecision.NOT_EVALUATED)) {
            return AgentOutcomeStatus.NOT_EVALUATED;
        }
        return AgentOutcomeStatus.ACHIEVED;
    }
}
