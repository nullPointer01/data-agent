import { useCallback, useEffect, useRef, useState } from 'react';
import { reduceRunEvidence } from '../agent/runEvidence.js';

/** 前端只拥有订阅：刷新从持久消息发现活动 Run，网络重连沿游标续读，停止才发送取消。 */
export function useBackgroundRun({ api, sessionId, messages, setMessages, loadMessages, loadSessions, toast, setProcessing }) {
  const [active, setActive] = useState(null);
  const [connection, setConnection] = useState('idle');
  const detached = useRef(new Set());
  const callbacks = useRef(null);
  callbacks.current = { setMessages, loadMessages, loadSessions, toast };

  useEffect(() => {
    if (active) return;
    const pending = messages.findLast((message) => message.role === 'assistant' && message.runId
      && !detached.current.has(message.runId)
      && message.runEvidence?.backgroundExecution && ['CREATED', 'RUNNING'].includes(message.runEvidence.status));
    if (pending) setActive({ runId: pending.runId, sessionId });
  }, [active, messages, sessionId]);

  useEffect(() => {
    if (!active) return undefined;
    setProcessing(true);
    let answer = '';
    let runEvidence = null;
    let citations = [];
    let knowledgeChecked = false;
    let knowledgeAvailable = true;
    let finished = false;
    let stop;
    const finish = () => {
      if (finished) return;
      finished = true;
      stop?.();
      detached.current.add(active.runId);
      setActive(null);
      setProcessing(false);
      callbacks.current.loadMessages(active.sessionId, { refresh: true })
        .catch(() => callbacks.current.toast('任务已结束，历史同步失败，请刷新重试', 'error'));
      callbacks.current.loadSessions().catch(() => {});
    };
    stop = api.subscribeRunEvents(active.runId, {
      onState: (state) => {
        setConnection(state);
        if (state === 'denied') {
          callbacks.current.toast('无法继续订阅此任务，请重新登录或刷新查看', 'error');
          finish();
        }
      },
      onEvent: (event) => {
        runEvidence = reduceRunEvidence(runEvidence, event);
        if (runEvidence) runEvidence.backgroundExecution = true;
        if (event.type === 'token') answer += event.content || '';
        if (event.type === 'rag_context') {
          citations = Array.isArray(event.citations) ? event.citations : [];
          knowledgeChecked = true;
          knowledgeAvailable = event.retrievalAvailable !== false;
        }
        if (event.type === 'approval_required') answer = `工具「${event.toolName || '未知工具'}」需要人工审批，审批通过后会继续执行。`;
        if (event.type === 'error') answer = `错误：${event.content || '任务执行失败'}`;
        if (event.type === 'run_state' && runEvidence?.terminal && event.status !== 'COMPLETED') {
          answer = `任务已结束：${event.content || event.status}`;
        }
        callbacks.current.setMessages((items) => items.map((message) => message.role === 'assistant' && message.runId === active.runId
          ? { ...message, content: answer || (event.status === 'CREATED' ? '任务已提交，等待执行…' : '处理中…'),
            historical: false, runEvidence, traceId: runEvidence?.traceId || '', executionMode: runEvidence?.mode || '',
            traceEvents: runEvidence?.events || [], citations, knowledgeChecked, knowledgeAvailable } : message));
        if (event.type === 'done' || (event.type === 'run_state' && event.segmentFinished
          && (runEvidence?.terminal || event.status === 'WAITING_APPROVAL'))) finish();
      }
    });
    return () => { stop(); };
  }, [active, api, setProcessing]);

  const start = useCallback((runId, sid) => setActive({ runId, sessionId: sid }), []);
  const cancel = useCallback(async () => {
    if (!active) return;
    const response = await api.post(`/api/v1/agent-runs/${encodeURIComponent(active.runId)}/cancel`, {});
    if (response.success !== true) throw new Error(response.message || '取消失败');
    // 等待主库状态经订阅确认；取消请求失败也不能误报“已停止”。
  }, [active, api]);
  return { activeRunId: active?.runId || '', connection, start, cancel };
}
