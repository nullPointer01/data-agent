import { useMemo } from 'react';

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function normalizeSeries(option) {
  if (!option || !Array.isArray(option.series) || option.series.length === 0) {
    return [];
  }
  return option.series.map((series, index) => ({
    name: series.name || `系列 ${index + 1}`,
    type: series.type || 'bar',
    data: Array.isArray(series.data) ? series.data : [],
    color: series.lineStyle?.color || series.itemStyle?.color || ['#1e40af', '#0f766e', '#f59e0b', '#dc2626'][index % 4]
  }));
}

function normalizeCategoryData(option) {
  return Array.isArray(option?.xAxis?.data) ? option.xAxis.data : [];
}

function toNumeric(value) {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : 0;
  }
  if (typeof value === 'string') {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  if (value && typeof value === 'object') {
    return toNumeric(value.value);
  }
  return 0;
}

function buildPieSlices(series, size, radius, center) {
  const values = series.data.map((item) => toNumeric(item));
  const total = values.reduce((sum, item) => sum + item, 0);
  if (total <= 0) {
    return [];
  }
  let start = -Math.PI / 2;
  return values.map((value, index) => {
    const ratio = value / total;
    const sweep = ratio * Math.PI * 2;
    const end = start + sweep;
    const largeArc = sweep > Math.PI ? 1 : 0;
    const x1 = center + Math.cos(start) * radius;
    const y1 = center + Math.sin(start) * radius;
    const x2 = center + Math.cos(end) * radius;
    const y2 = center + Math.sin(end) * radius;
    const path = [
      `M ${center} ${center}`,
      `L ${x1} ${y1}`,
      `A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2}`,
      'Z'
    ].join(' ');
    const slice = { path, color: series.color || '#1e40af', ratio, value };
    start = end;
    return { ...slice, color: Array.isArray(series.color) ? series.color[index % series.color.length] : slice.color };
  });
}

export function Chart({ option, height = 260 }) {
  const width = 420;
  const viewBox = `0 0 ${width} ${height}`;
  const categories = useMemo(() => normalizeCategoryData(option), [option]);
  const seriesList = useMemo(() => normalizeSeries(option), [option]);
  const barSeries = seriesList.find((series) => series.type === 'bar');
  const lineSeries = seriesList.find((series) => series.type === 'line');
  const pieSeries = seriesList.find((series) => series.type === 'pie');
  const maxValue = Math.max(
    1,
    ...seriesList.flatMap((series) => series.data.map((item) => toNumeric(item)))
  );
  const chartHeight = height - 40;
  const chartWidth = width - 48;
  const paddingLeft = 28;
  const paddingBottom = 30;
  const plotHeight = chartHeight - paddingBottom;
  const plotWidth = chartWidth - paddingLeft;
  const barWidth = categories.length > 0 ? plotWidth / categories.length : plotWidth;
  const pieRadius = Math.min(width, height) * 0.28;
  const pieCenter = Math.min(width, height) / 2;
  const pieSlices = pieSeries ? buildPieSlices(pieSeries, width, pieRadius, pieCenter) : [];

  return (
    <div className="chart" style={{ height }}>
      <svg viewBox={viewBox} role="img" aria-label="图表" className="chart-svg">
        {pieSeries ? (
          <>
            {pieSlices.map((slice, index) => (
              <path key={`${slice.value}-${index}`} d={slice.path} fill={slice.color} opacity="0.95" />
            ))}
          </>
        ) : (
          <>
            <g transform={`translate(${paddingLeft}, 12)`}>
              {Array.from({ length: 5 }, (_, index) => {
                const y = (plotHeight / 4) * index;
                const label = Math.round(maxValue - (maxValue / 4) * index);
                return (
                  <g key={label}>
                    <line x1="0" x2={plotWidth} y1={y} y2={y} stroke="#e2e8f0" strokeWidth="1" />
                    <text x="-8" y={y + 4} textAnchor="end" fontSize="10" fill="#64748b">{label}</text>
                  </g>
                );
              })}
              {barSeries && barSeries.data.map((item, index) => {
                const value = toNumeric(item);
                const barHeight = clamp((value / maxValue) * (plotHeight - 4), 0, plotHeight - 4);
                const x = index * barWidth + barWidth * 0.15;
                const y = plotHeight - barHeight;
                const widthValue = barWidth * 0.7;
                return (
                  <g key={`${categories[index] || index}-${index}`}>
                    <rect x={x} y={y} width={widthValue} height={barHeight} rx="4" fill={barSeries.color} />
                    <text x={x + widthValue / 2} y={plotHeight + 16} textAnchor="middle" fontSize="10" fill="#475569">
                      {categories[index] || index + 1}
                    </text>
                  </g>
                );
              })}
              {lineSeries && lineSeries.data.length > 0 && (
                <>
                  <polyline
                    fill="none"
                    stroke={lineSeries.color}
                    strokeWidth="2.5"
                    points={lineSeries.data
                      .map((item, index) => {
                        const value = toNumeric(item);
                        const x = index * barWidth + barWidth / 2;
                        const y = plotHeight - ((value / maxValue) * (plotHeight - 4));
                        return `${x},${y}`;
                      })
                      .join(' ')}
                  />
                  {lineSeries.data.map((item, index) => {
                    const value = toNumeric(item);
                    const x = index * barWidth + barWidth / 2;
                    const y = plotHeight - ((value / maxValue) * (plotHeight - 4));
                    return <circle key={`point-${index}`} cx={x} cy={y} r="3.5" fill={lineSeries.color} />;
                  })}
                </>
              )}
            </g>
          </>
        )}
      </svg>
    </div>
  );
}
