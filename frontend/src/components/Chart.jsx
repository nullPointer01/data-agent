import { useMemo } from 'react';

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

const DEFAULT_COLORS = ['#1e40af', '#0f766e', '#f59e0b', '#dc2626', '#64748b'];

function normalizeSeries(option) {
  if (!option || !Array.isArray(option.series) || option.series.length === 0) {
    return [];
  }
  const palette = Array.isArray(option.color) && option.color.length ? option.color : DEFAULT_COLORS;
  return option.series.map((series, index) => ({
    name: series.name || `系列 ${index + 1}`,
    type: series.type || 'bar',
    data: Array.isArray(series.data) ? series.data : [],
    color: series.lineStyle?.color || series.itemStyle?.color || palette[index % palette.length],
    palette
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

function buildPieSlices(series, radius, centerX, centerY) {
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
    const x1 = centerX + Math.cos(start) * radius;
    const y1 = centerY + Math.sin(start) * radius;
    const x2 = centerX + Math.cos(end) * radius;
    const y2 = centerY + Math.sin(end) * radius;
    const path = [
      `M ${centerX} ${centerY}`,
      `L ${x1} ${y1}`,
      `A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2}`,
      'Z'
    ].join(' ');
    const slice = { path, color: series.palette[index % series.palette.length], ratio, value };
    start = end;
    return slice;
  });
}

function categoryLabel(value) {
  const label = String(value ?? '-');
  const dateMatch = label.match(/^\d{4}-(\d{2}-\d{2})$/);
  if (dateMatch) {
    return dateMatch[1];
  }
  return label.length > 10 ? `${label.slice(0, 9)}...` : label;
}

function axisLabel(value, maximum) {
  if (maximum < 4) {
    return Number(value.toFixed(2)).toString();
  }
  return Math.round(value).toLocaleString();
}

export function Chart({ option, height = 260 }) {
  const width = 640;
  const viewBox = `0 0 ${width} ${height}`;
  const categories = useMemo(() => normalizeCategoryData(option), [option]);
  const seriesList = useMemo(() => normalizeSeries(option), [option]);
  const barSeries = seriesList.find((series) => series.type === 'bar');
  const lineSeries = seriesList.filter((series) => series.type === 'line');
  const pieSeries = seriesList.find((series) => series.type === 'pie');
  const maxValue = Math.max(
    1,
    ...seriesList.flatMap((series) => series.data.map((item) => toNumeric(item)))
  );
  const hasLegend = lineSeries.length > 1;
  const paddingTop = hasLegend ? 48 : 16;
  const paddingLeft = 58;
  const paddingRight = 20;
  const paddingBottom = 42;
  const plotHeight = Math.max(80, height - paddingTop - paddingBottom);
  const plotWidth = width - paddingLeft - paddingRight;
  const barWidth = categories.length > 0 ? plotWidth / categories.length : plotWidth;
  const pieRadius = Math.min(width * 0.25, height * 0.32);
  const pieCenterX = width / 2;
  const pieCenterY = height / 2;
  const pieSlices = pieSeries ? buildPieSlices(pieSeries, pieRadius, pieCenterX, pieCenterY) : [];
  const labelStep = Math.max(1, Math.ceil(categories.length / 7));

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
            {hasLegend && (
              <g transform="translate(18, 16)">
                {lineSeries.map((series, index) => (
                  <g key={series.name} transform={`translate(${index * 118}, 0)`}>
                    <line x1="0" x2="18" y1="6" y2="6" stroke={series.color} strokeWidth="3" />
                    <text x="24" y="10" fontSize="11" fill="#475569">{series.name}</text>
                  </g>
                ))}
              </g>
            )}
            <g transform={`translate(${paddingLeft}, ${paddingTop})`}>
              {Array.from({ length: 5 }, (_, index) => {
                const y = (plotHeight / 4) * index;
                const label = axisLabel(maxValue - (maxValue / 4) * index, maxValue);
                return (
                  <g key={`tick-${index}`}>
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
                    {(index % labelStep === 0 || index === categories.length - 1) && (
                      <text x={x + widthValue / 2} y={plotHeight + 20} textAnchor="middle" fontSize="10" fill="#475569">
                        {categoryLabel(categories[index] || index + 1)}
                      </text>
                    )}
                  </g>
                );
              })}
              {lineSeries.map((series) => series.data.length > 0 && (
                <g key={series.name}>
                  <polyline
                    fill="none"
                    stroke={series.color}
                    strokeWidth="2.5"
                    points={series.data
                      .map((item, index) => {
                        const value = toNumeric(item);
                        const x = categories.length <= 1 ? plotWidth / 2 : (index / (categories.length - 1)) * plotWidth;
                        const y = plotHeight - ((value / maxValue) * (plotHeight - 4));
                        return `${x},${y}`;
                      })
                      .join(' ')}
                  />
                  {series.data.map((item, index) => {
                    const value = toNumeric(item);
                    const x = categories.length <= 1 ? plotWidth / 2 : (index / (categories.length - 1)) * plotWidth;
                    const y = plotHeight - ((value / maxValue) * (plotHeight - 4));
                    return <circle key={`point-${index}`} cx={x} cy={y} r="3" fill={series.color} />;
                  })}
                </g>
              ))}
              {!barSeries && categories.map((category, index) => (
                (index % labelStep === 0 || index === categories.length - 1) && (
                  <text
                    key={`${category}-${index}`}
                    x={categories.length <= 1 ? plotWidth / 2 : (index / (categories.length - 1)) * plotWidth}
                    y={plotHeight + 20}
                    textAnchor="middle"
                    fontSize="10"
                    fill="#475569"
                  >
                    {categoryLabel(category)}
                  </text>
                )
              ))}
            </g>
          </>
        )}
      </svg>
    </div>
  );
}
