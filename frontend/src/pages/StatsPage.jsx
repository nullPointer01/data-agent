import { useServerList } from '../hooks/useServerList.js';
import { RefreshCw } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Chart } from '../components/Chart.jsx';
import { Badge, DataTable, ListLoadError, Metric, PageHeader } from '../components/ui.jsx';
import { formatTime } from '../utils/format.js';

const ACCOUNTING_LABELS = {
  ACTUAL: ['厂商用量', 'green'], ESTIMATED: ['完整响应估算', 'blue'],
  UNKNOWN: ['待核账（保守预扣）', 'amber'], LEGACY: ['历史记录', 'gray']
};

function formatTokens(value) { return value == null ? '-' : Number(value).toLocaleString(); }

function normalizePairs(value) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((item) => {
    if (Array.isArray(item)) {
      return { name: item[0] || '未命名', value: Number(item[1] || 0) };
    }
    return { name: item.name || item.modelName || item.skillName || '未命名', value: Number(item.value || item.totalTokens || 0) };
  });
}

/** 资源统计页：展示持久化 Token 使用记录的用户、模型和时间分布。 */
export function StatsPage({ api }) {
  const [summary, setSummary] = useState({});
  const [userSummary, setUserSummary] = useState({});
  const [quota, setQuota] = useState(null);

  const [summaryError, setSummaryError] = useState('');
  const { items: records, loading, error, refresh, pagination } = useServerList({
    api, url: '/api/v1/token/usage-records', mode: 'offset'
  });
  const loadSummary = async () => {
    setSummaryError('');
    try {
      // 余额查询会限量恢复本人过期预占；先完成恢复，再读取累计记账。
      const daily = await api.get('/api/v1/token/remaining-quota');
      const [tenant, user] = await Promise.all([
        api.get('/api/v1/token/tenant-summary'), api.get('/api/v1/token/user-summary')
      ]);
      if (tenant.success === false || user.success === false || daily.success !== true) {
        throw new Error(tenant.message || user.message || daily.message || 'Token 汇总加载失败');
      }
      setSummary(tenant); setUserSummary(user);
      setQuota(daily);
    } catch (failure) { setSummaryError(failure.message || 'Token 汇总加载失败'); }
  };
  const load = () => Promise.all([refresh(), loadSummary()]);
  useEffect(() => { loadSummary(); }, [api]);

  const byModel = useMemo(() => normalizePairs(summary.byModel), [summary]);
  const bySkill = useMemo(() => normalizePairs(summary.bySkill).filter((item) => item.value > 0), [summary]);
  const knownRecords = records.filter((item) => item.accountingStatus !== 'UNKNOWN');
  const totalPrompt = knownRecords.reduce((sum, item) => sum + Number(item.promptTokens || 0), 0);
  const totalCompletion = knownRecords.reduce((sum, item) => sum + Number(item.completionTokens || 0), 0);

  const modelOption = {
    tooltip: { trigger: 'axis' },
    grid: { top: 24, right: 16, bottom: 42, left: 56 },
    xAxis: { type: 'category', data: byModel.map((item) => item.name), axisLabel: { interval: 0, rotate: 20 } },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: byModel.map((item) => item.value), itemStyle: { color: '#1e40af', borderRadius: [4, 4, 0, 0] } }]
  };
  return (
    <>
      <PageHeader title="Token 统计" desc="区分厂商用量、估算与待核账预扣；可用余额已扣除进行中预占" actions={<button className="btn" onClick={load}><RefreshCw size={16} />刷新</button>} />
      <div className="grid grid-4">
        <Metric label="租户累计记账" value={formatTokens(summary.totalTokens)} hint="包含估算与待核账预扣" />
        <Metric label="当前用户累计记账" value={formatTokens(userSummary.totalTokens)} hint="包含估算与待核账预扣" />
        <Metric label="本页输入 Token" value={totalPrompt.toLocaleString()} hint="含估算，不含待核账预扣" />
        <Metric label="本页输出 Token" value={totalCompletion.toLocaleString()} hint="含估算，不含待核账预扣" />
      </div>
      <div className="grid grid-4" style={{ marginTop: 16 }}>
        <Metric label="今日可用 Token" value={quota?.remainingTokens === -1 ? '不限额' : formatTokens(quota?.remainingTokens)} hint={quota ? `${quota.quotaDate} · ${quota.zoneId}` : ''} />
        <Metric label="今日已记账" value={formatTokens(quota?.chargedTokens)} hint="当前用户，包含待核账预扣" />
        <Metric label="进行中预占" value={formatTokens(quota?.reservedTokens)} />
        <Metric label="待核账预扣" value={formatTokens(quota?.uncertainTokens)} hint="今日已记账中的未确认部分" />
      </div>
      <div className="grid grid-2 chart-grid">
        <section className="card"><div className="section-title">模型累计记账</div><Chart option={modelOption} /></section>
        <section className="card"><div className="section-title">调用来源累计记账</div><SkillConsumption items={bySkill} /></section>
      </div>
      <ListLoadError error={summaryError} onRetry={loadSummary} />
      <ListLoadError error={error} onRetry={refresh} />
      <DataTable serverPagination={pagination} loading={loading} columns={[
        { key: 'createdAt', title: '时间', render: (item) => formatTime(item.createdAt) },
        { key: 'userId', title: '用户' },
        { key: 'modelName', title: '模型' },
        { key: 'skillName', title: '技能' },
        { key: 'accountingStatus', title: '用量依据', render: (item) => {
          const [label, tone] = ACCOUNTING_LABELS[item.accountingStatus] || ACCOUNTING_LABELS.LEGACY;
          return <Badge tone={tone}>{label}</Badge>;
        } },
        { key: 'quotaDate', title: '配额日' },
        { key: 'promptTokens', title: '输入', render: (item) => item.accountingStatus === 'UNKNOWN' ? '-' : formatTokens(item.promptTokens) },
        { key: 'completionTokens', title: '输出', render: (item) => item.accountingStatus === 'UNKNOWN' ? '-' : formatTokens(item.completionTokens) },
        { key: 'totalTokens', title: '记账合计', render: (item) => `${formatTokens(item.totalTokens)}${item.accountingStatus === 'UNKNOWN' ? '（预扣）' : ''}` }
      ]} rows={records} rowKey="id" />
    </>
  );
}

function SkillConsumption({ items }) {
  if (!items.length) {
    return <div className="chart-empty">暂无 Token 记账记录</div>;
  }
  const maxValue = Math.max(...items.map((item) => item.value));
  return (
    <div className="skill-distribution" style={{ height: 260 }}>
      {items.slice(0, 6).map((item) => (
        <div className="skill-distribution-row" key={item.name}>
          <div className="skill-distribution-head">
            <span>{item.name}</span>
            <strong>{item.value.toLocaleString()}</strong>
          </div>
          <div className="skill-distribution-track">
            <span style={{ width: `${Math.max(2, (item.value / maxValue) * 100)}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}
