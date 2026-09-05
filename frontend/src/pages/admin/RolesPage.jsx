import { RefreshCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Badge, DataTable, EmptyState, Metric, PageHeader } from '../../components/ui.jsx';
import { formatTime } from '../../utils/format.js';

export function RolesPage({ api }) {
  const [roles, setRoles] = useState([]);
  const [permissions, setPermissions] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const [roleRes, permissionRes] = await Promise.all([
        api.get('/api/v1/admin/rbac/roles'),
        api.get('/api/v1/admin/rbac/permissions')
      ]);
      setRoles(roleRes.roles || []);
      setPermissions(permissionRes.permissions || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  return (
    <>
      <PageHeader
        title="角色权限"
        desc="查看系统角色、权限点和角色权限绑定关系"
        actions={<button className="btn" onClick={load}><RefreshCw size={16} />刷新</button>}
      />
      <div className="grid grid-4">
        <Metric label="角色总数" value={roles.length} />
        <Metric label="启用角色" value={roles.filter((item) => item.enabled).length} />
        <Metric label="权限点" value={permissions.length} />
        <Metric label="系统角色" value={roles.filter((item) => item.systemRole).length} />
      </div>
      <section className="card panel-card" style={{ marginTop: 16 }}>
        <div className="section-title">角色</div>
        <DataTable
          loading={loading}
          empty={<EmptyState title="暂无角色" desc="系统启动后会初始化默认角色。" />}
          columns={[
            { key: 'roleCode', title: '角色编码', render: (item) => <Badge tone="blue">{item.roleCode}</Badge> },
            { key: 'name', title: '角色名称' },
            { key: 'description', title: '说明' },
            { key: 'permissions', title: '权限', render: (item) => (item.permissions || []).map((code) => <Badge key={code} tone="gray">{code}</Badge>) },
            { key: 'enabled', title: '状态', render: (item) => <Badge tone={item.enabled ? 'green' : 'gray'}>{item.enabled ? '启用' : '禁用'}</Badge> },
            { key: 'updatedAt', title: '更新时间', render: (item) => formatTime(item.updatedAt) }
          ]}
          rows={roles}
          rowKey="roleCode"
        />
      </section>
      <section className="card panel-card" style={{ marginTop: 16 }}>
        <div className="section-title">权限点</div>
        <DataTable
          loading={loading}
          empty={<EmptyState title="暂无权限" desc="系统启动后会初始化默认权限点。" />}
          columns={[
            { key: 'permissionCode', title: '权限编码', render: (item) => <Badge tone="gray">{item.permissionCode}</Badge> },
            { key: 'name', title: '权限名称' },
            { key: 'resourceType', title: '资源类型' },
            { key: 'action', title: '动作' },
            { key: 'description', title: '说明' },
            { key: 'enabled', title: '状态', render: (item) => <Badge tone={item.enabled ? 'green' : 'gray'}>{item.enabled ? '启用' : '禁用'}</Badge> }
          ]}
          rows={permissions}
          rowKey="permissionCode"
        />
      </section>
    </>
  );
}
