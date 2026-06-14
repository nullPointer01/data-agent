import { Plus, RefreshCw, Save, Trash2 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Badge, ConfirmDialog, DataTable, EmptyState, Field, Metric, Modal, PageHeader, ToolbarSearch } from '../ui.jsx';
import { truncate } from '../../utils/format.js';

export function ResourcePage({
  api,
  toast,
  title,
  desc,
  listUrl,
  listKey,
  idKey,
  fields,
  createUrl,
  updateUrl,
  toggleUrl,
  deleteUrl,
  extraRowAction,
  headerExtra,
  templates,
  visibleFields
}) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({});
  const [confirm, setConfirm] = useState(null);

  const load = async () => {
    setLoading(true);
    try {
      setItems((await api.get(listUrl))[listKey] || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) {
      return items;
    }
    return items.filter((item) => JSON.stringify(item).toLowerCase().includes(keyword));
  }, [items, query]);

  const save = async () => {
    const id = editing?.[idKey];
    const payload = sanitizeForm(form);
    if (id && updateUrl) {
      await api.put(updateUrl(id), { ...payload, [idKey]: id });
      toast(`${title}已更新`, 'success');
    } else {
      await api.post(createUrl, payload);
      toast(`${title}已创建`, 'success');
    }
    setEditing(null);
    setForm({});
    load();
  };

  const columns = [
    ...fields
      .filter(([key]) => (visibleFields || fields.slice(0, 4).map(([fieldKey]) => fieldKey)).includes(key))
      .map(([key, label]) => ({ key, title: label, render: (item) => renderCell(item, key) })),
    { key: 'enabled', title: '状态', render: (item) => <Badge tone={item.enabled !== false ? 'green' : 'gray'}>{item.enabled !== false ? '启用' : '禁用'}</Badge> },
    {
      key: 'actions',
      title: '操作',
      render: (item) => (
        <div className="toolbar">
          {extraRowAction?.(item)}
          {toggleUrl && <button className="btn" onClick={async () => { await api.put(toggleUrl(item[idKey]), {}); toast('状态已更新', 'success'); load(); }}>启停</button>}
          <button className="btn" onClick={() => { setEditing(item); setForm(item); }}>编辑</button>
          {deleteUrl && <button className="btn danger" onClick={() => setConfirm({ message: `确认删除 ${item.name || item[idKey]}？`, onConfirm: async () => { await api.delete(deleteUrl(item[idKey])); setConfirm(null); toast('已删除', 'success'); load(); } })}><Trash2 size={16} /></button>}
        </div>
      )
    }
  ];

  return (
    <>
      <PageHeader
        title={title}
        desc={desc}
        actions={<><ToolbarSearch value={query} onChange={setQuery} placeholder="搜索当前列表" />{headerExtra}<button className="btn" onClick={load}><RefreshCw size={16} />刷新</button><button className="btn primary" onClick={() => { setEditing({}); setForm({ enabled: true }); }}><Plus size={16} />新增</button></>}
      />
      <div className="grid grid-4">
        <Metric label="总数" value={items.length} />
        <Metric label="启用" value={items.filter((item) => item.enabled !== false).length} />
        <Metric label="停用" value={items.filter((item) => item.enabled === false).length} />
        <Metric label="筛选结果" value={filtered.length} />
      </div>
      <DataTable columns={columns} rows={filtered} rowKey={idKey} loading={loading} empty={<EmptyState title="暂无资源" desc="新增资源后，这里会显示配置和状态。" />} />
      {editing !== null && <ResourceModal title={editing[idKey] ? `编辑${title}` : `新增${title}`} fields={fields} form={form} setForm={setForm} templates={templates} onClose={() => setEditing(null)} onSave={save} />}
      {confirm && <ConfirmDialog title="删除确认" message={confirm.message} danger confirmText="删除" onCancel={() => setConfirm(null)} onConfirm={confirm.onConfirm} />}
    </>
  );
}

function ResourceModal({ title, fields, form, setForm, templates, onClose, onSave }) {
  return (
    <Modal title={title} onClose={onClose} size="lg" actions={<button className="btn primary" onClick={onSave}><Save size={16} />保存</button>}>
      {templates && <div className="template-strip">
        {templates.map((template) => <button key={template.label} className="btn" onClick={() => setForm({ ...form, ...template.value })}>{template.label}</button>)}
      </div>}
      <div className="form-grid">
        {fields.map(([key, label, type, options]) => (
          <Field key={key} label={label} span={type === 'textarea'}>
            {renderField({ key, type, options, form, setForm })}
          </Field>
        ))}
      </div>
    </Modal>
  );
}

function renderField({ key, type, options, form, setForm }) {
  if (type === 'textarea') {
    return <textarea className="textarea mono" rows={6} value={form[key] || ''} onChange={(event) => setForm({ ...form, [key]: event.target.value })} />;
  }
  if (type === 'select') {
    return (
      <select className="select" value={form[key] || ''} onChange={(event) => {
        const value = event.target.value;
        const selected = (options || []).find((option) => option.value === value);
        const next = { ...form, [key]: value };
        // 选项可携带 fill：仅填充表单中尚未填写的字段（如选厂商后自动带出默认 Base URL / 模型名）
        if (selected?.fill) {
          Object.entries(selected.fill).forEach(([fillKey, fillValue]) => {
            if (next[fillKey] == null || next[fillKey] === '') {
              next[fillKey] = fillValue;
            }
          });
        }
        setForm(next);
      }}>
        <option value="">请选择</option>
        {(options || []).map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
    );
  }
  if (type === 'suggest') {
    return <SuggestField fieldKey={key} form={form} setForm={setForm} fetchOptions={options} />;
  }
  if (type === 'checkbox') {
    return <label className="switch-line"><input type="checkbox" checked={Boolean(form[key])} onChange={(event) => setForm({ ...form, [key]: event.target.checked })} />启用</label>;
  }
  return <input className="input" type={type || 'text'} value={form[key] ?? ''} onChange={(event) => setForm({ ...form, [key]: type === 'number' ? Number(event.target.value) : event.target.value })} />;
}

function SuggestField({ fieldKey, form, setForm, fetchOptions }) {
  const [candidates, setCandidates] = useState([]);
  const [loading, setLoading] = useState(false);
  const [hint, setHint] = useState('');
  const listId = `suggest-${fieldKey}`;

  const fetchList = async () => {
    setLoading(true);
    setHint('');
    try {
      const result = await fetchOptions(form);
      setCandidates(result);
      setHint(result.length ? `已获取 ${result.length} 个模型，点击输入框选择` : '未获取到模型，请手动填写');
    } catch (error) {
      setCandidates([]);
      setHint(error.message || '获取失败，请手动填写');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="toolbar" style={{ display: 'flex', gap: 8 }}>
        <input
          className="input"
          style={{ flex: 1 }}
          list={listId}
          value={form[fieldKey] ?? ''}
          onChange={(event) => setForm({ ...form, [fieldKey]: event.target.value })}
        />
        <datalist id={listId}>
          {candidates.map((candidate) => <option key={candidate} value={candidate} />)}
        </datalist>
        <button className="btn" type="button" disabled={loading} onClick={fetchList}>
          {loading ? '获取中…' : '获取列表'}
        </button>
      </div>
      {hint && <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>{hint}</div>}
    </div>
  );
}

function renderCell(item, key) {
  if (key === 'apiKey' || key === 'password') {
    return item[key] ? '******' : '-';
  }
  if (key.endsWith('Id')) {
    return <span className="mono muted">{item[key] || '-'}</span>;
  }
  return truncate(item[key] ?? '-', 100);
}

function sanitizeForm(form) {
  return Object.fromEntries(Object.entries(form).filter(([, value]) => value !== '****' && value !== '******'));
}
