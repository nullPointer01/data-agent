import { AlertTriangle, Bot, Pencil, Plus, Sparkles, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  activeCapabilityPacks,
  availablePackBindings,
  normalizeCapabilityBindings,
  PERSONAL_CAPABILITY_PACKS
} from '../agent/capabilityPacks.js';
import { CapabilityPackPicker } from '../components/CapabilityPackPicker.jsx';
import { Badge, ConfirmDialog, EmptyState, Field, LoadingState, Modal, PageHeader, Pagination, usePagination } from '../components/ui.jsx';

const EXPERT_PRESETS = [
  { id: 'knowledge', label: '知识研究', packIds: ['knowledge'] },
  { id: 'data', label: '数据分析', packIds: ['files', 'data'] },
  { id: 'general', label: '通用助手', packIds: ['knowledge', 'files', 'skills'] }
];

function presetBindings(preset, capabilities) {
  return normalizeCapabilityBindings(PERSONAL_CAPABILITY_PACKS
    .filter((pack) => preset.packIds.includes(pack.id))
    .flatMap((pack) => availablePackBindings(pack, capabilities)));
}

export function ExpertsPage({ api, toast }) {
  const [experts, setExperts] = useState([]);
  const [parentBindings, setParentBindings] = useState([]);
  const [models, setModels] = useState([]);
  const [capabilities, setCapabilities] = useState([]);
  const [capabilityDirectoryReady, setCapabilityDirectoryReady] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [confirm, setConfirm] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    setCapabilityDirectoryReady(false);
    try {
      const [agentResponse, modelResponse, capabilityResponse] = await Promise.all([
        api.get('/api/v1/my/agents'),
        api.get('/api/v1/my/models').catch(() => ({ models: [] })),
        api.get('/api/v1/my/capabilities').catch(() => null)
      ]);
      const agents = agentResponse.agents || [];
      const defaultAgent = agents.find((item) => item.defaultAgent);
      setExperts(agents.filter((item) => !item.defaultAgent));
      setParentBindings(normalizeCapabilityBindings(defaultAgent?.capabilityBindings));
      setModels(modelResponse.models || []);
      setCapabilities((capabilityResponse?.capabilities || [])
        .filter((item) => item.type === 'TOOL' || item.type === 'SKILL'));
      setCapabilityDirectoryReady(capabilityResponse?.success === true);
      if (capabilityResponse?.success !== true) {
        setLoadError('能力目录暂不可用。当前可以查看专家，但不能修改配置。');
      }
    } catch (requestError) {
      setLoadError(requestError.message || '专家助手加载失败，请刷新后重试');
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => { load(); }, [load]);

  const modelNames = useMemo(() => new Map(models.map((item) => [
    item.modelId,
    item.displayName || item.modelName || item.name
  ])), [models]);
  const expertPagination = usePagination(experts, { initialPageSize: 10, keyField: 'agentId' });

  const openCreate = () => {
    if (!capabilityDirectoryReady) {
      toast('能力目录暂不可用，不能创建专家助手', 'error');
      return;
    }
    const preset = EXPERT_PRESETS[0];
    setEditing(null);
    setForm({
      name: '',
      description: '',
      systemPrompt: '',
      modelId: '',
      capabilityBindings: presetBindings(preset, capabilities)
    });
    setError('');
  };

  const openEdit = (expert) => {
    if (!capabilityDirectoryReady) {
      toast('能力目录暂不可用，不能修改专家助手', 'error');
      return;
    }
    setEditing(expert);
    setForm({
      name: expert.name || '',
      description: expert.description || '',
      systemPrompt: expert.systemPrompt || '',
      modelId: expert.modelId || '',
      capabilityBindings: normalizeCapabilityBindings(expert.capabilityBindings)
    });
    setError('');
  };

  const applyPreset = (preset) => {
    const retainedAgentBindings = normalizeCapabilityBindings(form.capabilityBindings)
      .filter((identity) => identity.startsWith('agent:'));
    setForm({
      ...form,
      capabilityBindings: normalizeCapabilityBindings([
        ...presetBindings(preset, capabilities),
        ...retainedAgentBindings
      ])
    });
  };

  const save = async () => {
    if (!capabilityDirectoryReady) {
      setError('能力目录暂不可用，已阻止保存以避免覆盖专家能力');
      return;
    }
    if (!form?.name?.trim()) {
      setError('请填写专家名称');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const payload = {
        name: form.name.trim(),
        description: form.description?.trim() || '',
        systemPrompt: form.systemPrompt?.trim() || '',
        modelId: form.modelId || '',
        executionMode: 'auto',
        capabilityBindings: normalizeCapabilityBindings(form.capabilityBindings),
        enabled: true
      };
      const response = editing
        ? await api.put(`/api/v1/my/agents/${encodeURIComponent(editing.agentId)}`, payload)
        : await api.post('/api/v1/my/agents', payload);
      if (response?.success === false) {
        throw new Error(response.message || '保存失败');
      }
      setForm(null);
      setEditing(null);
      toast(editing ? '专家助手已更新' : '专家助手已创建，请在“我的 Agent”设置中启用', 'success');
      await load();
    } catch (requestError) {
      setError(requestError.message || '保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    try {
      const response = await api.delete(`/api/v1/my/agents/${encodeURIComponent(confirm.agentId)}`);
      if (response?.success === false) {
        throw new Error(response.message || '删除失败');
      }
      toast('专家助手已删除', 'success');
      setConfirm(null);
      await load();
    } catch (requestError) {
      toast(requestError.message || '删除失败，请重试', 'error');
    }
  };

  return (
    <>
      <PageHeader
        title="专家助手"
        desc="为不同任务准备专门助手，创建后在“我的 Agent”设置中决定是否启用"
        actions={<button className="btn primary" disabled={!capabilityDirectoryReady} onClick={openCreate}><Plus size={16} />新建专家</button>}
      />

      {loadError && <div className="alert danger expert-load-error" role="alert"><AlertTriangle size={16} />{loadError}</div>}
      {loading && <LoadingState label="加载专家助手" />}
      {!loading && experts.length === 0 && (
        <EmptyState
          title="还没有专家助手"
          desc="创建一个专门处理知识研究、文件理解或数据分析的助手。"
          actions={<button className="btn primary" disabled={!capabilityDirectoryReady} onClick={openCreate}><Plus size={16} />新建专家</button>}
        />
      )}
      {!loading && experts.length > 0 && (
        <div className="paginated-list">
          <div className="expert-grid">
          {expertPagination.pageItems.map((expert) => {
            const packs = activeCapabilityPacks(expert.capabilityBindings, capabilities);
            const connected = parentBindings.includes(`agent:${expert.agentId}`);
            return (
              <article className="expert-card" key={expert.agentId}>
                <div className="expert-card-head">
                  <span className="expert-icon"><Bot size={19} /></span>
                  <div>
                    <strong>{expert.name}</strong>
                    <span>{modelNames.get(expert.modelId) || '跟随系统默认模型'}</span>
                  </div>
                  <Badge tone={!expert.enabled ? 'gray' : connected ? 'green' : 'amber'}>
                    {!expert.enabled ? '已停用' : connected ? '已接入' : '未接入'}
                  </Badge>
                </div>
                <p>{expert.description || '尚未填写简介'}</p>
                <div className="expert-specialties">
                  {packs.length > 0
                    ? packs.map((pack) => <span key={pack.id}>{pack.label}</span>)
                    : <span>暂未开启能力</span>}
                </div>
                <div className="expert-card-actions">
                  <button className="btn" onClick={() => openEdit(expert)}><Pencil size={15} />编辑</button>
                  <button className="icon-button bordered danger" title="删除专家" onClick={() => setConfirm(expert)}><Trash2 size={15} /></button>
                </div>
              </article>
            );
          })}
          </div>
          <Pagination
            page={expertPagination.page}
            pageSize={expertPagination.pageSize}
            total={expertPagination.total}
            onPageChange={expertPagination.setPage}
            onPageSizeChange={expertPagination.setPageSize}
          />
        </div>
      )}

      {form && (
        <Modal
          title={editing ? '编辑专家助手' : '新建专家助手'}
          size="lg"
          onClose={() => setForm(null)}
          actions={<><button className="btn" onClick={() => setForm(null)}>取消</button><button className="btn primary" disabled={saving} onClick={save}>{saving ? '保存中...' : '保存'}</button></>}
        >
          <div className="expert-preset-strip">
            <span><Sparkles size={15} />快速开始</span>
            {EXPERT_PRESETS.map((preset) => (
              <button className="btn" type="button" key={preset.id} onClick={() => applyPreset(preset)}>{preset.label}</button>
            ))}
          </div>
          <div className="form-grid expert-form">
            <Field label="名称">
              <input className="input" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="例如：知识研究助手" />
            </Field>
            <Field label="模型">
              <select className="select" value={form.modelId} onChange={(event) => setForm({ ...form, modelId: event.target.value })}>
                <option value="">跟随系统默认模型</option>
                {models.map((item) => <option key={item.modelId} value={item.modelId}>{item.displayName}</option>)}
              </select>
            </Field>
            <Field label="执行模式" span>
              <select className="select" value="auto" disabled aria-label="执行模式：自主决策">
                <option value="auto">自主决策</option>
              </select>
            </Field>
            <Field label="简介" span>
              <input className="input" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} placeholder="这个专家适合处理什么任务" />
            </Field>
            <Field label="角色与要求" span>
              <textarea className="textarea" rows={4} value={form.systemPrompt} onChange={(event) => setForm({ ...form, systemPrompt: event.target.value })} placeholder="定义专业角色、输出要求和边界" />
            </Field>
            <div className="field span-2 agent-settings-section">
              <span id="expert-capability-label">专长</span>
              <CapabilityPackPicker
                capabilities={capabilities}
                selectedBindings={form.capabilityBindings}
                onChange={(capabilityBindings) => setForm({ ...form, capabilityBindings })}
                labelledBy="expert-capability-label"
              />
            </div>
            {error && <div className="agent-settings-error span-2">{error}</div>}
          </div>
        </Modal>
      )}

      {confirm && (
        <ConfirmDialog
          title="删除专家助手"
          message={`确认删除“${confirm.name}”？主 Agent 将无法再委派任务给它。`}
          danger
          confirmText="删除"
          onCancel={() => setConfirm(null)}
          onConfirm={remove}
        />
      )}
    </>
  );
}
