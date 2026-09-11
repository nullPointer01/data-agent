import {
  Activity,
  CheckCircle2,
  Clock3,
  Eye,
  FlaskConical,
  GitBranch,
  Play,
  RefreshCw,
  ShieldCheck,
  Wrench
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Badge, DataTable, EmptyState, Field, Modal, PageHeader } from '../components/ui.jsx';
import { TraceDetail } from './TracePage.jsx';
import { formatTime, truncate } from '../utils/format.js';

const REASON_TEXT = {
  TASK_OUTCOME_EVIDENCE_MISSING: '缺少可判定的任务结果',
  TOOL_LABEL_OR_COMPLETE_JOURNAL_MISSING: '缺少工具标签或完整 Tool Journal',
  LOOP_LABEL_OR_RUN_STATUS_MISSING: '缺少健康样本标签或 Run 终态',
  APPROVAL_LABEL_OR_EVIDENCE_MISSING: '缺少审批标签或审批证据',
  RUN_DURATION_EVIDENCE_MISSING: '缺少 Run 耗时证据',
  TOKEN_USAGE_EVIDENCE_MISSING: '缺少 Token 用量证据',
  RAG_SAMPLE_EVIDENCE_NOT_PERSISTED: '当前 Trace 尚未持久化样本级 RAG 引用',
  EVAL_RUN_PENDING: '评测正在排队',
  EVAL_RUN_RUNNING: '评测正在执行',
  EVAL_RUN_FAILED: '评测运行失败'
};

function statusTone(status) {
  if (status === 'COMPLETED' || status === 'ACHIEVED') return 'green';
  if (status === 'RUNNING') return 'blue';
  if (status === 'PENDING' || status === 'NOT_EVALUATED') return 'amber';
  return status ? 'red' : 'gray';
}

function statusText(status) {
  const values = {
    PENDING: '排队中', RUNNING: '执行中', COMPLETED: '已完成', FAILED: '失败',
    ACHIEVED: '达成', NOT_ACHIEVED: '未达成', NOT_EVALUATED: '未评估'
  };
  return values[status] || status || '-';
}

function formatMetric(metric) {
  if (!metric?.available) return '不可用';
  if (metric.unit === 'PERCENT') return `${Number(metric.value || 0).toFixed(1)}%`;
  if (metric.unit === 'MILLISECONDS') return `${Math.round(metric.value || 0)} ms`;
  if (metric.unit === 'TOKENS') return `${Math.round(metric.value || 0)} Token`;
  return String(metric.value ?? '-');
}

function metricHint(metric) {
  if (!metric?.available) return REASON_TEXT[metric?.unavailableReason] || metric?.unavailableReason || '证据不足';
  const coverage = `${metric.denominator || 0} 个有效样本`;
  return metric.excludedSamples ? `${coverage}，排除 ${metric.excludedSamples}` : coverage;
}

function booleanText(value) {
  if (value == null) return '无证据';
  return value ? '是' : '否';
}

export function AgentEvalPage({ api, toast }) {
  const [datasets, setDatasets] = useState([]);
  const [agents, setAgents] = useState([]);
  const [runs, setRuns] = useState([]);
  const [datasetId, setDatasetId] = useState('');
  const [agentId, setAgentId] = useState('');
  const [report, setReport] = useState(null);
  const [sample, setSample] = useState(null);
  const [trace, setTrace] = useState(null);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [traceLoading, setTraceLoading] = useState(false);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const [datasetResponse, runResponse, agentResponse] = await Promise.all([
        api.get('/api/v1/agent-evals/datasets'),
        api.get('/api/v1/agent-evals/runs?limit=50'),
        api.get('/api/v1/agents?enabledOnly=true')
      ]);
      const nextDatasets = Array.isArray(datasetResponse) ? datasetResponse : [];
      const nextRuns = Array.isArray(runResponse) ? runResponse : [];
      const nextAgents = Array.isArray(agentResponse.agents) ? agentResponse.agents : [];
      setDatasets(nextDatasets);
      setRuns(nextRuns);
      setAgents(nextAgents);
      setDatasetId((current) => current || nextDatasets[0]?.datasetId || '');
      setAgentId((current) => current || nextAgents[0]?.agentId || '');
      if (!report && nextRuns[0]?.evalRunId) {
        const latest = await api.get(`/api/v1/agent-evals/runs/${encodeURIComponent(nextRuns[0].evalRunId)}`);
        if (!latest.evalRunId) throw new Error(latest.message || '最新评测报告加载失败');
        setReport(latest);
      }
    } catch (requestError) {
      setError(requestError.message || 'Agent Eval 加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  useEffect(() => {
    if (!report?.evalRunId || !['PENDING', 'RUNNING'].includes(report.status)) return undefined;
    const timer = window.setInterval(async () => {
      try {
        const next = await api.get(`/api/v1/agent-evals/runs/${encodeURIComponent(report.evalRunId)}`);
        if (!next.evalRunId) throw new Error(next.message || '评测状态刷新失败');
        setReport(next);
        if (!['PENDING', 'RUNNING'].includes(next.status)) {
          const nextRuns = await api.get('/api/v1/agent-evals/runs?limit=50');
          setRuns(Array.isArray(nextRuns) ? nextRuns : []);
        }
      } catch (requestError) {
        setError(requestError.message || '评测状态刷新失败');
      }
    }, 2000);
    return () => window.clearInterval(timer);
  }, [api, report?.evalRunId, report?.status]);

  const datasetNames = useMemo(
    () => Object.fromEntries(datasets.map((item) => [item.datasetId, item.name])),
    [datasets]
  );

  const start = async () => {
    if (!datasetId || !agentId) return;
    setStarting(true);
    setError('');
    try {
      const response = await api.post('/api/v1/agent-evals/runs', { datasetId, agentId });
      if (response.success === false) throw new Error(response.message || '评测启动失败');
      setReport(response);
      setRuns((items) => [{
        evalRunId: response.evalRunId,
        status: response.status,
        datasetId: response.dataset?.datasetId,
        datasetVersion: response.dataset?.datasetVersion,
        agentId: response.agent?.agentId,
        modelId: response.agent?.modelId,
        counts: response.counts,
        startedAt: response.startedAt
      }, ...items.filter((item) => item.evalRunId !== response.evalRunId)]);
      toast?.('Agent Eval 已进入队列', 'success');
    } catch (requestError) {
      setError(requestError.message || '评测启动失败');
      toast?.(requestError.message || '评测启动失败', 'error');
    } finally {
      setStarting(false);
    }
  };

  const openReport = async (runId) => {
    setError('');
    try {
      const next = await api.get(`/api/v1/agent-evals/runs/${encodeURIComponent(runId)}`);
      if (!next.evalRunId) throw new Error(next.message || '评测报告加载失败');
      setReport(next);
    } catch (requestError) {
      setError(requestError.message || '评测报告加载失败');
    }
  };

  const openTrace = async (traceId) => {
    if (!traceId) return;
    setTraceLoading(true);
    try {
      const nextTrace = await api.get(`/api/v1/agent-traces/${encodeURIComponent(traceId)}`);
      if (!nextTrace.traceId) throw new Error(nextTrace.message || 'Run 证据加载失败');
      setTrace(nextTrace);
    } catch (requestError) {
      toast?.(requestError.message || 'Run 证据加载失败', 'error');
    } finally {
      setTraceLoading(false);
    }
  };

  const metrics = report?.metrics;
  const metricItems = [
    ['任务完成率', metrics?.taskCompletionRate, CheckCircle2],
    ['工具选择正确率', metrics?.correctToolSelectionRate, Wrench],
    ['无效循环率', metrics?.invalidLoopRate, Activity],
    ['审批策略准确率', metrics?.approvalPolicyAccuracy, ShieldCheck],
    ['P95 耗时', metrics?.p95DurationMs, Clock3],
    ['平均 Token', metrics?.averageTokenUsage, FlaskConical]
  ];

  return (
    <>
      <PageHeader
        title="Agent Eval"
        desc="用固定版本数据集验证任务完成、工具治理、循环健康、审批策略、延迟与成本"
        actions={<button className="btn" onClick={load} disabled={loading}><RefreshCw size={16} />刷新</button>}
      />

      <section className="eval-launch-band" aria-label="启动评测">
        <div className="eval-launch-copy">
          <FlaskConical size={20} />
          <div><strong>新建回归评测</strong><span>一次运行固定数据集、Agent 版本和 Harness 预算</span></div>
        </div>
        <label className="field">
          <span>数据集版本</span>
          <select className="select" value={datasetId} onChange={(event) => setDatasetId(event.target.value)}>
            {!datasets.length && <option value="">暂无 READY 数据集</option>}
            {datasets.map((item) => <option key={item.datasetId} value={item.datasetId}>{item.name} · v{item.datasetVersion} · {item.enabledSamples} 条</option>)}
          </select>
        </label>
        <label className="field">
          <span>Agent</span>
          <select className="select" value={agentId} onChange={(event) => setAgentId(event.target.value)}>
            {!agents.length && <option value="">暂无启用 Agent</option>}
            {agents.map((item) => <option key={item.agentId} value={item.agentId}>{item.name} · {item.executionMode}</option>)}
          </select>
        </label>
        <button className="btn primary eval-start-button" onClick={start} disabled={starting || !datasetId || !agentId}>
          <Play size={16} />{starting ? '提交中' : '开始评测'}
        </button>
      </section>

      <div className="eval-live-message" aria-live="polite" role={error ? 'alert' : undefined}>
        {error || (report && ['PENDING', 'RUNNING'].includes(report.status)
          ? `评测 ${report.evalRunId}：${statusText(report.status)}，已完成 ${report.counts?.completed || 0}/${report.counts?.total || 0}`
          : '')}
      </div>

      <section className="eval-section">
        <div className="eval-section-head"><div><strong>评测运行</strong><span>固定输入与版本快照</span></div></div>
        <DataTable loading={loading} empty={<EmptyState title="暂无评测运行" desc="创建 READY 数据集后可发起第一轮回归评测。" />} columns={[
          { key: 'startedAt', title: '开始时间', render: (item) => formatTime(item.startedAt) },
          { key: 'datasetId', title: '数据集', render: (item) => <><strong>{datasetNames[item.datasetId] || truncate(item.datasetId, 16)}</strong><div className="muted">v{item.datasetVersion}</div></> },
          { key: 'agentId', title: 'Agent / 模型', render: (item) => <><span className="mono">{truncate(item.agentId, 18)}</span><div className="muted">{item.modelId || '默认模型'}</div></> },
          { key: 'progress', title: '进度', render: (item) => `${item.counts?.completed || 0}/${item.counts?.total || 0}` },
          { key: 'status', title: '状态', render: (item) => <Badge tone={statusTone(item.status)}>{statusText(item.status)}</Badge> },
          { key: 'actions', title: '操作', render: (item) => <button className="btn" onClick={() => openReport(item.evalRunId)}><Eye size={16} />报告</button> }
        ]} rows={runs} rowKey="evalRunId" />
      </section>

      {report && (
        <section className="eval-report" aria-label="评测报告">
          <div className="eval-report-head">
            <div>
              <div className="toolbar"><Badge tone={statusTone(report.status)}>{statusText(report.status)}</Badge><strong>{report.dataset?.name || '评测报告'} · v{report.dataset?.datasetVersion || '-'}</strong></div>
              <span className="mono">{report.evalRunId}</span>
            </div>
            <div className="eval-report-meta"><span>开始 {formatTime(report.startedAt)}</span><span>完成 {formatTime(report.completedAt)}</span></div>
          </div>

          <div className="eval-metric-grid">
            {metricItems.map(([label, metric, Icon]) => (
              <div className={`eval-metric ${metric?.available ? '' : 'unavailable'}`} key={label}>
                <Icon size={18} />
                <span>{label}</span>
                <strong>{formatMetric(metric)}</strong>
                <small>{metricHint(metric)}</small>
              </div>
            ))}
          </div>

          <div className="eval-rag-dimension">
            <div><strong>RAG 质量维度</strong><span>引用命中率与端到端任务完成率独立计算</span></div>
            <Badge tone={metrics?.ragReferenceHitRate?.available ? 'green' : 'gray'}>{formatMetric(metrics?.ragReferenceHitRate)}</Badge>
            <small>{metricHint(metrics?.ragReferenceHitRate)}</small>
          </div>

          <div className="eval-section-head"><div><strong>样本证据</strong><span>{report.counts?.completed || 0} 条已执行，Run 与 Trace 可继续下钻</span></div></div>
          <DataTable loading={false} empty={<EmptyState title="暂无样本结果" desc="评测运行开始后将逐条写入样本证据。" />} columns={[
            { key: 'sampleKey', title: '样本', render: (item) => <><strong>{item.sampleKey}</strong><div className="muted">{truncate(item.question, 54)}</div></> },
            { key: 'outcomeStatus', title: 'Outcome', render: (item) => <Badge tone={statusTone(item.outcomeStatus)}>{statusText(item.outcomeStatus)}</Badge> },
            { key: 'tools', title: '工具', render: (item) => item.observedTools == null ? '无证据' : (item.observedTools.join(', ') || '未调用') },
            { key: 'approvalObserved', title: '审批', render: (item) => booleanText(item.approvalObserved) },
            { key: 'invalidLoopObserved', title: '无效循环', render: (item) => booleanText(item.invalidLoopObserved) },
            { key: 'usage', title: '消耗', render: (item) => item.durationMs == null ? '无证据' : <>{item.durationMs} ms<div className="muted">{item.tokenUsage || 0} Token</div></> },
            { key: 'actions', title: '操作', render: (item) => <button className="icon-button bordered" title="查看样本证据" aria-label={`查看样本 ${item.sampleKey} 证据`} onClick={() => setSample(item)}><Eye size={16} /></button> }
          ]} rows={report.samples || []} rowKey="sampleId" />
        </section>
      )}

      {sample && <SampleEvidenceModal sample={sample} traceLoading={traceLoading} onTrace={openTrace} onClose={() => setSample(null)} />}
      {trace && <TraceDetail trace={trace} api={api} onClose={() => setTrace(null)} />}
    </>
  );
}

function SampleEvidenceModal({ sample, traceLoading, onTrace, onClose }) {
  const criteria = Array.isArray(sample.outcomeEvaluation?.criteria) ? sample.outcomeEvaluation.criteria : [];
  return (
    <Modal title={`样本证据 · ${sample.sampleKey}`} size="lg" onClose={onClose} actions={sample.traceId && (
      <button className="btn primary" disabled={traceLoading} onClick={() => onTrace(sample.traceId)}><GitBranch size={16} />{traceLoading ? '加载中' : '查看 Trace'}</button>
    )}>
      <div className="detail-grid">
        <span>Outcome</span><span><Badge tone={statusTone(sample.outcomeStatus)}>{statusText(sample.outcomeStatus)}</Badge></span>
        <span>原因码</span><span className="mono">{sample.reasonCode || '-'}</span>
        <span>Run ID</span><span className="mono">{sample.agentRunId || '-'}</span>
        <span>Trace ID</span><span className="mono">{sample.traceId || '-'}</span>
        <span>期望工具</span><span>{sample.expectedTools == null ? '未标注' : (sample.expectedTools.join(', ') || '不应调用')}</span>
        <span>实际工具</span><span>{sample.observedTools == null ? '证据不完整' : (sample.observedTools.join(', ') || '未调用')}</span>
        <span>审批</span><span>期望 {booleanText(sample.expectedApprovalRequired)} · 实际 {booleanText(sample.approvalObserved)}</span>
        <span>无效循环</span><span>期望 {booleanText(sample.invalidLoopExpected)} · 实际 {booleanText(sample.invalidLoopObserved)}</span>
      </div>
      <Field label="固定问题"><pre className="code-panel">{sample.question}</pre></Field>
      {sample.errorSummary && <div className="alert danger" role="alert">{sample.errorSummary}</div>}
      <section className="eval-criteria-list">
        <div className="section-title">成功标准</div>
        {!criteria.length && <EmptyState title="无逐项评估" desc="该样本没有足够证据形成逐标准结果。" />}
        {criteria.map((criterion) => (
          <div className="eval-criterion" key={criterion.criterionId}>
            <Badge tone={statusTone(criterion.decision === 'PASSED' ? 'ACHIEVED' : criterion.decision === 'FAILED' ? 'NOT_ACHIEVED' : 'NOT_EVALUATED')}>{criterion.decision}</Badge>
            <div><strong>{criterion.criterionId} · {criterion.criterionType}</strong><span>{criterion.reasonCode}</span></div>
            <small>{Array.isArray(criterion.evidenceReferences) && criterion.evidenceReferences.length ? criterion.evidenceReferences.join(', ') : '无可引用证据'}</small>
          </div>
        ))}
      </section>
    </Modal>
  );
}
