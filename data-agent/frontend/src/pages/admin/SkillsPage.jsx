import { History, RotateCcw, Sparkles } from 'lucide-react';
import { useState } from 'react';
import { ResourcePage } from '../../components/admin/ResourcePage.jsx';
import { Badge, DataTable, EmptyState, Field, Modal } from '../../components/ui.jsx';
import { formatTime, truncate } from '../../utils/format.js';

export function SkillsPage({ api, toast }) {
  const [historySkill, setHistorySkill] = useState(null);
  const [history, setHistory] = useState([]);
  const [generateModal, setGenerateModal] = useState(false);
  const [generated, setGenerated] = useState(null);
  const [generateForm, setGenerateForm] = useState({ description: '', data: '' });

  const openHistory = async (skill) => {
    setHistorySkill(skill);
    const res = await api.get(`/api/v1/skills/${skill.skillId}/history`);
    setHistory(res.history || []);
  };

  const rollback = async (version) => {
    const res = await api.post(`/api/v1/skills/${historySkill.skillId}/rollback/${version}`, {});
    toast(res.message || '已回滚', res.success === false ? 'error' : 'success');
    await openHistory(historySkill);
  };

  const generate = async () => {
    const res = await api.post('/api/v1/skills/generate', generateForm);
    setGenerated(res.skill || res.generated || res);
    toast(res.message || '技能生成完成', res.success === false ? 'error' : 'success');
  };

  return (
    <>
      <ResourcePage
        api={api}
        toast={toast}
        title="技能管理"
        desc="维护技能 Prompt、关键词、版本历史和外部 API 配置"
        listUrl="/api/v1/skills/list"
        listKey="skills"
        idKey="skillId"
        createUrl="/api/v1/skills/create"
        updateUrl={(id) => `/api/v1/skills/update/${id}`}
        toggleUrl={(id) => `/api/v1/skills/toggle/${id}`}
        deleteUrl={(id) => `/api/v1/skills/delete/${id}`}
        fields={[
          ['name', '名称'],
          ['description', '描述'],
          ['keywords', '关键词'],
          ['promptTemplate', 'Prompt', 'textarea'],
          ['apiUrl', '外部 API'],
          ['apiMethod', 'API Method', 'select', [{ value: 'POST', label: 'POST' }, { value: 'GET', label: 'GET' }]],
          ['apiHeaders', 'API Headers', 'textarea'],
          ['steps', '步骤', 'textarea'],
          ['autoAttach', '自动挂载'],
          ['enabled', '启用', 'checkbox']
        ]}
        visibleFields={['name', 'description', 'keywords', 'source']}
        headerExtra={<button className="btn" onClick={() => setGenerateModal(true)}><Sparkles size={16} />生成技能</button>}
        extraRowAction={(item) => <button className="btn" onClick={() => openHistory(item)}><History size={16} />历史</button>}
      />
      {historySkill && <Modal title={`版本历史 - ${historySkill.name}`} onClose={() => setHistorySkill(null)} size="lg">
        <DataTable columns={[
          { key: 'version', title: '版本', render: (item) => <Badge tone="gray">v{item.version}</Badge> },
          { key: 'remark', title: '备注', render: (item) => item.remark || '-' },
          { key: 'promptTemplate', title: 'Prompt', render: (item) => truncate(item.promptTemplate, 160) },
          { key: 'createdAt', title: '时间', render: (item) => formatTime(item.createdAt) },
          { key: 'actions', title: '操作', render: (item) => <button className="btn" onClick={() => rollback(item.version)}><RotateCcw size={16} />回滚</button> }
        ]} rows={history} rowKey="id" empty={<EmptyState title="暂无历史版本" desc="编辑技能 Prompt 后会自动保存历史版本。" />} />
      </Modal>}
      {generateModal && <Modal title="AI 生成技能" onClose={() => setGenerateModal(false)} size="lg" actions={<button className="btn primary" onClick={generate}><Sparkles size={16} />生成</button>}>
        <div className="form-stack">
          <Field label="技能描述"><input className="input" value={generateForm.description} onChange={(event) => setGenerateForm({ ...generateForm, description: event.target.value })} /></Field>
          <Field label="样例数据"><textarea className="textarea mono" rows={8} value={generateForm.data} onChange={(event) => setGenerateForm({ ...generateForm, data: event.target.value })} /></Field>
          {generated && <pre className="code-panel">{JSON.stringify(generated, null, 2)}</pre>}
        </div>
      </Modal>}
    </>
  );
}
