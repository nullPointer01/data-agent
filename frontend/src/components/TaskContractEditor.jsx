import { ChevronDown, ListChecks, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';

const CRITERION_TYPES = [
  { value: 'ANSWER_CONTAINS', label: '回答包含文本', placeholder: '例如：已完成' },
  { value: 'JSON_FIELD_EQUALS', label: 'JSON 字段等于', placeholder: '{"path":"/status","value":"SUCCESS"}' },
  { value: 'TOOL_CALLED', label: '调用指定工具', placeholder: '输入工具名称' },
  { value: 'APPROVAL_STATUS', label: '审批状态', placeholder: 'APPROVED' },
  { value: 'RUN_STATUS', label: '运行状态', placeholder: 'COMPLETED' }
];

const APPROVAL_STATUSES = ['APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'SUCCEEDED', 'FAILED', 'BLOCKED'];
const RUN_STATUSES = ['COMPLETED', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'BUDGET_EXHAUSTED', 'REJECTED', 'EXPIRED'];
let criterionSequence = 0;

function createCriterion() {
  criterionSequence += 1;
  return {
    clientId: `criterion-${Date.now()}-${criterionSequence}`,
    type: 'ANSWER_CONTAINS',
    expectedValue: '',
    required: true
  };
}

export function emptyTaskContractDraft() {
  return { enabled: false, goal: '', criteria: [] };
}

export function buildTaskContractPayload(draft) {
  if (!draft?.enabled) return null;
  const goal = draft.goal?.trim() || '';
  if (!goal) throw new Error('请填写任务目标');
  if (!Array.isArray(draft.criteria) || draft.criteria.length === 0) {
    throw new Error('请至少添加一条成功标准');
  }
  return {
    goal,
    criteria: draft.criteria.map((criterion, index) => {
      const expectedValue = criterion.expectedValue?.trim() || '';
      if (!expectedValue) throw new Error(`请填写第 ${index + 1} 条成功标准的期望值`);
      return {
        criterionId: `criterion-${index + 1}`,
        type: criterion.type,
        expectedValue,
        required: criterion.required !== false
      };
    })
  };
}

function ExpectedValueControl({ criterion, availableTools, disabled, onChange }) {
  if (criterion.type === 'TOOL_CALLED' && availableTools.length > 0) {
    return (
      <select className="select" value={criterion.expectedValue} disabled={disabled} onChange={(event) => onChange(event.target.value)} aria-label="期望调用的工具">
        <option value="">选择工具</option>
        {availableTools.map((tool) => <option key={tool.name} value={tool.name}>{tool.name}</option>)}
      </select>
    );
  }
  if (criterion.type === 'APPROVAL_STATUS' || criterion.type === 'RUN_STATUS') {
    const statuses = criterion.type === 'APPROVAL_STATUS' ? APPROVAL_STATUSES : RUN_STATUSES;
    return (
      <select className="select" value={criterion.expectedValue} disabled={disabled} onChange={(event) => onChange(event.target.value)} aria-label="期望状态">
        <option value="">选择状态</option>
        {statuses.map((status) => <option key={status} value={status}>{status}</option>)}
      </select>
    );
  }
  const type = CRITERION_TYPES.find((item) => item.value === criterion.type);
  return (
    <input className="input" value={criterion.expectedValue} maxLength={2000} disabled={disabled} onChange={(event) => onChange(event.target.value)} placeholder={type?.placeholder || '期望值'} aria-label="期望值" />
  );
}

export function TaskContractEditor({ value, onChange, availableTools = [], disabled = false }) {
  const [open, setOpen] = useState(false);
  const draft = value || emptyTaskContractDraft();
  const update = (next) => onChange?.({ ...draft, ...next });

  const toggleEvaluation = (enabled) => {
    update({ enabled, criteria: enabled && draft.criteria.length === 0 ? [createCriterion()] : draft.criteria });
  };

  const updateCriterion = (clientId, changes) => {
    update({
      criteria: draft.criteria.map((criterion) => criterion.clientId === clientId
        ? { ...criterion, ...changes }
        : criterion)
    });
  };

  return (
    <section className={`task-contract-editor ${draft.enabled ? 'enabled' : ''}`}>
      <button type="button" className="task-contract-toggle" onClick={() => setOpen((current) => !current)} aria-expanded={open}>
        <ListChecks size={16} />
        <span><strong>成功标准</strong><small>{draft.enabled ? `${draft.criteria.length} 条标准` : '未设置'}</small></span>
        <ChevronDown className={open ? 'open' : ''} size={16} />
      </button>
      {open && (
        <div className="task-contract-body">
          <label className="task-contract-switch">
            <input type="checkbox" checked={draft.enabled} disabled={disabled} onChange={(event) => toggleEvaluation(event.target.checked)} />
            <span>评估本次任务结果</span>
          </label>
          {draft.enabled && (
            <>
              <label className="task-contract-goal" htmlFor="task-contract-goal">
                <span>任务目标</span>
                <textarea id="task-contract-goal" className="textarea" rows={2} maxLength={2000} value={draft.goal} disabled={disabled} onChange={(event) => update({ goal: event.target.value })} />
              </label>
              <div className="task-criteria-heading">
                <strong>判定条件</strong>
                <button type="button" className="btn compact" disabled={disabled || draft.criteria.length >= 20} onClick={() => update({ criteria: [...draft.criteria, createCriterion()] })}><Plus size={14} />添加</button>
              </div>
              <div className="task-criteria-list" aria-live="polite">
                {draft.criteria.map((criterion, index) => (
                  <div className="task-criterion-row" key={criterion.clientId}>
                    <span className="task-criterion-index">{index + 1}</span>
                    <select className="select" value={criterion.type} disabled={disabled} onChange={(event) => updateCriterion(criterion.clientId, { type: event.target.value, expectedValue: '' })} aria-label={`第 ${index + 1} 条标准类型`}>
                      {CRITERION_TYPES.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}
                    </select>
                    <ExpectedValueControl criterion={criterion} availableTools={availableTools} disabled={disabled} onChange={(expectedValue) => updateCriterion(criterion.clientId, { expectedValue })} />
                    <label className="task-required-check"><input type="checkbox" checked={criterion.required !== false} disabled={disabled} onChange={(event) => updateCriterion(criterion.clientId, { required: event.target.checked })} />必需</label>
                    <button type="button" className="icon-button bordered" title="删除成功标准" disabled={disabled || draft.criteria.length === 1} onClick={() => update({ criteria: draft.criteria.filter((item) => item.clientId !== criterion.clientId) })}><Trash2 size={14} /></button>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      )}
    </section>
  );
}
