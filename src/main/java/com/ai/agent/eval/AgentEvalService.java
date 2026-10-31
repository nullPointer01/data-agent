package com.ai.agent.eval;

import com.ai.api.ListPageResponse;
import com.ai.api.ListPaging;
import com.ai.agent.DataAnalysisAgent;
import com.ai.agent.eval.AgentEvalMetricCalculator.MetricInput;
import com.ai.agent.eval.dto.AgentEvalReportResponse;
import com.ai.agent.eval.dto.AgentEvalReportResponse.AgentSnapshot;
import com.ai.agent.eval.dto.AgentEvalReportResponse.DatasetSummary;
import com.ai.agent.eval.dto.AgentEvalReportResponse.RunCounts;
import com.ai.agent.eval.dto.AgentEvalReportResponse.RunSummary;
import com.ai.agent.eval.dto.AgentEvalReportResponse.SampleResult;
import com.ai.agent.outcome.AgentOutcomeEvaluation;
import com.ai.agent.outcome.AgentOutcomeStatus;
import com.ai.agent.outcome.AgentTaskContract;
import com.ai.agent.runtime.AgentRunTerminationReason;
import com.ai.agent.runtime.AgentRuntimeProperties;
import com.ai.model.AgentExecutionTrace;
import com.ai.model.AgentProfile;
import com.ai.model.AnalysisRequest;
import com.ai.model.AnalysisResponse;
import com.ai.repository.AgentExecutionTraceRepository;
import com.ai.repository.AgentProfileRepository;
import com.ai.security.SecurityContextHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 运行版本化 Agent Eval，并持久化可下钻的样本级安全证据。
 *
 * @author data-agent
 */
@Service
public class AgentEvalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentEvalService.class);
    private static final int MAX_ERROR_LENGTH = 1024;
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+|api[_-]?key\\s*[=:]\\s*|sk-)[a-z0-9._-]{8,}");

    private final AgentEvalMapper evalMapper;
    private final TransactionTemplate transactionTemplate;
    private final DataAnalysisAgent dataAnalysisAgent;
    private final AgentProfileRepository agentProfileRepository;
    private final AgentExecutionTraceRepository traceRepository;
    private final AgentEvalMetricCalculator metricCalculator;
    private final AgentRuntimeProperties runtimeProperties;
    private final SecurityContextHelper securityContextHelper;
    private final ObjectMapper objectMapper;

    public AgentEvalService(AgentEvalMapper evalMapper, TransactionTemplate transactionTemplate,
            DataAnalysisAgent dataAnalysisAgent, AgentProfileRepository agentProfileRepository,
            AgentExecutionTraceRepository traceRepository, AgentEvalMetricCalculator metricCalculator,
            AgentRuntimeProperties runtimeProperties, SecurityContextHelper securityContextHelper,
            ObjectMapper objectMapper) {
        this.evalMapper = evalMapper;
        this.transactionTemplate = transactionTemplate;
        this.dataAnalysisAgent = dataAnalysisAgent;
        this.agentProfileRepository = agentProfileRepository;
        this.traceRepository = traceRepository;
        this.metricCalculator = metricCalculator;
        this.runtimeProperties = runtimeProperties;
        this.securityContextHelper = securityContextHelper;
        this.objectMapper = objectMapper;
    }

    /** 创建等待后台执行的评测运行。 */
    public AgentEvalReportResponse startEvaluation(String datasetId, String agentId) {
        String tenantId = requireIdentity(securityContextHelper.getCurrentTenantId(), "租户身份");
        String userId = requireIdentity(securityContextHelper.getCurrentUserId(), "用户身份");
        String evalRunId = transactionTemplate.execute(status -> {
            AgentEvalDatasetEntity dataset = requireReadyDataset(datasetId, tenantId);
            AgentProfile profile = requireEnabledAgent(agentId, tenantId);
            long sampleCount = countEnabledSamples(dataset);
            if (sampleCount == 0L) {
                throw new IllegalArgumentException("评测数据集没有启用样本");
            }
            AgentEvalRunEntity run = new AgentEvalRunEntity();
            run.setEvalRunId(UUID.randomUUID().toString());
            run.setTenantId(tenantId);
            run.setDatasetId(dataset.getDatasetId());
            run.setDatasetVersion(dataset.getDatasetVersion());
            run.setAgentId(profile.getAgentId());
            run.setAgentProfileVersion(profileVersion(profile));
            run.setModelId(profile.getModelId());
            run.setHarnessConfigIdentity(harnessIdentity());
            run.setStatus("PENDING");
            run.setTotalSamples(Math.toIntExact(sampleCount));
            run.setCreatedBy(userId);
            run.onCreate();
            evalMapper.insertRun(run);
            return run.getEvalRunId();
        });
        return getReport(evalRunId);
    }

    /** 在已传播认证上下文的后台线程中执行固定样本。 */
    public void executeEvaluation(String evalRunId) {
        String tenantId = requireIdentity(securityContextHelper.getCurrentTenantId(), "租户身份");
        try {
            EvaluationPlan plan = transactionTemplate.execute(status -> claim(evalRunId, tenantId));
            if (plan == null) {
                return;
            }
            List<MetricInput> metricInputs = new ArrayList<>();
            for (AgentEvalSampleEntity sample : plan.samples()) {
                ensureAgentVersion(plan, tenantId);
                AgentEvalResultEntity result = executeSample(plan, sample);
                transactionTemplate.executeWithoutResult(status -> persistResult(plan.runId(), result));
                metricInputs.add(toMetricInput(sample, result));
            }
            AgentEvalReportResponse.Metrics metrics = metricCalculator.calculate(metricInputs);
            transactionTemplate.executeWithoutResult(status -> complete(plan.runId(), metrics));
        } catch (Exception exception) {
            String safeError = safeError(exception);
            LOGGER.error("Agent Eval 执行失败: evalRunId={}, error={}", evalRunId, safeError);
            markFailed(evalRunId, safeError);
        }
    }

    /** 将无法提交到后台队列的运行标记为失败。 */
    public void markFailed(String evalRunId, String detail) {
        String tenantId = requireIdentity(securityContextHelper.getCurrentTenantId(), "租户身份");
        transactionTemplate.executeWithoutResult(status -> {
            AgentEvalRunEntity run = findRun(evalRunId, tenantId);
            if (!"COMPLETED".equals(run.getStatus())) {
                run.setStatus("FAILED");
                run.setErrorSummary(safeText(detail));
                run.setCompletedAt(Instant.now());
                evalMapper.saveRun(run);
            }
        });
    }

    /** 查询当前租户可执行的 READY 数据集。 */
    public ListPageResponse<DatasetSummary> listDatasets(int page, int size) {
        ListPaging.page(page, size);
        String tenantId = requireIdentity(securityContextHelper.getCurrentTenantId(), "租户身份");
        return transactionTemplate.execute(status -> ListPageResponse.fetch(page, size,
                evalMapper.countReadyDatasets(tenantId), pageable -> evalMapper.listReadyDatasets(tenantId, pageable))
                .map(dataset -> datasetSummary(dataset, countEnabledSamples(dataset))));
    }

    /** 查询最近的评测运行摘要。 */
    public ListPageResponse<RunSummary> listRuns(int page, int size) {
        ListPaging.page(page, size);
        String tenantId = requireIdentity(securityContextHelper.getCurrentTenantId(), "租户身份");
        return transactionTemplate.execute(status -> ListPageResponse.fetch(page, size,
                evalMapper.countRuns(tenantId), pageable -> evalMapper.listRuns(tenantId, pageable))
                .map(this::runSummary));
    }

    /** 查询评测报告和样本级 Run 证据引用。 */
    public AgentEvalReportResponse getReport(String evalRunId) {
        String tenantId = requireIdentity(securityContextHelper.getCurrentTenantId(), "租户身份");
        return transactionTemplate.execute(status -> buildReport(findRun(evalRunId, tenantId)));
    }

    private EvaluationPlan claim(String evalRunId, String tenantId) {
        AgentEvalRunEntity run = evalMapper.lockRun(requireIdentity(evalRunId, "评测运行 ID"), tenantId);
        if (run == null || !tenantId.equals(run.getTenantId())) {
            throw new IllegalArgumentException("评测运行不存在或无权限");
        }
        if (!"PENDING".equals(run.getStatus())) {
            return null;
        }
        AgentProfile profile = requireEnabledAgent(run.getAgentId(), tenantId);
        if (!run.getAgentProfileVersion().equals(profileVersion(profile))) {
            throw new IllegalStateException("Agent 配置在评测开始前已变化，请重新发起评测");
        }
        run.setStatus("RUNNING");
        List<AgentEvalSampleEntity> samples = loadSamples(run.getDatasetId(), run.getDatasetVersion(), tenantId);
        if (samples.size() != run.getTotalSamples()) {
            throw new IllegalStateException("评测数据集在排队期间发生变化，请重新发起评测");
        }
        evalMapper.saveRun(run);
        return new EvaluationPlan(run.getEvalRunId(), run.getAgentId(), run.getAgentProfileVersion(), samples);
    }

    private AgentEvalResultEntity executeSample(EvaluationPlan plan, AgentEvalSampleEntity sample) {
        try {
            AgentTaskContract contract = objectMapper.readValue(sample.getTaskContractJson(), AgentTaskContract.class);
            AnalysisRequest request = new AnalysisRequest();
            request.setQuestion(sample.getQuestion());
            request.setAgentId(plan.agentId());
            request.setTaskContract(contract);
            AnalysisResponse response = dataAnalysisAgent.analyze(request);
            return resultFromResponse(plan.runId(), sample, response);
        } catch (Exception exception) {
            return failedResult(plan.runId(), sample, safeError(exception));
        }
    }

    private AgentEvalResultEntity resultFromResponse(String evalRunId, AgentEvalSampleEntity sample,
            AnalysisResponse response) {
        TraceEvidence evidence = readTraceEvidence(response, sample.getTenantId());
        AgentEvalResultEntity result = baseResult(evalRunId, sample);
        AgentOutcomeStatus outcome = response == null ? AgentOutcomeStatus.NOT_EVALUATED : response.getOutcomeStatus();
        result.setAgentRunId(response == null ? null : response.getRunId());
        result.setTraceId(response == null ? null : response.getTraceId());
        result.setObservedIntent(evidence.intent());
        result.setObservedMode(evidence.mode());
        result.setObservedContextPlanJson(evidence.contextPlanJson());
        result.setOutcomeStatus(outcome.name());
        result.setReasonCode(response != null && response.getOutcomeEvaluation() != null
                ? response.getOutcomeEvaluation().reasonCode() : "OUTCOME_EVIDENCE_MISSING");
        result.setOutcomeEvaluationJson(writeJson(response == null ? null : response.getOutcomeEvaluation()));
        result.setObservedToolsJson(evidence.completeTools() ? writeJson(evidence.tools()) : null);
        result.setApprovalObserved(evidence.approvalObserved());
        result.setInvalidLoopObserved(evidence.invalidLoopObserved());
        result.setDurationMs(evidence.durationMs() == null ? 0L : evidence.durationMs());
        result.setTokenUsage(evidence.tokenUsage() == null ? 0L : evidence.tokenUsage());
        result.setTokenUsageEstimated(response != null && response.getRunUsage() != null
                && response.getRunUsage().tokenUsageEstimated());
        result.setErrorSummary(response == null ? "Agent 未返回响应" : safeText(response.getError()));
        return result;
    }

    private TraceEvidence readTraceEvidence(AnalysisResponse response, String tenantId) {
        if (response == null || !StringUtils.hasText(response.getRunId())) {
            return TraceEvidence.missing();
        }
        AgentExecutionTrace trace = traceRepository.findByTraceIdAndTenantId(response.getRunId(), tenantId)
                .orElse(null);
        JsonNode governance = readJson(trace == null ? null : trace.getSharedContextJson()).path("toolGovernance");
        JsonNode requestPlan = readJson(trace == null ? null : trace.getPlanJson()).path("requestPlan");
        JsonNode records = governance.path("records");
        Set<String> tools = new TreeSet<>();
        boolean approvalInJournal = false;
        if (records.isArray()) {
            for (JsonNode record : records) {
                String toolName = record.path("toolName").asText("").trim();
                if (!toolName.isEmpty()) {
                    tools.add(toolName);
                }
                approvalInJournal |= "APPROVAL_REQUIRED".equals(record.path("status").asText());
            }
        }
        boolean completeTools = records.isArray() && governance.path("overflowCount").asLong(1L) == 0L;
        Boolean approval = StringUtils.hasText(response.getApprovalId()) || approvalInJournal;
        Boolean invalidLoop = StringUtils.hasText(response.getTerminationReason())
                ? AgentRunTerminationReason.ITERATION_LIMIT.name().equals(response.getTerminationReason()) : null;
        Long duration = response.getRunUsage() == null ? null : response.getRunUsage().durationMs();
        Long tokens = response.getRunUsage() == null ? null : response.getRunUsage().tokens();
        String intent = trace == null || !StringUtils.hasText(trace.getIntent()) ? null : trace.getIntent();
        String mode = trace == null || !StringUtils.hasText(trace.getSelectedType())
                ? null : trace.getSelectedType();
        String contextPlanJson = requestPlan.path("contextPlan").isObject()
                ? requestPlan.path("contextPlan").toString() : null;
        return new TraceEvidence(List.copyOf(tools), completeTools, approval, invalidLoop,
                duration, tokens, intent, mode, contextPlanJson);
    }

    private AgentEvalResultEntity failedResult(String evalRunId, AgentEvalSampleEntity sample, String error) {
        AgentEvalResultEntity result = baseResult(evalRunId, sample);
        result.setOutcomeStatus(AgentOutcomeStatus.NOT_EVALUATED.name());
        result.setReasonCode("EVAL_SAMPLE_EXECUTION_FAILED");
        result.setErrorSummary(error);
        return result;
    }

    private AgentEvalResultEntity baseResult(String evalRunId, AgentEvalSampleEntity sample) {
        AgentEvalResultEntity result = new AgentEvalResultEntity();
        result.setTenantId(sample.getTenantId());
        result.setEvalRunId(evalRunId);
        result.setSampleId(sample.getSampleId());
        result.setSampleKey(sample.getSampleKey());
        return result;
    }

    private void persistResult(String evalRunId, AgentEvalResultEntity result) {
        result.onCreate();
        evalMapper.insertResult(result);
        AgentEvalRunEntity run = evalMapper.findRun(evalRunId, securityContextHelper.getCurrentTenantId());
        run.setCompletedSamples(run.getCompletedSamples() + 1);
        switch (AgentOutcomeStatus.valueOf(result.getOutcomeStatus())) {
            case ACHIEVED -> run.setPassedSamples(run.getPassedSamples() + 1);
            case NOT_ACHIEVED -> run.setFailedSamples(run.getFailedSamples() + 1);
            case NOT_EVALUATED -> run.setNotEvaluatedSamples(run.getNotEvaluatedSamples() + 1);
        }
        evalMapper.saveRun(run);
    }

    private void complete(String evalRunId, AgentEvalReportResponse.Metrics metrics) {
        AgentEvalRunEntity run = evalMapper.findRun(evalRunId, securityContextHelper.getCurrentTenantId());
        run.setMetricsJson(writeJson(metrics));
        run.setStatus("COMPLETED");
        run.setCompletedAt(Instant.now());
        evalMapper.saveRun(run);
    }

    private AgentEvalReportResponse buildReport(AgentEvalRunEntity run) {
        AgentEvalDatasetEntity dataset = evalMapper.findDataset(run.getDatasetId(), run.getTenantId());
        return new AgentEvalReportResponse(!"FAILED".equals(run.getStatus()), run.getEvalRunId(), run.getStatus(),
                datasetSummary(dataset, run.getTotalSamples()),
                new AgentSnapshot(run.getAgentId(), run.getAgentProfileVersion(), run.getModelId(),
                        run.getHarnessConfigIdentity()), counts(run), readMetrics(run),
                run.getErrorSummary(), run.getStartedAt(), run.getCompletedAt());
    }

    /** 只读取当前页固定样本及其结果；审批、工具、Token 证据语义保持完整。 */
    public ListPageResponse<SampleResult> listSampleResults(String evalRunId, int page, int size) {
        ListPaging.page(page, size);
        String tenant = requireIdentity(securityContextHelper.getCurrentTenantId(), "租户身份");
        return transactionTemplate.execute(status -> {
            AgentEvalRunEntity run = findRun(evalRunId, tenant);
            var samples = ListPageResponse.fetch(page, size,
                    evalMapper.countEnabledSamples(run.getDatasetId(), run.getDatasetVersion(), tenant),
                    pageable -> evalMapper.loadSamplePage(run.getDatasetId(), run.getDatasetVersion(), tenant, pageable));
            Map<String, AgentEvalResultEntity> results = new LinkedHashMap<>();
            if (!samples.items().isEmpty()) {
                evalMapper.listPageResults(run.getEvalRunId(), tenant,
                        samples.items().stream().map(AgentEvalSampleEntity::getSampleId).toList())
                        .forEach(result -> results.put(result.getSampleId(), result));
            }
            return samples.map(sample -> sampleResult(sample, results.get(sample.getSampleId())));
        });
    }

    private AgentEvalReportResponse.Metrics readMetrics(AgentEvalRunEntity run) {
        if (!"COMPLETED".equals(run.getStatus())) {
            return metricCalculator.unavailableMetrics(run.getTotalSamples(), "EVAL_RUN_" + run.getStatus());
        }
        try {
            var stored = objectMapper.readValue(run.getMetricsJson(), AgentEvalReportResponse.Metrics.class);
            if (stored != null) return stored;
        } catch (Exception ignored) {
            // 仅旧报告没有完整指标快照时按整个固定数据集重算，不能用当前页计算总体指标。
        }
        var samples = loadSamples(run.getDatasetId(), run.getDatasetVersion(), run.getTenantId());
        Map<String, AgentEvalResultEntity> results = new LinkedHashMap<>();
        evalMapper.listResults(run.getEvalRunId(), run.getTenantId())
                .forEach(result -> results.put(result.getSampleId(), result));
        return metricCalculator.calculate(samples.stream()
                .map(sample -> toMetricInput(sample, results.get(sample.getSampleId()))).toList());
    }

    private SampleResult sampleResult(AgentEvalSampleEntity sample, AgentEvalResultEntity result) {
        if (result == null) {
            return new SampleResult(sample.getSampleId(), sample.getSampleKey(), sample.getQuestion(),
                    "PENDING", null, null,
                    sample.getExpectedIntent(), null, sample.getExpectedMode(), null,
                    readJsonMap(sample.getExpectedContextPlanJson()), null,
                    readStringList(sample.getExpectedToolsJson()), null,
                    sample.getExpectedApprovalRequired(), null, sample.getInvalidLoopExpected(), null,
                    null, null, false, null, null, null);
        }
        return new SampleResult(sample.getSampleId(), sample.getSampleKey(), sample.getQuestion(),
                result.getOutcomeStatus(), result.getReasonCode(), readOutcome(result.getOutcomeEvaluationJson()),
                sample.getExpectedIntent(), result.getObservedIntent(),
                sample.getExpectedMode(), result.getObservedMode(),
                readJsonMap(sample.getExpectedContextPlanJson()),
                readJsonMap(result.getObservedContextPlanJson()),
                readStringList(sample.getExpectedToolsJson()), readStringList(result.getObservedToolsJson()),
                sample.getExpectedApprovalRequired(), result.getApprovalObserved(), sample.getInvalidLoopExpected(),
                result.getInvalidLoopObserved(), hasRun(result) ? result.getDurationMs() : null,
                hasRun(result) ? result.getTokenUsage() : null, result.isTokenUsageEstimated(),
                result.getAgentRunId(), result.getTraceId(), result.getErrorSummary());
    }

    private MetricInput toMetricInput(AgentEvalSampleEntity sample, AgentEvalResultEntity result) {
        if (result == null) {
            return new MetricInput(null, readStringList(sample.getExpectedToolsJson()), null,
                    sample.getExpectedApprovalRequired(), null, sample.getInvalidLoopExpected(), null, null, null,
                    sample.getExpectedIntent(), null, sample.getExpectedMode(), null,
                    readJsonMap(sample.getExpectedContextPlanJson()), null);
        }
        return new MetricInput(readOutcomeStatus(result.getOutcomeStatus()),
                readStringList(sample.getExpectedToolsJson()), readStringList(result.getObservedToolsJson()),
                sample.getExpectedApprovalRequired(), result.getApprovalObserved(), sample.getInvalidLoopExpected(),
                result.getInvalidLoopObserved(), hasRun(result) ? result.getDurationMs() : null,
                hasRun(result) ? result.getTokenUsage() : null,
                sample.getExpectedIntent(), result.getObservedIntent(),
                sample.getExpectedMode(), result.getObservedMode(),
                readJsonMap(sample.getExpectedContextPlanJson()),
                readJsonMap(result.getObservedContextPlanJson()));
    }

    private AgentEvalDatasetEntity requireReadyDataset(String datasetId, String tenantId) {
        AgentEvalDatasetEntity dataset = evalMapper.findDataset(requireIdentity(datasetId, "数据集 ID"), tenantId);
        if (dataset == null || !tenantId.equals(dataset.getTenantId()) || !"READY".equals(dataset.getStatus())) {
            throw new IllegalArgumentException("评测数据集不存在、不可用或无权限");
        }
        return dataset;
    }

    private AgentProfile requireEnabledAgent(String agentId, String tenantId) {
        AgentProfile profile = agentProfileRepository.findByAgentIdAndTenantId(
                requireIdentity(agentId, "Agent ID"), tenantId).orElseThrow(
                () -> new IllegalArgumentException("Agent 不存在或无权限"));
        if (!profile.isEnabled()) {
            throw new IllegalArgumentException("Agent 已停用");
        }
        return profile;
    }

    private void ensureAgentVersion(EvaluationPlan plan, String tenantId) {
        AgentProfile current = requireEnabledAgent(plan.agentId(), tenantId);
        if (!plan.agentProfileVersion().equals(profileVersion(current))) {
            throw new IllegalStateException("Agent 配置在评测执行期间发生变化，已停止剩余样本");
        }
    }

    private AgentEvalRunEntity findRun(String evalRunId, String tenantId) {
        AgentEvalRunEntity run = evalMapper.findRun(requireIdentity(evalRunId, "评测运行 ID"), tenantId);
        if (run == null || !tenantId.equals(run.getTenantId())) {
            throw new IllegalArgumentException("评测运行不存在或无权限");
        }
        return run;
    }

    private List<AgentEvalSampleEntity> loadSamples(String datasetId, int version, String tenantId) {
        return evalMapper.loadSamples(datasetId, version, tenantId);
    }

    private long countEnabledSamples(AgentEvalDatasetEntity dataset) {
        return evalMapper.countEnabledSamples(dataset.getDatasetId(), dataset.getDatasetVersion(), dataset.getTenantId());
    }

    private DatasetSummary datasetSummary(AgentEvalDatasetEntity dataset, long sampleCount) {
        return new DatasetSummary(dataset.getDatasetId(), dataset.getName(), dataset.getDescription(),
                dataset.getDatasetVersion(), dataset.getStatus(), sampleCount);
    }

    private RunSummary runSummary(AgentEvalRunEntity run) {
        return new RunSummary(run.getEvalRunId(), run.getStatus(), run.getDatasetId(), run.getDatasetVersion(),
                run.getAgentId(), run.getModelId(), counts(run), run.getStartedAt(), run.getCompletedAt());
    }

    private RunCounts counts(AgentEvalRunEntity run) {
        return new RunCounts(run.getTotalSamples(), run.getCompletedSamples(), run.getPassedSamples(),
                run.getFailedSamples(), run.getNotEvaluatedSamples());
    }

    private String harnessIdentity() {
        var limits = runtimeProperties.toLimits();
        return "runtime-v1:t=" + limits.timeout().toMillis() + ";i=" + limits.maxIterations()
                + ";m=" + limits.maxModelCalls() + ";tool=" + limits.maxToolCalls() + ";tok=" + limits.maxTokens();
    }

    private String profileVersion(AgentProfile profile) {
        return profile.getUpdatedAt() == null ? "legacy" : profile.getUpdatedAt().toString();
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode array = objectMapper.readTree(json);
            if (array == null || !array.isArray()) {
                return null;
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : array) {
                if (!item.isTextual() || !StringUtils.hasText(item.asText())) {
                    return null;
                }
                values.add(item.asText().trim());
            }
            return values.stream().distinct().toList();
        } catch (Exception ignored) {
            return null;
        }
    }

    private AgentOutcomeEvaluation readOutcome(String json) {
        try {
            return StringUtils.hasText(json) ? objectMapper.readValue(json, AgentOutcomeEvaluation.class) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private AgentOutcomeStatus readOutcomeStatus(String value) {
        try {
            return AgentOutcomeStatus.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode readJson(String json) {
        try {
            return StringUtils.hasText(json) ? objectMapper.readTree(json) : objectMapper.createObjectNode();
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private Map<String, String> readJsonMap(String json) {
        JsonNode root = readJson(json);
        if (!root.isObject()) {
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>();
        root.fields().forEachRemaining(entry -> {
            if (entry.getValue().isTextual()) {
                values.put(entry.getKey(), entry.getValue().asText());
            }
        });
        return values.isEmpty() ? null : Map.copyOf(values);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("评测证据序列化失败", exception);
        }
    }

    private boolean hasRun(AgentEvalResultEntity result) {
        return StringUtils.hasText(result.getAgentRunId());
    }

    private String requireIdentity(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }

    private String safeError(Exception exception) {
        return safeText(exception == null ? null : exception.getMessage());
    }

    private String safeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sanitized = SECRET_PATTERN.matcher(value).replaceAll("$1[REDACTED]");
        return sanitized.length() <= MAX_ERROR_LENGTH ? sanitized : sanitized.substring(0, MAX_ERROR_LENGTH);
    }

    private record EvaluationPlan(String runId, String agentId, String agentProfileVersion,
            List<AgentEvalSampleEntity> samples) {
    }

    private record TraceEvidence(List<String> tools, boolean completeTools, Boolean approvalObserved,
            Boolean invalidLoopObserved, Long durationMs, Long tokenUsage,
            String intent, String mode, String contextPlanJson) {
        private static TraceEvidence missing() {
            return new TraceEvidence(List.of(), false, null, null, null, null,
                    null, null, null);
        }
    }
}
