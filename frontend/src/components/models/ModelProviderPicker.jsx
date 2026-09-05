import { Bot, Cable, Check, KeyRound, Network, Search, Server } from 'lucide-react';
import { useMemo, useState } from 'react';

const FEATURED_KEYS = ['openai', 'custom'];
const GROUPS = [
  ['DOMESTIC', '国内厂商'],
  ['AGGREGATOR', '聚合平台']
];

export function ModelProviderPicker({ providers, value, onChange, disabled }) {
  const [query, setQuery] = useState('');
  const keyword = query.trim().toLowerCase();
  const filtered = useMemo(() => {
    if (!keyword) return providers;
    return providers.filter((provider) => `${provider.label} ${provider.key}`.toLowerCase().includes(keyword));
  }, [providers, keyword]);

  const featured = keyword
    ? filtered
    : FEATURED_KEYS.map((key) => providers.find((provider) => provider.key === key)).filter(Boolean);
  const featuredKeys = new Set(featured.map((provider) => provider.key));

  return (
    <aside className="model-provider-sidebar" aria-label="模型供应商">
      <div className="provider-picker-heading">
        <div>
          <h3>选择供应商</h3>
          <p>{providers.length} 个兼容接入</p>
        </div>
      </div>

      <label className="provider-search">
        <Search size={16} />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="搜索 OpenAI、DeepSeek..."
          disabled={disabled}
        />
      </label>

      <div className="provider-list">
        {!!featured.length && (
          <ProviderGroup
            label={keyword ? '搜索结果' : '常用接入'}
            providers={featured}
            value={value}
            onChange={onChange}
            disabled={disabled}
          />
        )}

        {!keyword && GROUPS.map(([groupKey, groupLabel]) => {
          const groupProviders = filtered.filter((provider) =>
            provider.group === groupKey && !featuredKeys.has(provider.key)
          );
          if (!groupProviders.length) return null;
          return (
            <ProviderGroup
              key={groupKey}
              label={groupLabel}
              providers={groupProviders}
              value={value}
              onChange={onChange}
              disabled={disabled}
            />
          );
        })}

        {keyword && !featured.length && <div className="provider-empty">没有匹配的供应商</div>}
      </div>
    </aside>
  );
}

function ProviderGroup({ label, providers, value, onChange, disabled }) {
  return (
    <section className="provider-group">
      <h4>{label}</h4>
      <div className="provider-options">
        {providers.map((provider) => {
          const selected = provider.key === value;
          const Icon = providerIcon(provider);
          return (
            <button
              className={`provider-option ${selected ? 'selected' : ''}`}
              type="button"
              key={provider.key}
              onClick={() => onChange(provider)}
              disabled={disabled}
              aria-pressed={selected}
            >
              <span className="provider-option-icon"><Icon size={17} /></span>
              <span className="provider-option-copy">
                <strong>{provider.label}</strong>
                <small>
                  {provider.apiKeyRequired && <KeyRound size={11} />}
                  {providerCaption(provider)}
                </small>
              </span>
              {selected && <Check className="provider-option-check" size={17} />}
            </button>
          );
        })}
      </div>
    </section>
  );
}

function providerIcon(provider) {
  if (provider.key === 'openai') return Bot;
  if (provider.key === 'custom') return Cable;
  if (provider.group === 'AGGREGATOR') return Network;
  return Server;
}

function providerCaption(provider) {
  if (provider.key === 'openai') return 'OpenAI 官方 API';
  if (provider.key === 'custom') return 'GPT / 兼容网关';
  return provider.apiKeyRequired ? '需要 API Key' : 'API Key 可选';
}
