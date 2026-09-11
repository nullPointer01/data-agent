import { Activity, RefreshCw } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Chart } from '../components/Chart.jsx';
import { Badge, DataTable, EmptyState, Metric, PageHeader } from '../components/ui.jsx';

function percent(value) {
  return `${Math.round(Number(value || 0) * 100)}%`;
}

function percentValue(value) {
  return Math.round(Number(value || 0) * 100);
}

function scoreTone(score) {
  if (score >= 90) {
    return 'green';
  }
  if (score >= 75) {
    return 'blue';
  }
  if (score >= 60) {
    return 'amber';
  }
  return 'red';
}

function severityTone(severity) {
  if (severity === 'HIGH') {
    return 'red';
  }
  if (severity === 'MEDIUM') {
    return 'amber';
  }
  return 'gray';
}

function riskTone(code) {
  if (code === 'REACT_ITERATION_LIMIT' || code === 'DEPENDENCY_BLOCKED') {
    return 'red';
  }
  return 'gray';
}

function levelText(level) {
  const names = {
    EXCELLENT: '优秀',
    HEALTHY: '健康',
    WATCH: '观察',
    RISK: '风险'
  };
  return names[level] || level || '-';
}

export function QualityPage({ api, toast }) {
  const [dashboard, setDashboard] = useState({});
  const [reasoningHealth, setReasoningHealth] = useState(null);
  const [loading, setLoading] = useState(true);
  const [limit, setLimit] = useState('200');

  const load = async () => {
    setLoading(true);
    try {
      const response = await api.get(`/api/v1/agent-quality/dashboard?limit=${encodeURIComponent(limit)}`);
      const health = await api.get(`/api/v1/agent-quality/reasoning-health?limit=${encodeURIComponent(limit)}`)
        .catch(() => null);
      if (response.success === false) {
        toast?.(response.message || '质量看板加载失败', 'error');
        return;
      }
      setDashboard(response);
      setReasoningHealth(health);
    } catch (error) {
      toast?.(error.message || '质量看板加载失败', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [limit]);

  const risks = Array.isArray(dashboard.risks) ? dashboard.risks : [];
  const attributions = Array.isArray(dashboard.attributions) ? dashboard.attributions : [];
  const recommendations = Array.isArray(dashboard.recommendations) ? dashboard.recommendations : [];
  const agentStats = Array.isArray(dashboard.agentStats) ? dashboard.agentStats : [];
  const trendPoints = Array.isArray(dashboard.trendPoints) ? dashboard.trendPoints : [];
  const score = Number(dashboard.qualityScore || 0);
  const scoreLabel = useMemo(() => `${score} / 100`, [score]);
  const dependencyBlockedRisk = useMemo(
    () => risks.find((item) => item.riskCode === 'DEPENDENCY_BLOCKED'),
    [risks]
  );
  const trendOption = useMemo(() => {
    if (!trendPoints.length) {
      return null;
    }
    const dates = trendPoints.map((item) => item.date || '-');
    return {
      color: ['#1e40af', '#0f766e', '#dc2626', '#64748b'],
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#ffffff',
        borderColor: '#dbe4f0',
        textStyle: { color: '#0f172a' },
        formatter(params) {
          const items = Array.isArray(params) ? params : [params];
          const axisLabel = items[0]?.axisValue || '-';
          const rows = items.map((item) => {
            const suffix = item.seriesName === '质量分' || item.seriesName === '轨迹数' ? '' : '%';
            return `<div style="display:flex;justify-content:space-between;gap:12px;min-width:160px;">
              <span style="color:${item.color}">${item.marker || ''}${item.seriesName}</span>
              <strong>${item.value}${suffix}</strong>
            </div>`;
          }).join('');
          return `<div style="min-width:220px">
            <div style="font-weight:700;margin-bottom:8px;">${axisLabel}</div>
            ${rows}
          </div>`;
        }
      },
      legend: {
        top: 0,
        left: 0,
        icon: 'roundRect',
        textStyle: { color: '#475569' },
        data: ['质量分', '成功率', '回退率', '轨迹数']
      },
      grid: { top: 54, right: 56, bottom: 32, left: 56 },
      xAxis: {
        type: 'category',
        boundaryGap: true,
        data: dates,
        axisLabel: { color: '#64748b' },
        axisLine: { lineStyle: { color: '#cbd5e1' } }
      },
      yAxis: [
        {
          type: 'value',
          min: 0,
          max: 100,
          axisLabel: { color: '#64748b' },
          splitLine: { lineStyle: { color: '#e2e8f0' } }
        },
        {
          type: 'value',
          min: 0,
          axisLabel: { color: '#64748b' },
          splitLine: { show: false }
        }
      ],
      series: [
        {
          name: '质量分',
          type: 'line',
          yAxisIndex: 0,
          smooth: true,
          showSymbol: false,
          data: trendPoints.map((item) => Number(item.qualityScore || 0)),
          lineStyle: { width: 3, color: '#1e40af' },
          itemStyle: { color: '#1e40af' }
        },
        {
          name: '成功率',
          type: 'line',
          yAxisIndex: 0,
          smooth: true,
          showSymbol: false,
          data: trendPoints.map((item) => percentValue(item.successRate)),
          lineStyle: { width: 2, color: '#0f766e' },
          itemStyle: { color: '#0f766e' }
        },
        {
          name: '回退率',
          type: 'line',
          yAxisIndex: 0,
          smooth: true,
          showSymbol: false,
          data: trendPoints.map((item) => percentValue(item.fallbackRate)),
          lineStyle: { width: 2, color: '#dc2626' },
          itemStyle: { color: '#dc2626' }
        },
        {
          name: '轨迹数',
          type: 'bar',
          yAxisIndex: 1,
          data: trendPoints.map((item) => Number(item.traceCount || 0)),
          itemStyle: { color: 'rgba(30, 64, 175, 0.16)', borderRadius: [4, 4, 0, 0] }
        }
      ]
    };
  }, [trendPoints]);

  return (
    <>
      <PageHeader
        title="Agent 质量"
        desc="聚合执行轨迹、回退率和耗时，评估 Agent 在当前租户下的运行质量"
        actions={<><select className="select compact-select" value={limit} onChange={(event) => setLimit(event.target.value)}><option value="100">最近 100 条</option><option value="200">最近 200 条</option><option value="500">最近 500 条</option></select><button className="btn" onClick={load}><RefreshCw size={16} />刷新</button></>}
      />
      {dependencyBlockedRisk && (
        <section className="quality-alert">
          <div>
            <Badge tone="red">依赖阻断</Badge>
            <strong>最近有下游任务因上游失败被跳过</strong>
          </div>
          <p>{dependencyBlockedRisk.message}</p>
        </section>
      )}
      <div className="grid grid-4">
        <Metric label="质量分" value={scoreLabel} hint={levelText(dashboard.qualityLevel)} />
        <Metric label="执行成功率" value={percent(dashboard.successRate)} hint={`${dashboard.sampleSize || 0} 条样本`} />
        <Metric label="回退率" value={percent(dashboard.fallbackRate)} hint="越低越稳定" />
        <Metric label="平均耗时" value={`${dashboard.averageDurationMs || 0} ms`} hint="最近样本平均值" />
      </div>
      {reasoningHealth && <ReasoningHealthPanel health={reasoningHealth} />}
      <section className="grid grid-2 chart-grid quality-trend-section">
        <div className="card quality-trend-card">
          <div className="section-title">质量趋势</div>
          {trendPoints.length && trendOption
            ? <Chart option={trendOption} height={320} />
            : <EmptyState title="暂无趋势" desc="积累多天执行轨迹后，这里会展示质量趋势。" />}
        </div>
        <div className="card quality-trend-card">
          <div className="section-title">最近趋势摘要</div>
          {trendPoints.length ? (
            <div className="quality-trend-list">
              {trendPoints.slice(-7).reverse().map((item) => (
                <div className="quality-trend-item" key={item.date}>
                  <div className="quality-trend-head">
                    <strong>{item.date}</strong>
                    <Badge tone={scoreTone(item.qualityScore)}>{item.qualityScore}</Badge>
                  </div>
                  <div className="quality-trend-meta">
                    <span>成功 {percent(item.successRate)}</span>
                    <span>回退 {percent(item.fallbackRate)}</span>
                  </div>
                  <div className="quality-trend-meta">
                    <span>轨迹 {item.traceCount || 0}</span>
                    <span>耗时 {item.averageDurationMs || 0} ms</span>
                  </div>
                </div>
              ))}
            </div>
          ) : <EmptyState title="暂无趋势摘要" desc="当前没有足够的日级样本。" />}
        </div>
      </section>
      <section className="quality-insights">
        <div className="card">
          <div className="quality-score-head">
            <div>
              <div className="section-title">质量状态</div>
              <p>综合成功率、耗时和回退情况生成当前评分。</p>
            </div>
            <Badge tone={scoreTone(score)}>{levelText(dashboard.qualityLevel)}</Badge>
          </div>
          <div className="quality-score-ring">
            <Activity size={22} />
            <strong>{scoreLabel}</strong>
            <span>最近轨迹 {dashboard.sampleSize || 0} 条</span>
          </div>
          <div className="quality-meter large"><span style={{ width: `${Math.min(100, Math.max(0, score))}%` }} /></div>
        </div>
        <div className="card">
          <div className="section-title">优化建议</div>
          <div className="quality-recommendations">
            {recommendations.length ? recommendations.map((item, index) => (
              <div className="quality-recommendation" key={`${item}-${index}`}>{item}</div>
            )) : <EmptyState title="暂无建议" desc="当前质量指标未触发优化建议。" />}
          </div>
        </div>
      </section>
      <section className="card quality-risk-card">
        <div className="section-title">风险项</div>
        <div className="quality-issue-list">
          {risks.length ? risks.map((risk) => <QualityRisk risk={risk} key={risk.riskCode} />)
            : <EmptyState title="暂无风险" desc="最近样本中的核心质量指标处于稳定区间。" />}
        </div>
      </section>
      <section className="card quality-risk-card">
        <div className="section-title">问题归因</div>
        <div className="quality-issue-list">
          {attributions.length ? attributions.map((item) => <QualityAttribution item={item} key={item.causeCode} />)
            : <EmptyState title="暂无归因" desc="最近样本没有失败、回退或明显慢请求。" />}
        </div>
      </section>
      <DataTable loading={loading} empty={<EmptyState title="暂无 Agent 统计" desc="产生执行轨迹后，这里会按 Agent 展示质量分组。" />} columns={[
        { key: 'agentName', title: 'Agent', render: (item) => <><strong>{item.agentName}</strong><div className="muted">{item.agentType}</div></> },
        { key: 'qualityScore', title: '质量分', render: (item) => <Badge tone={scoreTone(item.qualityScore)}>{item.qualityScore}</Badge> },
        { key: 'traceCount', title: '轨迹' },
        { key: 'successRate', title: '成功率', render: (item) => percent(item.successRate) },
        { key: 'fallbackRate', title: '回退率', render: (item) => percent(item.fallbackRate) },
        { key: 'averageDurationMs', title: '平均耗时', render: (item) => `${item.averageDurationMs || 0} ms` }
      ]} rows={agentStats} rowKey="agentKey" />
    </>
  );
}

function ReasoningHealthPanel({ health }) {
  const checks = Array.isArray(health.acceptanceChecks) ? health.acceptanceChecks : [];
  const statusTone = health.accepted ? 'green' : health.healthy ? 'amber' : 'red';
  return (
    <section className="rag-health-panel reasoning-health-panel">
      <div className="rag-health-head">
        <div>
          <strong>增强推理验收</strong>
          <p>{health.accepted ? 'Phase 3 推理能力验收已通过。' : '仍有推理能力需要真实执行样本或配置确认。'}</p>
        </div>
        <div className="toolbar">
          <Badge tone={statusTone}>{health.accepted ? '已验收' : health.healthy ? '待样本' : '有失败项'}</Badge>
          <Badge tone={health.fastPathEnabled ? 'green' : 'red'}>快速路径</Badge>
          <Badge tone={health.planningEnabled ? 'green' : 'red'}>规划</Badge>
          <Badge tone={health.reflectionEnabled ? 'green' : 'red'}>反思</Badge>
        </div>
      </div>
      <div className="trace-metrics-grid">
        <ReasoningMetric label="轨迹样本" value={health.sampleSize || 0} hint="最近样本" />
        <ReasoningMetric label="简单样本" value={health.simpleSampleSize || 0} hint={`${health.averageSimpleDurationMs || 0} ms`} />
        <ReasoningMetric label="复杂样本" value={health.complexSampleSize || 0} hint={`${health.averageComplexDurationMs || 0} ms`} />
        <ReasoningMetric label="恢复样本" value={`${health.retryRecoveredCount || 0}/${health.retryCandidateCount || 0}`} hint={percent(health.retryRecoveryRate)} />
        <ReasoningMetric label="并行预检" value={health.parallelPrecheckEnabled ? '启用' : '关闭'} hint="配置状态" />
        <ReasoningMetric label="工作记忆" value={health.workingMemoryEnabled ? '启用' : '关闭'} hint="配置状态" />
      </div>
      <div className="rag-check-grid">
        {checks.map((check) => (
          <div className={`rag-check ${reasoningCheckClass(check.status)}`} key={check.key}>
            <Badge tone={reasoningStatusTone(check.status)}>{reasoningStatusText(check.status)}</Badge>
            <strong>{check.name}</strong>
            <span>{check.detail}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

function ReasoningMetric({ label, value, hint }) {
  return (
    <div className="trace-metric">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{hint}</small>
    </div>
  );
}

function reasoningCheckClass(status) {
  if (status === 'PASSED') {
    return 'passed';
  }
  if (status === 'FAILED') {
    return 'failed';
  }
  return 'pending';
}

function reasoningStatusTone(status) {
  if (status === 'PASSED') {
    return 'green';
  }
  if (status === 'FAILED') {
    return 'red';
  }
  return 'amber';
}

function reasoningStatusText(status) {
  if (status === 'PASSED') {
    return '通过';
  }
  if (status === 'FAILED') {
    return '失败';
  }
  return '待验收';
}

function QualityAttribution({ item }) {
  const blocked = item.causeCode === 'DEPENDENCY_BLOCKED';
  return (
    <div className={`quality-issue ${blocked ? 'blocked' : ''}`}>
      <div className="quality-issue-header">
        <strong>{item.causeName || item.causeCode}</strong>
        <div className="toolbar">
          <Badge tone={severityTone(item.severity)}>{item.severity || 'LOW'}</Badge>
          <Badge tone={riskTone(item.causeCode)}>{item.causeCode || '-'}</Badge>
          {blocked && <Badge tone="amber">依赖阻断</Badge>}
          <Badge tone="gray">{item.count || 0} 条</Badge>
        </div>
      </div>
      <div className="quality-meter"><span style={{ width: percent(item.ratio) }} /></div>
      <p>{item.recommendation}</p>
      <div className="quality-samples">
        <span>{item.sampleTraceId || '-'}</span>
        <span>{item.sampleQuestion || '暂无样本问题'}</span>
      </div>
    </div>
  );
}

function QualityRisk({ risk }) {
  const blocked = risk.riskCode === 'DEPENDENCY_BLOCKED';
  return (
    <div className={`quality-issue ${blocked ? 'blocked' : ''}`}>
      <div className="quality-issue-header">
        <strong>{risk.riskName || risk.riskCode}</strong>
        <div className="toolbar">
          <Badge tone={severityTone(risk.severity)}>{risk.severity || 'LOW'}</Badge>
          <Badge tone={riskTone(risk.riskCode)}>{risk.riskCode || '-'}</Badge>
          {blocked && <Badge tone="amber">依赖阻断</Badge>}
        </div>
      </div>
      <p>{risk.message}</p>
      <div className="quality-samples"><span>{risk.evidence || '-'}</span></div>
    </div>
  );
}
