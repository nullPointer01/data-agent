import { useEffect, useState } from 'react';
import { ResourcePage } from '../../components/admin/ResourcePage.jsx';
import { Badge } from '../../components/ui.jsx';

export function AgentsPage({ api, toast }) {
  const [references, setReferences] = useState({ models: [], skills: [], datasources: [] });
  const [availableTools, setAvailableTools] = useState([]);
  const [registry, setRegistry] = useState(null);
  const [detail, setDetail] = useState(null);

  const loadRegistry = async () => {
    const response = await api.get('/api/v1/agents/registry').catch(() => null);
    setRegistry(response);
  };

  useEffect(() => {
    Promise.all([
      api.get('/api/v1/models/list').catch(() => ({ models: [] })),
      api.get('/api/v1/skills/list').catch(() => ({ skills: [] })),
      api.get('/api/v1/datasources/list').catch(() => ({ datasources: [] })),
      api.get('/api/v1/agents/registry').catch(() => null),
      api.get('/api/v1/agents/available-tools').catch(() => ({ tools: [] }))
    ]).then(([models, skills, datasources, registryResponse, toolsResponse]) => {
      setReferences({
        models: models.models || [],
        skills: skills.skills || [],
        datasources: datasources.datasources || []
      });
      setRegistry(registryResponse);
      setAvailableTools(toolsResponse.tools || []);
    });
  }, []);

  return (
    <>
      <AgentRegistryPanel registry={registry} />
      <ResourcePage
        api={api}
        toast={toast}
        title="Agent 管理"
        desc="配置任务场景 Agent、系统提示词和资源绑定"
        listUrl="/api/v1/agents"
        listKey="agents"
        idKey="agentId"
        createUrl="/api/v1/agents"
        updateUrl={(id) => `/api/v1/agents/${id}`}
        toggleUrl={(id) => `/api/v1/agents/${id}/enabled`}
        deleteUrl={(id) => `/api/v1/agents/${id}`}
        headerExtra={<button className="btn" onClick={loadRegistry}>刷新注册中心</button>}
        fields={[
          ['name', '名称'],
          ['type', '类型', 'select', [
            { value: 'REACT', label: 'ReAct 工具 Agent' },
            { value: 'SKILL', label: '技能 Agent' },
            { value: 'DATA', label: '数据源 Agent' },
            { value: 'KNOWLEDGE', label: '知识检索 Agent' },
            { value: 'CHART', label: '图表 Agent' },
            { value: 'REPORT', label: '报告 Agent' },
            { value: 'CHAT', label: '对话 Agent' }
          ]],
          ['executionMode', '执行模式', 'select', [
            { value: 'react', label: 'ReAct 工具循环' },
            { value: 'chat', label: '纯对话' }
          ]],
          ['description', '描述'],
          ['systemPrompt', '系统提示词', 'textarea'],
          ['modelId', '模型', 'select', references.models.map((item) => ({ value: item.modelId, label: `${item.name} / ${item.modelName || '-'}` }))],
          ['tools', '工具', 'multiselect', availableTools.map((t) => ({ value: t.name, label: `${t.name} — ${t.description || ''}` }))],
          ['skillId', '技能', 'select', references.skills.map((item) => ({ value: item.skillId, label: item.name }))],
          ['datasourceId', '数据源', 'select', references.datasources.map((item) => ({ value: item.datasourceId, label: `${item.name} / ${item.type}` }))],
          ['enabled', '启用', 'checkbox']
        ]}
        visibleFields={['name', 'executionMode', 'description', 'modelId']}
        extraRowAction={(item) => <button className="btn" onClick={() => setDetail(item)}>详情</button>}
      />
      {detail && <AgentDetailModal api={api} agent={detail} onClose={() => setDetail(null)} />}
    </>
  );
}

function AgentDetailModal({ api, agent, onClose }) {
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
          <span>类型</span><span>{data.type || '-'}</span>
          <span>执行模式</span><span>{data.executionMode === 'chat' ? '纯对话' : 'ReAct 工具循环'}</span>
          <span>模型</span><span>{data.modelId || '-'}</span>
          <span>工具</span><span>{Array.isArray(data.tools) && data.tools.length > 0 ? data.tools.join(', ') : '全部（默认）'}</span>
          <span>技能</span><span>{data.skillId || '-'}</span>
          <span>数据源</span><span>{data.datasourceId || '-'}</span>
          <span>状态</span><span>{data.enabled === false ? '禁用' : '启用'}</span>
        </div>
        <div className="panel-card" style={{ marginTop: 16 }}>
          <div className="section-title">系统提示词</div>
          <pre className="code-panel">{data.systemPrompt || '-'}</pre>
        </div>
      </div>
    </div>
  );
}

function AgentRegistryPanel({ registry }) {
  if (!registry) {
    return null;
  }
  const capabilities = Array.isArray(registry.capabilities) ? registry.capabilities : [];
  return (
    <section className="agent-registry-panel">
      <div className="agent-registry-head">
        <div>
          <strong>Orchestrator 注册中心</strong>
          <p>当前编排器可调度的系统专家和租户自定义 Agent 能力目录。</p>
        </div>
        <div className="toolbar">
          <Badge tone="blue">系统 {registry.systemSpecialistCount || 0}</Badge>
          <Badge tone="green">启用 {registry.enabledTenantAgentCount || 0}</Badge>
          <Badge tone="gray">租户 {registry.tenantAgentCount || 0}</Badge>
        </div>
      </div>
      <div className="agent-capability-grid">
        {capabilities.map((capability) => (
          <div className="agent-capability-card" key={capability.type}>
            <div className="agent-capability-title">
              <strong>{capability.displayName || capability.type}</strong>
              <Badge tone={capability.systemAvailable ? 'green' : 'red'}>
                {capability.systemAvailable ? '可执行' : '缺实现'}
              </Badge>
            </div>
            <p>{capability.description}</p>
            <div className="agent-capability-meta">
              <span>{capability.capability}</span>
              <span>配置 {capability.tenantAgentCount || 0}</span>
              <span>启用 {capability.enabledTenantAgentCount || 0}</span>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
