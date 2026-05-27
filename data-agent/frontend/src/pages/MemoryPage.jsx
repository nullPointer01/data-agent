import { BrainCircuit, RefreshCw, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Badge, ConfirmDialog, DataTable, EmptyState, Metric, PageHeader } from '../components/ui.jsx';
import { formatTime, truncate } from '../utils/format.js';

const tierOptions = [
  { value: '', label: '全部层级' },
  { value: 'SHORT_TERM', label: '短期记忆' },
  { value: 'LONG_TERM', label: '长期记忆' }
];

function tierLabel(tier) {
  if (tier === 'SHORT_TERM') {
    return '短期';
  }
  if (tier === 'LONG_TERM') {
    return '长期';
  }
  if (tier === 'WORKING') {
    return '工作';
  }
  return tier || '-';
}

function tierTone(tier) {
  if (tier === 'LONG_TERM') {
    return 'blue';
  }
  if (tier === 'SHORT_TERM') {
    return 'green';
  }
  return 'gray';
}

function typeLabel(type) {
  const labels = {
    SUMMARY: '摘要',
    PREFERENCE: '偏好',
    ENTITY: '画像',
    CONCLUSION: '结论',
    CONVERSATION: '对话',
    INTENT: '意图',
    KNOWLEDGE: '知识'
  };
  return labels[type] || type || '-';
}

function numberValue(value) {
  return Number(value || 0);
}

function percent(value) {
  return `${Math.round(numberValue(value) * 100)}%`;
}

function durationMs(value) {
  const duration = numberValue(value);
  if (duration >= 100) {
    return `${Math.round(duration)} ms`;
  }
  return `${duration.toFixed(1)} ms`;
}

function quotaHint(stats) {
  const max = numberValue(stats?.maxMemoriesPerUser);
  if (max <= 0) {
    return '未配置配额';
  }
  return `${numberValue(stats?.totalMemories).toLocaleString()} / ${max.toLocaleString()}`;
}

function charsHint(stats) {
  return `${numberValue(stats?.storedContentChars).toLocaleString()} / ${numberValue(stats?.sourceContentChars).toLocaleString()} 字符`;
}

function MemoryStatsPanel({ stats }) {
  const typeCounts = stats?.typeCounts || {};
  const entries = Object.entries(typeCounts);
  return (
    <section className="card memory-stats-panel">
      <div className="section-title">记忆运行状态</div>
      <div className="memory-stats-grid">
        <div>
          <span>工作 / 短期 / 长期</span>
          <strong>
            {numberValue(stats?.workingMemoryCount)} / {numberValue(stats?.shortTermMemoryCount)} / {numberValue(stats?.longTermMemoryCount)}
          </strong>
        </div>
        <div>
          <span>保留策略</span>
          <strong>短期 {numberValue(stats?.shortTermRetentionDays)} 天</strong>
        </div>
        <div>
          <span>长期衰减</span>
          <strong>{numberValue(stats?.longTermDecayAfterDays)} 天后</strong>
        </div>
        <div>
          <span>上下文构建</span>
          <strong>{durationMs(stats?.averageContextBuildMs)}</strong>
          <small>{numberValue(stats?.contextBuildCount)} 次采样</small>
        </div>
        <div>
          <span>压缩后占比</span>
          <strong>{percent(stats?.compressionRatio)}</strong>
          <small>{charsHint(stats)}</small>
        </div>
        <div>
          <span>存储节省</span>
          <strong>{percent(stats?.storageSavingRatio)}</strong>
          <small>摘要化记忆</small>
        </div>
      </div>
      <div className="memory-type-row">
        <span>类型分布</span>
        <div>
          {entries.length ? entries.map(([type, count]) => (
            <Badge key={type} tone="blue">{typeLabel(type)} {count}</Badge>
          )) : <span className="muted">暂无类型统计</span>}
        </div>
      </div>
    </section>
  );
}

function ProfileList({ title, items }) {
  return (
    <div className="card memory-profile-card">
      <div className="section-title">{title}</div>
      {items?.length ? (
        <div className="memory-profile-list">
          {items.map((item) => (
            <div className="memory-profile-item" key={item.memoryId}>
              <span>{truncate(item.content, 120)}</span>
              <small>{formatTime(item.createdAt)}</small>
            </div>
          ))}
        </div>
      ) : (
        <div className="muted">暂无记录</div>
      )}
    </div>
  );
}

function valueOrDash(value) {
  return value || '-';
}

function ProfileSummary({ profile }) {
  const expertiseAreas = Array.isArray(profile?.expertiseAreas) ? profile.expertiseAreas : [];
  const topics = Array.isArray(profile?.frequentlyAskedTopics) ? profile.frequentlyAskedTopics : [];
  const dataSources = Array.isArray(profile?.dataSources) ? profile.dataSources : [];
  return (
    <section className="card memory-summary-card">
      <div className="section-title">结构化画像</div>
      <div className="memory-summary-grid">
        <div><span>称呼</span><strong>{valueOrDash(profile?.displayName)}</strong></div>
        <div><span>角色</span><strong>{valueOrDash(profile?.role)}</strong></div>
        <div><span>公司</span><strong>{valueOrDash(profile?.company)}</strong></div>
        <div><span>行业</span><strong>{valueOrDash(profile?.industry)}</strong></div>
        <div><span>沟通风格</span><strong>{valueOrDash(profile?.communicationStyle)}</strong></div>
        <div><span>输出偏好</span><strong>{valueOrDash(profile?.preferredFormat)}</strong></div>
        <div><span>置信度</span><strong>{Math.round(Number(profile?.confidence || 0) * 100)}%</strong></div>
      </div>
      <TagGroup title="专业领域" items={expertiseAreas} />
      <TagGroup title="高频话题" items={topics} />
      <TagGroup title="常用数据源" items={dataSources} />
    </section>
  );
}

function TagGroup({ title, items }) {
  return (
    <div className="memory-tag-group">
      <span>{title}</span>
      <div>
        {items.length ? items.map((item) => <Badge key={item} tone="blue">{item}</Badge>) : <span className="muted">暂无</span>}
      </div>
    </div>
  );
}

export function MemoryPage({ api, toast }) {
  const [memories, setMemories] = useState([]);
  const [profile, setProfile] = useState({});
  const [stats, setStats] = useState({});
  const [total, setTotal] = useState(0);
  const [tier, setTier] = useState('');
  const [loading, setLoading] = useState(true);
  const [confirm, setConfirm] = useState(null);

  const load = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (tier) {
        params.set('tier', tier);
      }
      params.set('limit', '100');
      const [listRes, profileRes, statsRes] = await Promise.all([
        api.get(`/api/v1/memories?${params.toString()}`),
        api.get('/api/v1/memories/profile'),
        api.get('/api/v1/memories/stats')
      ]);
      setMemories(listRes.memories || []);
      setTotal(listRes.total || 0);
      setProfile(profileRes || {});
      setStats(statsRes || {});
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [tier]);

  const deleteMemory = async () => {
    const res = await api.delete(`/api/v1/memories/${confirm.memoryId}`);
    toast(res.message || '记忆已删除', res.success === false ? 'error' : 'success');
    setConfirm(null);
    load();
  };

  const clearTier = async () => {
    const params = new URLSearchParams();
    if (tier) {
      params.set('tier', tier);
    }
    const url = params.toString() ? `/api/v1/memories?${params.toString()}` : '/api/v1/memories';
    const res = await api.delete(url);
    toast(res.message || '记忆已清理', res.success === false ? 'error' : 'success');
    load();
  };

  return (
    <>
      <PageHeader
        title="记忆中心"
        desc="查看 Agent 为当前用户沉淀的短期摘要、长期偏好、画像事实和结构化用户画像"
        actions={<><select className="select compact-select" value={tier} onChange={(event) => setTier(event.target.value)}>{tierOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select><button className="btn" onClick={load}><RefreshCw size={16} />刷新</button><button className="btn danger" onClick={clearTier}><Trash2 size={16} />清理</button></>}
      />
      <div className="grid grid-4">
        <Metric label="记忆总数" value={stats.totalMemories ?? profile.totalMemories ?? total} hint={quotaHint(stats)} />
        <Metric label="配额使用" value={percent(stats.quotaUsage)} hint="单用户记忆上限" />
        <Metric label="画像置信度" value={percent(stats.profileConfidence ?? profile.profile?.confidence)} />
        <Metric label="存储节省" value={percent(stats.storageSavingRatio)} hint={charsHint(stats)} />
      </div>
      <MemoryStatsPanel stats={stats} />
      <ProfileSummary profile={profile.profile || {}} />
      <div className="grid grid-3 memory-profile-grid">
        <ProfileList title="偏好" items={profile.preferences || []} />
        <ProfileList title="画像事实" items={profile.profileFacts || []} />
        <ProfileList title="结论" items={profile.conclusions || []} />
      </div>
      <DataTable loading={loading} empty={<EmptyState title="暂无记忆" desc="Agent 产生对话摘要或用户明确要求记住内容后，这里会显示记忆条目。" actions={<button className="btn primary" onClick={load}><BrainCircuit size={16} />重新加载</button>} />} columns={[
        { key: 'content', title: '内容', render: (item) => <><strong>{truncate(item.content, 140)}</strong><div className="muted">{item.sessionId || '跨会话记忆'}</div></> },
        { key: 'tier', title: '层级', render: (item) => <Badge tone={tierTone(item.tier)}>{tierLabel(item.tier)}</Badge> },
        { key: 'type', title: '类型', render: (item) => typeLabel(item.type) },
        { key: 'importance', title: '权重', render: (item) => Number(item.importance || 0).toFixed(2) },
        { key: 'createdAt', title: '创建时间', render: (item) => formatTime(item.createdAt) },
        { key: 'expiresAt', title: '过期时间', render: (item) => formatTime(item.expiresAt) },
        { key: 'actions', title: '操作', render: (item) => <button className="btn danger" onClick={() => setConfirm(item)}><Trash2 size={16} />删除</button> }
      ]} rows={memories} rowKey="memoryId" />
      {confirm && <ConfirmDialog title="删除记忆" message={`确认删除这条记忆？${truncate(confirm.content, 80)}`} danger confirmText="删除" onCancel={() => setConfirm(null)} onConfirm={deleteMemory} />}
    </>
  );
}
