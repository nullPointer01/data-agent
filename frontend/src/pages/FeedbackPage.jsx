import { Eye, GitBranch, RefreshCw, ThumbsDown } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Badge, DataTable, EmptyState, Field, Metric, Modal, PageHeader, ToolbarSearch } from '../components/ui.jsx';
import { TraceDetail } from './TracePage.jsx';
import { formatTime, truncate } from '../utils/format.js';

function ratingTone(rating) {
  return rating === 'UP' ? 'green' : 'red';
}

function ratingText(rating) {
  return rating === 'UP' ? '正向' : '负向';
}

function calculateRate(count, total) {
  if (!total) {
    return '0%';
  }
  return `${Math.round((count / total) * 100)}%`;
}

export function FeedbackPage({ api, toast }) {
  const [feedbacks, setFeedbacks] = useState([]);
  const [summary, setSummary] = useState({});
  const [insights, setInsights] = useState({});
  const [query, setQuery] = useState('');
  const [rating, setRating] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState(null);
  const [traceDetail, setTraceDetail] = useState(null);
  const [traceLoadingId, setTraceLoadingId] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ limit: '200' });
      if (rating !== 'ALL') {
        params.set('rating', rating);
      }
      const dashboard = await api.get(`/api/v1/agent-feedbacks/dashboard?${params.toString()}`);
      if (dashboard.success === false) {
        toast?.(dashboard.message || '反馈数据加载失败', 'error');
        return;
      }
      setSummary(dashboard.summary || {});
      setInsights(dashboard.insights || {});
      setFeedbacks(dashboard.feedbacks?.feedbacks || []);
    } catch (error) {
      toast?.(error.message || '反馈数据加载失败', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [rating]);

  const openTraceDetail = async (traceId) => {
    if (!traceId) {
      toast?.('当前反馈没有关联执行轨迹', 'error');
      return;
    }
    setTraceLoadingId(traceId);
    try {
      const response = await api.get(`/api/v1/agent-traces/${traceId}`);
      if (response.traceId) {
        setTraceDetail(response);
      } else {
        toast?.(response.message || '执行轨迹不存在或无权限', 'error');
      }
    } catch (error) {
      toast?.(error.message || '执行轨迹加载失败', 'error');
    } finally {
      setTraceLoadingId('');
    }
  };

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return feedbacks.filter((feedback) => {
      return !keyword || JSON.stringify(feedback).toLowerCase().includes(keyword);
    });
  }, [feedbacks, query]);

  const totalCount = Number(summary.totalCount ?? feedbacks.length);
  const upCount = Number(summary.upCount ?? feedbacks.filter((item) => item.rating === 'UP').length);
  const downCount = Number(summary.downCount ?? feedbacks.filter((item) => item.rating === 'DOWN').length);
  const positiveRate = summary.positiveRate == null ? calculateRate(upCount, totalCount) : `${Math.round(summary.positiveRate * 100)}%`;
  const latestNegative = summary.latestNegative || feedbacks.find((item) => item.rating === 'DOWN');

  return (
    <>
      <PageHeader
        title="回答反馈"
        desc="查看用户对 Agent 回答的正负反馈，用于排查低质量回答和优化提示词、工具与检索策略"
        actions={<><ToolbarSearch value={query} onChange={setQuery} placeholder="搜索用户、问题、回答、备注" /><select className="select compact-select" value={rating} onChange={(event) => setRating(event.target.value)}><option value="ALL">全部评分</option><option value="UP">正向</option><option value="DOWN">负向</option></select><button className="btn" onClick={load}><RefreshCw size={16} />刷新</button></>}
      />
      <div className="grid grid-4">
        <Metric label="反馈总数" value={totalCount} />
        <Metric label="正向反馈" value={upCount} hint={positiveRate} />
        <Metric label="负向反馈" value={downCount} hint={calculateRate(downCount, totalCount)} />
        <Metric label="筛选结果" value={filtered.length} />
      </div>
      {latestNegative && <section className="quality-alert">
        <div><ThumbsDown size={18} /><strong>最近负向反馈</strong></div>
        <p>{truncate(latestNegative.question || latestNegative.comment || latestNegative.answer, 180)}</p>
      </section>}
      <QualityInsights insights={insights} />
      <DataTable loading={loading} empty={<EmptyState title="暂无回答反馈" desc="用户在智能对话中点赞或点踩后，这里会展示反馈记录。" />} columns={[
        { key: 'createdAt', title: '时间', render: (item) => formatTime(item.createdAt) },
        { key: 'rating', title: '评分', render: (item) => <Badge tone={ratingTone(item.rating)}>{ratingText(item.rating)}</Badge> },
        { key: 'userId', title: '用户', render: (item) => <><strong>{item.userId || '-'}</strong><div className="muted">{item.tenantId || '-'}</div></> },
        { key: 'question', title: '问题', render: (item) => truncate(item.question, 90) || '-' },
        { key: 'answer', title: '回答摘要', render: (item) => truncate(item.answer, 120) || '-' },
        { key: 'comment', title: '备注', render: (item) => truncate(item.comment, 80) || '-' },
        { key: 'traceId', title: '轨迹', render: (item) => item.traceId ? <span className="mono">{truncate(item.traceId, 12)}</span> : '-' },
        { key: 'actions', title: '操作', render: (item) => <div className="toolbar"><button className="btn" onClick={() => setDetail(item)}><Eye size={16} />详情</button>{item.traceId && <button className="btn" disabled={traceLoadingId === item.traceId} onClick={() => openTraceDetail(item.traceId)}><GitBranch size={16} />轨迹</button>}</div> }
      ]} rows={filtered} rowKey="feedbackId" />
      {detail && <FeedbackDetail feedback={detail} traceLoading={traceLoadingId === detail.traceId} onOpenTrace={openTraceDetail} onClose={() => setDetail(null)} />}
      {traceDetail && <TraceDetail trace={traceDetail} api={api} onClose={() => setTraceDetail(null)} />}
    </>
  );
}

function QualityInsights({ insights }) {
  const issues = Array.isArray(insights.issues) ? insights.issues : [];
  const recommendations = Array.isArray(insights.recommendations) ? insights.recommendations : [];
  if (!issues.length && !recommendations.length) {
    return null;
  }
  return (
    <section className="quality-insights">
      <div className="card">
        <div className="section-title">问题分布</div>
        <div className="quality-issue-list">
          {issues.length ? issues.map((issue) => <QualityIssue key={issue.issueType} issue={issue} />)
            : <EmptyState title="暂无负向样本" desc="当前没有可归类的负向反馈。" />}
        </div>
      </div>
      <div className="card">
        <div className="section-title">优化建议</div>
        <div className="quality-recommendations">
          {recommendations.map((item, index) => <div className="quality-recommendation" key={`${item}-${index}`}>{item}</div>)}
        </div>
      </div>
    </section>
  );
}

function QualityIssue({ issue }) {
  const percent = `${Math.round(Number(issue.ratio || 0) * 100)}%`;
  const samples = Array.isArray(issue.samples) ? issue.samples : [];
  return (
    <div className="quality-issue">
      <div className="quality-issue-header">
        <strong>{issue.issueName || issue.issueType}</strong>
        <Badge tone="amber">{issue.count || 0} 条</Badge>
      </div>
      <div className="quality-meter"><span style={{ width: percent }} /></div>
      <p>{issue.recommendation}</p>
      {samples.length > 0 && <div className="quality-samples">
        {samples.map((sample) => <span key={sample.feedbackId}>{truncate(sample.comment || sample.question, 42)}</span>)}
      </div>}
    </div>
  );
}

function FeedbackDetail({ feedback, traceLoading, onOpenTrace, onClose }) {
  return (
    <Modal
      title="反馈详情"
      onClose={onClose}
      size="lg"
      actions={feedback.traceId && <button className="btn primary" disabled={traceLoading} onClick={() => onOpenTrace(feedback.traceId)}><GitBranch size={16} />查看执行轨迹</button>}
    >
      <div className="detail-grid">
        <span>反馈 ID</span><strong className="mono">{feedback.feedbackId}</strong>
        <span>时间</span><span>{formatTime(feedback.createdAt)}</span>
        <span>用户</span><span>{feedback.userId || '-'}</span>
        <span>租户</span><span>{feedback.tenantId || '-'}</span>
        <span>会话</span><span className="mono">{feedback.sessionId || '-'}</span>
        <span>轨迹</span><span className="mono">{feedback.traceId || '-'}</span>
        <span>评分</span><span><Badge tone={ratingTone(feedback.rating)}>{ratingText(feedback.rating)}</Badge></span>
        <span>更新时间</span><span>{formatTime(feedback.updatedAt)}</span>
      </div>
      <Field label="用户问题"><pre className="code-panel">{feedback.question || '-'}</pre></Field>
      <Field label="Agent 回答"><pre className="code-panel">{feedback.answer || '-'}</pre></Field>
      <Field label="备注"><pre className="code-panel">{feedback.comment || '-'}</pre></Field>
    </Modal>
  );
}
