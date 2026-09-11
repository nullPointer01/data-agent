import { Bot, Boxes, CheckCircle2, ChevronDown, CircleOff, Search, ShieldAlert, Wrench } from 'lucide-react';
import { useState } from 'react';
import { Badge } from './ui.jsx';

const GROUPS = [
  { type: 'TOOL', label: 'Tools', filterLabel: 'Tool', icon: Wrench },
  { type: 'SKILL', label: 'Skills', filterLabel: 'Skill', icon: Boxes },
  { type: 'SUB_AGENT', label: 'Sub Agents', filterLabel: '子 Agent', icon: Bot }
];

const RISK_LABELS = {
  LOW: { label: '低风险', tone: 'gray' },
  MEDIUM: { label: '中风险', tone: 'amber' },
  HIGH: { label: '高风险', tone: 'red' }
};

function sourceId(capability) {
  const identity = capability?.identity || '';
  const delimiter = identity.indexOf(':');
  return delimiter >= 0 ? identity.slice(delimiter + 1) : identity;
}

function capabilityType(identity) {
  if (identity.startsWith('tool:')) {
    return 'TOOL';
  }
  if (identity.startsWith('skill:')) {
    return 'SKILL';
  }
  if (identity.startsWith('agent:')) {
    return 'SUB_AGENT';
  }
  return null;
}

function missingCapability(identity) {
  const type = capabilityType(identity);
  if (!type) {
    return null;
  }
  return {
    identity,
    type,
    version: '-',
    name: sourceId({ identity }),
    description: '该历史绑定已不在当前能力目录中',
    availability: {
      available: false,
      summary: '仅可移除此绑定，不能重新选择'
    }
  };
}

function CapabilityStatus({ capability }) {
  const available = capability?.availability?.available === true;
  return available ? (
    <span className="capability-status available"><CheckCircle2 size={13} />可用</span>
  ) : (
    <span className="capability-status unavailable"><CircleOff size={13} />不可用</span>
  );
}

function CapabilityRow({ capability, selectedBindings, onToggleBinding }) {
  const [expanded, setExpanded] = useState(false);
  const selected = selectedBindings.includes(capability.identity);
  const available = capability?.availability?.available === true;
  const risk = capability?.risk?.level ? (RISK_LABELS[capability.risk.level] || RISK_LABELS.LOW) : null;
  const unavailableReason = capability?.availability?.summary || '当前运行环境不可用';
  const canToggle = available || selected;
  const controlId = `capability-${capability.identity.replace(/[^a-zA-Z0-9_-]/g, '-')}`;
  const actionNotice = capability?.risk?.approvalRequired
    ? '执行前会请求确认'
    : capability?.risk?.level === 'HIGH' ? '高风险能力，受运行策略限制' : '';

  return (
    <div className={`capability-option ${selected ? 'selected' : ''} ${!available ? 'unavailable' : ''}`}>
      <label className="capability-option-main" htmlFor={controlId}>
        <input
          id={controlId}
          type="checkbox"
          checked={selected}
          disabled={!canToggle}
          onChange={() => onToggleBinding(capability.identity)}
          aria-label={`${selected ? '取消' : '选择'} ${capability.name}`}
        />
        <span className="capability-option-copy">
          <span className="capability-option-title">
            <strong>{capability.name}</strong>
            {actionNotice && <Badge tone="red"><ShieldAlert size={11} />{actionNotice}</Badge>}
            <CapabilityStatus capability={capability} />
          </span>
          <small>{capability.description || '未提供能力描述'}</small>
        </span>
      </label>
      <button
        type="button"
        className="capability-detail-toggle"
        aria-expanded={expanded}
        aria-label={`${expanded ? '收起' : '查看'} ${capability.name} 技术信息`}
        title={`${expanded ? '收起' : '查看'}技术信息`}
        onClick={() => setExpanded((value) => !value)}
      >
        <ChevronDown size={15} />
      </button>
      {expanded && (
        <div className="capability-option-meta">
          <code title={capability.identity}>{capability.identity}</code>
          {capability.version && capability.version !== '-' && <span>版本 {capability.version}</span>}
          {risk && <Badge tone={risk.tone}>{risk.label}</Badge>}
          {capability?.risk?.readOnly === true && <span>只读</span>}
          {capability?.risk?.approvalRequired && <span>需要人工确认</span>}
          {!available && <span className="capability-unavailable-reason">{unavailableReason}</span>}
        </div>
      )}
    </div>
  );
}

export function CapabilityPicker({ capabilities, selectedBindings, onToggleBinding, labelledBy }) {
  const [query, setQuery] = useState('');
  const [activeType, setActiveType] = useState('ALL');
  const directoryItems = Array.isArray(capabilities) ? capabilities : [];
  const selections = Array.isArray(selectedBindings) ? selectedBindings : [];
  const directoryIdentities = new Set(directoryItems.map((item) => item.identity));
  const missingItems = selections
    .filter((identity) => !directoryIdentities.has(identity))
    .map(missingCapability)
    .filter(Boolean);
  const items = [...directoryItems, ...missingItems];
  const normalizedQuery = query.trim().toLowerCase();
  const visibleItems = items
    .filter((item) => activeType === 'ALL' || item.type === activeType)
    .filter((item) => !normalizedQuery || [item.name, item.description, item.identity]
      .some((value) => String(value || '').toLowerCase().includes(normalizedQuery)))
    .sort((left, right) => {
      const selectionDifference = Number(selections.includes(right.identity)) - Number(selections.includes(left.identity));
      if (selectionDifference !== 0) {
        return selectionDifference;
      }
      const availabilityDifference = Number(right?.availability?.available === true) - Number(left?.availability?.available === true);
      return availabilityDifference || String(left.name || '').localeCompare(String(right.name || ''));
    });

  if (items.length === 0) {
    return <div className="capability-picker-empty">当前没有可见能力</div>;
  }

  return (
    <div className="capability-picker" role="group" aria-labelledby={labelledBy}>
      <div className="capability-picker-toolbar">
        <label className="capability-search">
          <Search size={15} />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="搜索能力"
            aria-label="搜索能力"
          />
        </label>
        <div className="capability-type-filter" role="group" aria-label="按能力类型筛选">
          <button type="button" className={activeType === 'ALL' ? 'active' : ''} onClick={() => setActiveType('ALL')}>全部</button>
          {GROUPS.map((group) => (
            <button
              type="button"
              className={activeType === group.type ? 'active' : ''}
              key={group.type}
              onClick={() => setActiveType(group.type)}
            >
              {group.filterLabel}
            </button>
          ))}
        </div>
      </div>
      <div className="capability-picker-summary">{selections.length} 项已绑定 · 已选能力优先显示</div>
      <div className="capability-picker-groups">
        {GROUPS.map((group) => {
          const groupItems = visibleItems.filter((item) => item.type === group.type);
          if (groupItems.length === 0) {
            return null;
          }
          const GroupIcon = group.icon;
          const availableCount = groupItems.filter((item) => item?.availability?.available === true).length;
          const selectedCount = groupItems.filter((item) => selections.includes(item.identity)).length;
          return (
            <section className="capability-group" key={group.type} aria-labelledby={`capability-${group.type}`}>
              <div className="capability-group-header">
                <span><GroupIcon size={15} /><strong id={`capability-${group.type}`}>{group.label}</strong></span>
                <small>{selectedCount} 已选 · {availableCount}/{groupItems.length} 可用</small>
              </div>
              <div className="capability-option-list">
                {groupItems.map((capability) => (
                  <CapabilityRow
                    key={capability.identity}
                    capability={capability}
                    selectedBindings={selections}
                    onToggleBinding={onToggleBinding}
                  />
                ))}
              </div>
            </section>
          );
        })}
        {visibleItems.length === 0 && <div className="capability-picker-empty">没有匹配的能力</div>}
      </div>
    </div>
  );
}
