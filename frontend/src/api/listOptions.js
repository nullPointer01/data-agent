/** 配置选择器逐页读取完整候选；复用分页 API，不能只拿第一页冒充全部选项。 */
export async function loadListOptions(api, url, filters = {}, signal) {
  const items = [];
  for (let page = 0; ; page += 1) {
    const params = new URLSearchParams({ ...filters, page: String(page), size: '100' });
    const response = await api.get(`${url}?${params}`, { signal });
    if (response.success !== true || !Array.isArray(response.items)) {
      throw new Error(response.message || '配置选项加载失败');
    }
    if (response.page !== page) throw new Error('配置清单已变化，请刷新后重试');
    if (response.size !== 100 || !Number.isSafeInteger(response.total) || response.total < 0
      || response.items.length > 100 || (!response.items.length && page * 100 < response.total)) {
      throw new Error('配置选项分页异常，请刷新后重试');
    }
    items.push(...response.items);
    if ((page + 1) * 100 >= response.total) return items;
  }
}

/** 能力候选游标在 RBAC 过滤前推进；空页不代表结束，必须检查 hasMore。 */
export async function loadCapabilityOptions(api, agentId, types = ['TOOL', 'SKILL', 'SUB_AGENT'], signal) {
  const groups = await Promise.all(types.map(async (type) => {
    const items = new Map();
    const seenCursors = new Set();
    let after = '';
    for (;;) {
      const params = new URLSearchParams({ type, size: '100' });
      if (agentId) params.set('agentId', agentId);
      if (after) params.set('after', after);
      const response = await api.get(`/api/v1/my/capabilities?${params}`, { signal });
      if (response.success !== true || !Array.isArray(response.items)) {
        throw new Error(response.message || '能力目录加载失败');
      }
      response.items.forEach((item) => items.set(item.identity, item));
      if (response.hasMore === false) return [...items.values()];
      if (response.hasMore !== true || !response.nextCursor || seenCursors.has(response.nextCursor)) {
        throw new Error('能力目录分页异常，请刷新后重试');
      }
      after = response.nextCursor;
      seenCursors.add(after);
    }
  }));
  return groups.flat();
}

/** 设置页拿齐模型与能力后才允许保存；单页失败不能退化为残缺配置。 */
export async function loadAgentConfigurationOptions(api, agentId, types, signal) {
  const [models, capabilities] = await Promise.all([
    loadListOptions(api, '/api/v1/my/models', {}, signal),
    loadCapabilityOptions(api, agentId, types, signal)
  ]);
  return { models, capabilities };
}
