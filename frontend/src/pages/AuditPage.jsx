import { Eye, RefreshCw } from 'lucide-react';
import { useState } from 'react';
import { Badge, DataTable, EmptyState, Field, ListLoadError, Metric, Modal, PageHeader, ToolbarSearch } from '../components/ui.jsx';
import { useDebouncedValue, useServerList } from '../hooks/useServerList.js';
import { formatTime } from '../utils/format.js';

function tone(status) {
  return status === 'SUCCESS' ? 'green' : status === 'FAILED' ? 'red' : 'gray';
}

/** 审计页：按动作、资源和用户检索平台操作日志。 */
export function AuditPage({ api }) {
  const [query, setQuery] = useState('');
  const [userId, setUserId] = useState('');
  const [detail, setDetail] = useState(null);
  const keyword = useDebouncedValue(query.trim());
  const owner = useDebouncedValue(userId.trim());
  const list = useServerList({ api, url: '/api/v1/audit-logs', filters: { query: keyword, userId: owner } });
  const logs = list.items;

  return (
    <>
      <PageHeader
        title="审计日志"
        desc="查看关键资源操作、安全事件和请求来源"
        actions={<><ToolbarSearch value={query} onChange={setQuery} placeholder="搜索全部动作、资源、消息" /><input className="input compact" placeholder="用户 ID" value={userId} onChange={(event) => setUserId(event.target.value)} /><button className="btn" onClick={() => list.refresh({ reset: true })}><RefreshCw size={16} />刷新</button></>}
      />
      <div className="grid grid-4">
        <Metric label="本页日志" value={logs.length} />
        <Metric label="本页成功" value={logs.filter((item) => item.status === 'SUCCESS').length} />
        <Metric label="本页失败" value={logs.filter((item) => item.status === 'FAILED').length} />
        <Metric label="后续记录" value={list.response ? (list.response.hasMore ? '还有更多' : '已到末页') : '-'} />
      </div>
      <ListLoadError error={list.error} onRetry={() => list.refresh()} />
      {!list.error && <DataTable loading={list.loading} serverPagination={list.pagination} empty={<EmptyState title="暂无匹配审计日志" desc="调整筛选条件，或返回上一页查看记录。" />} columns={[
        { key: 'createdAt', title: '时间', render: (item) => formatTime(item.createdAt) },
        { key: 'username', title: '用户', render: (item) => item.username || item.userId || '-' },
        { key: 'action', title: '动作' },
        { key: 'resourceType', title: '资源', render: (item) => `${item.resourceType || '-'} / ${item.resourceId || '-'}` },
        { key: 'status', title: '状态', render: (item) => <Badge tone={tone(item.status)}>{item.status || '-'}</Badge> },
        { key: 'clientIp', title: 'IP' },
        { key: 'actions', title: '操作', render: (item) => <button className="btn" onClick={() => setDetail(item)}><Eye size={16} />详情</button> }
      ]} rows={logs} rowKey="id" />}
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
