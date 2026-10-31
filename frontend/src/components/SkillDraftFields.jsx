import { Field } from './ui.jsx';

/** 生成草稿和个人候选共用编辑合同；依赖填写稳定工具名，不填写接口地址。 */
export function SkillDraftFields({ value, onChange }) {
  const set = (key, text) => onChange({ ...value, [key]: text });
  return <div className="form-stack">
    <Field label="名称"><input className="input" maxLength={60} value={value.name || ''} onChange={(e) => set('name', e.target.value)} /></Field>
    <Field label="适用任务"><textarea className="textarea" maxLength={500} rows={2} value={value.description || ''} onChange={(e) => set('description', e.target.value)} /></Field>
    <Field label="操作方法（Markdown）"><textarea className="textarea mono" maxLength={12000} rows={8} value={value.promptTemplate || ''} onChange={(e) => set('promptTemplate', e.target.value)} /></Field>
    <Field label="步骤资料"><textarea className="textarea" maxLength={12000} rows={5} value={value.steps || ''} onChange={(e) => set('steps', e.target.value)} /></Field>
    <Field label="工具依赖（每行一个工具名，如 calculate、generateChart）"><textarea className="textarea mono" maxLength={2400} rows={3} value={value.toolDependencies || ''} onChange={(e) => set('toolDependencies', e.target.value)} /></Field>
    {value.reviewStatus === 'PUBLISHED' && <Field label="启用"><input type="checkbox" checked={value.enabled !== false} onChange={(e) => onChange({ ...value, enabled: e.target.checked })} /></Field>}
    <p className="muted">发布后仍需在“我的 Agent”绑定此技能及所需工具。激活不会扩大工具权限或跳过审批。</p>
  </div>;
}
