import { Edit3, Plus, Power, RefreshCw, Star, Trash2 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { ModelEditorModal } from '../../components/models/ModelEditorModal.jsx';
import {
  Badge,
  ConfirmDialog,
  DataTable,
  EmptyState,
  PageHeader,
  ToolbarSearch
} from '../../components/ui.jsx';

export function ModelsPage({ api, toast }) {
  const [models, setModels] = useState([]);
  const [providers, setProviders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState(null);
  const [confirm, setConfirm] = useState(null);

  const providerMap = useMemo(
    () => Object.fromEntries(providers.map((provider) => [provider.key, provider])),
    [providers]
  );
  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return models;
    return models.filter((model) => {
      const providerLabel = providerMap[model.provider]?.label || model.provider;
      return `${model.name} ${providerLabel} ${model.modelName} ${model.baseUrl}`.toLowerCase().includes(keyword);
    });
  }, [models, providerMap, query]);

  const load = async () => {
    setLoading(true);
    try {
      const [catalogResponse, modelsResponse] = await Promise.all([
        api.get('/api/v1/models/providers'),
        api.get('/api/v1/models/list')
      ]);
      setProviders(catalogResponse.providers || []);
      setModels(modelsResponse.models || []);
    } catch (error) {
      toast(error.message || '模型配置加载失败', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const toggle = async (model) => {
    const response = await api.put(`/api/v1/models/toggle/${model.modelId}`, {});
    if (response.success === false) {
      toast(response.message || '状态更新失败', 'error');
      return;
    }
    toast('模型状态已更新', 'success');
    await load();
  };

  const remove = async (model) => {
    const response = await api.delete(`/api/v1/models/delete/${model.modelId}`);
    if (response.success === false) {
      toast(response.message || '删除失败', 'error');
      return;
    }
    setConfirm(null);
    toast('模型配置已删除', 'success');
    await load();
  };

  const columns = [
    {
      key: 'name',
      title: '配置',
      render: (model) => (
        <div className="model-name-cell">
          <strong>{model.name}</strong>
          <span className="mono">{model.modelId}</span>
        </div>
      )
    },
    {
      key: 'provider',
      title: '厂商',
      render: (model) => providerMap[model.provider]?.label || model.provider || '-'
    },
    {
      key: 'modelName',
      title: '模型 ID',
      render: (model) => <span className="mono model-id-cell">{model.modelName || '-'}</span>
    },
    {
      key: 'status',
      title: '状态',
      render: (model) => (
        <div className="model-status-cell">
          <Badge tone={model.enabled ? 'green' : 'gray'}>{model.enabled ? '启用' : '停用'}</Badge>
          {model.isDefault && <Badge tone="amber"><Star size={12} />默认</Badge>}
        </div>
      )
    },
    {
      key: 'actions',
      title: '操作',
      render: (model) => (
        <div className="model-row-actions">
          <button className="icon-button bordered" title={model.enabled ? '停用' : '启用'} onClick={() => toggle(model)}><Power size={16} /></button>
          <button className="icon-button bordered" title="编辑" onClick={() => setEditing(model)}><Edit3 size={16} /></button>
          <button className="icon-button bordered model-delete-button" title="删除" onClick={() => setConfirm(model)}><Trash2 size={16} /></button>
        </div>
      )
    }
  ];

  return (
    <>
      <PageHeader
        title="模型管理"
        desc="统一管理 GPT、国产模型和 OpenAI-compatible 端点"
        actions={<>
          <ToolbarSearch value={query} onChange={setQuery} placeholder="搜索名称、厂商或模型" />
          <button className="icon-button bordered model-header-icon" title="刷新" onClick={load}><RefreshCw size={17} /></button>
          <button className="btn primary" onClick={() => setEditing({})} disabled={!providers.length}>
            <Plus size={16} />新增模型
          </button>
        </>}
      />

      <div className="model-summary" aria-label="模型配置概览">
        <div><span>配置总数</span><strong>{models.length}</strong></div>
        <div><span>已启用</span><strong>{models.filter((model) => model.enabled).length}</strong></div>
        <div><span>厂商目录</span><strong>{providers.length}</strong></div>
        <div><span>筛选结果</span><strong>{filtered.length}</strong></div>
      </div>

      <DataTable
        columns={columns}
        rows={filtered}
        rowKey="modelId"
        loading={loading}
        empty={<EmptyState
          title={query ? '没有匹配的模型' : '暂无模型配置'}
          desc={query ? '调整搜索条件后重试。' : '新增模型后，这里会显示运行时配置。'}
          actions={!query && <button className="btn primary" onClick={() => setEditing({})}><Plus size={16} />新增模型</button>}
        />}
      />

      {editing !== null && (
        <ModelEditorModal
          api={api}
          providers={providers}
          initialModel={editing}
          onClose={() => setEditing(null)}
          onSaved={async (message) => {
            setEditing(null);
            toast(message, 'success');
            await load();
          }}
        />
      )}

      {confirm && (
        <ConfirmDialog
          title="删除模型"
          message={`确认删除 ${confirm.name}？`}
          confirmText="删除"
          danger
          onCancel={() => setConfirm(null)}
          onConfirm={() => remove(confirm)}
        />
      )}
    </>
  );
}
