import {
  AlertCircle,
  Bot,
  Cable,
  CheckCircle2,
  ChevronDown,
  Eye,
  EyeOff,
  KeyRound,
  Link2,
  Loader2,
  RefreshCw,
  Save,
  SlidersHorizontal,
  TestTube2
} from 'lucide-react';
import { useMemo, useState } from 'react';
import { Field, Modal } from '../ui.jsx';
import { ModelProviderPicker } from './ModelProviderPicker.jsx';

const CONNECTION_FIELDS = new Set(['provider', 'apiKey', 'baseUrl', 'modelName', 'temperature']);

export function ModelEditorModal({ api, providers, initialModel, onClose, onSaved }) {
  const [form, setForm] = useState(() => normalizeForm(initialModel, providers));
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);
  const [availableModels, setAvailableModels] = useState([]);
  const [discovery, setDiscovery] = useState({ loading: false, message: '' });
  const [probe, setProbe] = useState({ status: 'idle', message: '', latencyMs: null, errorCode: null });
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const provider = useMemo(
    () => providers.find((candidate) => candidate.key === form.provider),
    [providers, form.provider]
  );
  const modelCandidates = useMemo(
    () => Array.from(new Set([...(availableModels || []), ...(provider?.recommendedModels || [])])),
    [availableModels, provider]
  );
  const isCustom = provider?.key === 'custom';
  const canConnect = Boolean(
    provider && form.baseUrl?.trim() && form.modelName?.trim()
      && (!provider.apiKeyRequired || form.apiKey?.trim())
  );
  const canSave = Boolean(form.name?.trim() && canConnect);

  const updateField = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
    setSaveError('');
    if (CONNECTION_FIELDS.has(field)) {
      setProbe({ status: 'idle', message: '', latencyMs: null, errorCode: null });
    }
  };

  const selectProvider = (nextProvider) => {
    const previous = providers.find((candidate) => candidate.key === form.provider);
    const baseUrlWasAutomatic = !form.baseUrl || form.baseUrl === previous?.defaultBaseUrl;
    const modelWasAutomatic = !form.modelName || form.modelName === previous?.defaultModelName;
    setForm((current) => ({
      ...current,
      provider: nextProvider.key,
      apiKey: nextProvider.key === previous?.key ? current.apiKey : '',
      baseUrl: baseUrlWasAutomatic ? (nextProvider.defaultBaseUrl || '') : current.baseUrl,
      modelName: modelWasAutomatic ? (nextProvider.defaultModelName || '') : current.modelName
    }));
    setAvailableModels([]);
    setDiscovery({ loading: false, message: '' });
    setProbe({ status: 'idle', message: '', latencyMs: null, errorCode: null });
    setSaveError('');
  };

  const discoverModels = async () => {
    setDiscovery({ loading: true, message: '' });
    try {
      const response = await api.post('/api/v1/models/available', connectionPayload(form));
      const models = response.models || [];
      setAvailableModels(models);
      setDiscovery({
        loading: false,
        message: response.success
          ? (models.length ? `已获取 ${models.length} 个模型` : '未发现模型，可直接填写模型 ID')
          : (response.message || '自动发现不可用，可直接填写模型 ID')
      });
    } catch (error) {
      setAvailableModels([]);
      setDiscovery({ loading: false, message: error.message || '自动发现不可用，可直接填写模型 ID' });
    }
  };

  const testConnection = async () => {
    setProbe({ status: 'loading', message: '正在连接', latencyMs: null, errorCode: null });
    try {
      const response = await api.post('/api/v1/models/probe', connectionPayload(form));
      setProbe({
        status: response.success ? 'success' : 'error',
        message: response.message || (response.success ? '连接成功' : '连接失败'),
        latencyMs: response.latencyMs,
        errorCode: response.errorCode
      });
    } catch (error) {
      setProbe({ status: 'error', message: error.message || '连接失败', latencyMs: null, errorCode: 'UNKNOWN' });
    }
  };

  const save = async () => {
    setSaving(true);
    setSaveError('');
    try {
      const payload = sanitizePayload(form);
      const response = form.modelId
        ? await api.put(`/api/v1/models/update/${form.modelId}`, payload)
        : await api.post('/api/v1/models/add', payload);
      if (response.success === false) {
        throw new Error(response.message || '保存失败');
      }
      await onSaved(form.modelId ? '模型配置已更新' : '模型配置已创建');
    } catch (error) {
      setSaving(false);
      setSaveError(error.message || '保存失败');
    }
  };

  const ProviderIcon = isCustom ? Cable : Bot;

  return (
    <Modal
      title={form.modelId ? '编辑模型接入' : '新增模型接入'}
      onClose={onClose}
      size="xl"
      actions={<>
        <button className="btn" type="button" onClick={onClose} disabled={saving}>取消</button>
        <button
          className="btn primary"
          type="button"
          onClick={save}
          disabled={saving || probe.status === 'loading' || !canSave}
        >
          {saving ? <Loader2 className="spin" size={16} /> : <Save size={16} />}
          {saving ? '保存中' : '保存配置'}
        </button>
      </>}
    >
      <div className="model-editor-shell">
        <ModelProviderPicker providers={providers} value={form.provider} onChange={selectProvider} disabled={saving} />

        <div className="model-config-panel">
          <header className="model-provider-summary">
            <span className="model-provider-mark"><ProviderIcon size={20} /></span>
            <div>
              <h3>{provider?.label || '选择模型供应商'}</h3>
              <p>{isCustom ? 'OpenAI 官方、GPT 代理或兼容网关' : provider?.defaultBaseUrl}</p>
            </div>
            <span className="model-protocol-badge">OpenAI-compatible</span>
          </header>

          <section className="model-config-section">
            <div className="model-config-section-title">
              <Link2 size={16} />
              <span>连接信息</span>
            </div>
            <div className="form-grid">
              <Field label="配置名称">
                <input
                  className="input"
                  value={form.name}
                  placeholder="例如：生产 GPT"
                  onChange={(event) => updateField('name', event.target.value)}
                />
              </Field>
              <div className="field">
                <label htmlFor="model-api-key">API Key</label>
                <div className="input-with-actions">
                  <KeyRound size={15} />
                  <input
                    id="model-api-key"
                    type={showApiKey ? 'text' : 'password'}
                    value={form.apiKey}
                    placeholder={isCustom ? '输入目标端点签发的 API Key' : '输入 API Key'}
                    onChange={(event) => updateField('apiKey', event.target.value)}
                    autoComplete="new-password"
                  />
                  <button
                    className="input-action"
                    type="button"
                    onClick={() => setShowApiKey((visible) => !visible)}
                    title={showApiKey ? '隐藏 API Key' : '显示 API Key'}
                    aria-label={showApiKey ? '隐藏 API Key' : '显示 API Key'}
                  >
                    {showApiKey ? <EyeOff size={15} /> : <Eye size={15} />}
                  </button>
                </div>
              </div>

              {isCustom && (
                <Field label="Base URL" span>
                  <div className="input-with-actions">
                    <Cable size={15} />
                    <input
                      className="mono"
                      value={form.baseUrl}
                      placeholder="https://api.openai.com/v1"
                      onChange={(event) => updateField('baseUrl', event.target.value)}
                    />
                  </div>
                  {form.baseUrl?.trim().toLowerCase().startsWith('http://') && (
                    <small className="model-field-warning">HTTP 连接不会加密 API Key 和 Prompt</small>
                  )}
                </Field>
              )}

              <Field label="模型 ID" span>
                <div className="model-discovery-row">
                  <input
                    className="input mono"
                    list="model-candidates"
                    value={form.modelName}
                    placeholder={isCustom ? '例如：gpt-5-mini' : '输入或选择模型 ID'}
                    onChange={(event) => updateField('modelName', event.target.value)}
                  />
                  <datalist id="model-candidates">
                    {modelCandidates.map((modelName) => <option key={modelName} value={modelName} />)}
                  </datalist>
                  <button
                    className="btn"
                    type="button"
                    onClick={discoverModels}
                    disabled={discovery.loading || !provider?.modelDiscoverySupported || !form.baseUrl?.trim()}
                    title={provider?.modelDiscoverySupported ? '获取模型列表' : '该厂商不支持自动获取'}
                  >
                    {discovery.loading ? <Loader2 className="spin" size={16} /> : <RefreshCw size={16} />}
                    获取模型
                  </button>
                </div>
                {discovery.message && <small className="model-field-message">{discovery.message}</small>}
                {!!provider?.recommendedModels?.length && (
                  <div className="model-recommendations">
                    <span>推荐模型</span>
                    {provider.recommendedModels.map((modelName) => (
                      <button type="button" key={modelName} onClick={() => updateField('modelName', modelName)}>
                        {modelName}
                      </button>
                    ))}
                  </div>
                )}
              </Field>
            </div>
          </section>

          <section className="model-config-section">
            <div className={`model-probe-state ${probe.status}`} aria-live="polite">
              <div className="model-probe-copy">
                {probe.status === 'loading' && <Loader2 className="spin" size={18} />}
                {probe.status === 'success' && <CheckCircle2 size={18} />}
                {probe.status === 'error' && <AlertCircle size={18} />}
                {probe.status === 'idle' && <TestTube2 size={18} />}
                <div>
                  <strong>{probe.status === 'idle' ? '连接尚未验证' : probe.message}</strong>
                  {probe.status === 'success' && <small>{probe.latencyMs} ms</small>}
                  {probe.status === 'error' && probe.errorCode && <small>{probe.errorCode}</small>}
                </div>
              </div>
              <button
                className="btn"
                type="button"
                onClick={testConnection}
                disabled={probe.status === 'loading' || saving || !canConnect}
              >
                <TestTube2 size={16} />测试连接
              </button>
            </div>
          </section>

          <section className="model-config-section model-config-section-last">
            <button className="model-advanced-toggle" type="button" onClick={() => setAdvancedOpen((open) => !open)}>
              <span><SlidersHorizontal size={16} /><strong>高级设置</strong><small>生成参数与运行状态</small></span>
              <ChevronDown className={advancedOpen ? 'open' : ''} size={18} />
            </button>
            {advancedOpen && (
              <div className="form-grid model-advanced-fields">
                {!isCustom && (
                  <Field label="Base URL" span>
                    <input className="input mono" value={form.baseUrl} onChange={(event) => updateField('baseUrl', event.target.value)} />
                  </Field>
                )}
                <Field label="Temperature">
                  <input className="input" type="number" min="0" max="2" step="0.1" value={form.temperature} onChange={(event) => updateField('temperature', numberOrEmpty(event.target.value))} />
                </Field>
                <Field label="Max Tokens">
                  <input className="input" type="number" min="1" max="1000000" value={form.maxTokens} onChange={(event) => updateField('maxTokens', numberOrEmpty(event.target.value))} />
                </Field>
                <label className="model-toggle">
                  <input type="checkbox" checked={form.enabled} onChange={(event) => updateField('enabled', event.target.checked)} />
                  <span className="model-switch-control" aria-hidden="true" />
                  <span>启用模型</span>
                </label>
                <label className="model-toggle">
                  <input type="checkbox" checked={form.isDefault} onChange={(event) => updateField('isDefault', event.target.checked)} />
                  <span className="model-switch-control" aria-hidden="true" />
                  <span>设为默认</span>
                </label>
              </div>
            )}
          </section>

          {saveError && <div className="model-save-error" role="alert"><AlertCircle size={16} />{saveError}</div>}
        </div>
      </div>
    </Modal>
  );
}

function normalizeForm(model, providers) {
  const firstProvider = providers[0] || {};
  const requestedProvider = providers.find((provider) => provider.key === model?.provider);
  const fallbackProvider = requestedProvider || providers.find((provider) => provider.key === 'custom') || firstProvider;
  return {
    modelId: model?.modelId || '',
    name: model?.name || '',
    provider: fallbackProvider.key || '',
    apiKey: model?.apiKey || '',
    baseUrl: model?.baseUrl || fallbackProvider.defaultBaseUrl || '',
    modelName: model?.modelName || fallbackProvider.defaultModelName || '',
    temperature: model?.temperature ?? 0.7,
    maxTokens: model?.maxTokens ?? 4096,
    enabled: model?.enabled ?? true,
    isDefault: model?.isDefault ?? false
  };
}

function connectionPayload(form) {
  return {
    modelId: form.modelId || null,
    provider: form.provider,
    apiKey: form.apiKey,
    baseUrl: form.baseUrl,
    modelName: form.modelName,
    temperature: form.temperature
  };
}

function sanitizePayload(form) {
  const payload = { ...form };
  delete payload.modelId;
  if (!payload.apiKey || payload.apiKey.includes('****')) delete payload.apiKey;
  return payload;
}

function numberOrEmpty(value) {
  return value === '' ? '' : Number(value);
}
