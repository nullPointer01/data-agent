async (page) => {
  await page.unroute('**/api/**');
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url();
    let body = { success: true };
    if (url.includes('/models/providers')) {
      body = {
        success: true,
        providers: [
          provider('openai', 'OpenAI / GPT', 'GLOBAL', 'https://api.openai.com/v1', 'gpt-5-mini', ['gpt-5', 'gpt-5-mini', 'gpt-4.1']),
          provider('custom', '自定义 GPT / OpenAI-compatible', 'CUSTOM', null, null, [], false),
          provider('deepseek', 'DeepSeek 深度求索', 'DOMESTIC', 'https://api.deepseek.com/v1', 'deepseek-chat', ['deepseek-chat']),
          provider('qwen', '通义千问', 'DOMESTIC', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'qwen-plus', ['qwen-plus']),
          provider('kimi', 'Kimi 月之暗面', 'DOMESTIC', 'https://api.moonshot.cn/v1', 'kimi-k2.5', ['kimi-k2.5']),
          provider('zhipu', '智谱 GLM', 'DOMESTIC', 'https://open.bigmodel.cn/api/paas/v4', 'glm-4.5', ['glm-4.5']),
          provider('siliconflow', '硅基流动', 'AGGREGATOR', 'https://api.siliconflow.cn/v1', 'deepseek-ai/DeepSeek-V3', ['deepseek-ai/DeepSeek-V3'])
        ]
      };
    }
    if (url.includes('/models/list')) {
      body = { success: true, models: [] };
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });

  await page.goto('http://127.0.0.1:5173');
  await page.evaluate(() => {
    localStorage.setItem('accessToken', 'visual-token');
    localStorage.setItem('refreshToken', 'visual-refresh');
    localStorage.setItem('userInfo', JSON.stringify({
      username: 'admin', nickname: '管理员', tenantId: 'default', roles: ['ADMIN']
    }));
  });
  await page.reload();

  function provider(key, label, group, defaultBaseUrl, defaultModelName, recommendedModels, apiKeyRequired = true) {
    return {
      key,
      label,
      group,
      protocol: 'OPENAI_COMPATIBLE',
      defaultBaseUrl,
      defaultModelName,
      recommendedModels,
      apiKeyRequired,
      modelDiscoverySupported: true
    };
  }
}
