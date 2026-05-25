import { ResourcePage } from '../../components/admin/ResourcePage.jsx';

export function ModelsPage({ api, toast }) {
  const providerOptions = [
    { value: 'openai', label: 'OpenAI' },
    { value: 'deepseek', label: 'DeepSeek' },
    { value: 'qwen', label: '通义千问' },
    { value: 'kimi', label: 'Kimi' },
    { value: 'moonshot', label: 'Moonshot' },
    { value: 'ollama', label: 'Ollama' },
    { value: 'custom', label: '自定义' }
  ];
  const templates = [
    { label: 'OpenAI GPT-4o', value: { provider: 'openai', baseUrl: 'https://api.openai.com/v1', modelName: 'gpt-4o', temperature: 0.7, maxTokens: 4096 } },
    { label: 'DeepSeek Chat', value: { provider: 'deepseek', baseUrl: 'https://api.deepseek.com/v1', modelName: 'deepseek-chat', temperature: 0.7, maxTokens: 4096 } },
    { label: 'Qwen Turbo', value: { provider: 'qwen', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelName: 'qwen-turbo', temperature: 0.7, maxTokens: 4096 } },
    { label: 'Moonshot Kimi K2.6', value: { provider: 'kimi', baseUrl: 'https://api.moonshot.cn/v1', modelName: 'kimi-k2.6', temperature: 0.6, maxTokens: 8192 } },
    { label: 'Moonshot Kimi K2.5', value: { provider: 'kimi', baseUrl: 'https://api.moonshot.cn/v1', modelName: 'kimi-k2.5', temperature: 0.7, maxTokens: 8192 } },
    { label: 'Moonshot Kimi Thinking', value: { provider: 'kimi', baseUrl: 'https://api.moonshot.cn/v1', modelName: 'kimi-k2-thinking', temperature: 0.6, maxTokens: 8192 } },
    { label: 'Kimi Coding', value: { provider: 'kimi', baseUrl: 'https://api.kimi.com/coding/v1', modelName: 'kimi-for-coding', temperature: 0.6, maxTokens: 8192 } },
    { label: 'Ollama Local', value: { provider: 'ollama', baseUrl: 'http://localhost:11434/v1', modelName: 'llama3.1', temperature: 0.6, maxTokens: 4096 } }
  ];

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
        ['modelName', '模型名'],
        ['temperature', 'Temperature', 'number'],
        ['maxTokens', 'Max Tokens', 'number'],
        ['isDefault', '默认模型', 'checkbox']
      ]}
      visibleFields={['name', 'provider', 'modelName', 'baseUrl']}
    />
  );
}
