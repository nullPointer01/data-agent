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

  return {
    get: (url) => request('GET', url),
    post: (url, body) => request('POST', url, body),
    put: (url, body) => request('PUT', url, body),
    delete: (url) => request('DELETE', url),
    upload: (url, formData) => request('POST', url, formData, { form: true })
  };
}
