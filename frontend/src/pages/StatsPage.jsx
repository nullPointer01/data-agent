import { RefreshCw } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Chart } from '../components/Chart.jsx';
import { DataTable, Metric, PageHeader } from '../components/ui.jsx';
import { formatTime } from '../utils/format.js';

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

export function StatsPage({ api }) {
  const [summary, setSummary] = useState({});
  const [records, setRecords] = useState([]);
  const [userSummary, setUserSummary] = useState({});
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const [tenant, user, usage] = await Promise.all([
        api.get('/api/v1/token/tenant-summary'),
        api.get('/api/v1/token/user-summary'),
        api.get('/api/v1/token/usage-records?page=0&size=500')
      ]);
      setSummary(tenant);
      setUserSummary(user);
      setRecords(usage.records || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const byModel = useMemo(() => normalizePairs(summary.byModel), [summary]);
  const bySkill = useMemo(() => normalizePairs(summary.bySkill).filter((item) => item.value > 0), [summary]);
  const totalPrompt = records.reduce((sum, item) => sum + Number(item.promptTokens || 0), 0);
  const totalCompletion = records.reduce((sum, item) => sum + Number(item.completionTokens || 0), 0);

  const modelOption = {
    tooltip: { trigger: 'axis' },
    grid: { top: 24, right: 16, bottom: 42, left: 56 },
    xAxis: { type: 'category', data: byModel.map((item) => item.name), axisLabel: { interval: 0, rotate: 20 } },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: byModel.map((item) => item.value), itemStyle: { color: '#1e40af', borderRadius: [4, 4, 0, 0] } }]
  };
  return (
    <>
      <PageHeader title="Token 统计" desc="按租户、模型、技能和请求明细查看 Token 消耗" actions={<button className="btn" onClick={load}><RefreshCw size={16} />刷新</button>} />
      <div className="grid grid-4">
        <Metric label="租户总消耗" value={(summary.totalTokens || 0).toLocaleString()} />
        <Metric label="当前用户消耗" value={(userSummary.totalTokens || 0).toLocaleString()} />
        <Metric label="输入 Token" value={totalPrompt.toLocaleString()} />
        <Metric label="输出 Token" value={totalCompletion.toLocaleString()} />
      </div>
      <div className="grid grid-2 chart-grid">
        <section className="card"><div className="section-title">模型消耗</div><Chart option={modelOption} /></section>
        <section className="card"><div className="section-title">技能消耗</div><SkillConsumption items={bySkill} /></section>
      </div>
      <DataTable loading={loading} columns={[
        { key: 'createdAt', title: '时间', render: (item) => formatTime(item.createdAt) },
        { key: 'userId', title: '用户' },
        { key: 'modelName', title: '模型' },
        { key: 'skillName', title: '技能' },
        { key: 'promptTokens', title: '输入' },
        { key: 'completionTokens', title: '输出' },
        { key: 'totalTokens', title: '合计' }
      ]} rows={records} rowKey="id" />
    </>
  );
}

function SkillConsumption({ items }) {
  if (!items.length) {
    return <div className="chart-empty">暂无技能 Token 消耗</div>;
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
