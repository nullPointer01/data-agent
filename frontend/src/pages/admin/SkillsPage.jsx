import { useServerList } from '../../hooks/useServerList.js';
import { History, RotateCcw, Sparkles } from 'lucide-react';
import { useState } from 'react';
import { ResourcePage } from '../../components/admin/ResourcePage.jsx';
import { Badge, DataTable, EmptyState, ListLoadError, Field, Modal } from '../../components/ui.jsx';
import { formatTime, truncate } from '../../utils/format.js';
import { SkillDraftFields } from '../../components/SkillDraftFields.jsx';

/** Skill 管理页：手工维护/生成 Skill，并查看版本历史与回滚。 */
export function SkillsPage({ api, toast }) {
  const [historySkill, setHistorySkill] = useState(null);
  const [generateModal, setGenerateModal] = useState(false);
  const [generated, setGenerated] = useState(null);
  const [generateForm, setGenerateForm] = useState({ description: '', data: '' });
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);

  const openHistory = (skill) => setHistorySkill(skill);

  const generate = async () => {
    if (busy) return;
    setBusy(true); setGenerated(null);
    try {
      const res = await api.post('/api/v1/skills/generate', generateForm);
      if (res.success !== false) setGenerated(res);
      toast(res.message || '草稿生成完成', res.success === false ? 'error' : 'success');
    } catch (error) { toast(error.message, 'error'); }
    finally { setBusy(false); }
  };

  const createGenerated = async () => {
    if (busy || !generated) return;
    setBusy(true);
    try {
      const res = await api.post('/api/v1/skills/create', { ...generated, enabled: true, source: 'reviewed_generation' });
      if (res.success === false) throw new Error(res.message);
      toast('技能已确认创建'); setGenerateModal(false); setGenerated(null); setRevision((n) => n + 1);
    } catch (error) { toast(error.message, 'error'); }
    finally { setBusy(false); }
  };

  return (
    <>
      <ResourcePage
        key={revision}
        api={api}
        toast={toast}
        title="技能管理"
        desc="维护可复用的 Agent 操作指引与步骤资料，按需激活；外部动作请配置 Tool/MCP"
        listUrl="/api/v1/skills/list"
        idKey="skillId"
        createUrl="/api/v1/skills/create"
        updateUrl={(id) => `/api/v1/skills/update/${id}`}
        toggleUrl={(id) => `/api/v1/skills/toggle/${id}`}
        deleteUrl={(id) => `/api/v1/skills/delete/${id}`}
        fields={[
          ['name', '名称'],
          ['description', '描述'],
          ['promptTemplate', '技能指令（Markdown，最多 12000 字符）', 'textarea'],
          ['steps', '步骤资料（按需读取，最多 12000 字符）', 'textarea'],
          ['toolDependencies', '工具依赖（每行一个工具名，激活后按权限开放）', 'textarea'],
          ['apiUrl', '旧 API 地址（仅供迁移识别；迁入 Tool/MCP 后清空）'],
          ['enabled', '启用', 'checkbox']
        ]}
        visibleFields={['name', 'description', 'source']}
        tableColumns={[
          { key: 'name', title: '名称' },
          { key: 'description', title: '用途', render: (item) => truncate(item.description, 100) },
          { key: 'configurationIssue', title: '运行配置', render: (item) => item.configurationIssue || '配置有效' }
        ]}
        toggleBlockedReason={(item) => item.enabled === false ? item.configurationIssue : ''}
        headerExtra={<button className="btn" onClick={() => { setGenerated(null); setGenerateModal(true); }}><Sparkles size={16} />生成技能</button>}
        extraRowAction={(item) => <button className="btn" onClick={() => openHistory(item)}><History size={16} />历史</button>}
      />
      {historySkill && <SkillHistoryModal api={api} toast={toast} skill={historySkill} onClose={() => setHistorySkill(null)} />}
      {generateModal && <Modal title="AI 生成技能草稿" onClose={() => { if (!busy) setGenerateModal(false); }} size="lg" actions={<>
        <button className="btn" disabled={busy} onClick={generate}><Sparkles size={16} />{busy ? '处理中…' : '生成草稿'}</button>
        {generated && <button className="btn primary" disabled={busy} onClick={createGenerated}>确认创建</button>}
      </>}>
        <div className="form-stack">
          <Field label="技能描述"><input className="input" value={generateForm.description} onChange={(event) => setGenerateForm({ ...generateForm, description: event.target.value })} /></Field>
          <Field label="样例数据"><textarea className="textarea mono" rows={8} value={generateForm.data} onChange={(event) => setGenerateForm({ ...generateForm, data: event.target.value })} /></Field>
          {generated && <SkillDraftFields value={generated} onChange={setGenerated} />}
        </div>
      </Modal>}
    </>
  );
}

/** 历史版本独立按页加载，回滚按版本定点查询，不再下载全部历史再匹配。 */
export function SkillHistoryModal({ api, toast, skill, onClose, baseUrl = '/api/v1/skills', onChanged }) {
  const list = useServerList({ api, url: `${baseUrl}/${skill.skillId}/history`, mode: 'offset' });
  const rollback = async (version) => {
    const response = await api.post(`${baseUrl}/${skill.skillId}/rollback/${version}`, {});
    toast(response.message || '已回滚', response.success === false ? 'error' : 'success');
    if (response.success !== false) { await list.refresh({ reset: true }); await onChanged?.(); }
  };
  return <Modal title={`版本历史 - ${skill.name}`} onClose={onClose} size="lg">
    <ListLoadError error={list.error} onRetry={list.refresh} />
    <DataTable serverPagination={list.pagination} loading={list.loading} columns={[
      { key: 'version', title: '版本', render: (item) => <Badge tone="gray">v{item.version}</Badge> },
      { key: 'remark', title: '备注', render: (item) => item.remark || '-' },
      { key: 'promptTemplate', title: 'Prompt', render: (item) => truncate(item.promptTemplate, 160) },
      { key: 'toolDependencies', title: '工具依赖', render: (item) => item.toolDependencies || '无' },
      { key: 'createdAt', title: '时间', render: (item) => formatTime(item.createdAt) },
      { key: 'actions', title: '操作', render: (item) => <button className="btn" onClick={() => rollback(item.version)}><RotateCcw size={16} />回滚</button> }
    ]} rows={list.items} rowKey="id" empty={<EmptyState title="暂无历史版本" desc="编辑技能 Prompt 后会自动保存历史版本。" />} />
  </Modal>;
}
