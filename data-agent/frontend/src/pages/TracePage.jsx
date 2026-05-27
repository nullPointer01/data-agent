import { Eye, RefreshCw } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Badge, DataTable, EmptyState, Field, Metric, Modal, PageHeader, ToolbarSearch } from '../components/ui.jsx';
import { formatTime, truncate } from '../utils/format.js';

function statusTone(success) {
  return success ? 'green' : 'red';
}

function typeTone(type) {
  if (type === 'DATA') {
    return 'blue';
  }
  if (type === 'SKILL') {
    return 'amber';
  }
  if (type === 'KNOWLEDGE') {
    return 'purple';
  }
  if (type === 'CHART') {
    return 'blue';
  }
  if (type === 'REPORT') {
    return 'amber';
  }
  if (type === 'CHAT') {
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

export function TracePage({ api }) {
  const [traces, setTraces] = useState([]);
  const [query, setQuery] = useState('');
  const [userId, setUserId] = useState('');
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
        desc="查看 Agent 编排决策、任务计划、专家执行结果和错误详情"
        actions={<><ToolbarSearch value={query} onChange={setQuery} placeholder="搜索问题、Agent、意图" /><input className="input compact" placeholder="用户 ID" value={userId} onChange={(event) => setUserId(event.target.value)} /><button className="btn" onClick={load}><RefreshCw size={16} />刷新</button></>}
      />
      <div className="grid grid-4">
        <Metric label="轨迹总数" value={traces.length} />
        <Metric label="成功" value={traces.filter((item) => item.success).length} />
        <Metric label="失败" value={traces.filter((item) => !item.success).length} />
        <Metric label="平均耗时" value={averageDuration(traces)} />
      </div>
      <DataTable loading={loading} empty={<EmptyState title="暂无执行轨迹" desc="通过智能对话触发默认 Orchestrator 后，这里会显示编排记录。" />} columns={[
        { key: 'createdAt', title: '时间', render: (item) => formatTime(item.createdAt) },
        { key: 'question', title: '问题', render: (item) => truncate(item.question, 90) },
        { key: 'selectedAgent', title: 'Agent', render: (item) => <><strong>{item.selectedAgent || '-'}</strong><div className="muted">{item.userId || '-'}</div></> },
        { key: 'selectedType', title: '类型', render: (item) => <Badge tone={typeTone(item.selectedType)}>{item.selectedType || '-'}</Badge> },
        { key: 'intent', title: '意图', render: (item) => item.intent || '-' },
        { key: 'taskCount', title: '任务', render: (item) => item.taskCount || 0 },
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
  const tasks = parseJson(trace.taskResultsJson, []);
  const sharedContext = parseJson(trace.sharedContextJson, {});
  const collaborationSummary = plan.collaborationSummary || sharedContext.collaborationSummary || {};
  const reactExecutions = Array.isArray(plan.reactExecutions) ? plan.reactExecutions : [];
  const [feedbacks, setFeedbacks] = useState([]);

  useEffect(() => {
    if (!api || !trace.traceId) {
      return;
    }
    let cancelled = false;
    api.get(`/api/v1/agent-feedbacks?limit=20&traceId=${encodeURIComponent(trace.traceId)}`)
      .then((response) => {
        if (!cancelled) {
          setFeedbacks(response.feedbacks || []);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setFeedbacks([]);
        }
      });
    return () => { cancelled = true; };
  }, [api, trace.traceId]);

  return (
    <Modal title="执行轨迹详情" onClose={onClose} size="lg">
      <div className="detail-grid">
        <span>轨迹 ID</span><strong className="mono">{trace.traceId}</strong>
        <span>时间</span><span>{formatTime(trace.createdAt)}</span>
        <span>用户</span><span>{trace.userId || '-'}</span>
        <span>会话</span><span className="mono">{trace.sessionId || '-'}</span>
        <span>Agent</span><span>{trace.selectedAgent || '-'}</span>
        <span>类型</span><span>{trace.selectedType || '-'}</span>
        <span>意图</span><span>{trace.intent || '-'}</span>
        <span>复杂度</span><span>{trace.complexity || '-'}</span>
        <span>状态</span><span>{trace.success ? '成功' : '失败'}</span>
        <span>耗时</span><span>{trace.durationMs || 0} ms</span>
        <span>回退</span><span>{trace.fallbackUsed ? '使用内置回退' : '未使用'}</span>
        <span>路由原因</span><span>{trace.reason || '-'}</span>
        <span>错误</span><span>{trace.error || '-'}</span>
      </div>
      <Field label="用户问题"><pre className="code-panel">{trace.question || '-'}</pre></Field>
        <section className="trace-detail-section">
          <div className="section-title">编排计划</div>
          <TracePlan plan={plan} />
        </section>
        <section className="trace-detail-section">
          <div className="section-title">协作摘要</div>
          <TraceCollaborationSummary summary={collaborationSummary} />
        </section>
        <section className="trace-detail-section">
          <div className="section-title">任务结果</div>
          <TraceTasks tasks={Array.isArray(tasks) ? tasks : []} />
      </section>
      <section className="trace-detail-section">
        <div className="section-title">ReAct 执行细节</div>
        <TraceReactExecutions executions={reactExecutions} />
      </section>
      <section className="trace-detail-section">
        <div className="section-title">关联反馈</div>
        <TraceFeedbacks feedbacks={feedbacks} />
      </section>
      <Field label="共享上下文"><pre className="code-panel">{JSON.stringify(sharedContext, null, 2)}</pre></Field>
    </Modal>
  );
}

function TraceFeedbacks({ feedbacks }) {
  if (!feedbacks.length) {
    return <EmptyState title="暂无关联反馈" desc="该轨迹还没有用户反馈。" />;
  }
  return (
    <div className="trace-plan-list">
      {feedbacks.map((feedback) => (
        <div className="trace-plan-item" key={feedback.feedbackId}>
          <div className="toolbar">
            <Badge tone={feedback.rating === 'UP' ? 'green' : 'red'}>{feedback.rating === 'UP' ? '正向' : '负向'}</Badge>
            <span className="muted">{formatTime(feedback.createdAt)}</span>
          </div>
          <p>{truncate(feedback.comment || feedback.question || feedback.answer || '-', 260)}</p>
        </div>
      ))}
    </div>
  );
}

function TracePlan({ plan }) {
  const tasks = Array.isArray(plan.tasks) ? plan.tasks : [];
  if (!tasks.length) {
    return <EmptyState title="暂无计划" desc="该轨迹没有记录编排计划。" />;
  }
  return (
    <div className="trace-plan-list">
      {!!Array.isArray(plan.executionPhases) && plan.executionPhases.length > 0 && (
        <div className="trace-plan-item">
          <div className="toolbar">
            <Badge tone="blue">阶段</Badge>
            <strong>{plan.executionPhases.length} 个</strong>
          </div>
          <div className="trace-mini-list">
            {plan.executionPhases.map((phase) => (
              <span key={phase.phase || phase.tasks?.join('-')}>
                第 {phase.phase || 1} 阶段 {phase.parallel ? '并行' : '串行'} - {Array.isArray(phase.tasks) ? phase.tasks.join(', ') : '-'}
              </span>
            ))}
          </div>
        </div>
      )}
      {tasks.map((task, index) => (
        <div className="trace-plan-item" key={task.taskId || index}>
          <Badge tone="gray">{task.taskId || index + 1}</Badge>
          <strong>{task.description || '-'}</strong>
          <span>{task.specialistName || '-'}</span>
          {Array.isArray(task.inputFrom) && task.inputFrom.length > 0 && (
            <div className="trace-mini-list">
              <span>依赖: {task.inputFrom.join(', ')}</span>
            </div>
          )}
          {task.expectedOutput && <p>{task.expectedOutput}</p>}
        </div>
      ))}
    </div>
  );
}

function TraceTasks({ tasks }) {
  if (!tasks.length) {
    return <EmptyState title="暂无任务结果" desc="该轨迹没有记录专家任务结果。" />;
  }
  return (
    <div className="trace-plan-list">
      {tasks.map((task, index) => (
        <div className="trace-plan-item" key={task.taskId || index}>
          <div className="toolbar">
            <Badge tone={task.skipped ? 'amber' : statusTone(task.success)}>{task.skipped ? '跳过' : (task.success ? '成功' : '失败')}</Badge>
            <strong>{task.specialistId || '-'}</strong>
            <span className="muted">{task.executionTimeMs || 0} ms</span>
          </div>
          <p>{truncate(task.result || task.error || '-', 360)}</p>
          {task.skipped && task.skipReason && <div className="trace-mini-list"><span>{task.skipReason}</span></div>}
          {Array.isArray(task.blockingDependencies) && task.blockingDependencies.length > 0 && (
            <div className="trace-mini-list">
              <span>阻断依赖: {task.blockingDependencies.join(', ')}</span>
            </div>
          )}
          {task.outputContext && Object.keys(task.outputContext).length > 0 && (
            <Field label="任务上下文"><pre className="code-panel">{JSON.stringify(task.outputContext, null, 2)}</pre></Field>
          )}
        </div>
      ))}
    </div>
  );
}

function TraceCollaborationSummary({ summary }) {
  const totalTasks = Number(summary?.totalTasks || 0);
  const successfulTasks = Array.isArray(summary?.successfulTasks) ? summary.successfulTasks : [];
  const failedTasks = Array.isArray(summary?.failedTasks) ? summary.failedTasks : [];
  const skippedTasks = Array.isArray(summary?.skippedTasks) ? summary.skippedTasks : [];
  const specialists = Array.isArray(summary?.specialists) ? summary.specialists : [];
  if (!totalTasks) {
    return <EmptyState title="暂无协作摘要" desc="该轨迹没有记录协作摘要。" />;
  }
  return (
    <div className="trace-plan-list">
      <div className="trace-plan-item">
        <div className="trace-mini-list">
          <span>总任务 {totalTasks}</span>
          <span>成功 {successfulTasks.length}</span>
          <span>失败 {failedTasks.length}</span>
          <span>跳过 {skippedTasks.length}</span>
        </div>
        <div className="trace-mini-list">
          <span>专家: {specialists.length ? specialists.join(', ') : '-'}</span>
        </div>
      </div>
      <div className="trace-plan-item">
        <strong>成功任务</strong>
        <p>{successfulTasks.length ? successfulTasks.join(', ') : '-'}</p>
      </div>
      <div className="trace-plan-item">
        <strong>失败任务</strong>
        <p>{failedTasks.length ? failedTasks.join(', ') : '-'}</p>
      </div>
      <div className="trace-plan-item">
        <strong>跳过任务</strong>
        <p>{skippedTasks.length ? skippedTasks.join(', ') : '-'}</p>
      </div>
    </div>
  );
}

function TraceReactExecutions({ executions }) {
  if (!executions.length) {
    return <EmptyState title="暂无 ReAct 明细" desc="该轨迹没有记录内置 ReAct 的计划、预检或反思步骤。" />;
  }
  return (
    <div className="trace-plan-list">
      {executions.map((execution, index) => {
        const steps = Array.isArray(execution?.executionPlan?.steps) ? execution.executionPlan.steps : [];
        const prechecks = Array.isArray(execution?.parallelPrecheck?.stepResults) ? execution.parallelPrecheck.stepResults : [];
        const thinkingSteps = Array.isArray(execution?.thinkingSteps) ? execution.thinkingSteps : [];
        return (
          <div className="trace-plan-item" key={`${execution?.mode || 'react'}-${index}`}>
            <div className="toolbar">
              <Badge tone={execution?.mode === 'fast_path' ? 'green' : 'blue'}>{execution?.mode || 'reasoning'}</Badge>
              <strong>{execution?.classification?.complexity || '-'}</strong>
              <span className="muted">{execution?.iterations || 0} 轮</span>
            </div>
            {execution?.classification?.reason && <p>{execution.classification.reason}</p>}
            {!!steps.length && (
              <div className="trace-mini-list">
                {steps.map((step) => <span key={step.id}>{step.phase || 1}. {step.objective}</span>)}
              </div>
            )}
            {!!prechecks.length && (
              <div className="trace-mini-list">
                {prechecks.map((item) => <span key={item.stepId}>{item.toolName}: {item.success ? '成功' : '失败'} - {truncate(item.result || '-', 120)}</span>)}
              </div>
            )}
            {!!thinkingSteps.length && <TraceThinkingSteps steps={thinkingSteps} />}
          </div>
        );
      })}
    </div>
  );
}

function TraceThinkingSteps({ steps }) {
  return (
    <div className="trace-mini-list">
      {steps.slice(0, 8).map((step, index) => (
        <span key={`${step.type || 'step'}-${step.step || index}`}>
          {step.step || index + 1}. {step.type || 'step'} - {truncate(step.content || step.toolResult || '-', 120)}
        </span>
      ))}
      {steps.length > 8 && <span>还有 {steps.length - 8} 个步骤</span>}
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
