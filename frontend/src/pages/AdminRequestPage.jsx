import { RefreshCw, Send, ShieldCheck } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Badge, DataTable, EmptyState, Field, Metric, PageHeader } from '../components/ui.jsx';
import { formatTime } from '../utils/format.js';

export function AdminRequestPage({ api, user, admin, toast, goPage }) {
  const [requests, setRequests] = useState([]);
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      setRequests((await api.get('/api/v1/admin-role-requests/mine')).requests || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const latest = requests[0];
  const pending = useMemo(() => requests.some((item) => item.status === 'PENDING'), [requests]);

  const submit = async () => {
    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      toast('请填写申请理由', 'error');
      return;
    }
    const res = await api.post('/api/v1/admin-role-requests', { reason: trimmedReason });
    if (res.success !== false) {
      toast('管理员申请已提交', 'success');
      setReason('');
      load();
      return;
    }
    toast(res.message || '申请提交失败', 'error');
  };

  return (
    <>
      <PageHeader
        title="管理员申请"
        desc="提交管理员权限申请，等待现有管理员审核"
        actions={<><button className="btn" onClick={load}><RefreshCw size={16} />刷新</button>{admin && <button className="btn primary" onClick={() => goPage('users')}><ShieldCheck size={16} />进入审核</button>}</>}
      />
      <div className="grid grid-4">
        <Metric label="当前账号" value={user.username || '-'} hint={user.tenantId || 'default'} />
        <Metric label="当前角色" value={(user.roles || []).join(', ') || 'USER'} />
        <Metric label="申请总数" value={requests.length} />
        <Metric label="最新状态" value={latest?.status || '未申请'} />
      </div>
      {!admin && (
        <section className="card panel-card" style={{ marginTop: 16 }}>
          <div className="section-title">提交申请</div>
          <div className="form-stack">
            <Field label="申请理由">
              <textarea
                className="textarea"
                rows={5}
                maxLength={512}
                placeholder="说明你需要管理哪些资源、承担什么维护职责。"
                value={reason}
                onChange={(event) => setReason(event.target.value)}
              />
            </Field>
            <div className="toolbar">
              <button className="btn primary" disabled={pending} onClick={submit}><Send size={16} />提交申请</button>
              {pending && <Badge tone="amber">已有待审核申请</Badge>}
            </div>
          </div>
        </section>
      )}
      {admin && (
        <section className="card panel-card" style={{ marginTop: 16 }}>
          <div className="section-title">管理员账号</div>
          <p className="panel-muted">当前账号已经具备管理员权限，可以进入用户管理页处理其他用户的申请。</p>
        </section>
      )}
      <div style={{ marginTop: 16 }}>
        <DataTable
          loading={loading}
          empty={<EmptyState title="暂无申请记录" desc="提交管理员申请后，这里会显示审核进度。" />}
          columns={[
            { key: 'createdAt', title: '提交时间', render: (item) => formatTime(item.createdAt) },
            { key: 'reason', title: '理由' },
            { key: 'status', title: '状态', render: (item) => <Badge tone={requestTone(item.status)}>{statusText(item.status)}</Badge> },
            { key: 'reviewerName', title: '审核人', render: (item) => item.reviewerName || '-' },
            { key: 'reviewComment', title: '审核备注', render: (item) => item.reviewComment || '-' },
            { key: 'reviewedAt', title: '审核时间', render: (item) => formatTime(item.reviewedAt) }
          ]}
          rows={requests}
          rowKey="requestId"
        />
      </div>
    </>
  );
}

export function requestTone(status) {
  if (status === 'APPROVED') {
    return 'green';
  }
  if (status === 'REJECTED') {
    return 'red';
  }
  return 'amber';
}

export function statusText(status) {
  if (status === 'APPROVED') {
    return '已通过';
  }
  if (status === 'REJECTED') {
    return '已拒绝';
  }
  if (status === 'PENDING') {
    return '待审核';
  }
  return status || '-';
}
