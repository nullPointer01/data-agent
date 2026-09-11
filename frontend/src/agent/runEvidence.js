const MAX_TIMELINE_EVENTS = 80;
const MAX_EVENT_SUMMARY_LENGTH = 1200;

const TERMINAL_STATUSES = new Set([
  'COMPLETED',
  'FAILED',
  'CANCELLED',
  'TIMED_OUT',
  'BUDGET_EXHAUSTED',
  'REJECTED',
  'EXPIRED'
]);

function safeText(value, maximum = MAX_EVENT_SUMMARY_LENGTH) {
  if (value === null || value === undefined) {
    return '';
  }
  const text = typeof value === 'string' ? value : JSON.stringify(value);
  return text.length > maximum ? `${text.slice(0, maximum)}...` : text;
}

function numberOrZero(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function normalizeContextEvidence(value) {
  if (!value || typeof value !== 'object') return null;
  if (!('decision' in value) && !('estimatedTokensBefore' in value)) return null;
  return {
    decision: safeText(value.decision, 32) || 'ADMITTED',
    reason: safeText(value.reason, 80) || 'WITHIN_BUDGET',
    strategy: safeText(value.strategy, 80) || 'NONE',
    estimatedTokensBefore: numberOrZero(value.estimatedTokensBefore),
    estimatedTokensAfter: numberOrZero(value.estimatedTokensAfter),
    originalMessageCount: numberOrZero(value.originalMessageCount),
    retainedMessageCount: numberOrZero(value.retainedMessageCount),
    protectedMessageCount: numberOrZero(value.protectedMessageCount),
    modelWindowTokens: numberOrZero(value.modelWindowTokens),
    runRemainingTokens: numberOrZero(value.runRemainingTokens),
    inputTokenLimit: numberOrZero(value.inputTokenLimit),
    tokenEstimateUsed: value.tokenEstimateUsed !== false
  };
}

function normalizeUsage(value, fallback = {}) {
  const usage = value && typeof value === 'object' ? value : {};
  return {
    status: usage.status || fallback.status || '',
    terminationReason: usage.terminationReason || fallback.terminationReason || '',
    detail: safeText(usage.detail || fallback.detail || '', 320),
    startedAt: usage.startedAt || fallback.startedAt || '',
    deadline: usage.deadline || fallback.deadline || '',
    completedAt: usage.completedAt || fallback.completedAt || '',
    durationMs: numberOrZero(usage.durationMs ?? fallback.durationMs),
    iterations: numberOrZero(usage.iterations ?? fallback.iterations),
    modelCalls: numberOrZero(usage.modelCalls ?? fallback.modelCalls),
    toolCalls: numberOrZero(usage.toolCalls ?? fallback.toolCalls),
    tokens: numberOrZero(usage.tokens ?? fallback.tokens),
    tokenUsageEstimated: Boolean(usage.tokenUsageEstimated ?? fallback.tokenUsageEstimated),
    tokenBudgetOvershoot: numberOrZero(usage.tokenBudgetOvershoot ?? fallback.tokenBudgetOvershoot),
    remainingActiveTimeoutMs: numberOrZero(
      usage.remainingActiveTimeoutMs ?? fallback.remainingActiveTimeoutMs
    )
  };
}

function normalizeOutcomeEvaluation(value) {
  if (!value || typeof value !== 'object') return null;
  return {
    status: safeText(value.status, 48) || 'NOT_EVALUATED',
    evaluatorId: safeText(value.evaluatorId, 80),
    evaluatorVersion: safeText(value.evaluatorVersion, 40),
    reasonCode: safeText(value.reasonCode, 120),
    criteria: (Array.isArray(value.criteria) ? value.criteria : []).slice(0, 20).map((criterion) => ({
      criterionId: safeText(criterion?.criterionId, 64),
      criterionType: safeText(criterion?.criterionType, 48),
      required: criterion?.required !== false,
      decision: safeText(criterion?.decision, 48) || 'NOT_EVALUATED',
      evaluatorVersion: safeText(criterion?.evaluatorVersion, 40),
      reasonCode: safeText(criterion?.reasonCode, 120),
      evidenceReferences: (Array.isArray(criterion?.evidenceReferences) ? criterion.evidenceReferences : [])
        .slice(0, 8)
        .map((reference) => safeText(reference, 120))
        .filter(Boolean)
    }))
  };
}

function describeRetrieval(event) {
  if (event.retrievalAvailable === false) {
    return '知识检索服务暂不可用';
  }
  const hitCount = numberOrZero(event.hitCount ?? event.citations?.length);
  return hitCount > 0 ? `命中 ${hitCount} 条知识证据` : '未命中可用知识证据';
}

function describeBudget(event) {
  const usage = normalizeUsage(event.usage);
  return `模型 ${usage.modelCalls} 次 · 工具 ${usage.toolCalls} 次 · Token ${usage.tokens}`;
}

function timelineEvent(event) {
  const common = {
    type: event.type,
    eventTime: event.eventTime || '',
    status: event.status || event.usage?.status || '',
    title: '',
    summary: '',
    approvalId: event.approvalId || '',
    details: normalizeContextEvidence(event.evidence || event.details)
  };

  switch (event.type) {
    case 'run_started':
      return { ...common, title: '运行已开始', summary: `执行模式：${event.mode || 'AUTO'}` };
    case 'rag_context':
      return { ...common, title: '知识检索', summary: describeRetrieval(event) };
    case 'thinking_start':
      return {
        ...common,
        title: `执行第 ${numberOrZero(event.iteration) || 1} 轮`,
        summary: '模型正在选择下一步动作'
      };
    case 'execution_plan':
      return { ...common, title: event.title || '执行计划', summary: safeText(event.content) };
    case 'parallel_precheck':
      return { ...common, title: event.title || '并行预检', summary: safeText(event.content) };
    case 'orchestration':
      return { ...common, title: event.title || '编排任务', summary: safeText(event.content) };
    case 'tool_call':
      return {
        ...common,
        title: `工具 · ${event.toolName || 'unknown'}`,
        summary: safeText(event.result || event.status || '工具调用已完成')
      };
    case 'approval_required':
      return {
        ...common,
        title: `等待审批 · ${event.toolName || '工具动作'}`,
        summary: safeText(event.argumentSummary || `风险等级：${event.risk || 'UNKNOWN'}`)
      };
    case 'reflection':
      return { ...common, title: '执行策略已调整', summary: 'Agent 根据上一轮结果调整了后续动作' };
    case 'budget_updated':
      return { ...common, title: '运行预算已更新', summary: describeBudget(event) };
    case 'context_governed': {
      const evidence = normalizeContextEvidence(event.evidence || event.details);
      const reduced = evidence?.decision === 'REDUCED';
      const rejected = evidence?.decision === 'REJECTED';
      return {
        ...common,
        status: evidence?.decision || common.status,
        title: rejected ? '上下文被拒绝' : reduced ? '上下文已压缩' : '上下文未压缩',
        summary: evidence
          ? `Token ${evidence.estimatedTokensBefore} -> ${evidence.estimatedTokensAfter} · 保留 ${evidence.retainedMessageCount}/${evidence.originalMessageCount} 条消息`
          : '上下文治理已完成',
        details: evidence
      };
    }
    case 'resume':
      return {
        ...common,
        title: event.title || '恢复执行',
        summary: safeText(event.summary || event.detail || event.status)
      };
    case 'error':
      return { ...common, title: '运行出现错误', summary: safeText(event.content || event.message) };
    case 'done': {
      const status = event.status || event.usage?.status || '';
      const paused = status === 'WAITING_APPROVAL';
      return {
        ...common,
        status,
        title: paused ? '运行已暂停' : '运行已结束',
        summary: safeText(event.detail || event.usage?.detail || event.terminationReason || status)
      };
    }
    default:
      return null;
  }
}

function appendTimeline(events, nextEvent) {
  if (!nextEvent) {
    return events;
  }
  const current = Array.isArray(events) ? events : [];
  const last = current[current.length - 1];
  if (nextEvent.type === 'budget_updated' && last?.type === 'budget_updated') {
    return [...current.slice(0, -1), nextEvent];
  }
  if (nextEvent.type === 'done' && last?.type === 'done' && last.status === nextEvent.status) {
    return current;
  }
  return [...current, nextEvent].slice(-MAX_TIMELINE_EVENTS);
}

function normalizePersistedEvent(event) {
  if (!event || typeof event !== 'object') {
    return null;
  }
  return {
    type: safeText(event.type, 48),
    eventTime: safeText(event.eventTime, 80),
    status: safeText(event.status, 48),
    title: safeText(event.title, 160),
    summary: safeText(event.summary),
    approvalId: safeText(event.approvalId, 80),
    details: normalizeContextEvidence(event.details)
  };
}

export function normalizePersistedRunEvidence(value) {
  if (!value || typeof value !== 'object' || !value.runId) {
    return null;
  }
  const status = safeText(value.status, 48);
  const usage = normalizeUsage(value.usage, {
    status,
    terminationReason: value.terminationReason,
    durationMs: value.durationMs,
    iterations: value.usedIterations,
    modelCalls: value.usedModelCalls,
    toolCalls: value.usedToolCalls,
    tokens: value.usedTokens,
    tokenUsageEstimated: value.tokenUsageEstimated,
    remainingActiveTimeoutMs: value.remainingActiveTimeoutMs,
    startedAt: value.startedAt,
    completedAt: value.completedAt
  });
  const events = (Array.isArray(value.events) ? value.events : [])
    .map(normalizePersistedEvent)
    .filter(Boolean)
    .slice(-MAX_TIMELINE_EVENTS);
  return {
    runId: safeText(value.runId, 80),
    traceId: safeText(value.traceId, 80),
    approvalId: safeText(value.approvalId, 80),
    mode: safeText(value.executionMode, 48),
    status,
    terminationReason: safeText(value.terminationReason, 80),
    detail: safeText(value.statusDetail, 320),
    usage,
    startedAt: safeText(value.startedAt, 80),
    completedAt: safeText(value.completedAt, 80),
    waitingApproval: status === 'WAITING_APPROVAL',
    terminal: TERMINAL_STATUSES.has(status),
    source: 'history',
    evidenceSource: safeText(value.evidenceSource, 48),
    eventHistoryComplete: value.eventHistoryComplete === true,
    taskContractPresent: value.taskContractPresent === true,
    outcomeEvaluation: normalizeOutcomeEvaluation(value.outcomeEvaluation),
    contextEvidence: events.filter((event) => event.type === 'context_governed' && event.details).at(-1)?.details || null,
    events
  };
}

export function reduceRunEvidence(current, event) {
  if (!event || typeof event !== 'object') {
    return current || null;
  }
  const eventRunId = safeText(event.runId, 80);
  const currentRunId = current?.runId || '';
  if (!eventRunId && !currentRunId) {
    return current || null;
  }

  const base = eventRunId && currentRunId && eventRunId !== currentRunId ? {} : (current || {});
  const usage = normalizeUsage(event.usage, base.usage);
  const status = event.status || usage.status || base.status || '';
  const terminal = event.type === 'done' && TERMINAL_STATUSES.has(status);
  const waitingApproval = status === 'WAITING_APPROVAL';
  const outcomeEvaluation = normalizeOutcomeEvaluation(event.outcomeEvaluation) || base.outcomeEvaluation || null;
  const eventContextEvidence = event.type === 'context_governed'
    ? normalizeContextEvidence(event.evidence || event.details)
    : null;

  return {
    runId: eventRunId || currentRunId,
    traceId: safeText(event.traceId, 80) || base.traceId || '',
    approvalId: safeText(event.approvalId, 80) || base.approvalId || '',
    mode: event.mode || base.mode || '',
    status,
    terminationReason: event.terminationReason || usage.terminationReason || base.terminationReason || '',
    detail: safeText(event.detail || usage.detail || base.detail || '', 320),
    limits: event.limits || base.limits || null,
    usage,
    startedAt: usage.startedAt || base.startedAt || (event.type === 'run_started' ? event.eventTime : ''),
    completedAt: usage.completedAt || base.completedAt || (terminal ? event.eventTime : ''),
    waitingApproval,
    terminal,
    source: 'live',
    taskContractPresent: event.taskContractPresent === true
      || base.taskContractPresent === true
      || (outcomeEvaluation != null && outcomeEvaluation.reasonCode !== 'TASK_CONTRACT_MISSING'),
    outcomeEvaluation,
    contextEvidence: eventContextEvidence || base.contextEvidence || null,
    events: appendTimeline(base.events, timelineEvent(event))
  };
}
