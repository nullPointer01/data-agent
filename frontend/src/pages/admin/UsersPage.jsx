import { RefreshCw, Save, UserPlus } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { loadListOptions } from '../../api/listOptions.js';
import { Badge, DataTable, EmptyState, Field, ListLoadError, Metric, Modal, PageHeader, ToolbarSearch } from '../../components/ui.jsx';
import { useDebouncedValue, useServerList } from '../../hooks/useServerList.js';
import { formatTime } from '../../utils/format.js';

/** 用户治理页：管理账号、角色、启用状态、密码和 Token 配额。 */
export function UsersPage({ api, toast }) {
  const [roles, setRoles] = useState([]);
  const [rolesReady, setRolesReady] = useState(false);
  const [rolesError, setRolesError] = useState('');
  const roleRequest = useRef(null);
  const [query, setQuery] = useState('');
  const keyword = useDebouncedValue(query.trim());
  const list = useServerList({ api, url: '/api/v1/admin/users', mode: 'offset', filters: { query: keyword } });
  const users = list.items;
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({});

  const empty = {
    username: '',
    password: '',
    nickname: '',
    email: '',
    tenantId: 'default',
    roles: ['USER'],
    enabled: true,
    dailyTokenLimit: 0
  };

  const loadRoles = useCallback(async () => {
    roleRequest.current?.abort();
    const controller = new AbortController();
    roleRequest.current = controller;
    setRolesReady(false);
    setRolesError('');
    try {
      const options = await loadListOptions(api, '/api/v1/admin/rbac/roles', {}, controller.signal);
      if (controller.signal.aborted) return;
      setRoles(options);
      setRolesReady(true);
    } catch (error) {
      if (!controller.signal.aborted) setRolesError(error.message || '角色选项加载失败');
    }
  }, [api]);

  const load = async () => {
    await Promise.all([list.refresh(), loadRoles()]);
  };

  useEffect(() => { loadRoles(); return () => roleRequest.current?.abort(); }, [loadRoles]);

  const open = (user) => {
    if (!rolesReady) { toast('角色选项尚未完整加载，请重试', 'error'); return; }
    setEditing(user || {});
    setForm(user ? { ...user, roles: user.roles || ['USER'], password: '' } : empty);
  };

  const save = async () => {
    if (!rolesReady) { toast('角色选项不可用，暂不能保存', 'error'); return; }
    if (editing.userId) {
      const updated = await api.put(`/api/v1/admin/users/${editing.userId}`, form);
      if (updated.success === false) { toast(updated.message || '用户更新失败', 'error'); return; }
      const assigned = await api.put(`/api/v1/admin/users/${editing.userId}/roles`, { roles: form.roles });
      if (assigned.success === false) { toast(assigned.message || '角色更新失败', 'error'); return; }
      toast('用户已更新', 'success');
    } else {
      const created = await api.post('/api/v1/admin/users', form);
      if (created.success === false) { toast(created.message || '用户创建失败', 'error'); return; }
      toast('用户已创建', 'success');
    }
    setEditing(null);
    list.refresh({ reset: true });
  };

  const columns = [
    { key: 'user', title: '用户', render: (user) => <><strong>{user.username}</strong><div className="muted">{user.nickname || '-'} · {user.email || '-'}</div></> },
    { key: 'tenantId', title: '租户' },
    { key: 'roles', title: '角色', render: (user) => (user.roles || []).map((role) => <Badge key={role}>{role}</Badge>) },
    { key: 'dailyTokenLimit', title: '配额', render: (user) => formatQuota(user.dailyTokenLimit) },
    { key: 'enabled', title: '状态', render: (user) => <Badge tone={user.enabled ? 'green' : 'gray'}>{user.enabled ? '启用' : '禁用'}</Badge> },
    { key: 'createdAt', title: '创建时间', render: (user) => formatTime(user.createdAt) },
    { key: 'actions', title: '操作', render: (user) => <button className="btn" onClick={() => open(user)}>编辑</button> }
  ];

  return (
    <>
      <PageHeader
        title="用户管理"
        desc="由现有管理员创建账号、分配角色并配置 Token 配额"
        actions={<><ToolbarSearch value={query} onChange={setQuery} placeholder="搜索用户、租户、邮箱" /><button className="btn" onClick={load}><RefreshCw size={16} />刷新</button><button className="btn primary" onClick={() => open()}><UserPlus size={16} />创建用户</button></>}
      />
      <div className="grid grid-3">
        <Metric label="匹配用户" value={list.response?.total ?? '-'} hint="全部搜索结果" />
        <Metric label="启用账号" value={list.response?.enabledCount ?? '-'} hint="全部搜索结果" />
        <Metric label="管理员" value={list.response?.adminCount ?? '-'} hint="全部搜索结果" />
      </div>
      <div style={{ marginTop: 16 }}>
        <ListLoadError error={rolesError} onRetry={loadRoles} />
        <ListLoadError error={list.error} onRetry={() => list.refresh()} />
        {!list.error && <DataTable columns={columns} rows={users} rowKey="userId" loading={list.loading} serverPagination={list.pagination} empty={<EmptyState title="暂无匹配用户" desc="调整搜索条件或创建用户。" />} />}
      </div>
      {editing && <UserModal form={form} setForm={setForm} editing={editing} roles={roles} ready={rolesReady} onClose={() => setEditing(null)} onSave={save} />}
    </>
  );
}

function UserModal({ form, setForm, editing, roles, ready, onClose, onSave }) {
  const toggleRole = (role) => {
    const roles = new Set(form.roles || []);
    if (roles.has(role)) {
      roles.delete(role);
    } else {
      roles.add(role);
    }
    setForm({ ...form, roles: Array.from(roles) });
  };

  return (
    <Modal title={editing.userId ? '编辑用户' : '创建用户'} onClose={onClose} actions={<button className="btn primary" disabled={!ready} onClick={onSave}><Save size={16} />保存</button>}>
      <div className="form-grid">
        <Field label="用户名"><input className="input" disabled={!!editing.userId} value={form.username || ''} onChange={(event) => setForm({ ...form, username: event.target.value })} /></Field>
        {!editing.userId && <Field label="密码"><input className="input" type="password" value={form.password || ''} onChange={(event) => setForm({ ...form, password: event.target.value })} /></Field>}
        <Field label="昵称"><input className="input" value={form.nickname || ''} onChange={(event) => setForm({ ...form, nickname: event.target.value })} /></Field>
        <Field label="邮箱"><input className="input" value={form.email || ''} onChange={(event) => setForm({ ...form, email: event.target.value })} /></Field>
        <Field label="租户"><input className="input" value={form.tenantId || ''} onChange={(event) => setForm({ ...form, tenantId: event.target.value })} /></Field>
        <Field label="Token 配额"><input className="input" type="number" value={form.dailyTokenLimit ?? 0} onChange={(event) => setForm({ ...form, dailyTokenLimit: Number(event.target.value) })} /></Field>
      </div>
      <div className="toolbar" style={{ marginTop: 14 }}>
        {roles.map((role) => (
          <label className="badge" key={role.roleCode}>
            <input type="checkbox" checked={(form.roles || []).includes(role.roleCode)} onChange={() => toggleRole(role.roleCode)} />
            {role.roleCode}
          </label>
        ))}
        <label className="badge"><input type="checkbox" checked={form.enabled !== false} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} />启用</label>
      </div>
    </Modal>
  );
}

function formatQuota(value) {
  if (value === -1) {
    return '不限';
  }
  if (value === 0 || value == null) {
    return '全局默认';
  }
  return Number(value).toLocaleString();
}
