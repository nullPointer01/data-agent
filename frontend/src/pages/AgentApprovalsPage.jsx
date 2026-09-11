import { Eye, RefreshCw, ShieldCheck } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AgentApprovalDetail } from '../components/admin/AgentApprovalDetail.jsx';
import { Badge, DataTable, EmptyState, Metric, PageHeader } from '../components/ui.jsx';
import { formatTime, truncate } from '../utils/format.js';

const FILTERS = ['', 'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED'];

function tone(status) {
  if (status === 'APPROVED') return 'green';
  if (status === 'REJECTED' || status === 'EXPIRED' || status === 'CANCELLED') return 'red';
  return 'amber';
}

function ApprovalUser({ item }) {
  const displayName = item.requesterNickname || item.requesterUsername || '未知用户';
  const account = item.requesterUsername
    ? `@${item.requesterUsername}`
    : item.requesterUserId ? `用户 ID：${item.requesterUserId}` : '缺少申请人信息';
  return (
    <div className="approval-user-identity">
      <strong>{displayName}</strong>
      <span>{account}</span>
    </div>
  );
}

export function AgentApprovalsPage({ api, toast }) {
  const [items, setItems] = useState([]);
  const [status, setStatus] = useState('PENDING');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedId, setSelectedId] = useState('');
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [now, setNow] = useState(Date.now());

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const response = await api.agentApprovals.list(status);
      if (response.message && !Array.isArray(response.items)) throw new Error(response.message);
      setItems(response.items || []);
    } catch (requestError) {
      setError(requestError.message || '审批列表加载失败');
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [api, status]);

  const openDetail = async (approvalId) => {
    setSelectedId(approvalId);
    setDetail(null);
    setDetailLoading(true);
    try {
      const response = await api.agentApprovals.detail(approvalId);
      if (response.message && !response.approvalId) throw new Error(response.message);
      setDetail(response);
    } catch (requestError) {
      toast(requestError.message || '审批详情加载失败', 'error');
      setSelectedId('');
    } finally {
      setDetailLoading(false);
    }
  };

  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30000);
    return () => window.clearInterval(timer);
  }, []);

  const pendingCount = useMemo(() => items.filter((item) => item.decisionStatus === 'PENDING').length, [items]);
  const highRiskCount = useMemo(() => items.filter((item) => item.risk === 'HIGH').length, [items]);

  const decide = async (action, comment) => {
    if (!selectedId || submitting) return;
    setSubmitting(true);
    try {
      const response = action === 'approve'
        ? await api.agentApprovals.approve(selectedId, comment)
        : await api.agentApprovals.reject(selectedId, comment);
      if (!response.approvalId) throw new Error(response.message || '审批状态提交失败');
      setDetail(response);
      toast(action === 'approve' ? '动作已批准，等待恢复执行' : '动作已拒绝', 'success');
      await load();
    } catch (requestError) {
      toast(requestError.message || '审批状态可能已变化，正在刷新', 'error');
      await openDetail(selectedId);
      await load();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader
        title="动作审批"
        desc="审核 Agent 提议的高风险动作，并追踪恢复执行状态"
        actions={(
          <div className="approval-toolbar">
            <label className="field compact-field">
              <span>决定状态</span>
              <select className="select compact-select" value={status} onChange={(event) => setStatus(event.target.value)}>
                {FILTERS.map((value) => <option key={value || 'ALL'} value={value}>{value || '全部'}</option>)}
              </select>
            </label>
            <button className="btn" disabled={loading} onClick={load}><RefreshCw size={16} />刷新</button>
          </div>
        )}
      />
      <div className="grid grid-4">
        <Metric label="当前结果" value={items.length} hint={status || '全部状态'} />
        <Metric label="待处理" value={pendingCount} hint="需要四眼审批" />
        <Metric label="高风险" value={highRiskCount} hint="服务端策略判定" />
        <Metric label="审批规则" value="四眼原则" hint="申请人与审批人不可相同" />
      </div>

      {error && <div className="alert danger approval-load-error" role="alert">{error}</div>}
      <div className="approval-table-section">
        <DataTable
          loading={loading}
          empty={<EmptyState title="暂无审批动作" desc="当前状态下没有需要展示的 Agent 动作。" />}
          columns={[
            { key: 'requestedAt', title: '申请时间', render: (item) => formatTime(item.requestedAt) },
            { key: 'toolName', title: '工具', render: (item) => <strong>{item.toolName}</strong> },
            { key: 'risk', title: '风险', render: (item) => <Badge tone={item.risk === 'HIGH' ? 'red' : 'amber'}>{item.risk}</Badge> },
            { key: 'requesterUserId', title: '申请人', render: (item) => <ApprovalUser item={item} /> },
            { key: 'safeArgumentSummary', title: '参数摘要', render: (item) => <span className="approval-table-summary">{truncate(item.safeArgumentSummary, 72)}</span> },
            { key: 'decisionStatus', title: '决定', render: (item) => <Badge tone={tone(item.decisionStatus)}>{item.decisionStatus}</Badge> },
            { key: 'executionStatus', title: '执行', render: (item) => <Badge tone="gray">{item.executionStatus}</Badge> },
            { key: 'actions', title: '操作', render: (item) => <button className="icon-button bordered" title="查看审批详情" aria-label="查看审批详情" onClick={() => openDetail(item.approvalId)}><Eye size={16} /></button> }
          ]}
          rows={items}
          rowKey="approvalId"
        />
      </div>

      {selectedId && (
        <AgentApprovalDetail
          detail={detail}
          loading={detailLoading}
          now={now}
          submitting={submitting}
          toast={toast}
          onClose={() => { setSelectedId(''); setDetail(null); }}
          onDecision={decide}
        />
      )}
      <div className="approval-boundary-note"><ShieldCheck size={15} />批准只授权当前不可修改动作，不扩大 Agent 的其他工具权限。</div>
    </>
  );
}
