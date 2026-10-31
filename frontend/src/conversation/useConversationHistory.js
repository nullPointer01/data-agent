import { useCallback, useRef, useState } from 'react';

export const SESSION_PREVIEW_LIMIT = 5;

function assertSuccess(result, fallback) {
  if (result?.success === false) throw new Error(result.message || fallback);
  return result;
}

/** 会话生命周期和历史分页独立于 SSE；旧请求不能覆盖刚切换的会话。 */
export function useConversationHistory({ api, toast, processing, hydrateMessages }) {
  const [sessions, setSessions] = useState([]);
  const [sessionId, updateSessionId] = useState(localStorage.getItem('chatSessionId') || '');
  const [messages, setMessages] = useState([]);
  const [sessionStatus, setSessionStatus] = useState('ACTIVE');
  const [sessionView, setSessionView] = useState('ACTIVE');
  const [sessionsExpanded, setSessionsExpanded] = useState(false);
  const [nextSessionCursor, setNextSessionCursor] = useState(null);
  const [nextBeforeId, setNextBeforeId] = useState(null);
  const [loadingMoreSessions, setLoadingMoreSessions] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [loadingEarlier, setLoadingEarlier] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [mutating, setMutating] = useState(false);
  const selectedRef = useRef(sessionId);
  const viewRef = useRef('ACTIVE');
  const listRequestRef = useRef(0);
  const messageRequestRef = useRef(0);
  const messagesRef = useRef(messages);
  messagesRef.current = messages;

  const rememberSession = useCallback((sid) => {
    if (selectedRef.current !== sid) {
      messageRequestRef.current += 1;
      setLoadingMessages(false);
      setLoadingEarlier(false);
      setHistoryError('');
    }
    selectedRef.current = sid;
    updateSessionId(sid);
    if (sid) localStorage.setItem('chatSessionId', sid);
    else localStorage.removeItem('chatSessionId');
  }, []);

  const clearSelection = useCallback(() => {
    rememberSession('');
    setMessages([]);
    setNextBeforeId(null);
    setSessionStatus('ACTIVE');
  }, [rememberSession]);

  const loadSessions = useCallback(async ({ status = viewRef.current, cursor = null, append = false } = {}) => {
    const request = ++listRequestRef.current;
    const params = new URLSearchParams({ status, limit: String(append ? 20 : SESSION_PREVIEW_LIMIT) });
    if (cursor) params.set('cursor', cursor);
    const result = assertSuccess(await api.get(`/api/v1/analysis/sessions?${params}`), '会话加载失败');
    const rows = result.sessions || [];
    if (request === listRequestRef.current && status === viewRef.current) {
      setSessions((current) => append
        ? [...new Map([...current, ...rows].map((item) => [item.sessionId, item])).values()]
        : rows);
      setNextSessionCursor(result.nextCursor || null);
    }
    return rows;
  }, [api]);

  const loadMessages = useCallback(async (sid, { beforeId = null, refresh = false, beforePrepend } = {}) => {
    if (!sid) return;
    const request = ++messageRequestRef.current;
    if (!beforeId && !refresh) setLoadingMessages(true);
    try {
      const params = new URLSearchParams({ limit: '50' });
      if (beforeId) params.set('beforeId', String(beforeId));
      const result = assertSuccess(await api.get(`/api/v1/analysis/session/${encodeURIComponent(sid)}/messages?${params}`), '消息加载失败');
      const loaded = await hydrateMessages(api, result.messages || [], messagesRef.current);
      if (request !== messageRequestRef.current || selectedRef.current !== sid) return;
      if (beforeId) beforePrepend?.();
      setMessages((current) => {
        if (!beforeId && !refresh) return loaded;
        // 刷新审批结果时保留已展开的旧历史；无 ID 的临时流消息由落库消息替换。
        const persisted = [...new Map([...current.filter((item) => item.id), ...loaded].map((item) => [item.id, item])).values()]
          .sort((left, right) => left.id - right.id);
        return beforeId ? [...persisted, ...current.filter((item) => !item.id)] : persisted;
      });
      if (!refresh) setNextBeforeId(result.nextBeforeId || null);
      setSessionStatus(result.sessionStatus || 'ACTIVE');
      if (!beforeId && !refresh) setHistoryError('');
    } catch (error) {
      if (!beforeId && !refresh && request === messageRequestRef.current && selectedRef.current === sid) {
        setHistoryError(error.message || '消息加载失败');
      }
      throw error;
    } finally {
      if (request === messageRequestRef.current) setLoadingMessages(false);
    }
  }, [api, hydrateMessages]);

  const bootHistory = useCallback(async () => {
    const rows = await loadSessions();
    const stored = localStorage.getItem('chatSessionId') || '';
    let selected = rows.find((item) => item.sessionId === stored);
    if (stored && !selected) {
      // 选中的旧会话可能不在首屏，不能因此丢掉登录前的位置。
      selected = await api.get(`/api/v1/analysis/session/${encodeURIComponent(stored)}`).catch(() => null);
      if (!selected?.sessionId || !['ACTIVE', 'ARCHIVED'].includes(selected.status)) selected = null;
    }
    selected ||= rows[0];
    if (!selected) { clearSelection(); return; }
    if (selected.status !== viewRef.current) {
      viewRef.current = selected.status;
      setSessionView(selected.status);
      await loadSessions({ status: selected.status });
    }
    setSessions((current) => current.some((item) => item.sessionId === selected.sessionId) ? current : [...current, selected]);
    rememberSession(selected.sessionId);
    await loadMessages(selected.sessionId);
  }, [api, clearSelection, loadMessages, loadSessions, rememberSession]);

  const switchSession = async (sid) => {
    if (processing || mutating) return;
    rememberSession(sid);
    setMessages([]);
    setNextBeforeId(null);
    try { await loadMessages(sid); }
    catch (error) { toast(error.message || '消息加载失败', 'error'); }
  };

  const changeView = async (status) => {
    if (processing || mutating || loadingMoreSessions) return;
    viewRef.current = status;
    setSessionView(status);
    setSessionsExpanded(false);
    setSessions([]);
    setNextSessionCursor(null);
    setLoadingMoreSessions(true);
    try { await loadSessions({ status }); }
    catch (error) { toast(error.message || '会话加载失败', 'error'); }
    finally { setLoadingMoreSessions(false); }
  };

  const loadMoreSessions = async () => {
    if (loadingMoreSessions || mutating) return;
    if (!sessionsExpanded && sessions.length > SESSION_PREVIEW_LIMIT) { setSessionsExpanded(true); return; }
    if (!nextSessionCursor) return;
    setLoadingMoreSessions(true);
    try {
      await loadSessions({ cursor: nextSessionCursor, append: true });
      setSessionsExpanded(true);
    } catch (error) { toast(error.message || '会话加载失败', 'error'); }
    finally { setLoadingMoreSessions(false); }
  };

  const loadEarlier = async (beforePrepend) => {
    if (!sessionId || !nextBeforeId || processing || loadingMessages || loadingEarlier) return;
    setLoadingEarlier(true);
    try { await loadMessages(sessionId, { beforeId: nextBeforeId, beforePrepend }); }
    catch (error) { toast(error.message || '历史消息加载失败', 'error'); }
    finally { setLoadingEarlier(false); }
  };

  const mutateSession = async (sid, action) => {
    if (processing || mutating) return;
    setMutating(true);
    try {
      const path = `/api/v1/analysis/session/${encodeURIComponent(sid)}`;
      assertSuccess(action === 'delete' ? await api.delete(path) : await api.post(`${path}/${action}`, {}), '操作失败');
      if (sid === selectedRef.current) {
        if (action === 'delete' || action === 'archive') clearSelection();
        else setSessionStatus('ACTIVE');
      }
      const labels = { delete: '会话已删除', archive: '会话已归档', restore: '会话已恢复' };
      toast(labels[action], 'success');
      if (action === 'restore') {
        viewRef.current = 'ACTIVE';
        setSessionView('ACTIVE');
      }
      setSessionsExpanded(false);
      await loadSessions();
    } catch (error) { toast(error.message || '操作失败，请重试', 'error'); }
    finally { setMutating(false); }
  };

  return {
    sessions, setSessions, sessionId, rememberSession, messages, setMessages, sessionStatus, sessionView,
    sessionsExpanded, setSessionsExpanded, nextSessionCursor, nextBeforeId,
    loadingMoreSessions, loadingMessages, loadingEarlier, historyError, mutating,
    loadSessions, loadMessages, bootHistory, clearSelection, switchSession, changeView,
    loadMoreSessions, loadEarlier, mutateSession
  };
}
