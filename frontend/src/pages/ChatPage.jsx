import DOMPurify from 'dompurify';
import { AlertTriangle, Archive, ArchiveRestore, BookOpenCheck, Bot, Check, CheckCircle2, ChevronDown, ChevronUp, CircleSlash2, Link2, Pencil, Plus, RefreshCw, Send, Settings2, ShieldCheck, Square, Trash2, X } from 'lucide-react';
import { marked } from 'marked';
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { loadAgentConfigurationOptions } from '../api/listOptions.js';
import { useApprovalEvents } from '../hooks/useApprovalEvents.js';
import { useBackgroundRun } from '../hooks/useBackgroundRun.js';
import { ApprovalConnectionNotice } from '../components/ApprovalConnectionNotice.jsx';
import { SESSION_PREVIEW_LIMIT, useConversationHistory } from '../conversation/useConversationHistory.js';
import { activeCapabilityPacks, normalizeCapabilityBindings, subAgentBindings } from '../agent/capabilityPacks.js';
import { normalizePersistedRunEvidence } from '../agent/runEvidence.js';
import { CapabilityPackPicker } from '../components/CapabilityPackPicker.jsx';
import { Badge, ConfirmDialog, EmptyState, Field, LoadingState, Modal, PageHeader } from '../components/ui.jsx';
import { formatTime } from '../utils/format.js';

const APPROVAL_REFRESH_STATUSES = new Set(['WAITING_APPROVAL', 'RESUMING']);
const APPROVAL_FAILURE_STATUSES = new Set([
  'FAILED',
  'CANCELLED',
  'TIMED_OUT',
  'BUDGET_EXHAUSTED',
  'REJECTED',
  'EXPIRED'
]);

function initialCapabilityBindings(agent) {
  return normalizeCapabilityBindings(agent?.capabilityBindings);
}

async function hydrateRunEvidence(api, messages, knownMessages = []) {
  const items = Array.isArray(messages) ? messages : [];
  // 已知终态不反复查询，翻页/发送后同步历史只补新 Run 或尚未完成的审批。
  const evidenceByRun = new Map(knownMessages
    .filter((message) => message.runId && message.runEvidence?.terminal)
    .map((message) => [message.runId, message.runEvidence]));
  const runIds = [...new Set(items
    .filter((message) => message.role === 'assistant' && message.runId && !evidenceByRun.has(message.runId))
    .map((message) => message.runId))];
  for (let index = 0; index < runIds.length; index += 4) {
    const batch = runIds.slice(index, index + 4);
    const results = await Promise.all(batch.map(async (runId) => {
      try {
        const response = await api.get(`/api/v1/agent-runs/${encodeURIComponent(runId)}`);
        return [runId, normalizePersistedRunEvidence(response)];
      } catch {
        return [runId, null];
      }
    }));
    results.forEach(([runId, evidence]) => evidenceByRun.set(runId, evidence));
  }
  return items.map((message) => {
    const runEvidence = message.runId ? evidenceByRun.get(message.runId) : null;
    return attachRunEvidence(message, runEvidence);
  });
}

function attachRunEvidence(message, runEvidence) {
  return {
    ...message,
    historical: true,
    runEvidence,
    runEvidenceUnavailable: Boolean(message.runId && !runEvidence),
    traceId: runEvidence?.traceId || '',
    executionMode: runEvidence?.mode || '',
    traceEvents: runEvidence?.events || []
  };
}

function hasApprovalJourney(message) {
  const events = Array.isArray(message?.traceEvents) ? message.traceEvents : [];
  return Boolean(message?.runEvidence?.approvalId) || events.some((event) => (
    event.type === 'approval_required'
    || event.type === 'resume'
    || (event.type === 'tool_call' && event.status === 'APPROVAL_REQUIRED')
  ));
}

function shouldRefreshApprovalRun(message) {
  return message?.role === 'assistant'
    && (hasApprovalJourney(message) || message.runEvidenceUnavailable)
    && (!message.runEvidence || APPROVAL_REFRESH_STATUSES.has(message.runEvidence.status));
}

function approvalNoticeFor(message) {
  const events = Array.isArray(message?.traceEvents) ? message.traceEvents : [];
  const approvalEvent = events.filter((event) => event.type === 'approval_required').at(-1);
  const resumeEvent = events.filter((event) => event.type === 'resume').at(-1);
  const status = message?.runEvidence?.status || approvalEvent?.status || '';

  if (status === 'WAITING_APPROVAL' && message?.runEvidence?.approvalExecutionStatus === 'READY') {
    return {
      tone: 'progress', icon: RefreshCw,
      title: '审批已通过，等待执行',
      content: '系统已登记批准结果，恢复任务正在排队。'
    };
  }

  if (status === 'RESUMING') {
    return {
      tone: 'progress',
      icon: RefreshCw,
      title: '审批已通过，正在恢复执行',
      content: resumeEvent?.summary || '系统正在重新校验权限并执行已批准的动作。'
    };
  }
  if (status === 'COMPLETED') {
    return {
      tone: 'success',
      icon: CheckCircle2,
      title: '操作执行成功',
      content: resumeEvent?.status === 'SUCCEEDED' ? resumeEvent.summary : '审批已通过，本次运行已完成。'
    };
  }
  if (APPROVAL_FAILURE_STATUSES.has(status)) {
    const labels = {
      REJECTED: '审批已拒绝',
      EXPIRED: '审批已过期',
      CANCELLED: '操作已取消'
    };
    return {
      tone: 'danger',
      icon: AlertTriangle,
      title: labels[status] || '操作执行失败',
      content: message?.runEvidence?.detail || resumeEvent?.summary || '本次操作未完成，请查看回答内容或联系管理员。'
    };
  }
  return {
    tone: 'waiting',
    icon: ShieldCheck,
    title: approvalEvent?.title || '等待管理员审批',
    content: approvalEvent?.summary || '审批通过后系统会自动恢复执行，并在这里显示结果。'
  };
}

/** 个人 Agent 主工作区：配置默认 Agent、管理会话并展示 SSE 运行/审批证据。 */
export function ChatPage({ api, toast, onOpenExperts }) {
  const [text, setText] = useState('');
  const [models, setModels] = useState([]);
  const [agent, setAgent] = useState(null);
  const [capabilities, setCapabilities] = useState([]);
  const [capabilityDirectoryReady, setCapabilityDirectoryReady] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [savingSettings, setSavingSettings] = useState(false);
  const [settingsError, setSettingsError] = useState('');
  const [agentDraft, setAgentDraft] = useState(null);
  const [loading, setLoading] = useState(true);
  const configurationRequest = useRef(null);
  const [processing, setProcessing] = useState(false);
  const [confirm, setConfirm] = useState(null);
  const [renameDraft, setRenameDraft] = useState(null);
  const pendingSubmissionRef = useRef(null);
  const scrollRef = useRef(null);
  const messagesViewportRef = useRef(null);
  const historyScrollRef = useRef(null);
  const {
    sessions, setSessions, sessionId, rememberSession, messages, setMessages, sessionStatus, sessionView,
    sessionsExpanded, setSessionsExpanded, nextSessionCursor, nextBeforeId,
    loadingMoreSessions, loadingMessages, loadingEarlier, historyError, mutating,
    loadSessions, loadMessages, bootHistory, clearSelection, switchSession, changeView,
    loadMoreSessions, loadEarlier, mutateSession
  } = useConversationHistory({ api, toast, processing, hydrateMessages: hydrateRunEvidence });
  const backgroundRun = useBackgroundRun({ api, sessionId, messages, setMessages, loadMessages, loadSessions, toast, setProcessing });

  const startNewConversation = useCallback(() => {
    if (processing) return;
    clearSelection();
    setRenameDraft(null);
    if (sessionView === 'ARCHIVED') changeView('ACTIVE');
  }, [clearSelection, processing, sessionView, changeView]);

  const boot = useCallback(async () => {
    configurationRequest.current?.abort();
    const controller = new AbortController();
    configurationRequest.current = controller;
    setLoading(true);
    setCapabilityDirectoryReady(false);
    try {
      const agentRes = await api.get('/api/v1/my/agents/default', { signal: controller.signal });
      if (controller.signal.aborted) return;
      const loadedAgent = agentRes?.agent || agentRes;
      setAgent(loadedAgent?.agentId ? loadedAgent : null);
      if (!loadedAgent?.agentId) {
        toast(agentRes?.message || '个人 Agent 加载失败', 'error');
      } else {
        try {
          const options = await loadAgentConfigurationOptions(api, loadedAgent.agentId, undefined, controller.signal);
          if (controller.signal.aborted) return;
          setModels(options.models);
          setCapabilities(options.capabilities);
          setCapabilityDirectoryReady(true);
        } catch (error) {
          if (controller.signal.aborted) return;
          toast(error.message || '配置选项加载失败，暂不能修改 Agent 设置', 'error');
        }
      }
      await bootHistory();
    } catch (error) {
      if (!controller.signal.aborted) toast(error.message || '会话加载失败，请刷新重试', 'error');
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, [api, bootHistory, toast]);

  useEffect(() => { boot(); return () => configurationRequest.current?.abort(); }, []);
  useLayoutEffect(() => {
    const previous = historyScrollRef.current;
    const viewport = messagesViewportRef.current;
    if (previous && viewport) {
      viewport.scrollTop = previous.top + viewport.scrollHeight - previous.height;
      historyScrollRef.current = null;
    } else {
      scrollRef.current?.scrollIntoView({ block: 'end' });
    }
  }, [messages]);
  const activeApprovalRunKey = [...new Set(messages
    .filter(shouldRefreshApprovalRun)
    .map((message) => message.runId)
    .filter(Boolean))].join(',');
  const approvalConnection = useApprovalEvents({
    api, sessionId, enabled: !processing && Boolean(activeApprovalRunKey),
    onChange: async (isDisposed) => {
      const runIds = activeApprovalRunKey.split(',');
      const results = await Promise.all(runIds.map(async (runId) => {
        const response = await api.get(`/api/v1/agent-runs/${encodeURIComponent(runId)}`);
        const evidence = normalizePersistedRunEvidence(response);
        if (!evidence) throw new Error('审批状态同步失败');
        return [runId, evidence];
      }));
      if (isDisposed()) return;
      if (results.some(([, evidence]) => evidence?.terminal)) {
        await loadMessages(sessionId, { refresh: true });
        return;
      }
      const evidenceByRun = new Map(results);
      setMessages((items) => items.map((message) => (
        evidenceByRun.has(message.runId)
          ? attachRunEvidence(message, evidenceByRun.get(message.runId))
          : message
      )));
    }
  });

  const deleteSession = async (sid) => {
    await mutateSession(sid, 'delete');
    setConfirm(null);
  };

  const renameSession = async () => {
    const title = renameDraft?.title?.trim();
    if (!renameDraft?.sid || !title) {
      toast('会话标题不能为空', 'error');
      return;
    }
    setRenameDraft((current) => ({ ...current, saving: true }));
    try {
      const result = await api.put(`/api/v1/analysis/session/${encodeURIComponent(renameDraft.sid)}/title`, { title });
      if (result?.success === false) {
        throw new Error(result.message || '重命名失败');
      }
      setSessions((items) => items.map((item) => (
        item.sessionId === renameDraft.sid ? { ...item, title } : item
      )));
      setRenameDraft(null);
      toast('会话已重命名', 'success');
    } catch (error) {
      setRenameDraft((current) => ({ ...current, saving: false }));
      toast(error?.message || '重命名失败，请重试', 'error');
    }
  };

  const send = async () => {
    if (processing) {
      if (!backgroundRun.activeRunId) { toast('正在提交任务，请稍候', 'info'); return; }
      try { await backgroundRun.cancel(); }
      catch (error) { toast(error.message || '取消失败，请重试', 'error'); }
      return;
    }
    if (sessionStatus === 'ARCHIVED' || loadingMessages || historyError || mutating) return;
    const question = text.trim();
    if (!question) return;
    setText('');
    setProcessing(true);
    let activeSessionId = sessionId;
    try {
      if (!activeSessionId) {
        const created = await api.post('/api/v1/analysis/session', {});
        activeSessionId = created.sessionId || created.data?.sessionId;
        if (!created.success || !activeSessionId) throw new Error(created.message || '创建会话失败');
        rememberSession(activeSessionId);
      }
      const previous = pendingSubmissionRef.current;
      const requestKey = previous?.question === question && previous.sessionId === activeSessionId
        ? previous.requestKey : crypto.randomUUID();
      pendingSubmissionRef.current = { question, sessionId: activeSessionId, requestKey };
      setMessages((items) => items.some((message) => message.requestKey === requestKey)
        ? items.map((message) => message.requestKey === requestKey ? { ...message, content: '正在提交任务…' } : message)
        : [...items, { role: 'user', content: question },
          { role: 'assistant', content: '正在提交任务…', requestKey }]);
      const body = { requestKey, request: { question, sessionId: activeSessionId } };
      // 网络失败时只重试同一提交键；不会通过重新订阅再次执行问题。
      let submitted;
      try { submitted = await api.post('/api/v1/agent-runs', body); }
      catch { submitted = await api.post('/api/v1/agent-runs', body); }
      if (!submitted.runId) throw new Error(submitted.message || '任务提交失败');
      pendingSubmissionRef.current = null;
      setMessages((items) => items.map((message) => message.requestKey === requestKey
        ? { ...message, runId: submitted.runId, content: '任务已提交，等待执行…' } : message));
      backgroundRun.start(submitted.runId, activeSessionId);
      loadSessions().catch(() => {});
    } catch (error) {
      setProcessing(false);
      setText(question);
      toast(error.message || '提交结果暂未确认，请重试；相同请求会复用原任务', 'error');
      // 即使提交响应丢失，落库占位消息也能找回同一个 Run。
      if (activeSessionId) loadMessages(activeSessionId, { refresh: true }).catch(() => {});
    }
  };

  const openSettings = () => {
    if (!agent) {
      toast('个人 Agent 尚未加载完成', 'error');
      return;
    }
    if (!capabilityDirectoryReady) {
      toast('能力目录加载失败，暂不能修改 Agent 配置，请刷新后重试', 'error');
      return;
    }
    setAgentDraft({
      name: agent.name || '我的 Agent',
      description: agent.description || '',
      systemPrompt: agent.systemPrompt || '',
      modelId: agent.modelId || '',
      capabilityBindings: initialCapabilityBindings(agent)
    });
    setSettingsError('');
    setSettingsOpen(true);
  };

  const saveSettings = async () => {
    if (!capabilityDirectoryReady) {
      const message = '能力目录不可用，已阻止保存以避免错误的能力授权';
      setSettingsError(message);
      toast(message, 'error');
      return;
    }
    if (!agentDraft?.name?.trim()) {
      const message = 'Agent 名称不能为空';
      setSettingsError(message);
      toast(message, 'error');
      return;
    }
    setSettingsError('');
    setSavingSettings(true);
    try {
      const capabilityBindings = normalizeCapabilityBindings(agentDraft.capabilityBindings);
      const result = await api.put(`/api/v1/my/agents/${agent.agentId}`, {
        name: agentDraft.name.trim(),
        description: agentDraft.description?.trim() || '',
        systemPrompt: agentDraft.systemPrompt?.trim() || '',
        modelId: agentDraft.modelId || '',
        executionMode: 'auto',
        capabilityBindings,
        enabled: true
      });
      if (result?.success === false) {
        throw new Error(result.message || '保存失败');
      }
      const refreshed = await api.get('/api/v1/my/agents/default');
      const refreshedAgent = refreshed?.agent || refreshed;
      if (!refreshedAgent?.agentId) {
        throw new Error(refreshed?.message || 'Agent 配置刷新失败');
      }
      setAgent(refreshedAgent);
      setSettingsOpen(false);
      toast('个人 Agent 配置已更新', 'success');
    } catch (error) {
      const message = error?.message || '保存失败，请重试';
      setSettingsError(message);
      toast(message, 'error');
    } finally {
      setSavingSettings(false);
    }
  };

  const toggleDraftExpert = (identity) => {
    setAgentDraft((current) => ({
      ...current,
      capabilityBindings: current.capabilityBindings.includes(identity)
        ? current.capabilityBindings.filter((value) => value !== identity)
        : [...current.capabilityBindings, identity]
    }));
  };

  const selectedModel = models.find((item) => item.modelId === agent?.modelId);
  const activeBindings = initialCapabilityBindings(agent);
  const activePacks = activeCapabilityPacks(activeBindings, capabilities);
  const activeExperts = subAgentBindings(activeBindings);
  const expertCapabilities = capabilities.filter((item) => item.type === 'SUB_AGENT');
  const sessionPreview = sessions.slice(0, SESSION_PREVIEW_LIMIT);
  const activeSession = sessions.find((item) => item.sessionId === sessionId);
  const visibleSessions = sessionsExpanded || sessions.length <= SESSION_PREVIEW_LIMIT
    ? sessions
    : activeSession && !sessionPreview.some((item) => item.sessionId === activeSession.sessionId)
      ? [...sessionPreview.slice(0, SESSION_PREVIEW_LIMIT - 1), activeSession]
      : sessionPreview;
  const canLoadMoreSessions = Boolean(nextSessionCursor) || (!sessionsExpanded && sessions.length > SESSION_PREVIEW_LIMIT);
  const sessionActionsDisabled = processing || mutating || loadingMessages;

  return (
    <>
      <PageHeader
        title="我的 Agent"
        desc={agent?.description || '定义能力，发起任务，并核对每次运行的结果与证据'}
        actions={<><button className="btn" onClick={boot} disabled={sessionActionsDisabled || loading}><RefreshCw size={16} />刷新</button><button className="btn" onClick={openSettings}><Settings2 size={16} />设置</button><button className="btn primary" onClick={startNewConversation} disabled={sessionActionsDisabled}><Plus size={16} />新对话</button></>}
      />
      <div className="chat-layout">
        <div className="chat-sidebar">
          <div className="panel-title">
            <strong>{sessionView === 'ARCHIVED' ? '已归档会话' : '会话'}</strong>
            <button className="btn" disabled={sessionActionsDisabled || loadingMoreSessions}
              onClick={() => { setRenameDraft(null); changeView(sessionView === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE'); }}>
              <Archive size={15} />{sessionView === 'ACTIVE' ? '查看归档' : '返回会话'}
            </button>
          </div>
          <div className="session-list">
            {loading && <LoadingState label="加载会话" />}
            {!loading && loadingMoreSessions && <LoadingState label="加载会话" />}
            {!loading && !loadingMoreSessions && sessions.length === 0 && <EmptyState title={sessionView === 'ARCHIVED' ? '暂无归档' : '暂无会话'} desc={sessionView === 'ARCHIVED' ? '归档的会话可以在这里查看和恢复。' : '尚未保存任何对话记录。'} />}
            {!loading && visibleSessions.map((session) => (
              <div className={`session-row ${session.sessionId === sessionId ? 'active' : ''}`} key={session.sessionId}>
                {renameDraft?.sid === session.sessionId ? (
                  <input
                    className="session-rename-input"
                    value={renameDraft.title}
                    maxLength={80}
                    autoFocus
                    aria-label="会话标题"
                    disabled={renameDraft.saving}
                    onFocus={(event) => event.target.select()}
                    onChange={(event) => setRenameDraft({ ...renameDraft, title: event.target.value })}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') {
                        event.preventDefault();
                        renameSession();
                      }
                      if (event.key === 'Escape') {
                        setRenameDraft(null);
                      }
                    }}
                  />
                ) : (
                  <button className="session-item" disabled={processing || mutating} onClick={() => switchSession(session.sessionId)}>
                    <strong title={session.title || '新对话'}>{session.title || '新对话'}</strong>
                    <div className="muted">{session.messageCount || 0} 条 · {formatTime(session.lastAccessAt)}</div>
                  </button>
                )}
                <div className="session-actions">
                  {renameDraft?.sid === session.sessionId ? (
                    <>
                      <button className="icon-button" disabled={renameDraft.saving} onClick={renameSession} title="保存标题" aria-label="保存标题"><Check size={15} /></button>
                      <button className="icon-button" disabled={renameDraft.saving} onClick={() => setRenameDraft(null)} title="取消重命名" aria-label="取消重命名"><X size={15} /></button>
                    </>
                  ) : (
                    <>
                      <button className="icon-button" disabled={sessionActionsDisabled} onClick={() => setRenameDraft({ sid: session.sessionId, title: session.title || '' })} title="重命名" aria-label="重命名会话"><Pencil size={15} /></button>
                      <button className="icon-button" disabled={sessionActionsDisabled}
                        onClick={() => mutateSession(session.sessionId, session.status === 'ARCHIVED' ? 'restore' : 'archive')}
                        title={session.status === 'ARCHIVED' ? '恢复' : '归档'} aria-label={session.status === 'ARCHIVED' ? '恢复会话' : '归档会话'}>
                        {session.status === 'ARCHIVED' ? <ArchiveRestore size={15} /> : <Archive size={15} />}
                      </button>
                      <button className="icon-button" disabled={sessionActionsDisabled} onClick={() => setConfirm({ sid: session.sessionId, title: session.title || '新对话' })} title="删除" aria-label="删除会话"><Trash2 size={15} /></button>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
          {!loading && canLoadMoreSessions && (
            <button type="button" className="session-list-toggle" onClick={loadMoreSessions}
              disabled={loadingMoreSessions || sessionActionsDisabled}>
              <ChevronDown size={16} />{loadingMoreSessions ? '加载中…' : '加载更多'}
            </button>
          )}
          {!loading && sessionsExpanded && sessions.length > SESSION_PREVIEW_LIMIT && (
            <button type="button" className="session-list-toggle" onClick={() => setSessionsExpanded(false)}>
              <ChevronUp size={16} />收起
            </button>
          )}
        </div>
        <div className="chat-main">
          <div className="agent-identity">
            <div className="agent-avatar"><Bot size={20} /></div>
            <div className="agent-identity-copy">
              <strong>{agent?.name || '我的 Agent'}</strong>
              <span>{selectedModel?.displayName || '系统默认模型'}</span>
            </div>
            <div className="agent-capabilities">
              {activePacks.slice(0, 3).map((pack) => <Badge tone="blue" key={pack.id}>{pack.label}</Badge>)}
              {activeExperts.length > 0 && <Badge tone="green"><Bot size={12} />{activeExperts.length} 位专家</Badge>}
            </div>
            <button className="icon-button bordered" onClick={openSettings} title="配置我的 Agent"><Settings2 size={16} /></button>
          </div>
          <div className="messages" ref={messagesViewportRef}>
            {backgroundRun.activeRunId && ['connecting', 'reconnecting'].includes(backgroundRun.connection) && (
              <div className="notice">正在恢复任务连接，后台执行仍在继续…</div>
            )}
            <ApprovalConnectionNotice state={approvalConnection} enabled={!processing && Boolean(activeApprovalRunKey)}
              onRefresh={() => loadMessages(sessionId, { refresh: true })} />
            {loadingMessages && <LoadingState label="加载消息" />}
            {historyError && <EmptyState title="历史消息加载失败" desc={historyError} actions={
              <button className="btn" disabled={loadingMessages} onClick={() => loadMessages(sessionId).catch(() => {})}>重试</button>
            } />}
            {nextBeforeId && <button className="session-list-toggle" disabled={loadingEarlier || processing || loadingMessages}
              onClick={() => loadEarlier(() => {
                const viewport = messagesViewportRef.current;
                if (viewport) historyScrollRef.current = { top: viewport.scrollTop, height: viewport.scrollHeight };
              })}>
              <ChevronUp size={16} />{loadingEarlier ? '加载中…' : '加载更早消息'}
            </button>}
            {messages.length === 0 && <EmptyState title="今天想完成什么？" desc="直接提出问题或任务，我会决定是否检索知识、使用工具或直接回答。" actions={<Bot size={24} />} />}
            {messages.map((message, index) => <MessageBubble key={message.id || `${message.role}-${index}`} message={message} api={api} toast={toast} />)}
            <div ref={scrollRef} />
          </div>
          <div className="composer">
            {sessionStatus === 'ARCHIVED' && <div className="panel-title">
              <span className="muted">此会话已归档，恢复后可继续对话。</span>
              <button className="btn" disabled={mutating} onClick={() => mutateSession(sessionId, 'restore')}><ArchiveRestore size={15} />恢复会话</button>
            </div>}
            <div className="composer-input">
              <textarea className="textarea" rows={3} value={text} disabled={sessionStatus === 'ARCHIVED' || loadingMessages || Boolean(historyError) || mutating} onChange={(event) => setText(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) send(); }} placeholder="告诉我的 Agent 你想完成什么" />
              <button className={`btn ${processing ? 'danger' : 'primary'}`} disabled={!processing && (sessionStatus === 'ARCHIVED' || loadingMessages || Boolean(historyError) || mutating)} onClick={send}>{processing ? <Square size={16} /> : <Send size={16} />}{processing ? '停止' : '发送'}</button>
            </div>
          </div>
        </div>
      </div>
      {confirm && <ConfirmDialog title="删除会话" message={`确认删除「${confirm.title}」？删除后无法在页面恢复，尚未执行的任务会被取消；已完成的操作不会撤销。`} danger confirmText={mutating ? '删除中…' : '删除'} onCancel={() => setConfirm(null)} onConfirm={() => deleteSession(confirm.sid)} />}
      {settingsOpen && agentDraft && (
        <Modal
          title="配置我的 Agent"
          size="lg"
          onClose={() => setSettingsOpen(false)}
          actions={<><button className="btn" onClick={() => setSettingsOpen(false)}>取消</button><button className="btn primary" disabled={savingSettings || !capabilityDirectoryReady} onClick={saveSettings}>{savingSettings ? '保存中...' : '保存配置'}</button></>}
        >
          <div className="form-grid agent-settings-form">
            <Field label="名称">
              <input className="input" value={agentDraft.name} onChange={(event) => setAgentDraft({ ...agentDraft, name: event.target.value })} />
            </Field>
            <Field label="模型">
              <select className="select" value={agentDraft.modelId} onChange={(event) => setAgentDraft({ ...agentDraft, modelId: event.target.value })}>
                <option value="">跟随系统默认模型</option>
                {agentDraft.modelId && !models.some((item) => item.modelId === agentDraft.modelId)
                  && <option value={agentDraft.modelId}>已配置模型（已停用或不可选）</option>}
                {models.map((item) => <option key={item.modelId} value={item.modelId}>{item.displayName}</option>)}
              </select>
            </Field>
            <Field label="简介" span>
              <input className="input" value={agentDraft.description} onChange={(event) => setAgentDraft({ ...agentDraft, description: event.target.value })} />
            </Field>
            <Field label="角色与行为" span>
              <textarea className="textarea" rows={4} value={agentDraft.systemPrompt} onChange={(event) => setAgentDraft({ ...agentDraft, systemPrompt: event.target.value })} />
            </Field>
            <div className="field span-2 agent-settings-section capability-pack-section">
              <span id="agent-capability-pack-label">能力</span>
              <CapabilityPackPicker
                capabilities={capabilities}
                selectedBindings={agentDraft.capabilityBindings}
                onChange={(capabilityBindings) => setAgentDraft({ ...agentDraft, capabilityBindings })}
                labelledBy="agent-capability-pack-label"
              />
            </div>
            <div className="field span-2 agent-settings-section">
              <span id="agent-expert-label">
                专家助手
                <button className="text-button" type="button" onClick={() => { setSettingsOpen(false); onOpenExperts?.(); }}>管理专家</button>
              </span>
              {expertCapabilities.length === 0 ? (
                <button className="expert-empty-action" type="button" onClick={() => { setSettingsOpen(false); onOpenExperts?.(); }}>
                  <Bot size={18} />
                  <span><strong>添加专家助手</strong><small>为知识研究或数据分析准备专门助手</small></span>
                  <Plus size={16} />
                </button>
              ) : (
                <div className="expert-binding-list" role="group" aria-labelledby="agent-expert-label">
                  {expertCapabilities.map((expert) => {
                    const selected = agentDraft.capabilityBindings.includes(expert.identity);
                    return (
                      <button
                        className={`expert-binding-option ${selected ? 'active' : ''}`}
                        type="button"
                        aria-pressed={selected}
                        key={expert.identity}
                        disabled={!expert.availability?.available}
                        onClick={() => toggleDraftExpert(expert.identity)}
                      >
                        <Bot size={17} />
                        <span><strong>{expert.name}</strong><small>{expert.description || '专家助手'}</small></span>
                        <span>{selected ? '已启用' : '未启用'}</span>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
            {settingsError && (
              <div className="agent-settings-error span-2" role="alert">
                <AlertTriangle size={15} />{settingsError}
              </div>
            )}
          </div>
        </Modal>
      )}
    </>
  );
}

function MessageBubble({ message, api, toast }) {
  const [learning, setLearning] = useState(false);
  const learn = async () => {
    if (learning) return;
    setLearning(true);
    try {
      const result = await api.post(`/api/v1/my/skills/from-run/${encodeURIComponent(message.runId)}`, {});
      toast(result.reviewStatus === 'PENDING' ? '候选已提炼，请到上下文的技能经验检查并确认' : '此任务已提炼过技能，可到上下文查看');
    } catch (error) { toast(error.message, 'error'); }
    finally { setLearning(false); }
  };
  const html = DOMPurify.sanitize(marked.parse(message.content || ''));
  if (message.role === 'assistant') {
    return (
      <div className="message assistant">
        <div className="markdown-body" dangerouslySetInnerHTML={{ __html: html }} />
        <MessageOutcome message={message} />
        {message.runId && message.runEvidence?.status === 'COMPLETED' && <button className="btn" disabled={learning} onClick={learn}>{learning ? '提炼中…' : '提炼为技能'}</button>}
      </div>
    );
  }
  return <div className="message user">{message.content || ''}</div>;
}

function MessageOutcome({ message }) {
  const citations = message.citations || [];
  const events = message.traceEvents || [];
  const toolActionCount = events.filter((item) => (
    item.type === 'tool_call' && item.status !== 'APPROVAL_REQUIRED'
  )).length;
  const approvalNotice = hasApprovalJourney(message) ? approvalNoticeFor(message) : null;
  const ApprovalIcon = approvalNotice?.icon;
  if (!message.knowledgeChecked && toolActionCount === 0 && !approvalNotice) {
    return null;
  }
  return (
    <div className="message-outcome">
      <div className="outcome-summary">
        {message.knowledgeChecked && message.knowledgeAvailable === false && (
          <span className="outcome-status warning"><AlertTriangle size={14} />知识检索暂不可用</span>
        )}
        {message.knowledgeChecked && message.knowledgeAvailable !== false && citations.length > 0 && (
          <span className="outcome-status success"><BookOpenCheck size={14} />参考了你的知识库 · {citations.length} 条</span>
        )}
        {message.knowledgeChecked && message.knowledgeAvailable !== false && citations.length === 0 && (
          <span className="outcome-status muted"><CircleSlash2 size={14} />未使用个人知识库</span>
        )}
        {toolActionCount > 0 && (
          <span className="outcome-status"><CheckCircle2 size={14} />执行了 {toolActionCount} 个动作</span>
        )}
      </div>
      {approvalNotice && (
        <div className={`approval-notice ${approvalNotice.tone}`} role="status" aria-live="polite">
          <ApprovalIcon size={16} className={approvalNotice.tone === 'progress' ? 'approval-progress-icon' : ''} />
          <div><strong>{approvalNotice.title}</strong><span>{approvalNotice.content}</span></div>
        </div>
      )}
      {citations.length > 0 && <CitationList citations={citations} />}
    </div>
  );
}

function renderCitationLocation(item) {
  const range = Number(item?.charEnd || 0) > Number(item?.charStart || 0) ? `字符 ${item.charStart}-${item.charEnd}` : '';
  return [item?.sectionPath, range].filter(Boolean).join(' · ');
}

function CitationList({ citations }) {
  return (
    <details className="message-details citation-list">
      <summary><Link2 size={14} />查看知识来源</summary>
      <div className="citation-items">
        {citations.map((item) => (
          <div className="citation-item" key={`${item.referenceId}-${item.sourceId}`}>
            <div className="citation-item-title">
              <Badge tone={item.sourceType === 'knowledge' ? 'blue' : 'gray'}>{item.referenceId}</Badge>
              <strong>{item.sourceName || (item.sourceType === 'file' ? '我的文件' : '我的知识')}</strong>
            </div>
            {renderCitationLocation(item) && <small>{renderCitationLocation(item)}</small>}
            <small className="mono">来源 ID：{item.sourceId || '-'}</small>
            <p>{item.snippet}</p>
          </div>
        ))}
      </div>
    </details>
  );
}
