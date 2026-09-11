import { CheckCircle2 } from 'lucide-react';
import { availablePackBindings, normalizeCapabilityBindings, PERSONAL_CAPABILITY_PACKS } from '../agent/capabilityPacks.js';

export function CapabilityPackPicker({ capabilities, selectedBindings, onChange, labelledBy }) {
  const bindings = normalizeCapabilityBindings(selectedBindings);

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
    </div>
  );
}
