import { CheckCircle2, Plug } from 'lucide-react';
import { availablePackBindings, normalizeCapabilityBindings, PERSONAL_CAPABILITY_PACKS } from '../agent/capabilityPacks.js';

/** 普通用户的能力包选择器，最终仍保存后端稳定 Capability identity。 */
export function CapabilityPackPicker({ capabilities, selectedBindings, onChange, labelledBy }) {
  const bindings = normalizeCapabilityBindings(selectedBindings);
  // 外部工具逐项授权，不混入默认能力包；已失联或被移除的绑定仍可手动关闭。
  const externalTools = new Map((capabilities || [])
    .filter((item) => item.type === 'TOOL' && item.risk?.assessmentSource === 'LOCAL_MCP_POLICY')
    .map((item) => [item.identity, item]));
  bindings.filter((identity) => identity.startsWith('tool:mcp_')).forEach((identity) => {
    if (!externalTools.has(identity)) externalTools.set(identity, { identity, description: '已绑定的外部工具（暂不可用）' });
  });

  const togglePack = (pack) => {
    const packBindings = availablePackBindings(pack, capabilities);
    if (packBindings.length === 0) {
      return;
    }
    const packSet = new Set(packBindings);
    const allSelected = packBindings.every((identity) => bindings.includes(identity));
    onChange(allSelected
      ? bindings.filter((identity) => !packSet.has(identity))
      : normalizeCapabilityBindings([...bindings, ...packBindings]));
  };

  return (
    <div className="capability-pack-list" role="group" aria-labelledby={labelledBy}>
      {PERSONAL_CAPABILITY_PACKS.map((pack) => {
        const packBindings = availablePackBindings(pack, capabilities);
        const selectedCount = packBindings.filter((identity) => bindings.includes(identity)).length;
        const active = packBindings.length > 0 && selectedCount === packBindings.length;
        const partial = selectedCount > 0 && !active;
        const PackIcon = pack.icon;
        return (
          <button
            type="button"
            className={`capability-pack-option ${active ? 'active' : ''} ${partial ? 'partial' : ''}`}
            aria-pressed={active}
            disabled={packBindings.length === 0}
            key={pack.id}
            onClick={() => togglePack(pack)}
          >
            <span className="capability-pack-icon"><PackIcon size={17} /></span>
            <span className="capability-pack-copy">
              <strong>{pack.label}</strong>
              <small>{pack.description}</small>
            </span>
            <span className="capability-pack-count">
              {packBindings.length === 0 ? '暂不可用' : active ? '已开启' : partial ? '部分开启' : '未开启'}
              {active && <CheckCircle2 size={15} />}
            </span>
          </button>
        );
      })}
      {[...externalTools.values()].map((tool) => {
        const selected = bindings.includes(tool.identity);
        const available = tool.availability?.available === true;
        return (
          <button
            type="button"
            className={`capability-pack-option ${selected ? 'active' : ''}`}
            aria-pressed={selected}
            disabled={!available && !selected}
            key={tool.identity}
            title={tool.identity}
            onClick={() => onChange(selected
              ? bindings.filter((identity) => identity !== tool.identity)
              : [...bindings, tool.identity])}
          >
            <span className="capability-pack-icon"><Plug size={17} /></span>
            <span className="capability-pack-copy">
              <strong>{tool.description || tool.name}</strong>
              <small>外部工具 · 只读</small>
            </span>
            <span className="capability-pack-count">
              {!available ? (selected ? '不可用，可关闭' : '暂不可用') : selected ? '已开启' : '未开启'}
              {selected && <CheckCircle2 size={15} />}
            </span>
          </button>
        );
      })}
    </div>
  );
}
