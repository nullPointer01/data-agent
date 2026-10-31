import { RefreshCw } from 'lucide-react';
import { useState } from 'react';
import { Badge, DataTable, EmptyState, ListLoadError, Metric, PageHeader, ToolbarSearch } from '../../components/ui.jsx';
import { useDebouncedValue, useServerList } from '../../hooks/useServerList.js';
import { formatTime } from '../../utils/format.js';

/** RBAC 只读概览：两张表独立搜索/分页，指标来自全部匹配结果。 */
export function RolesPage({ api }) {
  const [roleQuery, setRoleQuery] = useState('');
  const [permissionQuery, setPermissionQuery] = useState('');
  const roleKeyword = useDebouncedValue(roleQuery.trim());
  const permissionKeyword = useDebouncedValue(permissionQuery.trim());
  const roles = useServerList({ api, url: '/api/v1/admin/rbac/roles', mode: 'offset', filters: { query: roleKeyword } });
  const permissions = useServerList({ api, url: '/api/v1/admin/rbac/permissions', mode: 'offset', filters: { query: permissionKeyword } });
  const load = () => Promise.all([roles.refresh(), permissions.refresh()]);

  return (
    <>
      <PageHeader
        title="角色权限"
        desc="查看系统角色、权限点和角色权限绑定关系"
        actions={<button className="btn" onClick={load}><RefreshCw size={16} />刷新</button>}
      />
      <div className="grid grid-4">
        <Metric label="匹配角色" value={roles.response?.total ?? '-'} hint="全部角色搜索结果" />
        <Metric label="启用角色" value={roles.response?.enabledCount ?? '-'} hint="全部角色搜索结果" />
        <Metric label="匹配权限点" value={permissions.response?.total ?? '-'} hint="全部权限搜索结果" />
        <Metric label="系统角色" value={roles.response?.systemCount ?? '-'} hint="全部角色搜索结果" />
      </div>
      <section className="card panel-card" style={{ marginTop: 16 }}>
        <div className="panel-title"><span>角色</span><ToolbarSearch value={roleQuery} onChange={setRoleQuery} placeholder="搜索角色编码、名称、说明" /></div>
        <ListLoadError error={roles.error} onRetry={roles.refresh} />
        {!roles.error && <DataTable
          loading={roles.loading}
          serverPagination={roles.pagination}
          empty={<EmptyState title="暂无匹配角色" desc="调整搜索条件后重试。" />}
          columns={[
            { key: 'roleCode', title: '角色编码', render: (item) => <Badge tone="blue">{item.roleCode}</Badge> },
            { key: 'name', title: '角色名称' },
            { key: 'description', title: '说明' },
            { key: 'permissions', title: '权限', render: (item) => (item.permissions || []).map((code) => <Badge key={code} tone="gray">{code}</Badge>) },
            { key: 'enabled', title: '状态', render: (item) => <Badge tone={item.enabled ? 'green' : 'gray'}>{item.enabled ? '启用' : '禁用'}</Badge> },
            { key: 'updatedAt', title: '更新时间', render: (item) => formatTime(item.updatedAt) }
          ]}
          rows={roles.items}
          rowKey="roleCode"
        />}
      </section>
      <section className="card panel-card" style={{ marginTop: 16 }}>
        <div className="panel-title"><span>权限点</span><ToolbarSearch value={permissionQuery} onChange={setPermissionQuery} placeholder="搜索权限编码、名称、资源、动作" /></div>
        <ListLoadError error={permissions.error} onRetry={permissions.refresh} />
        {!permissions.error && <DataTable
          loading={permissions.loading}
          serverPagination={permissions.pagination}
          empty={<EmptyState title="暂无匹配权限" desc="调整搜索条件后重试。" />}
          columns={[
            { key: 'permissionCode', title: '权限编码', render: (item) => <Badge tone="gray">{item.permissionCode}</Badge> },
            { key: 'name', title: '权限名称' },
            { key: 'resourceType', title: '资源类型' },
            { key: 'action', title: '动作' },
            { key: 'description', title: '说明' },
            { key: 'enabled', title: '状态', render: (item) => <Badge tone={item.enabled ? 'green' : 'gray'}>{item.enabled ? '启用' : '禁用'}</Badge> }
          ]}
          rows={permissions.items}
          rowKey="permissionCode"
        />}
      </section>
    </>
  );
}
