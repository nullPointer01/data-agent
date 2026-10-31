import { useState } from 'react';
import { useDebouncedValue, useServerList } from '../../hooks/useServerList.js';
import { ListLoadError, Modal, ServerPagination, ToolbarSearch } from '../ui.jsx';

/** 归属用户选择器按需搜索全库账号，不依赖当前 Agent 页碰巧有哪些归属。 */
export function AgentOwnerFilter({ api, value, onChange }) {
  const [open, setOpen] = useState(false);
  const [label, setLabel] = useState('');
  return <>
    <button className="btn" onClick={() => setOpen(true)}>{value ? label || value : '全部归属用户'}</button>
    {value && <button className="btn" onClick={() => { onChange(''); setLabel(''); }}>清除归属</button>}
    {open && <OwnerPicker api={api} onClose={() => setOpen(false)} onSelect={(user) => {
      onChange(user.userId); setLabel(user.nickname || user.username); setOpen(false);
    }} />}
  </>;
}

function OwnerPicker({ api, onClose, onSelect }) {
  const [query, setQuery] = useState('');
  const keyword = useDebouncedValue(query);
  const list = useServerList({ api, url: '/api/v1/admin/users', mode: 'offset', filters: { query: keyword } });
  return <Modal title="选择归属用户" onClose={onClose}>
    <ToolbarSearch value={query} onChange={setQuery} placeholder="搜索账号或姓名" />
    <ListLoadError error={list.error} onRetry={list.refresh} />
    {!list.loading && !list.error && !list.items.length && <p>没有匹配的用户</p>}
    {list.loading ? <p>加载中…</p> : list.items.map((user) =>
      <button key={user.userId} className="btn" onClick={() => onSelect(user)}>
        {user.username}{user.nickname ? ` · ${user.nickname}` : ''}
      </button>
    )}
    <ServerPagination {...list.pagination} count={list.items.length} />
  </Modal>;
}
