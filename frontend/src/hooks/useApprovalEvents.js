import { useEffect, useRef, useState } from 'react';

/** 审批事件是刷新提示；合并短时间通知，串行读取主库，正在刷新时的新通知不能丢失。 */
export function useApprovalEvents({ api, sessionId, reviewer = false, enabled = true, onChange }) {
  const callback = useRef(onChange);
  callback.current = onChange;
  const [state, setState] = useState('connecting');
  useEffect(() => {
    if (!enabled || (!reviewer && !sessionId)) return undefined;
    let disposed = false;
    let running = false;
    let pending = false;
    let timer;
    const refresh = async () => {
      if (running || disposed) return;
      running = true;
      try {
        while (pending && !disposed) {
          pending = false;
          try {
            await callback.current?.(() => disposed);
          } catch {
            // 读取失败保留原状态；重连、下一条通知或人工刷新再次读取。
            if (!disposed) setState('sync-failed');
          }
        }
      } finally {
        running = false;
      }
    };
    const unsubscribe = api.subscribeApprovalEvents(reviewer ? null : sessionId, {
      onState: (next) => { if (!disposed) setState(next); },
      onEvent: (event) => {
        if (event.type !== 'ready' && event.type !== 'approval_changed') return;
        pending = true;
        window.clearTimeout(timer);
        timer = window.setTimeout(refresh, 150);
      }
    });
    return () => {
      disposed = true;
      window.clearTimeout(timer);
      unsubscribe();
    };
  }, [api, sessionId, reviewer, enabled]);
  return state;
}
