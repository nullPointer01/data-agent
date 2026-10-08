import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { isDeepStrictEqual } from 'node:util';

const stages = ['VECTOR', 'BM25', 'RRF', 'RERANK'];
const metrics = ['recallAt5', 'recallAt10', 'recallAt20', 'mrrAt10', 'ndcgAt10', 'hitAt6'];
const fixedConfiguration = [
  'embeddingProvider', 'embeddingModelId', 'embeddingIndexVersion', 'embeddingDimension',
  'embeddingNormalized', 'embeddingMetric', 'vectorProvider', 'fullTextProvider',
  'rerankerProvider', 'rerankerModel', 'candidateTopK', 'topK', 'minScore', 'maxContextChars',
  'queryCompressionEnabled', 'queryExpansionEnabled', 'queryModelId', 'maxQueries', 'maxQueryChars',
];
const changedConfiguration = ['fullTextIndexName', 'fullTextAnalyzer', 'configuredChunkingVersion', 'chunkSize', 'chunkOverlap'];

function requireValue(value, message) { if (!value) throw new Error(message); }

function validate(report) {
  requireValue(report.dataset && report.configuration && Array.isArray(report.caseResults), '报告缺少数据集/配置/逐案证据');
  requireValue(report.dataset.caseCount >= 30 && report.dataset.caseCount === report.caseResults.length, '用例数不完整');
  for (const field of [...fixedConfiguration, ...changedConfiguration]) {
    requireValue(Object.hasOwn(report.configuration, field) && report.configuration[field] !== null,
      `报告缺少配置快照：${field}；旧报告不能自动视为公平对照`);
  }
  requireValue(Array.isArray(report.stageSummaries), '缺少阶段汇总');
  const summaries = new Map(report.stageSummaries.map(summary => [summary.stage, summary]));
  requireValue(summaries.size === stages.length && report.stageSummaries.length === stages.length, '阶段缺失或重复');
  for (const stage of stages) {
    const summary = summaries.get(stage);
    requireValue(summary?.caseCount === report.dataset.caseCount, `阶段用例数不完整：${stage}`);
    for (const field of metrics) requireValue(Number.isFinite(summary[field]) && summary[field] >= 0 && summary[field] <= 1, `指标无效：${stage}.${field}`);
    requireValue(Number.isFinite(summary.p95LatencyMs) && summary.p95LatencyMs >= 0, `延迟无效：${stage}`);
  }
  const cases = new Map();
  for (const item of report.caseResults) {
    requireValue(item.caseId && !cases.has(item.caseId) && item.query, '用例编号缺失/重复或问句缺失');
    requireValue(Array.isArray(item.retrievalQueries) && item.retrievalQueries.length
      && item.retrievalQueries.every(query => typeof query === 'string' && query.trim()), `缺少实际检索问句：${item.caseId}`);
    requireValue(!item.errorType && item.failureStage !== 'EXECUTION_ERROR', `存在执行错误：${item.caseId}`);
    requireValue(!item.rerankerFallback, `存在精排降级，不能作为正常模型对照：${item.caseId}`);
    cases.set(item.caseId, item);
  }
  requireValue(report.qualityGate && typeof report.qualityGate.passed === 'boolean', '缺少原有质量门禁');
  return { summaries, cases };
}

/** 仅允许索引/analyzer/分块配置变化；查询变换及模型必须固定，不生成虚构收益。 */
export function compareReports(baseline, candidate) {
  const a = validate(baseline), b = validate(candidate);
  for (const field of ['datasetId', 'corpusVersion', 'caseCount', 'sha256']) {
    requireValue(baseline.dataset[field] !== undefined && baseline.dataset[field] !== ''
      && isDeepStrictEqual(baseline.dataset[field], candidate.dataset[field]), `数据集不一致：${field}`);
  }
  for (const field of fixedConfiguration) {
    requireValue(isDeepStrictEqual(baseline.configuration[field], candidate.configuration[field]), `非目标配置发生变化：${field}`);
  }
  for (const [id, oldCase] of a.cases) {
    const newCase = b.cases.get(id);
    for (const field of ['query', 'retrievalQueries', 'tags', 'rerankerProvider', 'rerankerModel']) {
      requireValue(newCase && isDeepStrictEqual(oldCase[field], newCase[field]), `用例或查询变换不一致：${id}.${field}`);
    }
  }
  for (const field of ['recallStage', 'rerankStage', 'recallAt20Target', 'mrrAt10Target']) {
    requireValue(baseline.qualityGate[field] !== undefined
      && baseline.qualityGate[field] === candidate.qualityGate[field], `质量门禁发生变化：${field}`);
  }
  const deltas = stages.map(stage => {
    const old = a.summaries.get(stage), current = b.summaries.get(stage);
    return {
      stage, ...Object.fromEntries(metrics.map(field => [field, current[field] - old[field]])),
      p95LatencyDeltaMs: current.p95LatencyMs - old.p95LatencyMs,
      p95LatencyRatio: old.p95LatencyMs > 0 ? current.p95LatencyMs / old.p95LatencyMs : null,
    };
  });
  const recall = deltas.find(delta => delta.stage === 'RRF');
  const rerank = deltas.find(delta => delta.stage === 'RERANK');
  return {
    datasetSha256: baseline.dataset.sha256,
    configurationChanges: Object.fromEntries(changedConfiguration
      .filter(field => !isDeepStrictEqual(baseline.configuration[field], candidate.configuration[field]))
      .map(field => [field, { baseline: baseline.configuration[field], candidate: candidate.configuration[field] }])),
    deltas,
    aggregateChecks: {
      recallAt20NotRegressed: recall.recallAt20 >= 0,
      mrrAt10NotRegressed: rerank.mrrAt10 >= 0,
      p95Within20Percent: rerank.p95LatencyRatio !== null && rerank.p95LatencyRatio <= 1.2,
      originalQualityGatePassed: candidate.qualityGate.passed,
    },
    failedCaseIds: candidate.failedCaseIds,
    note: '这是单轮全组对照；中文分类收益、技术编号/英文漏召回和相同预热下至少三轮验收仍需人工核对。',
  };
}

async function main() {
  const paths = process.argv.slice(2);
  if (paths.length !== 2) throw new Error('用法：node scripts/rag-compare-reports.mjs 基线.json 改造后.json');
  const [baseline, candidate] = await Promise.all(paths.map(async path => JSON.parse(await readFile(path, 'utf8'))));
  console.log(JSON.stringify(compareReports(baseline, candidate), null, 2));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
