import {
  Activity,
  BookOpenCheck,
  Bot,
  CheckCircle2,
  CircleAlert,
  CircleStop,
  Cpu,
  Gauge,
  GitBranch,
  Hourglass,
  Layers3,
  RefreshCcw,
  Route,
  ShieldCheck,
  Timer,
  Wrench
} from 'lucide-react';
import { Badge } from './ui.jsx';

const STATUS_LABELS = {
  CREATED: '已创建',
  RUNNING: '运行中',
  WAITING_APPROVAL: '等待审批',
  RESUMING: '恢复中',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
  TIMED_OUT: '已超时',
  BUDGET_EXHAUSTED: '预算耗尽',
  REJECTED: '已拒绝',
  EXPIRED: '已过期'
};

const MODE_LABELS = {
  CHAT: 'Chat',
  REACT: 'ReAct',
  ORCHESTRATED: 'Orchestrated'
};

const OUTCOME_LABELS = {
  ACHIEVED: '目标已达成',
  NOT_ACHIEVED: '目标未达成',
  NOT_EVALUATED: '无法评估'
};

const CRITERION_LABELS = {
  ANSWER_CONTAINS: '回答包含文本',
  JSON_FIELD_EQUALS: 'JSON 字段等于',
  TOOL_CALLED: '调用指定工具',
  APPROVAL_STATUS: '审批状态',
  RUN_STATUS: '运行状态'
};

const CONTEXT_DECISION_LABELS = {
  ADMITTED: '未压缩',
  REDUCED: '已压缩',
  REJECTED: '已拒绝'
};

const CONTEXT_REASON_LABELS = {
  WITHIN_BUDGET: '上下文在预算内',
  COMPACTION_DISABLED: '上下文超限且压缩未开启',
  CONSERVATIVE_TRIM_APPLIED: '已执行保守裁剪',
  PROTECTED_CONTEXT_EXCEEDS_LIMIT: '必要上下文超过预算',
  REDUCTION_INSUFFICIENT: '裁剪后仍超过预算',
  MODEL_WINDOW_LIMIT: '超过模型窗口',
  RUN_TOKEN_BUDGET_LIMIT: '超过 Run Token 预算'
};

function statusTone(status) {
  if (status === 'COMPLETED') return 'green';
  if (status === 'WAITING_APPROVAL' || status === 'RESUMING') return 'amber';
  if (status === 'FAILED' || status === 'TIMED_OUT' || status === 'BUDGET_EXHAUSTED'
      || status === 'REJECTED' || status === 'EXPIRED') return 'red';
  return 'gray';
}

function formatDuration(milliseconds) {
  const value = Number(milliseconds || 0);
  if (value < 1000) return `${value} ms`;
  if (value < 60_000) return `${(value / 1000).toFixed(value < 10_000 ? 1 : 0)} s`;
  return `${Math.floor(value / 60_000)}m ${Math.round((value % 60_000) / 1000)}s`;
}

function formatEventTime(value) {
  if (!value) return '--:--:--';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '--:--:--';
  return date.toLocaleTimeString('zh-CN', { hour12: false });
}

function shortId(value) {
  if (!value) return '-';
  return value.length > 12 ? `${value.slice(0, 8)}...${value.slice(-4)}` : value;
}

function eventIcon(type) {
  if (type === 'rag_context') return BookOpenCheck;
  if (type === 'tool_call') return Wrench;
  if (type === 'approval_required') return ShieldCheck;
  if (type === 'execution_plan' || type === 'parallel_precheck') return Route;
  if (type === 'orchestration') return GitBranch;
  if (type === 'budget_updated') return Gauge;
  if (type === 'context_governed') return Layers3;
  if (type === 'resume') return RefreshCcw;
  if (type === 'error') return CircleAlert;
  if (type === 'done') return CircleStop;
  if (type === 'thinking_start') return Bot;
  return Activity;
}

function normalizedEvidence(evidence, fallback) {
  if (evidence?.runId) return evidence;
  const runId = fallback?.runId || fallback?.traceId || '';
  if (!runId) return null;
  return {
    runId,
    traceId: fallback?.traceId || '',
    mode: fallback?.executionMode || '',
    status: fallback?.runStatus || '',
    terminationReason: fallback?.terminationReason || '',
    usage: fallback?.runUsage || {},
    taskContractPresent: fallback?.taskContractPresent === true,
    outcomeEvaluation: fallback?.outcomeEvaluation || null,
    contextEvidence: fallback?.contextEvidence || null,
    events: fallback?.traceEvents || [],
    source: 'message'
  };
}

export function RunInspector({ evidence, fallback, citations = [] }) {
  const run = normalizedEvidence(evidence, fallback);
  if (!run) return null;

  const usage = run.usage || {};
  const events = Array.isArray(run.events) ? run.events : [];
  const status = run.status || usage.status || 'RUNNING';
  const outcome = run.outcomeEvaluation;
  const contextEvidence = run.contextEvidence;
  const showOutcome = run.taskContractPresent === true || (outcome && outcome.reasonCode !== 'TASK_CONTRACT_MISSING');
  const metrics = [
    { label: '耗时', value: formatDuration(usage.durationMs), icon: Timer },
    { label: '迭代', value: Number(usage.iterations || 0), icon: Activity },
    { label: '模型调用', value: Number(usage.modelCalls || 0), icon: Cpu },
    { label: '工具调用', value: Number(usage.toolCalls || 0), icon: Wrench },
    { label: 'Token', value: Number(usage.tokens || 0).toLocaleString(), icon: Gauge },
    { label: '剩余执行时间', value: formatDuration(usage.remainingActiveTimeoutMs), icon: Hourglass }
  ];

  return (
    <details className="run-inspector">
      <summary>
        <span className="run-inspector-summary-title"><Activity size={15} />运行详情</span>
        <span className="run-inspector-summary-meta">
          <Badge tone={statusTone(status)}>{STATUS_LABELS[status] || status}</Badge>
          <span>{MODE_LABELS[run.mode] || run.mode || 'Auto'}</span>
          <code title={run.runId}>{shortId(run.runId)}</code>
        </span>
      </summary>
      <div className="run-inspector-body">
        <div className="run-inspector-head">
          <div>
            <span className="run-kicker">RUN EVIDENCE</span>
            <strong>{run.detail || STATUS_LABELS[status] || status}</strong>
          </div>
          <div className="run-identity">
            <span>Run ID</span>
            <code title={run.runId}>{run.runId}</code>
          </div>
        </div>

        <div className="run-metric-grid" aria-label="运行资源用量">
          {metrics.map((metric) => {
            const Icon = metric.icon;
            return (
              <div className="run-metric" key={metric.label}>
                <Icon size={14} />
                <span>{metric.label}</span>
                <strong>{metric.value}</strong>
              </div>
            );
          })}
        </div>

        <div className="run-facts">
          <span><strong>终止原因</strong>{run.terminationReason || usage.terminationReason || '运行中'}</span>
          <span><strong>Token 口径</strong>{usage.tokenUsageEstimated ? '包含估算' : '模型返回'}</span>
          <span><strong>证据来源</strong>{run.source === 'live' ? '实时事件' : '历史记录'}</span>
          {citations.length > 0 && <span><strong>知识引用</strong>{citations.length} 条</span>}
        </div>

        {showOutcome && (
          <section className={`run-outcome ${String(outcome?.status || 'NOT_EVALUATED').toLowerCase()}`} aria-label="任务结果评估">
            <div className="run-section-heading">
              <strong>任务结果</strong>
              <Badge tone={outcome?.status === 'ACHIEVED' ? 'green' : outcome?.status === 'NOT_ACHIEVED' ? 'red' : 'gray'}>{OUTCOME_LABELS[outcome?.status] || '等待评估'}</Badge>
            </div>
            {outcome?.criteria?.length > 0 ? (
              <ul className="run-outcome-criteria">
                {outcome.criteria.map((criterion) => (
                  <li key={criterion.criterionId}>
                    <span className={`criterion-decision ${String(criterion.decision || 'NOT_EVALUATED').toLowerCase()}`}>{criterion.decision === 'PASSED' ? '通过' : criterion.decision === 'FAILED' ? '未通过' : '未评估'}</span>
                    <div><strong>{CRITERION_LABELS[criterion.criterionType] || criterion.criterionType}</strong><code>{criterion.criterionId}</code></div>
                  </li>
                ))}
              </ul>
            ) : (
              <div className="run-empty-evidence"><CircleAlert size={15} />没有可展示的逐项评估结果</div>
            )}
          </section>
        )}

        {contextEvidence && (
          <section className={`run-context-governance ${String(contextEvidence.decision || 'ADMITTED').toLowerCase()}`} aria-label="上下文治理证据" aria-live="polite">
            <div className="run-section-heading">
              <strong>上下文治理</strong>
              <Badge tone={contextEvidence.decision === 'REJECTED' ? 'red' : contextEvidence.decision === 'REDUCED' ? 'amber' : 'green'}>
                {CONTEXT_DECISION_LABELS[contextEvidence.decision] || contextEvidence.decision}
              </Badge>
            </div>
            <div className="run-context-metrics">
              <span><small>治理前 Token</small><strong>{Number(contextEvidence.estimatedTokensBefore || 0).toLocaleString()}</strong></span>
              <span><small>治理后 Token</small><strong>{Number(contextEvidence.estimatedTokensAfter || 0).toLocaleString()}</strong></span>
              <span><small>保留消息</small><strong>{Number(contextEvidence.retainedMessageCount || 0)} / {Number(contextEvidence.originalMessageCount || 0)}</strong></span>
              <span><small>Protected</small><strong>{Number(contextEvidence.protectedMessageCount || 0)}</strong></span>
            </div>
            <div className="run-context-facts">
              <span><strong>输入上限</strong>{Number(contextEvidence.inputTokenLimit || 0).toLocaleString()} Token</span>
              <span><strong>策略</strong>{contextEvidence.strategy || 'NONE'}</span>
              <span><strong>原因</strong>{CONTEXT_REASON_LABELS[contextEvidence.reason] || contextEvidence.reason}</span>
              <span><strong>计数口径</strong>{contextEvidence.tokenEstimateUsed ? '估算' : '模型返回'}</span>
            </div>
          </section>
        )}

        {run.source === 'history' && run.eventHistoryComplete !== true && (
          <div className="run-empty-evidence"><CircleAlert size={15} />历史时间线仅包含已持久化的安全事件，可能不完整</div>
        )}

        <section className="run-timeline" aria-label="Agent Run 事件时间线">
          <div className="run-section-heading">
            <strong>执行时间线</strong>
            <span>{events.length} 个操作事件</span>
          </div>
          {events.length === 0 ? (
            <div className="run-empty-evidence"><CircleAlert size={15} />该历史消息没有可恢复的事件证据</div>
          ) : (
            <ol>
              {events.map((event, index) => {
                const Icon = eventIcon(event.type);
                return (
                  <li key={`${event.type}-${event.eventTime || index}-${index}`}>
                    <div className="run-event-icon"><Icon size={14} /></div>
                    <time>{formatEventTime(event.eventTime)}</time>
                    <div className="run-event-copy">
                      <strong>{event.title || event.type}</strong>
                      {(event.summary || event.content) && <p>{event.summary || event.content}</p>}
                      {event.approvalId && <code>Approval {shortId(event.approvalId)}</code>}
                    </div>
                  </li>
                );
              })}
            </ol>
          )}
        </section>

        {status === 'COMPLETED' && (
          <div className="run-complete-note"><CheckCircle2 size={15} />运行正常结束仅表示执行完成，不等同于业务目标已达成。</div>
        )}
      </div>
    </details>
  );
}
