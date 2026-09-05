import { Eye, RefreshCw, Trash2, Upload } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Badge, ConfirmDialog, DataTable, EmptyState, Metric, Modal, PageHeader, ToolbarSearch } from '../components/ui.jsx';
import { formatBytes, formatTime } from '../utils/format.js';

function statusTone(status) {
  if (status === 'COMPLETED') {
    return 'green';
  }
  if (status === 'FAILED') {
    return 'red';
  }
  if (status === 'PROCESSING' || status === 'QUEUED') {
    return 'amber';
  }
  return 'gray';
}

export function FilesPage({ api, toast }) {
  const [files, setFiles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [detail, setDetail] = useState(null);
  const [confirm, setConfirm] = useState(null);

  const load = async () => {
    setLoading(true);
    try {
      setFiles((await api.get('/api/v1/files/list')).files || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) {
      return files;
    }
    return files.filter((file) => JSON.stringify(file).toLowerCase().includes(keyword));
  }, [files, query]);

  const upload = async (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    const formData = new FormData();
    formData.append('file', file);
    const res = await api.upload('/api/v1/files/upload', formData);
    event.target.value = '';
    toast(res.message || '文件已上传', res.success === false ? 'error' : 'success');
    load();
  };

  const openDetail = async (file) => {
    const res = await api.get(`/api/v1/files/${file.fileId}`);
    setDetail(res.success === false ? file : res);
  };

  const statusCounts = files.reduce((acc, file) => {
    const status = file.processingStatus || file.status || 'UNKNOWN';
    acc[status] = (acc[status] || 0) + 1;
    return acc;
  }, {});

  return (
    <>
      <PageHeader
        title="文件"
        desc="上传结构化或非结构化文件，后台解析后用于对话分析"
        actions={<><ToolbarSearch value={query} onChange={setQuery} placeholder="搜索文件名、类型、状态" /><button className="btn" onClick={load}><RefreshCw size={16} />刷新</button><label className="btn primary"><Upload size={16} />上传文件<input type="file" hidden onChange={upload} /></label></>}
      />
      <div className="grid grid-4">
        <Metric label="文件总数" value={files.length} />
        <Metric label="处理完成" value={statusCounts.COMPLETED || 0} />
        <Metric label="处理中" value={(statusCounts.QUEUED || 0) + (statusCounts.PROCESSING || 0)} />
        <Metric label="失败" value={statusCounts.FAILED || 0} />
      </div>
      <DataTable columns={[
        { key: 'filename', title: '文件名', render: (file) => <><strong>{file.filename}</strong><div className="muted">{file.fileId}</div></> },
        { key: 'contentType', title: '类型', render: (file) => file.contentType || '-' },
        { key: 'size', title: '大小', render: (file) => formatBytes(file.size) },
        { key: 'status', title: '状态', render: (file) => <Badge tone={statusTone(file.processingStatus || file.status)}>{file.processingStatus || file.status || '-'}</Badge> },
        { key: 'uploadedAt', title: '上传时间', render: (file) => formatTime(file.uploadedAt) },
        { key: 'actions', title: '操作', render: (file) => <div className="toolbar"><button className="btn" onClick={() => openDetail(file)}><Eye size={16} />详情</button><button className="btn danger" onClick={() => setConfirm(file)}><Trash2 size={16} /></button></div> }
      ]} rows={filtered} rowKey="fileId" loading={loading} empty={<EmptyState title="暂无文件" desc="上传文件后，这里会显示解析状态和失败原因。" />} />
      {detail && <Modal title="文件详情" onClose={() => setDetail(null)}>
        <div className="detail-grid">
          <span>文件名</span><strong>{detail.filename || '-'}</strong>
          <span>类型</span><span>{detail.contentType || '-'}</span>
          <span>大小</span><span>{formatBytes(detail.size)}</span>
          <span>状态</span><span>{detail.processingStatus || detail.status || '-'}</span>
          <span>上传时间</span><span>{formatTime(detail.uploadedAt)}</span>
          <span>完成时间</span><span>{formatTime(detail.processedAt)}</span>
        </div>
        {detail.processingError && <div className="alert danger">{detail.processingError}</div>}
      </Modal>}
      {confirm && <ConfirmDialog title="删除文件" message={`确认删除「${confirm.filename}」？`} danger confirmText="删除" onCancel={() => setConfirm(null)} onConfirm={async () => { await api.delete(`/api/v1/files/delete/${confirm.fileId}`); setConfirm(null); toast('文件已删除', 'success'); load(); }} />}
    </>
  );
}
