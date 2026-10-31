import { useCallback, useEffect, useRef, useState } from 'react';

/** 搜索停顿后再查库，避免每输入一个字符都发起请求。 */
export function useDebouncedValue(value, delay = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

/** 服务端页码/游标列表共用请求治理；筛选和页大小改变时从首页开始。 */
export function useServerList({ api, url, filters = {}, mode = 'cursor' }) {
  const filterKey = JSON.stringify(Object.entries(filters).filter(([, value]) => value != null && value !== '')
    .sort(([left], [right]) => left.localeCompare(right)));
  const key = `${mode}:${url}:${filterKey}`;
  const [location, setLocation] = useState({ key, page: 0, size: 20, cursors: [null] });
  const current = location.key === key ? location : { key, page: 0, size: location.size, cursors: [null] };
  const { page, size } = current;
  const after = current.cursors[page] || null;
  const requestKey = JSON.stringify([key, page, size, after]);
  const activeKey = useRef(requestKey);
  activeKey.current = requestKey;
  const requestRef = useRef(0);
  const abortRef = useRef(null);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    const revision = ++requestRef.current;
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams(JSON.parse(filterKey));
      params.set('size', String(size));
      if (mode === 'offset') params.set('page', String(page));
      else if (after) params.set('after', after);
      const response = await api.get(`${url}?${params}`, { signal: controller.signal });
      if (response?.success !== true || !Array.isArray(response.items)) {
        throw new Error(response?.message || '列表加载失败，请重试');
      }
      if (revision !== requestRef.current || activeKey.current !== requestKey) return null;
      if (mode === 'offset' && response.page !== page) {
        setLocation({ key, page: response.page, size, cursors: [null] });
      } else {
        setResult({ ...response, requestKey });
      }
      return response;
    } catch (failure) {
      if (revision === requestRef.current && activeKey.current === requestKey && failure.name !== 'AbortError') {
        setResult(null);
        setError(failure.message || '列表加载失败，请重试');
      }
      return null;
    } finally {
      if (revision === requestRef.current && activeKey.current === requestKey) setLoading(false);
    }
  }, [api, url, filterKey, mode, page, size, after, key, requestKey]);

  useEffect(() => {
    load();
    return () => {
      requestRef.current += 1;
      abortRef.current?.abort();
    };
  }, [load]);

  const visible = result?.requestKey === requestKey ? result : null;
  const changePage = (nextPage) => {
    if (loading || nextPage < 0) return;
    if (mode === 'cursor') {
      if (nextPage > page && !visible?.hasMore) return;
      const cursors = current.cursors.slice(0, nextPage + 1);
      if (nextPage > page) cursors[nextPage] = visible.nextCursor;
      setLocation({ key, page: nextPage, size, cursors });
    } else {
      setLocation({ key, page: nextPage, size, cursors: [null] });
    }
  };

  const refresh = useCallback(async ({ reset = false } = {}) => {
    if (reset && page > 0) {
      setLocation({ key, page: 0, size, cursors: [null] });
      return null;
    }
    return load();
  }, [page, key, size, load]);

  return {
    items: visible?.items || [], response: visible, loading, error, refresh,
    pagination: {
      mode, page, size, total: visible?.total || 0, hasMore: visible?.hasMore === true,
      disabled: loading, onPageChange: changePage,
      onPageSizeChange: (nextSize) => setLocation({ key, page: 0, size: Number(nextSize), cursors: [null] })
    }
  };
}
