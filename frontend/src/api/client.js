import { subscribeServerEvents } from './serverEvents.js';

// 统一处理 JSON/文本响应、JWT 失效和审批接口，页面不各自复制请求细节。
async function parseResponse(response) {
  const text = await response.text();
  if (!text) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    return { success: false, message: text };
  }
}

export function createApiClient(token, onUnauthorized) {
  async function request(method, url, body, options = {}) {
    const headers = options.form ? {} : { 'Content-Type': 'application/json' };
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(url, {
      method,
      headers,
      body: options.form ? body : body == null ? undefined : JSON.stringify(body),
      signal: options.signal
    });

    if (response.status === 401) {
      onUnauthorized?.();
      throw new Error('登录已过期');
    }

    const data = await parseResponse(response);
    if (!response.ok && !data.message) {
      data.message = `请求失败：${response.status}`;
    }
    return data;
  }

  const client = {
    get: (url, options) => request('GET', url, undefined, options),
    post: (url, body) => request('POST', url, body),
    put: (url, body) => request('PUT', url, body),
    delete: (url) => request('DELETE', url),
    upload: (url, formData) => request('POST', url, formData, { form: true })
  };
  client.agentApprovals = {
    detail: (approvalId, options) => client.get(`/api/v1/agent-approvals/${encodeURIComponent(approvalId)}`, options),
    approve: (approvalId, comment) => client.post(
      `/api/v1/agent-approvals/${encodeURIComponent(approvalId)}/approve`, { comment }),
    reject: (approvalId, comment) => client.post(
      `/api/v1/agent-approvals/${encodeURIComponent(approvalId)}/reject`, { comment })
  };
  client.subscribeApprovalEvents = (sessionId, callbacks) => subscribeServerEvents(
    sessionId ? `/api/v1/my/approval-events?${new URLSearchParams({ sessionId })}` : '/api/v1/agent-approvals/events',
    { ...callbacks, token, onUnauthorized });
  client.subscribeRunEvents = (runId, callbacks) => subscribeServerEvents(
    `/api/v1/agent-runs/${encodeURIComponent(runId)}/events`, { ...callbacks, token, onUnauthorized, replay: true });
  return client;
}
