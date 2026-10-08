import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

// 教学模板与实际上传的六篇文档对应；不创建知识、不编造来源 ID。
const names = {
  REPLACE_WITH_ACCOUNT_SECURITY_ID: ['账号与设备安全', '01-account-security.md'],
  REPLACE_WITH_SUBSCRIPTION_REFUND_ID: ['订阅与退款', '02-subscription-refund.md'],
  REPLACE_WITH_OFFLINE_CONTENT_ID: ['离线内容', '03-offline-content.md'],
  REPLACE_WITH_TEAM_WORKSPACE_ID: ['团队空间', '04-team-workspace.md'],
  REPLACE_WITH_PRIVACY_EXPORT_ID: ['隐私与数据导出', '05-privacy-export.md'],
  REPLACE_WITH_ERROR_CODES_ID: ['错误码与故障处理', '06-error-codes.md'],
};

export function bindDataset(template, sources) {
  if (!Array.isArray(sources)) throw new Error('来源文件必须是 sources 数组');
  const mapping = new Map();
  const used = new Set();
  for (const [placeholder, titles] of Object.entries(names)) {
    const matches = sources.filter(source => titles.includes(source.title));
    if (matches.length !== 1) throw new Error(`文档缺失或重复：${titles.join(' / ')}`);
    const id = matches[0].sourceId;
    if (typeof id !== 'string' || !id.trim() || id.startsWith('REPLACE_WITH_') || used.has(id)) {
      throw new Error(`来源 ID 无效或重复：${titles[0]}`);
    }
    used.add(id);
    mapping.set(placeholder, id);
  }
  const result = structuredClone(template);
  if (!Array.isArray(result.cases) || result.cases.length < 30) throw new Error('黄金集至少需要 30 条人工标注问题');
  const caseIds = new Set();
  for (const item of result.cases) {
    if (!item.caseId || caseIds.has(item.caseId) || typeof item.query !== 'string' || !item.query.trim()) {
      throw new Error('用例编号重复、缺失或问句为空');
    }
    caseIds.add(item.caseId);
    // 本工具绑定文档级标注；分块变化后块级答案必须重新人工核对。
    if (item.expectedChunkIds?.length || Object.keys(item.relevanceGrades ?? {}).length) {
      throw new Error('绑定工具只处理文档级模板，块级标注请人工核对');
    }
    if (!Array.isArray(item.expectedSourceIds) || !item.expectedSourceIds.length) throw new Error('缺少文档级标注');
    item.expectedSourceIds = item.expectedSourceIds.map(id => {
      if (!mapping.has(id)) throw new Error('模板来源不是已知占位符');
      return mapping.get(id);
    });
  }
  return result;
}

export async function readSources(baseUrl, token, fetcher = fetch) {
  if (!token) throw new Error('请设置 RAG_ADMIN_TOKEN，工具不会输出 Token');
  const base = new URL(baseUrl);
  if (base.username || base.password || base.search || base.hash
      || !['http:', 'https:'].includes(base.protocol)) throw new Error('base-url 无效');
  const sources = [];
  const cursors = new Set();
  let after = '';
  for (;;) {
    const url = new URL('/api/v1/rag/indexes/sources', base);
    url.search = new URLSearchParams({ kind: 'KNOWLEDGE', limit: '50', after }).toString();
    const response = await fetcher(url, {
      headers: { Authorization: `Bearer ${token}` }, redirect: 'error', signal: AbortSignal.timeout(30_000),
    });
    if (!response.ok) throw new Error(`来源查询失败：HTTP ${response.status}`);
    const page = await response.json();
    if (!Array.isArray(page.items) || typeof page.hasMore !== 'boolean') throw new Error('来源分页响应无效');
    sources.push(...page.items);
    if (!page.hasMore) break;
    if (typeof page.nextCursor !== 'string' || !page.nextCursor || cursors.has(page.nextCursor)) {
      throw new Error('来源分页游标缺失或重复');
    }
    cursors.add(page.nextCursor);
    after = page.nextCursor;
  }
  return sources;
}

async function main() {
  const [templatePath, outputPath, mode, source] = process.argv.slice(2);
  if (!templatePath || !outputPath || !['--sources', '--base-url'].includes(mode) || !source
      || process.argv.length !== 6) {
    throw new Error('用法：node scripts/rag-golden-dataset.mjs 模板.json 输出.json --sources 来源数组.json（或 --base-url http://localhost:8080）');
  }
  const template = JSON.parse(await readFile(templatePath, 'utf8'));
  const sources = mode === '--sources' ? JSON.parse(await readFile(source, 'utf8'))
    : await readSources(source, process.env.RAG_ADMIN_TOKEN);
  const result = bindDataset(template, sources);
  // 不覆盖已有标注、模板或报告，操作结果可供人工检查后配置到应用。
  await writeFile(outputPath, `${JSON.stringify(result, null, 2)}\n`, { flag: 'wx' });
  console.log(`已绑定 ${result.cases.length} 条教学问题：${resolve(outputPath)}；尚未执行检索评测。`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
