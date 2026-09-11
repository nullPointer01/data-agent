import { GitBranch } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { ResourcePage } from '../../components/admin/ResourcePage.jsx';
import { Badge } from '../../components/ui.jsx';
import { formatTime, truncate } from '../../utils/format.js';

export function AgentsPage({ api, toast, onOpenTraces }) {
  const [models, setModels] = useState([]);
  const [users, setUsers] = useState([]);
  const [ownerFilter, setOwnerFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [detail, setDetail] = useState(null);

  useEffect(() => {
    Promise.all([
      api.get('/api/v1/models/list').catch(() => ({ models: [] })),
      api.get('/api/v1/admin/users').catch(() => ({ data: [] }))
    ]).then(([modelResponse, userResponse]) => {
      setModels(modelResponse.models || []);
      setUsers(userResponse.data || []);
    });
  }, []);

  const userById = useMemo(() => new Map(users.map((user) => [user.userId, user])), [users]);
  const modelById = useMemo(() => new Map(models.map((model) => [
    model.modelId,
    model.name || model.displayName || model.modelName || model.modelId
  ])), [models]);

  const ownerLabel = (createdBy) => {
    const owner = userById.get(createdBy);
    return owner ? [owner.username, owner.nickname].filter(Boolean).join(' ') : createdBy || '';
  };

  const tableColumns = [
    {
      key: 'name',
      title: '名称',
      render: (item) => <><strong>{item.name || '-'}</strong><div className="muted">{truncate(item.description || '未填写描述', 48)}</div></>
    },
    {
      key: 'agentRole',
      title: '角色',
      render: (item) => <Badge tone={item.defaultAgent ? 'blue' : 'purple'}>{item.defaultAgent ? '主 Agent' : '专家助手'}</Badge>
    },
    {
      key: 'owner',
      title: '归属用户',
      render: (item) => {
        const owner = userById.get(item.createdBy);
        return <><strong>{owner?.username || '未知用户'}</strong><div className="mono muted" title={item.createdBy || ''}>{owner?.nickname || truncate(item.createdBy || '-', 18)}</div></>;
      }
    },
    { key: 'executionMode', title: '执行模式', render: (item) => executionModeLabel(item.executionMode) },
    { key: 'modelId', title: '模型', render: (item) => modelById.get(item.modelId) || '系统默认' },
    { key: 'updatedAt', title: '更新时间', render: (item) => formatTime(item.updatedAt) }
  ];

  return (
    <>
      <ResourcePage
        api={api}
        toast={toast}
        title="用户 Agent"
        desc="查看用户 Agent 的归属、能力和运行状态；个性化配置由所属用户维护"
        listUrl="/api/v1/agents"
        listKey="agents"
        idKey="agentId"
        toggleUrl={(id) => `/api/v1/agents/${id}/enabled`}
        searchPlaceholder="搜索名称或归属用户"
        searchText={(item) => [item.name, item.description, item.createdBy, ownerLabel(item.createdBy), item.defaultAgent ? '主 Agent' : '专家助手'].filter(Boolean).join(' ')}
        filterPredicate={(item) => (!ownerFilter || item.createdBy === ownerFilter)
          && (!roleFilter || (roleFilter === 'PRIMARY') === Boolean(item.defaultAgent))}
        headerExtra={<>
          <select className="select compact-select" aria-label="按 Agent 角色筛选" value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)}>
            <option value="">全部角色</option>
            <option value="PRIMARY">主 Agent</option>
            <option value="EXPERT">专家助手</option>
          </select>
          <select className="select compact-select" aria-label="按归属用户筛选" value={ownerFilter} onChange={(event) => setOwnerFilter(event.target.value)}>
            <option value="">全部用户</option>
            {users.map((user) => <option key={user.userId} value={user.userId}>{user.username}{user.nickname ? ` · ${user.nickname}` : ''}</option>)}
          </select>
        </>}
        fields={[]}
        tableColumns={tableColumns}
        toggleLabel={(item) => item.enabled === false ? '启用' : '停用'}
        toggleBlockedReason={(item) => item.defaultAgent && item.enabled !== false ? '默认主 Agent 不能直接停用' : ''}
        extraRowAction={(item) => <><button className="btn" onClick={() => setDetail(item)}>详情</button><button className="btn" title="查看该归属用户的运行追踪" onClick={() => onOpenTraces?.(item.createdBy)}><GitBranch size={15} />用户追踪</button></>}
      />
      {detail && <AgentDetailModal api={api} agent={detail} users={userById} onClose={() => setDetail(null)} />}
    </>
  );
}

function AgentDetailModal({ api, agent, users, onClose }) {
  const [runtime, setRuntime] = useState(null);

  useEffect(() => {
    let cancelled = false;
    api.get(`/api/v1/agents/${agent.agentId}`).then((response) => {
      if (!cancelled) {
        setRuntime(response.agent || response);
      }
    }).catch(() => {
      if (!cancelled) {
        setRuntime(agent);
      }
    });
    return () => { cancelled = true; };
  }, [api, agent.agentId]);

  const data = runtime || agent;
  const owner = users.get(data.createdBy);
  const capabilityBindings = Array.isArray(data.capabilityBindings) ? data.capabilityBindings : [];
  const modeLabels = {
    auto: '自主决策',
    chat: '纯对话',
    react: 'ReAct 工具循环',
    orchestrated: 'Orchestrated 子 Agent 编排'
  };
  return (
    <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div className="modal lg">
        <div className="page-header">
          <div>
            <h2 className="page-title">{data.name || 'Agent 详情'}</h2>
            <p className="page-desc">{data.description || 'Agent 配置详情'}</p>
          </div>
          <button className="icon-button bordered" onClick={onClose}>×</button>
        </div>
        <div className="detail-grid">
          <span>Agent ID</span><span className="mono">{data.agentId || '-'}</span>
          <span>角色</span><span>{data.defaultAgent ? '主 Agent' : '专家助手'}</span>
          <span>归属用户</span><span>{owner?.username || '未知用户'}{owner?.nickname ? ` · ${owner.nickname}` : ''}</span>
          <span>用户 ID</span><span className="mono">{data.createdBy || '-'}</span>
          <span>执行模式</span><span>{modeLabels[data.executionMode] || data.executionMode || '-'}</span>
          <span>模型</span><span>{data.modelId || '-'}</span>
          <span>能力绑定</span>
          <span className="mono">
            {capabilityBindings.length > 0 ? capabilityBindings.join(', ') : '未绑定能力'}
          </span>
          <span>状态</span><span>{data.enabled === false ? '禁用' : '启用'}</span>
          <span>更新时间</span><span>{formatTime(data.updatedAt)}</span>
        </div>
        <div className="panel-card" style={{ marginTop: 16 }}>
          <div className="section-title">系统提示词</div>
          <pre className="code-panel">{data.systemPrompt || '-'}</pre>
        </div>
      </div>
    </div>
  );
}

function executionModeLabel(mode) {
  return {
    auto: '自主决策',
    chat: 'Chat',
    react: 'ReAct',
    orchestrated: 'Orchestrated'
  }[mode] || mode || '-';
}
