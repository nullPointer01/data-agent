import { BookOpen, FileText, Plus, RefreshCw, Search, Trash2, Upload } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Badge, ConfirmDialog, DataTable, EmptyState, Field, Metric, Modal, PageHeader, ToolbarSearch } from '../components/ui.jsx';
import { formatBytes, formatTime, truncate } from '../utils/format.js';

function statusTone(status) {
  if (status === 'INDEXED' || status === 'COMPLETED') {
    return 'green';
  }
  if (status === 'FAILED') {
    return 'red';
  }
  if (status === 'QUEUED' || status === 'PROCESSING') {
    return 'amber';
  }
  return 'gray';
}

function typeLabel(type) {
  return type === 'KNOWLEDGE' ? '知识' : '文件';
}

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

function channelLabel(channel) {
  if (channel === 'vector') {
    return '向量';
  }
  if (channel === 'full_text') {
    return '全文';
  }
  return channel;
}

function scoreText(label, value) {
  return value == null ? '' : `${label} ${Number(value || 0).toFixed(2)}`;
}

function percentText(value) {
  return `${Math.round(Number(value || 0) * 100)}%`;
}

function scoreTone(value) {
  if (Number(value || 0) >= 0.8) {
    return 'green';
  }
  if (Number(value || 0) >= 0.6) {
    return 'amber';
  }
  return 'red';
}

function TraceMetric({ label, value, hint }) {
  return (
    <div className="trace-metric">
      <span>{label}</span>
      <strong>{value}</strong>
      {hint && <small>{hint}</small>}
    </div>
  );
}

function QualityMetric({ label, value, hint }) {
  return (
    <div className={`quality-metric ${scoreTone(value)}`}>
      <span>{label}</span>
      <strong>{percentText(value)}</strong>
      {hint && <small>{hint}</small>}
    </div>
  );
}

export function ResourceCenterPage({ api, toast }) {
  const [assets, setAssets] = useState([]);
  const [summary, setSummary] = useState({});
  const [ragSettings, setRagSettings] = useState({});
  const [ragHealth, setRagHealth] = useState(null);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [resourceType, setResourceType] = useState('');
  const [textModal, setTextModal] = useState(false);
  const [searchModal, setSearchModal] = useState(false);
  const [confirm, setConfirm] = useState(null);
  const [textForm, setTextForm] = useState({ name: '', description: '', content: '' });
  const [probe, setProbe] = useState({ query: '', topK: 5, results: [], rag: null, quality: null });

  const load = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (keyword.trim()) {
        params.set('keyword', keyword.trim());
      }
      if (resourceType) {
        params.set('resourceType', resourceType);
      }
      const [res, rag] = await Promise.all([
        api.get(`/api/v1/resources/assets?${params.toString()}`),
        api.get('/api/v1/rag/settings').catch(() => ({}))
      ]);
      const health = await api.get('/api/v1/rag/health').catch(() => null);
      setAssets(res.assets || []);
      setSummary(res.summary || {});
      setRagSettings(rag || {});
      setRagHealth(health);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const filtered = useMemo(() => assets, [assets]);
  const relevanceMetric = (probe.quality?.metrics || []).find((metric) => metric.key === 'RELEVANCE');
  const relevanceHint = probe.quality?.expectedKeywordCount > 0
    ? `${probe.quality.matchedKeywordCount || 0}/${probe.quality.expectedKeywordCount} 标注关键词`
    : relevanceMetric?.detail || '暂无相关性证据';

  const uploadFile = async (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    const formData = new FormData();
    formData.append('file', file);
    const res = await api.upload('/api/v1/files/upload', formData);
    event.target.value = '';
    toast(res.message || '文件已进入处理队列', res.success === false ? 'error' : 'success');
    load();
  };

  const uploadKnowledge = async (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    const formData = new FormData();
    formData.append('file', file);
    const res = await api.upload('/api/v1/knowledge/upload', formData);
    event.target.value = '';
    toast(res.message || '知识文件已完成索引', res.success === false ? 'error' : 'success');
    load();
  };

  const addTextKnowledge = async () => {
    const res = await api.post('/api/v1/knowledge/add-text', textForm);
    toast(res.message || '文本知识已保存', res.success === false ? 'error' : 'success');
    setTextModal(false);
    setTextForm({ name: '', description: '', content: '' });
    load();
  };

  const deleteAsset = async () => {
    if (confirm.resourceType === 'KNOWLEDGE') {
      await api.delete(`/api/v1/knowledge/delete/${confirm.id}`);
      toast('知识已删除', 'success');
    } else {
      await api.delete(`/api/v1/files/delete/${confirm.id}`);
      toast('文件已删除', 'success');
    }
    setConfirm(null);
    load();
  };

  const runSearch = async () => {
    const [res, rag] = await Promise.all([
      api.post('/api/v1/knowledge/search', { query: probe.query, topK: Number(probe.topK || 5) }),
      api.post('/api/v1/rag/retrieve', { query: probe.query }).catch(() => null)
    ]);
    const quality = rag ? await api.post('/api/v1/rag/evaluate', { query: probe.query, response: rag }).catch(() => null) : null;
    setProbe({ ...probe, results: Array.isArray(res.results) ? res.results : [], rag, quality });
  };

  return (
    <>
      <PageHeader
        title="资料中心"
        desc="统一管理原始文件、知识条目和向量索引状态"
        actions={<><ToolbarSearch value={keyword} onChange={setKeyword} placeholder="搜索资料名称、来源、描述" /><select className="select compact-select" value={resourceType} onChange={(event) => setResourceType(event.target.value)}><option value="">全部类型</option><option value="FILE">文件</option><option value="KNOWLEDGE">知识</option></select><button className="btn" onClick={load}><RefreshCw size={16} />刷新</button></>}
      />
      <div className="grid grid-4">
        <Metric label="资料总数" value={summary.totalAssets || 0} />
        <Metric label="原始文件" value={summary.fileCount || 0} />
        <Metric label="知识条目" value={summary.knowledgeCount || 0} />
        <Metric label="向量片段" value={summary.indexedChunkCount || 0} hint={summary.usingMilvus ? 'Milvus' : 'Milvus 未就绪'} />
      </div>
      <div className="rag-panel">
        <div>
          <strong>Agent RAG</strong>
          <p>Agent 执行前会从向量库召回知识/文件上下文，并注入推理提示词。</p>
        </div>
        <div className="rag-settings">
          <Badge tone={ragSettings.enabled === false ? 'gray' : ragHealth?.healthy === false ? 'amber' : 'green'}>{ragSettings.enabled === false ? '未启用' : ragHealth?.healthy === false ? '待验收' : '已启用'}</Badge>
          <span>TopK {ragSettings.topK ?? '-'}</span>
          <span>候选 {ragSettings.candidateTopK ?? '-'}</span>
          <span>阈值 {ragSettings.minScore ?? '-'}</span>
          <span>上下文 {ragSettings.maxContextChars ?? '-'} 字符</span>
          <span>全文 {ragSettings.fullTextProvider || '-'}</span>
          <span>向量 {ragSettings.vectorProvider || '-'}</span>
        </div>
      </div>
      {ragHealth && <div className="rag-health-panel">
        <div className="rag-health-head">
          <div>
            <strong>RAG 验收状态</strong>
            <p>{ragHealth.healthy ? '当前 RAG 核心链路健康。' : '仍有验收项需要真实检索样本或外部服务确认。'}</p>
          </div>
          <div className="toolbar">
            <Badge tone={ragHealth.vectorHealthy ? 'green' : 'red'}>Milvus {ragHealth.vectorHealthy ? '正常' : '异常'}</Badge>
            <Badge tone={ragHealth.fullTextHealthy ? 'green' : 'red'}>全文 {ragHealth.fullTextHealthy ? '正常' : '异常'}</Badge>
          </div>
        </div>
        <div className="rag-check-grid">
          {(ragHealth.acceptanceChecks || []).map((check) => (
            <div className={`rag-check ${check.passed ? 'passed' : 'pending'}`} key={check.key}>
              <Badge tone={check.passed ? 'green' : 'amber'}>{check.passed ? '通过' : '待验收'}</Badge>
              <strong>{check.name}</strong>
              <span>{check.detail}</span>
            </div>
          ))}
        </div>
      </div>}
      <div className="resource-actions">
        <label className="action-tile">
          <FileText size={22} />
          <strong>上传原始文件</strong>
          <span>进入异步解析队列，可用于审计和重建索引。</span>
          <input type="file" hidden onChange={uploadFile} />
        </label>
        <label className="action-tile">
          <Upload size={22} />
          <strong>上传并索引知识</strong>
          <span>解析后直接写入知识库和向量索引。</span>
          <input type="file" hidden onChange={uploadKnowledge} />
        </label>
        <button className="action-tile" onClick={() => setTextModal(true)}>
          <Plus size={22} />
          <strong>录入文本知识</strong>
          <span>适合制度、FAQ、业务说明等文本内容。</span>
        </button>
        <button className="action-tile" onClick={() => setSearchModal(true)}>
          <Search size={22} />
          <strong>检索测试</strong>
          <span>验证向量索引是否能召回相关资料。</span>
        </button>
      </div>
      <DataTable loading={loading} empty={<EmptyState title="暂无资料" desc="上传文件或添加文本知识后，这里会显示统一资料列表。" />} columns={[
        { key: 'name', title: '名称', render: (item) => <><strong>{item.name}</strong><div className="muted">{truncate(item.description || item.sourceFilename || item.contentType, 90)}</div></> },
        { key: 'resourceType', title: '类型', render: (item) => <Badge tone={item.resourceType === 'KNOWLEDGE' ? 'blue' : 'gray'}>{typeLabel(item.resourceType)}</Badge> },
        { key: 'status', title: '状态', render: (item) => <Badge tone={statusTone(item.status)}>{item.status || '-'}</Badge> },
        { key: 'size', title: '容量', render: (item) => formatBytes(item.size || item.contentLength) },
        { key: 'chunkCount', title: '片段', render: (item) => item.chunkCount || '-' },
        { key: 'createdAt', title: '创建时间', render: (item) => formatTime(item.createdAt) },
        { key: 'actions', title: '操作', render: (item) => <button className="btn danger" onClick={() => setConfirm(item)}><Trash2 size={16} />删除</button> }
      ]} rows={filtered} rowKey="id" />
      {textModal && <Modal title="录入文本知识" onClose={() => setTextModal(false)} size="lg" actions={<button className="btn primary" onClick={addTextKnowledge}><BookOpen size={16} />保存并索引</button>}>
        <div className="form-stack">
          <Field label="名称"><input className="input" value={textForm.name} onChange={(event) => setTextForm({ ...textForm, name: event.target.value })} /></Field>
          <Field label="描述"><input className="input" value={textForm.description} onChange={(event) => setTextForm({ ...textForm, description: event.target.value })} /></Field>
          <Field label="内容"><textarea className="textarea mono" rows={12} value={textForm.content} onChange={(event) => setTextForm({ ...textForm, content: event.target.value })} /></Field>
        </div>
      </Modal>}
      {searchModal && <Modal title="知识检索测试" onClose={() => setSearchModal(false)} size="lg" actions={<button className="btn primary" onClick={runSearch}><Search size={16} />检索</button>}>
        <div className="form-grid">
          <Field label="查询内容" span><input className="input" value={probe.query} onChange={(event) => setProbe({ ...probe, query: event.target.value })} /></Field>
          <Field label="Top K"><input className="input" type="number" value={probe.topK} onChange={(event) => setProbe({ ...probe, topK: Number(event.target.value) })} /></Field>
        </div>
        {probe.rag && <div className="rag-probe-panel">
          <div className="toolbar">
            <Badge tone="blue">{probe.rag.queryType || 'FACT'}</Badge>
            <span className="muted">改写：{probe.rag.rewrittenQuery || '-'}</span>
            <span className="muted">命中 {probe.rag.hitCount || 0}</span>
          </div>
          {probe.rag.trace && <div className="trace-metrics-grid">
            <TraceMetric label="候选" value={probe.rag.trace.candidateCount || 0} hint={`最终 ${probe.rag.trace.finalCount || 0}`} />
            <TraceMetric label="向量命中" value={probe.rag.trace.vectorCandidateCount || 0} hint={probe.rag.trace.vectorProvider || 'milvus'} />
            <TraceMetric label="全文命中" value={probe.rag.trace.fullTextCandidateCount || 0} hint={probe.rag.trace.fullTextProvider || '-'} />
            <TraceMetric label="混合命中" value={probe.rag.trace.hybridCandidateCount || 0} hint={probe.rag.trace.compressed ? '已压缩' : '未压缩'} />
            <TraceMetric label="召回耗时" value={`${probe.rag.trace.retrievalTimeMs || 0} ms`} hint={`重排 ${probe.rag.trace.rerankTimeMs || 0} ms`} />
            <TraceMetric label="总耗时" value={`${probe.rag.trace.totalTimeMs || 0} ms`} hint={`${probe.rag.trace.contextChars || 0} 字符`} />
          </div>}
          <div className="quality-samples">
            {(probe.rag.keywords || []).map((keyword) => <span key={keyword}>{keyword}</span>)}
          </div>
          {probe.quality && <div className="rag-quality-panel">
            <div className="rag-quality-head">
              <div>
                <strong>质量评估</strong>
                <p>基于标注关键词或向量相关性、引用完整性、混合召回和链路延迟评估本次检索。</p>
              </div>
              <Badge tone={probe.quality.passed ? 'green' : 'amber'}>
                {probe.quality.passed ? '通过' : '待优化'} {percentText(probe.quality.overallScore)}
              </Badge>
            </div>
            <div className="quality-metrics-grid">
              <QualityMetric label="相关性" value={probe.quality.relevanceScore} hint={relevanceHint} />
              <QualityMetric label="引用准确率" value={probe.quality.citationAccuracy} hint={`${probe.quality.validCitationCount || 0}/${probe.quality.citationCount || 0} 引用`} />
              <QualityMetric label="混合召回" value={probe.quality.hybridCoverage} hint="向量 + 全文" />
              <QualityMetric label="延迟健康度" value={probe.quality.latencyScore} hint={`${probe.rag.trace?.totalTimeMs || 0} ms`} />
            </div>
            {probe.quality.metrics?.length > 0 && <div className="rag-quality-checks">
              {probe.quality.metrics.map((metric) => (
                <div className="rag-quality-check" key={metric.key}>
                  <Badge tone={metric.passed ? 'green' : 'amber'}>{metric.passed ? '通过' : '待优化'}</Badge>
                  <strong>{metric.name}</strong>
                  <span>{metric.detail}</span>
                </div>
              ))}
            </div>}
            {probe.quality.issues?.length > 0 && <div className="rag-quality-issues">
              {probe.quality.issues.map((issue) => <span key={issue}>{issue}</span>)}
            </div>}
            {probe.quality.recommendations?.length > 0 && <div className="rag-quality-recommendations">
              {probe.quality.recommendations.map((item) => <span key={item}>{item}</span>)}
            </div>}
          </div>}
          <div className="citation-list">
            {(probe.rag.citations || []).map((citation) => (
              <div className="citation-item" key={citation.referenceId}>
                <strong>[{citation.referenceId}] {citation.sourceType} / {citation.sourceId}</strong>
                <small>{[citation.chunkId, `RRF ${Number(citation.score || 0).toFixed(2)}`, scoreText('向量', citation.vectorScore), scoreText('全文', citation.fullTextScore)].filter(Boolean).join(' · ')}</small>
                {citation.channels?.length > 0 && <div className="citation-meta">
                  {citation.channels.map((channel) => <Badge tone={channel === 'vector' ? 'green' : 'blue'} key={channel}>{channelLabel(channel)}</Badge>)}
                  {citation.parentContextUsed && <Badge tone="gray">父级上下文</Badge>}
                  {structureTags(citation).map((tag) => <Badge tone="gray" key={tag}>{tag}</Badge>)}
                </div>}
                {renderCitationLocation(citation) && <small>{renderCitationLocation(citation)}</small>}
                <p>{truncate(citation.snippet, 260)}</p>
              </div>
            ))}
          </div>
        </div>}
        <div className="result-list">
          {probe.results.length ? probe.results.map((item, index) => (
            <div className="result-item" key={`${item.sourceId}-${item.chunkId}-${index}`}>
              <div className="toolbar">
                <Badge tone="blue">{item.referenceId || `R${index + 1}`}</Badge>
                <Badge tone={item.sourceType === 'knowledge' ? 'blue' : 'gray'}>{item.sourceType || '-'}</Badge>
                <span className="muted">{item.sourceId || item.chunkId}</span>
                <span className="muted">{[`RRF ${Number(item.score || 0).toFixed(2)}`, scoreText('向量', item.vectorScore), scoreText('全文', item.fullTextScore)].filter(Boolean).join(' · ')}</span>
              </div>
              {(renderCitationLocation(item) || structureTags(item).length > 0 || item.channels?.length > 0) && <div className="citation-meta">
                {renderCitationLocation(item) && <span>{renderCitationLocation(item)}</span>}
                {(item.channels || []).map((channel) => <Badge tone={channel === 'vector' ? 'green' : 'blue'} key={channel}>{channelLabel(channel)}</Badge>)}
                {structureTags(item).map((tag) => <Badge tone="gray" key={tag}>{tag}</Badge>)}
              </div>}
              <p>{truncate(item.content, 420)}</p>
            </div>
          )) : <EmptyState title="暂无检索结果" desc="输入查询内容后执行检索，这里会展示向量召回的资料片段。" />}
        </div>
      </Modal>}
      {confirm && <ConfirmDialog title="删除资料" message={`确认删除「${confirm.name}」？`} danger confirmText="删除" onCancel={() => setConfirm(null)} onConfirm={deleteAsset} />}
    </>
  );
}
