/** 带 Authorization 的 SSE 订阅；原生 EventSource 无法设置 Bearer 头，因此使用 fetch。 */
export function subscribeServerEvents(url, { token, onUnauthorized, onEvent, onState, replay = false }) {
  let stopped = false;
  let active = null;
  let retryTimer = null;
  let failures = 0;
  let cursor = '0';

  const connect = async () => {
    if (stopped) return;
    const controller = new AbortController();
    active = controller;
    let idleTimer;
    let reader;
    let denied = false;
    const resetIdle = () => {
      window.clearTimeout(idleTimer);
      idleTimer = window.setTimeout(() => controller.abort(), 45000);
    };
    onState?.('connecting');
    try {
      resetIdle();
      const target = replay ? `${url}${url.includes('?') ? '&' : '?'}after=${encodeURIComponent(cursor)}` : url;
      const response = await fetch(target, {
        headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
        signal: controller.signal,
        cache: 'no-store'
      });
      if ([400, 401, 403, 404].includes(response.status)) {
        denied = true;
        if (response.status === 401) onUnauthorized?.();
        throw new Error('事件订阅不可用或无权限');
      }
      if (!response.ok || !response.body || !response.headers.get('content-type')?.includes('text/event-stream')) {
        throw new Error('事件订阅连接失败');
      }
      reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let eventName = '';
      let eventId = '';
      let data = [];
      const line = (value) => {
        if (stopped) return;
        const content = value.endsWith('\r') ? value.slice(0, -1) : value;
        if (!content) {
          if (data.length) {
            failures = 0;
            onState?.('connected');
            if (!eventId || !replay || BigInt(eventId) > BigInt(cursor)) {
              // 处理成功后才推进游标；回调失败时重连可以重读未应用事件。
              onEvent?.({ type: eventName || 'message', ...JSON.parse(data.join('\n')) });
              if (eventId && replay) cursor = eventId;
            }
          }
          eventName = '';
          eventId = '';
          data = [];
        } else if (content.startsWith('event:')) eventName = content.slice(6).trim();
        else if (content.startsWith('id:')) {
          eventId = content.slice(3).trim();
          if (replay && !/^\d{1,19}$/.test(eventId)) throw new Error('无效事件游标');
        } else if (content.startsWith('data:')) {
          data.push(content.slice(5).replace(/^ /, ''));
          if (data.reduce((total, part) => total + part.length, 0) > (replay ? 262144 : 16384)) throw new Error('事件超过大小限制');
        }
      };
      while (!stopped) {
        const { done, value } = await reader.read();
        if (done) break;
        resetIdle();
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop();
        for (const valueLine of lines) line(valueLine);
        if (buffer.length > (replay ? 262144 : 16384)) throw new Error('事件超过大小限制');
      }
    } catch {
      if (!stopped) failures += 1;
    } finally {
      window.clearTimeout(idleTimer);
      controller.abort();
      if (reader) {
        try { await reader.cancel(); } catch { /* 断开后的 reader 可能已经关闭。 */ }
        reader.releaseLock();
      }
      if (!stopped) {
        onState?.(denied ? 'denied' : failures > 0 ? 'reconnecting' : 'connecting');
        if (!denied) {
          const delay = Math.min(30000, 1000 * (2 ** Math.min(failures, 5))) + Math.random() * 500;
          retryTimer = window.setTimeout(connect, delay);
        }
      }
    }
  };
  connect();
  return () => {
    stopped = true;
    window.clearTimeout(retryTimer);
    active?.abort();
  };
}
