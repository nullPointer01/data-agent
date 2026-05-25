import { Eye, RefreshCw } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Badge, DataTable, EmptyState, Field, Metric, Modal, PageHeader, ToolbarSearch } from '../components/ui.jsx';
import { formatTime } from '../utils/format.js';

function tone(status) {
  return status === 'SUCCESS' ? 'green' : status === 'FAILED' ? 'red' : 'gray';
}

export function AuditPage({ api }) {
  const [logs, setLogs] = useState([]);
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
      setLogs((await api.get(`/api/v1/audit-logs?${params.toString()}`)).logs || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) {
      return logs;
    }
    return logs.filter((log) => JSON.stringify(log).toLowerCase().includes(keyword));
  }, [logs, query]);

  return (
    <>
      <PageHeader
        title="审计日志"
        desc="查看关键资源操作、安全事件和请求来源"
        actions={<><ToolbarSearch value={query} onChange={setQuery} placeholder="搜索动作、资源、消息" /><input className="input compact" placeholder="用户 ID" value={userId} onChange={(event) => setUserId(event.target.value)} /><button className="btn" onClick={load}><RefreshCw size={16} />刷新</button></>}
      />
      <div className="grid grid-4">
        <Metric label="日志总数" value={logs.length} />
        <Metric label="成功" value={logs.filter((item) => item.status === 'SUCCESS').length} />
        <Metric label="失败" value={logs.filter((item) => item.status === 'FAILED').length} />
        <Metric label="筛选结果" value={filtered.length} />
      </div>
      <DataTable loading={loading} empty={<EmptyState title="暂无审计日志" desc="关键操作发生后会写入审计日志。" />} columns={[
        { key: 'createdAt', title: '时间', render: (item) => formatTime(item.createdAt) },
        { key: 'username', title: '用户', render: (item) => item.username || item.userId || '-' },
        { key: 'action', title: '动作' },
        { key: 'resourceType', title: '资源', render: (item) => `${item.resourceType || '-'} / ${item.resourceId || '-'}` },
        { key: 'status', title: '状态', render: (item) => <Badge tone={tone(item.status)}>{item.status || '-'}</Badge> },
        { key: 'clientIp', title: 'IP' },
        { key: 'actions', title: '操作', render: (item) => <button className="btn" onClick={() => setDetail(item)}><Eye size={16} />详情</button> }
      ]} rows={filtered} rowKey="id" />
      {detail && <Modal title="审计详情" onClose={() => setDetail(null)} size="lg">
        <div className="detail-grid">
          <span>时间</span><strong>{formatTime(detail.createdAt)}</strong>
          <span>用户</span><span>{detail.username || detail.userId || '-'}</span>
          <span>动作</span><span>{detail.action || '-'}</span>
          <span>资源</span><span>{detail.resourceType || '-'} / {detail.resourceId || '-'}</span>
          <span>状态</span><span>{detail.status || '-'}</span>
          <span>IP</span><span>{detail.clientIp || '-'}</span>
          <span>消息</span><span>{detail.message || '-'}</span>
        </div>
        <Field label="原始数据"><pre className="code-panel">{JSON.stringify(detail, null, 2)}</pre></Field>
      </Modal>}
    </>
  );
}
