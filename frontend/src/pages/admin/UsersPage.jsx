import { Check, RefreshCw, Save, UserPlus, X } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Badge, ConfirmDialog, DataTable, EmptyState, Field, Metric, Modal, PageHeader, ToolbarSearch } from '../../components/ui.jsx';
import { requestTone, statusText } from '../AdminRequestPage.jsx';
import { formatTime } from '../../utils/format.js';

export function UsersPage({ api, toast }) {
  const [users, setUsers] = useState([]);
  const [requests, setRequests] = useState([]);
  const [roles, setRoles] = useState([]);
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [loadingRequests, setLoadingRequests] = useState(true);
  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState(null);
  const [confirm, setConfirm] = useState(null);
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

  const loadUsers = async () => {
    setLoadingUsers(true);
    try {
      setUsers((await api.get('/api/v1/admin/users')).data || []);
    } finally {
      setLoadingUsers(false);
    }
  };

  const loadRequests = async () => {
    setLoadingRequests(true);
    try {
      setRequests((await api.get('/api/v1/admin-role-requests?status=PENDING')).requests || []);
    } finally {
      setLoadingRequests(false);
    }
  };

  const loadRoles = async () => {
    const res = await api.get('/api/v1/admin/rbac/roles');
    setRoles(res.roles || []);
  };

  const load = async () => {
    await Promise.all([loadUsers(), loadRequests(), loadRoles()]);
  };

  useEffect(() => { load(); }, []);

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) {
      return users;
    }
    return users.filter((user) => [user.username, user.nickname, user.email, user.tenantId]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword));
  }, [query, users]);

  const open = (user) => {
    setEditing(user || {});
    setForm(user ? { ...user, roles: user.roles || ['USER'], password: '' } : empty);
  };

  const save = async () => {
    if (editing.userId) {
      await api.put(`/api/v1/admin/users/${editing.userId}`, form);
      await api.put(`/api/v1/admin/users/${editing.userId}/roles`, { roles: form.roles });
      toast('用户已更新', 'success');
    } else {
      await api.post('/api/v1/admin/users', form);
      toast('用户已创建', 'success');
    }
    setEditing(null);
    loadUsers();
  };

  const approve = (request) => {
    setConfirm({
      message: `确认通过 ${request.username} 的管理员申请？通过后该用户重新登录即可获得管理权限。`,
      onConfirm: async () => {
        await api.post(`/api/v1/admin-role-requests/${request.requestId}/approve`, { comment: '审核通过' });
        setConfirm(null);
        toast('管理员申请已通过', 'success');
        load();
      }
    });
  };

  const reject = (request) => {
    setConfirm({
      message: `确认拒绝 ${request.username} 的管理员申请？`,
      danger: true,
      onConfirm: async () => {
        await api.post(`/api/v1/admin-role-requests/${request.requestId}/reject`, { comment: '审核拒绝' });
        setConfirm(null);
        toast('管理员申请已拒绝', 'success');
        loadRequests();
      }
    });
  };

  const requestColumns = [
    { key: 'createdAt', title: '提交时间', render: (item) => formatTime(item.createdAt) },
    { key: 'username', title: '申请人', render: (item) => <><strong>{item.username}</strong><div className="muted">{item.tenantId}</div></> },
    { key: 'reason', title: '申请理由' },
    { key: 'status', title: '状态', render: (item) => <Badge tone={requestTone(item.status)}>{statusText(item.status)}</Badge> },
    {
      key: 'actions',
      title: '操作',
      render: (item) => (
        <div className="toolbar">
          <button className="btn primary" onClick={() => approve(item)}><Check size={16} />通过</button>
          <button className="btn danger" onClick={() => reject(item)}><X size={16} />拒绝</button>
        </div>
      )
    }
  ];

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
        desc="创建用户、角色授权、审批管理员申请和配置 Token 配额"
        actions={<><ToolbarSearch value={query} onChange={setQuery} placeholder="搜索用户、租户、邮箱" /><button className="btn" onClick={load}><RefreshCw size={16} />刷新</button><button className="btn primary" onClick={() => open()}><UserPlus size={16} />创建用户</button></>}
      />
      <div className="grid grid-4">
        <Metric label="用户总数" value={users.length} />
        <Metric label="启用账号" value={users.filter((item) => item.enabled).length} />
        <Metric label="管理员" value={users.filter((item) => (item.roles || []).includes('ADMIN')).length} />
        <Metric label="待审申请" value={requests.length} />
      </div>
      <section className="card panel-card" style={{ marginTop: 16 }}>
        <div className="section-title">管理员申请审核</div>
        <DataTable
          columns={requestColumns}
          rows={requests}
          rowKey="requestId"
          loading={loadingRequests}
          empty={<EmptyState title="暂无待审申请" desc="普通用户提交管理员申请后，会出现在这里。" />}
        />
      </section>
      <div style={{ marginTop: 16 }}>
        <DataTable columns={columns} rows={filtered} rowKey="userId" loading={loadingUsers} empty={<EmptyState title="暂无用户" desc="创建第一个用户后，这里会显示账号和权限信息。" />} />
      </div>
      {editing && <UserModal form={form} setForm={setForm} editing={editing} roles={roles} onClose={() => setEditing(null)} onSave={save} />}
      {confirm && <ConfirmDialog title="确认操作" message={confirm.message} danger={confirm.danger} onCancel={() => setConfirm(null)} onConfirm={confirm.onConfirm} />}
    </>
  );
}

function UserModal({ form, setForm, editing, roles, onClose, onSave }) {
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
    <Modal title={editing.userId ? '编辑用户' : '创建用户'} onClose={onClose} actions={<button className="btn primary" onClick={onSave}><Save size={16} />保存</button>}>
      <div className="form-grid">
        <Field label="用户名"><input className="input" disabled={!!editing.userId} value={form.username || ''} onChange={(event) => setForm({ ...form, username: event.target.value })} /></Field>
        {!editing.userId && <Field label="密码"><input className="input" type="password" value={form.password || ''} onChange={(event) => setForm({ ...form, password: event.target.value })} /></Field>}
        <Field label="昵称"><input className="input" value={form.nickname || ''} onChange={(event) => setForm({ ...form, nickname: event.target.value })} /></Field>
        <Field label="邮箱"><input className="input" value={form.email || ''} onChange={(event) => setForm({ ...form, email: event.target.value })} /></Field>
        <Field label="租户"><input className="input" value={form.tenantId || ''} onChange={(event) => setForm({ ...form, tenantId: event.target.value })} /></Field>
        <Field label="Token 配额"><input className="input" type="number" value={form.dailyTokenLimit ?? 0} onChange={(event) => setForm({ ...form, dailyTokenLimit: Number(event.target.value) })} /></Field>
      </div>
      <div className="toolbar" style={{ marginTop: 14 }}>
        {(roles.length ? roles : [{ roleCode: 'USER' }, { roleCode: 'ADMIN' }]).map((role) => (
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
