import { BookOpenCheck, ChartNoAxesCombined, FileText, Sparkles } from 'lucide-react';

const KNOWLEDGE_TOOLS = new Set([
  'searchKnowledge',
  'searchMemory',
  'getMemoryStats',
  'getConversationHistory'
]);
const FILE_TOOLS = new Set(['listFiles', 'getFileContent', 'analyzeFileData']);
const DATA_TOOLS = new Set([
  'calculate',
  'getCurrentTime',
  'getDatabaseSchema',
  'listDataSources',
  'executeSql',
  'previewDataSource',
  'generateChart',
  'queryHotelOccupancy'
]);
const ASSISTANCE_TOOLS = new Set(['askUserForInfo', 'updateHotelPrice']);

export const PERSONAL_CAPABILITY_PACKS = [
  {
    id: 'knowledge',
    label: '知识与记忆',
    description: '检索你的知识库、历史对话和长期记忆。',
    icon: BookOpenCheck,
    matches: (capability) => capability.type === 'TOOL'
      && KNOWLEDGE_TOOLS.has(sourceIdFromIdentity(capability.identity))
  },
  {
    id: 'files',
    label: '文件理解',
    description: '读取上传文件，并理解其中的文本和数据。',
    icon: FileText,
    matches: (capability) => capability.type === 'TOOL'
      && FILE_TOOLS.has(sourceIdFromIdentity(capability.identity))
  },
  {
    id: 'data',
    label: '数据分析',
    description: '查询已连接的数据源、计算指标并生成图表。',
    icon: ChartNoAxesCombined,
    matches: (capability) => capability.type === 'TOOL'
      && DATA_TOOLS.has(sourceIdFromIdentity(capability.identity))
  },
  {
    id: 'skills',
    label: '专业技能',
    description: '使用已启用的专业技能，并在缺少信息时向你确认。',
    icon: Sparkles,
    matches: (capability) => capability.type === 'SKILL'
      || (capability.type === 'TOOL'
        && ASSISTANCE_TOOLS.has(sourceIdFromIdentity(capability.identity)))
  }
];

export function sourceIdFromIdentity(identity) {
  const value = typeof identity === 'string' ? identity : '';
  const separator = value.indexOf(':');
  return separator >= 0 ? value.slice(separator + 1) : value;
}

export function normalizeCapabilityBindings(values) {
  return [...new Set((Array.isArray(values) ? values : [])
    .map((value) => typeof value === 'string' ? value.trim() : '')
    .filter(Boolean))];
}

export function availablePackBindings(pack, capabilities) {
  return (Array.isArray(capabilities) ? capabilities : [])
    .filter((capability) => capability?.availability?.available === true && pack.matches(capability))
    .map((capability) => capability.identity);
}

export function activeCapabilityPacks(bindings, capabilities) {
  const selected = new Set(normalizeCapabilityBindings(bindings));
  return PERSONAL_CAPABILITY_PACKS.filter((pack) => {
    const available = availablePackBindings(pack, capabilities);
    return available.length > 0 && available.some((identity) => selected.has(identity));
  });
}

export function subAgentBindings(values) {
  return normalizeCapabilityBindings(values).filter((identity) => identity.startsWith('agent:'));
}
