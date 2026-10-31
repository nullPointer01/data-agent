import { useDebouncedValue, useServerList } from '../hooks/useServerList.js';
import { Eye, Plus, RefreshCw, Save, Search, Trash2, Upload } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Badge, ConfirmDialog, DataTable, EmptyState, ListLoadError, Field, Metric, Modal, PageHeader, ToolbarSearch } from '../components/ui.jsx';
import { formatBytes, formatTime, truncate } from '../utils/format.js';

function renderCitationLocation(item) {
  const range = Number(item?.charEnd || 0) > Number(item?.charStart || 0) ? `字符 ${item.charStart}-${item.charEnd}` : '';
  return [item?.sectionPath, range].filter(Boolean).join(' · ');
}

function structureTags(item) {
  return [
    item?.containsTable ? '表格' : '',
    item?.containsCode ? '代码' : '',
    item?.containsList ? '列表' : ''
  ].filter(Boolean);
}

/** 个人知识页：管理文本/文档知识、同步配置和实时检索验证。 */
export function KnowledgePage({ api, toast }) {
  const [stats, setStats] = useState({});
  const [query, setQuery] = useState('');
  const [textModal, setTextModal] = useState(false);
  const [searchModal, setSearchModal] = useState(false);
  const [detail, setDetail] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const [form, setForm] = useState({ name: '', description: '', content: '' });
  const [probe, setProbe] = useState({ query: '', topK: 5, results: [] });

  const keyword = useDebouncedValue(query);
  const { items, response, loading, error, refresh, pagination } = useServerList({
    api, url: '/api/v1/knowledge/list', mode: 'offset', filters: { query: keyword }
  });
  const loadStats = async () => {
    try { setStats(await api.get('/api/v1/knowledge/stats')); }
    catch (failure) { toast(failure.message || '知识统计加载失败', 'error'); }
  };
  const load = () => Promise.all([refresh(), loadStats()]);
  useEffect(() => { loadStats(); }, [api]);

  // 仅当前页存在进行中的索引时刷新本页；不再全量读取用户所有索引任务。
  const hasPendingIndex = items.some((item) => ['PENDING', 'RUNNING', 'RETRY'].includes(item.indexing?.state));
  useEffect(() => {
    if (!hasPendingIndex) return undefined;
    let active = true;
    let timer;
    const poll = async () => {
      await Promise.all([refresh(), loadStats()]);
      if (active) timer = window.setTimeout(poll, 3000);
    };
    timer = window.setTimeout(poll, 3000);
    return () => { active = false; window.clearTimeout(timer); };
  }, [hasPendingIndex, refresh]);

  const retryIndex = async (item) => {
    const response = await api.post(`/api/v1/knowledge/${item.knowledgeId}/index-retry`, {});
    toast(response.message, response.success === false ? 'error' : 'success');
    load();
  };


  const upload = async (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    const formData = new FormData();
    formData.append('file', file);
    const res = await api.upload('/api/v1/knowledge/upload', formData);
    event.target.value = '';
    toast(res.message || '知识文件已提交处理', res.success === false ? 'error' : 'success');
    load();
  };

  const saveText = async () => {
    if (detail?.knowledgeId) {
      await api.put(`/api/v1/knowledge/update/${detail.knowledgeId}`, form);
      toast('知识已更新', 'success');
    } else {
      await api.post('/api/v1/knowledge/add-text', form);
      toast('文本知识已保存', 'success');
    }
    setTextModal(false);
    setDetail(null);
    setForm({ name: '', description: '', content: '' });
    load();
  };

  const openDetail = async (item) => {
    const res = await api.get(`/api/v1/knowledge/${item.knowledgeId}`);
    setDetail(res.knowledge || item);
  };

  const editDetail = async (item) => {
    try {
      const response = await api.get(`/api/v1/knowledge/${item.knowledgeId}`);
      if (response.success === false || !response.knowledge) throw new Error(response.message || '知识详情加载失败');
      const knowledge = response.knowledge;
      setDetail(knowledge);
      setForm({ name: knowledge.name || '', description: knowledge.description || '', content: knowledge.content || '' });
      setTextModal(true);
    } catch (failure) { toast(failure.message, 'error'); }
  };

  const runSearch = async () => {
    const res = await api.post('/api/v1/knowledge/search', { query: probe.query, topK: Number(probe.topK || 5) });
    setProbe({ ...probe, results: res.results || res.matches || [] });
  };

  return (
    <>
      <PageHeader
        title="知识库"
        desc="上传文档或录入文本，构建可检索的企业知识库"
        actions={<><ToolbarSearch value={query} onChange={setQuery} placeholder="搜索知识名称、描述、来源" /><button className="btn" onClick={() => setSearchModal(true)}><Search size={16} />检索测试</button><label className="btn"><Upload size={16} />上传<input type="file" hidden onChange={upload} /></label><button className="btn primary" onClick={() => { setDetail(null); setForm({ name: '', description: '', content: '' }); setTextModal(true); }}><Plus size={16} />添加文本</button></>}
      />
      <div className="grid grid-4">
        <Metric label="知识文档" value={stats.knowledgeCount ?? 0} />
        <Metric label="预计知识片段" value={stats.vectorStoreCount || 0} />
        <Metric label="存储后端" value={stats.usingMilvus ? 'Milvus' : '内存'} />
        <Metric label="匹配文档" value={response?.total || 0} />
      </div>
      <ListLoadError error={error} onRetry={refresh} />
      <DataTable serverPagination={pagination} columns={[
        { key: 'name', title: '名称', render: (item) => <><strong>{item.name}</strong><div className="muted">{truncate(item.description, 90)}</div></> },
        { key: 'sourceType', title: '来源', render: (item) => <Badge tone="gray">{item.sourceType || 'TEXT'}</Badge> },
        { key: 'chunkCount', title: '片段' },
        { key: 'indexing', title: '索引状态', render: (item) => <Badge tone={item.indexing?.state === 'SUCCEEDED' ? 'green' : item.indexing?.state === 'FAILED' ? 'red' : 'amber'}>{({ PENDING: '等待索引', RUNNING: '索引中', RETRY: '等待重试', FAILED: '索引失败', SUCCEEDED: '已就绪' })[item.indexing?.state] || '待验证'}</Badge> },
        { key: 'contentLength', title: '大小', render: (item) => formatBytes(item.contentLength) },
        { key: 'createdAt', title: '创建时间', render: (item) => formatTime(item.createdAt) },
        { key: 'actions', title: '操作', render: (item) => <div className="toolbar">{['FAILED', 'UNKNOWN'].includes(item.indexing?.state) && <button className="btn" onClick={() => retryIndex(item)}><RefreshCw size={16} />{item.indexing?.state === 'UNKNOWN' ? '建立索引' : '重试索引'}</button>}<button className="btn" onClick={() => openDetail(item)}><Eye size={16} />详情</button><button className="btn" onClick={() => editDetail(item)}>编辑</button><button className="btn danger" onClick={() => setConfirm(item)}><Trash2 size={16} /></button></div> }
      ]} rows={items} rowKey="knowledgeId" loading={loading} empty={<EmptyState title="暂无知识" desc="上传文件或添加文本后，这里会显示知识条目。" />} />
      {textModal && <Modal title={detail?.knowledgeId ? '编辑知识' : '添加文本知识'} onClose={() => setTextModal(false)} size="lg" actions={<button className="btn primary" onClick={saveText}><Save size={16} />保存</button>}>
        <div className="form-stack">
          <Field label="名称"><input className="input" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></Field>
          <Field label="描述"><input className="input" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></Field>
          <Field label="知识内容"><textarea className="textarea mono" rows={12} value={form.content} onChange={(event) => setForm({ ...form, content: event.target.value })} /></Field>
        </div>
      </Modal>}
      {detail && !textModal && <Modal title="知识详情" onClose={() => setDetail(null)} size="lg">
        <div className="detail-grid">
          <span>名称</span><strong>{detail.name || '-'}</strong>
          <span>来源</span><span>{detail.sourceType || '-'}</span>
          <span>片段</span><span>{detail.chunkCount || 0}</span>
          <span>创建时间</span><span>{formatTime(detail.createdAt)}</span>
        </div>
        <pre className="code-panel">{detail.content || '暂无内容预览'}</pre>
      </Modal>}
      {searchModal && <Modal title="知识检索测试" onClose={() => setSearchModal(false)} size="lg" actions={<button className="btn primary" onClick={runSearch}><Search size={16} />检索</button>}>
        <div className="form-grid">
          <Field label="查询内容" span><input className="input" value={probe.query} onChange={(event) => setProbe({ ...probe, query: event.target.value })} /></Field>
          <Field label="Top K"><input className="input" type="number" value={probe.topK} onChange={(event) => setProbe({ ...probe, topK: Number(event.target.value) })} /></Field>
        </div>
        <div className="result-list">
          {(probe.results || []).map((item, index) => <div className="result-item" key={index}>
            <div className="toolbar">
              <Badge tone="blue">{item.referenceId || `R${index + 1}`}</Badge>
              <Badge tone={item.sourceType === 'knowledge' ? 'blue' : 'gray'}>{item.sourceType || '-'}</Badge>
              <span className="muted">{item.sourceId || item.chunkId || item.name || item.metadata?.name}</span>
              <span className="muted">score {Number(item.score || 0).toFixed(2)}</span>
            </div>
            {(renderCitationLocation(item) || structureTags(item).length > 0) && <div className="citation-meta">
              {renderCitationLocation(item) && <span>{renderCitationLocation(item)}</span>}
              {structureTags(item).map((tag) => <Badge tone="gray" key={tag}>{tag}</Badge>)}
            </div>}
            <p>{truncate(item.content || item.text || JSON.stringify(item), 420)}</p>
          </div>)}
        </div>
      </Modal>}
      {confirm && <ConfirmDialog title="删除知识" message={`确认删除「${confirm.name}」？`} danger confirmText="删除" onCancel={() => setConfirm(null)} onConfirm={async () => { await api.delete(`/api/v1/knowledge/delete/${confirm.knowledgeId}`); setConfirm(null); toast('已删除知识', 'success'); load(); }} />}
    </>
  );
}
