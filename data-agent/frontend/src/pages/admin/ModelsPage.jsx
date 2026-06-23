import { useEffect, useState } from 'react';
import { ResourcePage } from '../../components/admin/ResourcePage.jsx';

export function ModelsPage({ api, toast }) {
  // 厂商目录由后端 ModelProviderCatalog 提供，前后端单一来源；选厂商时自动带出默认 Base URL 和模型名
  const [providerOptions, setProviderOptions] = useState([]);
  useEffect(() => {
    api.get('/api/v1/models/providers')
      .then((res) => setProviderOptions((res.providers || []).map((provider) => ({
        value: provider.key,
        label: provider.label,
        fill: { baseUrl: provider.defaultBaseUrl, modelName: provider.defaultModelName }
      }))))
      .catch(() => setProviderOptions([]));
  }, []);
  const templates = [
    { label: 'DeepSeek Chat', value: { provider: 'deepseek', baseUrl: 'https://api.deepseek.com/v1', modelName: 'deepseek-chat', temperature: 0, maxTokens: 4096 } },
    { label: 'DeepSeek Reasoner', value: { provider: 'deepseek', baseUrl: 'https://api.deepseek.com/v1', modelName: 'deepseek-reasoner', temperature: 0.6, maxTokens: 8192 } },
    { label: '通义千问 Plus', value: { provider: 'qwen', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelName: 'qwen-plus', temperature: 0, maxTokens: 4096 } },
    { label: '通义千问 Max', value: { provider: 'qwen', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelName: 'qwen-max', temperature: 0, maxTokens: 8192 } },
    { label: 'Kimi K2.6', value: { provider: 'kimi', baseUrl: 'https://api.moonshot.cn/v1', modelName: 'kimi-k2.6', temperature: 0.6, maxTokens: 8192 } },
    { label: 'Kimi Thinking', value: { provider: 'kimi', baseUrl: 'https://api.moonshot.cn/v1', modelName: 'kimi-k2-thinking', temperature: 0.6, maxTokens: 8192 } },
    { label: 'Kimi Coding', value: { provider: 'kimi', baseUrl: 'https://api.kimi.com/coding/v1', modelName: 'kimi-for-coding', temperature: 0.6, maxTokens: 8192 } },
    { label: '智谱 GLM-4-Plus', value: { provider: 'zhipu', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', modelName: 'glm-4-plus', temperature: 0, maxTokens: 4096 } },
    { label: '豆包 1.5 Pro', value: { provider: 'doubao', baseUrl: 'https://ark.cn-beijing.volces.com/api/v3', modelName: 'doubao-1-5-pro-32k-250115', temperature: 0, maxTokens: 4096 } },
    { label: '腾讯混元 Turbo', value: { provider: 'hunyuan', baseUrl: 'https://api.hunyuan.cloud.tencent.com/v1', modelName: 'hunyuan-turbo', temperature: 0, maxTokens: 4096 } },
    { label: '文心一言 4.0', value: { provider: 'ernie', baseUrl: 'https://qianfan.baidubce.com/v2', modelName: 'ernie-4.0-8k', temperature: 0, maxTokens: 4096 } },
    { label: '讯飞星火 3.5', value: { provider: 'spark', baseUrl: 'https://spark-api-open.xf-yun.com/v1', modelName: 'generalv3.5', temperature: 0, maxTokens: 4096 } },
    { label: 'MiniMax Text-01', value: { provider: 'minimax', baseUrl: 'https://api.minimaxi.com/v1', modelName: 'MiniMax-Text-01', temperature: 0, maxTokens: 4096 } },
    { label: '百川 4 Turbo', value: { provider: 'baichuan', baseUrl: 'https://api.baichuan-ai.com/v1', modelName: 'Baichuan4-Turbo', temperature: 0, maxTokens: 4096 } },
    { label: '零一万物 Yi-Large', value: { provider: 'yi', baseUrl: 'https://api.lingyiwanwu.com/v1', modelName: 'yi-large', temperature: 0, maxTokens: 4096 } },
    { label: '阶跃星辰 Step-2', value: { provider: 'stepfun', baseUrl: 'https://api.stepfun.com/v1', modelName: 'step-2-16k', temperature: 0, maxTokens: 4096 } },
    { label: '硅基流动 DeepSeek-V3', value: { provider: 'siliconflow', baseUrl: 'https://api.siliconflow.cn/v1', modelName: 'deepseek-ai/DeepSeek-V3', temperature: 0, maxTokens: 4096 } },
    { label: 'Ollama Local', value: { provider: 'ollama', baseUrl: 'http://localhost:11434/v1', modelName: 'llama3.1', temperature: 0.6, maxTokens: 4096 } }
  ];

  // 配好 API Key 后调厂商 /models 接口拉取可用模型，配置时直接选择
  const fetchAvailableModels = async (form) => {
    const res = await api.post('/api/v1/models/available', {
      modelId: form.modelId,
      provider: form.provider,
      apiKey: form.apiKey,
      baseUrl: form.baseUrl
    });
    if (!res.success) {
      throw new Error(res.message || '获取模型列表失败');
    }
    return res.models || [];
  };

  return (
    <ResourcePage
      api={api}
      toast={toast}
      title="模型管理"
      desc="配置模型供应商、API Key、Base URL、模型名和启用状态"
      templates={templates}
      listUrl="/api/v1/models/list"
      listKey="models"
      idKey="modelId"
      createUrl="/api/v1/models/add"
      updateUrl={(id) => `/api/v1/models/update/${id}`}
      toggleUrl={(id) => `/api/v1/models/toggle/${id}`}
      deleteUrl={(id) => `/api/v1/models/delete/${id}`}
      fields={[
        ['name', '名称'],
        ['provider', '供应商', 'select', providerOptions],
        ['apiKey', 'API Key', 'password'],
        ['baseUrl', 'Base URL'],
        ['modelName', '模型名', 'suggest', fetchAvailableModels],
        ['temperature', 'Temperature', 'number'],
        ['maxTokens', 'Max Tokens', 'number'],
        ['isDefault', '默认模型', 'checkbox']
      ]}
      visibleFields={['name', 'provider', 'modelName', 'baseUrl']}
    />
  );
}
