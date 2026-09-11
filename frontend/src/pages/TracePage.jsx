import { Eye, RefreshCw } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Badge, DataTable, EmptyState, Field, Metric, Modal, PageHeader, ToolbarSearch } from '../components/ui.jsx';
import { formatTime, truncate } from '../utils/format.js';

function statusTone(success) {
  return success ? 'green' : 'red';
}

function modeTone(mode) {
  if (mode === 'REACT') {
    return 'amber';
  }
  if (mode === 'ORCHESTRATED') {
    return 'purple';
  }
  if (mode === 'CHAT') {
    return 'green';
  }
  return 'gray';
}

function parseJson(value, fallback) {
  try {
    return value ? JSON.parse(value) : fallback;
  } catch {
    return fallback;
  }
}

export function TracePage({ api, initialUserId = '' }) {
  const [traces, setTraces] = useState([]);
  const [query, setQuery] = useState('');
  const [userId, setUserId] = useState(initialUserId);
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState(null);

  const load = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ limit: '100' });
      if (userId.trim()) {
        params.set('userId', userId.trim());
      }
      const response = await api.get(`/api/v1/agent-traces?${params.toString()}`);
      setTraces(response.traces || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) {
      return traces;
    }
    return traces.filter((trace) => JSON.stringify(trace).toLowerCase().includes(keyword));
  }, [traces, query]);

  const openDetail = async (trace) => {
    const response = await api.get(`/api/v1/agent-traces/${trace.traceId}`);
    setDetail(response);
  };

  return (
    <>
      <PageHeader
        title="执行追踪"
        desc="查看请求计划、Run 预算、工具治理、上下文治理和最终状态"
        actions={<><ToolbarSearch value={query} onChange={setQuery} placeholder="搜索问题、Agent、意图" /><input className="input compact" placeholder="用户 ID" value={userId} onChange={(event) => setUserId(event.target.value)} /><button className="btn" onClick={load}><RefreshCw size={16} />刷新</button></>}
      />
      <div className="grid grid-4">
        <Metric label="轨迹总数" value={traces.length} />
        <Metric label="成功" value={traces.filter((item) => item.success).length} />
        <Metric label="失败" value={traces.filter((item) => !item.success).length} />
        <Metric label="平均耗时" value={averageDuration(traces)} />
      </div>
      <DataTable loading={loading} empty={<EmptyState title="暂无执行轨迹" desc="通过我的 Agent 发起任务后，这里会显示运行模式、工具调用和终态记录。" />} columns={[
        { key: 'createdAt', title: '时间', render: (item) => formatTime(item.createdAt) },
        { key: 'question', title: '问题', render: (item) => truncate(item.question, 90) },
        { key: 'selectedAgent', title: 'Agent', render: (item) => <><strong>{item.selectedAgent || '-'}</strong><div className="muted">{item.userId || '-'}</div></> },
        { key: 'selectedType', title: '运行模式', render: (item) => <Badge tone={modeTone(item.selectedType)}>{item.selectedType || '-'}</Badge> },
        { key: 'intent', title: '意图', render: (item) => item.intent || '-' },
        { key: 'taskCount', title: '工具调用', render: (item) => item.taskCount || 0 },
        { key: 'durationMs', title: '耗时', render: (item) => `${item.durationMs || 0} ms` },
        { key: 'success', title: '状态', render: (item) => <Badge tone={statusTone(item.success)}>{item.success ? '成功' : '失败'}</Badge> },
        { key: 'actions', title: '操作', render: (item) => <button className="btn" onClick={() => openDetail(item)}><Eye size={16} />详情</button> }
      ]} rows={filtered} rowKey="traceId" />
      {detail && <TraceDetail trace={detail} api={api} onClose={() => setDetail(null)} />}
    </>
  );
}

export function TraceDetail({ trace, api, onClose }) {
  const plan = parseJson(trace.planJson, {});
  const sharedContext = parseJson(trace.sharedContextJson, {});
  const requestPlan = plan.requestPlan || sharedContext.requestPlan || {};
  const run = sharedContext.run || plan;
  const toolGovernance = sharedContext.toolGovernance || {};
  const contextGovernance = sharedContext.contextGovernance || {};
  const outcomeEvaluation = sharedContext.outcomeEvaluation || {};
  return (
    <Modal title="执行轨迹详情" onClose={onClose} size="lg">
      <div className="detail-grid">
        <span>轨迹 ID</span><strong className="mono">{trace.traceId}</strong>
        <span>时间</span><span>{formatTime(trace.createdAt)}</span>
        <span>用户</span><span>{trace.userId || '-'}</span>
        <span>会话</span><span className="mono">{trace.sessionId || '-'}</span>
        <span>Agent</span><span>{trace.selectedAgent || '-'}</span>
        <span>运行模式</span><span>{trace.selectedType || '-'}</span>
        <span>意图</span><span>{trace.intent || '-'}</span>
        <span>状态</span><span>{trace.success ? '成功' : '失败'}</span>
        <span>耗时</span><span>{trace.durationMs || 0} ms</span>
        <span>工具选择</span><span>{trace.fallbackUsed ? '使用绑定能力兜底' : '精确候选'}</span>
        <span>路由原因</span><span>{trace.reason || '-'}</span>
        <span>错误</span><span>{trace.error || '-'}</span>
      </div>
      <Field label="用户问题"><pre className="code-panel">{trace.question || '-'}</pre></Field>
      <section className="trace-detail-section">
        <div className="section-title">请求计划</div>
        <TraceRequestPlan plan={requestPlan} />
      </section>
      <section className="trace-detail-section">
        <div className="section-title">Run 预算与终态</div>
        <TraceRunSummary run={run} />
      </section>
      <section className="trace-detail-section">
        <div className="section-title">工具治理</div>
        <TraceToolGovernance governance={toolGovernance} />
      </section>
      <section className="trace-detail-section">
        <div className="section-title">上下文与结果评估</div>
        <TraceEvidence contextGovernance={contextGovernance} outcomeEvaluation={outcomeEvaluation} />
      </section>
      <Field label="完整运行证据"><pre className="code-panel">{JSON.stringify(sharedContext, null, 2)}</pre></Field>
    </Modal>
  );
}

function TraceRequestPlan({ plan }) {
  if (!plan || !plan.mode) {
    return <EmptyState title="暂无请求计划" desc="该轨迹没有记录请求级规划证据。" />;
  }
  const tools = Array.isArray(plan.candidateTools) ? plan.candidateTools : [];
  return (
    <div className="trace-plan-list">
      <div className="trace-plan-item">
        <div className="toolbar">
          <Badge tone={modeTone(plan.mode)}>{plan.mode}</Badge>
          <strong>{plan.intent || '-'}</strong>
          <span className="muted">置信度 {Math.round(Number(plan.confidence || 0) * 100)}%</span>
        </div>
        <p>{plan.reason || '-'}</p>
        <div className="trace-mini-list">
          <span>模型 {plan.modelRequired ? '开启' : '关闭'}</span>
          <span>知识库 {plan.ragRequired ? '开启' : '关闭'}</span>
          <span>记忆 {plan.memoryRequired ? '开启' : '关闭'}</span>
          <span>历史 {plan.historyRequired ? '开启' : '关闭'}</span>
        </div>
        <div className="trace-mini-list">
          <span>规则: {plan.matchedRule || '-'}</span>
          <span>候选工具: {tools.length ? tools.join(', ') : '无'}</span>
        </div>
      </div>
    </div>
  );
}

function TraceRunSummary({ run }) {
  return (
    <div className="trace-plan-list">
      <div className="trace-plan-item">
        <div className="toolbar">
          <Badge tone={run.status === 'COMPLETED' ? 'green' : 'amber'}>{run.status || '-'}</Badge>
          <strong>{run.terminationReason || '-'}</strong>
        </div>
        <div className="trace-mini-list">
          <span>迭代 {run.iterations || 0}</span>
          <span>模型调用 {run.modelCalls || 0}</span>
          <span>工具调用 {run.toolCalls || 0}</span>
          <span>Token {run.tokens || 0}</span>
          <span>耗时 {run.durationMs || 0} ms</span>
        </div>
      </div>
    </div>
  );
}

function TraceToolGovernance({ governance }) {
  const records = Array.isArray(governance.records) ? governance.records : [];
  if (!records.length) {
    return <EmptyState title="未调用工具" desc="本次 Run 没有产生工具执行记录。" />;
  }
  return (
    <div className="trace-plan-list">
      {records.map((record, index) => (
        <div className="trace-plan-item" key={record.toolCallId || index}>
          <div className="toolbar">
            <Badge tone={record.status === 'SUCCESS' ? 'green' : 'amber'}>{record.status || '-'}</Badge>
            <strong>{record.toolName || '-'}</strong>
            <span className="muted">{record.durationMs || 0} ms</span>
          </div>
          <p>{record.safeMessage || record.detail || '-'}</p>
        </div>
      ))}
    </div>
  );
}

function TraceEvidence({ contextGovernance, outcomeEvaluation }) {
  const eventCount = Number(contextGovernance.eventCount || 0);
  return (
    <div className="trace-plan-list">
      <div className="trace-plan-item">
        <strong>上下文治理</strong>
        <p>记录 {eventCount} 次，最近决策：{contextGovernance.latest?.decision || '-'}</p>
      </div>
      <div className="trace-plan-item">
        <strong>结果评估</strong>
        <p>{outcomeEvaluation.status || '未配置验收条件'} {outcomeEvaluation.reasonCode || ''}</p>
      </div>
    </div>
  );
}

function averageDuration(items) {
  if (!items.length) {
    return '0 ms';
  }
  const total = items.reduce((sum, item) => sum + Number(item.durationMs || 0), 0);
  return `${Math.round(total / items.length)} ms`;
}
