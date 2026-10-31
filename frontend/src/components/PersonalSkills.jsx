import { useState } from 'react';
import { useServerList } from '../hooks/useServerList.js';
import { Badge, ConfirmDialog, DataTable, EmptyState, ListLoadError, Modal, PageHeader } from './ui.jsx';
import { SkillDraftFields } from './SkillDraftFields.jsx';
import { SkillHistoryModal } from '../pages/admin/SkillsPage.jsx';
import { truncate } from '../utils/format.js';

/** 程序性记忆由用户审核，普通用户只能管理自己的候选与发布技能。 */
export function PersonalSkills({ api, toast }) {
  const list = useServerList({ api, url: '/api/v1/my/skills', mode: 'offset' });
  const [editing, setEditing] = useState(null);
  const [history, setHistory] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [busy, setBusy] = useState(false);
  const mutate = async (action) => {
    if (busy) return;
    setBusy(true);
    try { const response = await action(); if (response?.success === false) throw new Error(response.message); await list.refresh(); }
    catch (error) { toast(error.message, 'error'); }
    finally { setBusy(false); }
  };
  const save = () => mutate(async () => {
    const pending = editing.reviewStatus === 'PENDING';
    const response = pending ? await api.post(`/api/v1/my/skills/${editing.skillId}/confirm`, editing)
      : await api.put(`/api/v1/my/skills/${editing.skillId}`, editing);
    if (response.success === false) throw new Error(response.message);
    toast(pending ? '技能已确认发布，请到我的 Agent 绑定技能和所需工具' : '技能已更新'); setEditing(null);
    return response;
  });
  return <section>
    <PageHeader title="技能经验" desc="保存可复用的做事方法，与个人事实和偏好分开；从已完成对话提炼，或由 Agent 提议，确认后启用" actions={<button className="btn" onClick={list.refresh}>刷新</button>} />
    <ListLoadError error={list.error} onRetry={list.refresh} />
    <DataTable loading={list.loading} serverPagination={list.pagination} rows={list.items} rowKey="skillId"
      empty={<EmptyState title="暂无技能经验" desc="在已完成的助手回答下点击“提炼为技能”；如需 Agent 主动提议，在我的 Agent 中绑定 proposeSkill 工具。" />}
      columns={[
        { key: 'name', title: '名称' },
        { key: 'description', title: '用途', render: (item) => truncate(item.description, 100) },
        { key: 'reviewStatus', title: '状态', render: (item) => <Badge tone={item.reviewStatus === 'PUBLISHED' ? 'green' : 'gray'}>{({ PENDING: '待确认', PUBLISHED: item.enabled ? '已发布' : '已停用', REJECTED: '已拒绝' })[item.reviewStatus]}</Badge> },
        { key: 'toolDependencies', title: '工具依赖', render: (item) => item.toolDependencies || '无' },
        { key: 'actions', title: '操作', render: (item) => <div className="toolbar">
          {item.reviewStatus !== 'REJECTED' && <button className="btn" disabled={busy} onClick={() => setEditing({ ...item })}>{item.reviewStatus === 'PENDING' ? '检查并确认' : '编辑'}</button>}
          {item.reviewStatus === 'PENDING' && <button className="btn" disabled={busy} onClick={() => mutate(() => api.post(`/api/v1/my/skills/${item.skillId}/reject`, {}))}>拒绝</button>}
          {item.reviewStatus === 'PUBLISHED' && <button className="btn" onClick={() => setHistory(item)}>历史</button>}
          <button className="btn danger" disabled={busy} onClick={() => setDeleting(item)}>删除</button>
        </div> }
      ]} />
    {editing && <Modal title={editing.reviewStatus === 'PENDING' ? '检查技能候选' : '编辑个人技能'} size="lg" onClose={() => { if (!busy) setEditing(null); }} actions={<button className="btn primary" disabled={busy} onClick={save}>{busy ? '保存中…' : editing.reviewStatus === 'PENDING' ? '确认发布' : '保存'}</button>}>
      {editing.evidence && <p className="muted">来源原文：{editing.evidence}</p>}
      <SkillDraftFields value={editing} onChange={setEditing} />
    </Modal>}
    {history && <SkillHistoryModal api={api} toast={toast} skill={history} baseUrl="/api/v1/my/skills" onClose={() => setHistory(null)} onChanged={list.refresh} />}
    {deleting && <ConfirmDialog title="删除个人技能" message={`确认删除“${deleting.name}”？已绑定的 Agent 将不能再激活它。`} danger busy={busy}
      onCancel={() => { if (!busy) setDeleting(null); }} onConfirm={() => mutate(async () => {
        const response = await api.delete(`/api/v1/my/skills/${deleting.skillId}`);
        if (response.success === false) throw new Error(response.message);
        setDeleting(null); toast('技能已删除'); return response;
      })} />}
  </section>;
}
