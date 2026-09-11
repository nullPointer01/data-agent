import DOMPurify from 'dompurify';
import { AlertTriangle, BookOpenCheck, Bot, Check, CheckCircle2, ChevronDown, ChevronUp, CircleSlash2, Link2, Pencil, Plus, RefreshCw, Send, Settings2, ShieldCheck, Square, Trash2, X } from 'lucide-react';
import { marked } from 'marked';
import { useCallback, useEffect, useRef, useState } from 'react';
import { activeCapabilityPacks, normalizeCapabilityBindings, subAgentBindings } from '../agent/capabilityPacks.js';
import { normalizePersistedRunEvidence, reduceRunEvidence } from '../agent/runEvidence.js';
import { CapabilityPackPicker } from '../components/CapabilityPackPicker.jsx';
import { Badge, ConfirmDialog, EmptyState, Field, LoadingState, Modal, PageHeader } from '../components/ui.jsx';
import { formatTime } from '../utils/format.js';

const APPROVAL_REFRESH_STATUSES = new Set(['WAITING_APPROVAL', 'RESUMING']);
const SESSION_PREVIEW_LIMIT = 5;
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

function resolveStreamErrorMessage(error) {
  if (error?.name === 'AbortError') {
    return '已停止生成';
  }
  if (error?.message?.startsWith('请求失败')) {
    return error.message;
  }
  return '连接中断：后端流式响应超时、服务重启或网络连接不可用，请稍后重试。';
}

async function hydrateRunEvidence(api, messages) {
  const items = Array.isArray(messages) ? messages : [];
  const runIds = [...new Set(items
    .filter((message) => message.role === 'assistant' && message.runId)
    .map((message) => message.runId))];
  const evidenceByRun = new Map();
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
    && hasApprovalJourney(message)
    && APPROVAL_REFRESH_STATUSES.has(message?.runEvidence?.status);
}

function approvalNoticeFor(message) {
  const events = Array.isArray(message?.traceEvents) ? message.traceEvents : [];
  const approvalEvent = events.filter((event) => event.type === 'approval_required').at(-1);
  const resumeEvent = events.filter((event) => event.type === 'resume').at(-1);
  const status = message?.runEvidence?.status || approvalEvent?.status || '';

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
      content: resumeEvent?.summary || '审批已通过，本次运行已完成。'
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

export function ChatPage({ api, token, toast, onOpenExperts }) {
  const [sessions, setSessions] = useState([]);
  const [sessionId, setSessionId] = useState(localStorage.getItem('chatSessionId') || '');
  const [messages, setMessages] = useState([]);
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
  const [processing, setProcessing] = useState(false);
  const [confirm, setConfirm] = useState(null);
  const [renameDraft, setRenameDraft] = useState(null);
  const [sessionsExpanded, setSessionsExpanded] = useState(false);
  const abortRef = useRef(null);
  const activeRunRef = useRef('');
  const scrollRef = useRef(null);

  const loadSessions = useCallback(async () => {
    const res = await api.get('/api/v1/analysis/sessions');
    const loadedSessions = res.sessions || [];
    setSessions(loadedSessions);
    return loadedSessions;
  }, [api]);

  const loadMessages = useCallback(async (sid) => {
    if (!sid) {
      setMessages([]);
      return;
    }
    const res = await api.get(`/api/v1/analysis/session/${sid}/messages`);
    setMessages(await hydrateRunEvidence(api, res.messages));
  }, [api]);

  const startNewConversation = useCallback(() => {
    if (processing) return;
    setSessionId('');
    setMessages([]);
    setRenameDraft(null);
    localStorage.removeItem('chatSessionId');
  }, [processing]);

  const boot = useCallback(async () => {
    setLoading(true);
    setCapabilityDirectoryReady(false);
    try {
      const [modelRes, agentRes] = await Promise.all([
        api.get('/api/v1/my/models').catch(() => ({ models: [] })),
        api.get('/api/v1/my/agents/default').catch(() => null)
      ]);
      setModels(modelRes.models || []);
      const loadedAgent = agentRes?.agent || agentRes;
      setAgent(loadedAgent?.agentId ? loadedAgent : null);
      if (!loadedAgent?.agentId) {
        toast(agentRes?.message || '个人 Agent 加载失败', 'error');
      }
      const capabilityRes = loadedAgent?.agentId
        ? await api.get(`/api/v1/my/capabilities?agentId=${encodeURIComponent(loadedAgent.agentId)}`).catch(() => null)
        : null;
      const visibleCapabilities = capabilityRes?.capabilities || [];
      setCapabilities(visibleCapabilities);
      setCapabilityDirectoryReady(capabilityRes?.success === true);
      const loadedSessions = await loadSessions();
      const storedSessionId = localStorage.getItem('chatSessionId') || '';
      const selectedSession = loadedSessions.find((item) => item.sessionId === storedSessionId)
        || loadedSessions[0];
      if (selectedSession?.sessionId) {
        setSessionId(selectedSession.sessionId);
        localStorage.setItem('chatSessionId', selectedSession.sessionId);
        await loadMessages(selectedSession.sessionId);
      } else {
        setSessionId('');
        setMessages([]);
        localStorage.removeItem('chatSessionId');
      }
    } finally {
      setLoading(false);
    }
  }, [api, loadMessages, loadSessions, toast]);

  useEffect(() => { boot(); }, []);
  useEffect(() => { scrollRef.current?.scrollIntoView({ block: 'end' }); }, [messages]);
  const activeApprovalRunKey = [...new Set(messages
    .filter(shouldRefreshApprovalRun)
    .map((message) => message.runId)
    .filter(Boolean))].join(',');
  useEffect(() => {
    if (!sessionId || processing || !activeApprovalRunKey) {
      return undefined;
    }
    const runIds = activeApprovalRunKey.split(',');
    let disposed = false;
    let requestInFlight = false;
    const refreshApprovalState = async () => {
      if (requestInFlight) return;
      requestInFlight = true;
      try {
        const results = await Promise.all(runIds.map(async (runId) => {
          const response = await api.get(`/api/v1/agent-runs/${encodeURIComponent(runId)}`);
          return [runId, normalizePersistedRunEvidence(response)];
        }));
        if (disposed) return;
        if (results.some(([, evidence]) => evidence?.terminal)) {
          await new Promise((resolve) => window.setTimeout(resolve, 400));
          if (!disposed) await loadMessages(sessionId);
          return;
        }
        const evidenceByRun = new Map(results);
        setMessages((items) => items.map((message) => (
          evidenceByRun.has(message.runId)
            ? attachRunEvidence(message, evidenceByRun.get(message.runId))
            : message
        )));
      } catch {
        // 保留当前状态并继续轮询，短暂网络异常不应抹掉审批进度。
      } finally {
        requestInFlight = false;
      }
    };
    refreshApprovalState();
    const timer = window.setInterval(refreshApprovalState, 2500);
    return () => {
      disposed = true;
      window.clearInterval(timer);
    };
  }, [activeApprovalRunKey, api, loadMessages, processing, sessionId]);

  const switchSession = async (sid) => {
    setSessionId(sid);
    localStorage.setItem('chatSessionId', sid);
    await loadMessages(sid);
  };

  const deleteSession = async (sid) => {
    try {
      await api.delete(`/api/v1/analysis/session/${sid}`);
      if (sid === sessionId) {
        localStorage.removeItem('chatSessionId');
        setSessionId('');
        setMessages([]);
      }
      toast('会话已删除', 'success');
    } catch (e) {
      toast('删除失败，请重试', 'error');
    } finally {
      setConfirm(null);
      await loadSessions();
    }
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
    if (processing && abortRef.current) {
      const runId = activeRunRef.current;
      const cancelRequest = runId
        ? api.post(`/api/v1/analysis/runs/${runId}/cancel`, {}).catch(() => null)
        : Promise.resolve();
      abortRef.current.abort();
      activeRunRef.current = '';
      await cancelRequest;
      return;
    }
    const question = text.trim();
    if (!question) {
      return;
    }
    setText('');
    setMessages((items) => [...items, { role: 'user', content: question }, {
      role: 'assistant',
      content: '思考中...'
    }]);
    setProcessing(true);
    abortRef.current = new AbortController();
    try {
      let activeSessionId = sessionId;
      if (!activeSessionId) {
        const created = await api.post('/api/v1/analysis/session', {});
        activeSessionId = created.sessionId || created.data?.sessionId;
        if (!created.success || !activeSessionId) {
          throw new Error(created.message || '创建会话失败');
        }
        setSessionId(activeSessionId);
        localStorage.setItem('chatSessionId', activeSessionId);
      }
      const response = await fetch('/api/v1/analysis/analyze/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ question, sessionId: activeSessionId }),
        signal: abortRef.current.signal
      });
      if (!response.ok || !response.body) {
        throw new Error(`请求失败：${response.status}`);
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let answer = '';
      let responseTraceId = '';
      let responseSessionId = activeSessionId;
      let citations = [];
      let knowledgeChecked = false;
      let knowledgeAvailable = true;
      let runEvidence = null;
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop();
        lines.forEach((line) => {
          if (!line.startsWith('data:')) {
            return;
          }
          const payload = line.slice(5).trim();
          if (!payload) {
            return;
          }
          try {
            const event = JSON.parse(payload);
            runEvidence = reduceRunEvidence(runEvidence, event);
            if (event.type === 'run_started' && event.runId) {
              activeRunRef.current = event.runId;
            }
            if (event.type === 'rag_context') {
              citations = Array.isArray(event.citations) ? event.citations : [];
              knowledgeChecked = true;
              knowledgeAvailable = event.retrievalAvailable !== false;
            }
            if (event.type === 'token') {
              answer += event.content || '';
            }
            if (event.type === 'approval_required') {
              answer = `工具「${event.toolName || '未知工具'}」需要人工审批，审批通过后会继续执行。`;
            }
            if (event.type === 'error') {
              answer = `错误：${event.content || event.message || '未知错误'}`;
            }
            if (event.sessionId) {
              responseSessionId = event.sessionId;
              setSessionId(event.sessionId);
              localStorage.setItem('chatSessionId', event.sessionId);
            }
            if (event.traceId) {
              responseTraceId = event.traceId;
            }
            if (event.type === 'done') {
              activeRunRef.current = '';
            }
          } catch {
            return;
          }
          setMessages((items) => [...items.slice(0, -1), {
            role: 'assistant',
            content: answer || '处理中...',
            question,
            sessionId: responseSessionId,
            traceId: responseTraceId,
            runId: runEvidence?.runId || '',
            executionMode: runEvidence?.mode || '',
            citations,
            knowledgeChecked,
            knowledgeAvailable,
            traceEvents: runEvidence?.events || [],
            runEvidence
          }]);
        });
      }
      await loadSessions();
    } catch (error) {
      setMessages((items) => [...items.slice(0, -1), { role: 'assistant', content: resolveStreamErrorMessage(error) }]);
    } finally {
      setProcessing(false);
      abortRef.current = null;
      activeRunRef.current = '';
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
  const canToggleSessions = sessions.length > SESSION_PREVIEW_LIMIT;

  return (
    <>
      <PageHeader
        title="我的 Agent"
        desc={agent?.description || '定义能力，发起任务，并核对每次运行的结果与证据'}
        actions={<><button className="btn" onClick={boot}><RefreshCw size={16} />刷新</button><button className="btn" onClick={openSettings}><Settings2 size={16} />设置</button><button className="btn primary" onClick={startNewConversation} disabled={processing}><Plus size={16} />新对话</button></>}
      />
      <div className="chat-layout">
        <div className="chat-sidebar">
          <div className="panel-title"><strong>会话</strong><Badge tone="gray">{sessions.length}</Badge></div>
          <div className="session-list">
            {loading && <LoadingState label="加载会话" />}
            {!loading && sessions.length === 0 && <EmptyState title="暂无会话" desc="尚未保存任何对话记录。" />}
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
                  <button className="session-item" onClick={() => switchSession(session.sessionId)}>
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
                      <button className="icon-button" onClick={() => setRenameDraft({ sid: session.sessionId, title: session.title || '' })} title="重命名" aria-label="重命名会话"><Pencil size={15} /></button>
                      <button className="icon-button" onClick={() => setConfirm({ sid: session.sessionId, title: session.title || '新对话' })} title="删除" aria-label="删除会话"><Trash2 size={15} /></button>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
          {!loading && canToggleSessions && (
            <button
              type="button"
              className="session-list-toggle"
              onClick={() => setSessionsExpanded((expanded) => !expanded)}
              aria-expanded={sessionsExpanded}
            >
              {sessionsExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
              {sessionsExpanded ? '收起' : '加载更多'}
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
          <div className="messages">
            {messages.length === 0 && <EmptyState title="今天想完成什么？" desc="直接提出问题或任务，我会决定是否检索知识、使用工具或直接回答。" actions={<Bot size={24} />} />}
            {messages.map((message, index) => <MessageBubble key={`${message.role}-${index}`} message={message} />)}
            <div ref={scrollRef} />
          </div>
          <div className="composer">
            <div className="composer-input">
              <textarea className="textarea" rows={3} value={text} onChange={(event) => setText(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) send(); }} placeholder="告诉我的 Agent 你想完成什么" />
              <button className={`btn ${processing ? 'danger' : 'primary'}`} onClick={send}>{processing ? <Square size={16} /> : <Send size={16} />}{processing ? '停止' : '发送'}</button>
            </div>
          </div>
        </div>
      </div>
      {confirm && <ConfirmDialog title="删除会话" message={`确认删除「${confirm.title}」？`} danger confirmText="删除" onCancel={() => setConfirm(null)} onConfirm={() => deleteSession(confirm.sid)} />}
      {settingsOpen && agentDraft && (
        <Modal
          title="配置我的 Agent"
          size="lg"
          onClose={() => setSettingsOpen(false)}
          actions={<><button className="btn" onClick={() => setSettingsOpen(false)}>取消</button><button className="btn primary" disabled={savingSettings} onClick={saveSettings}>{savingSettings ? '保存中...' : '保存配置'}</button></>}
        >
          <div className="form-grid agent-settings-form">
            <Field label="名称">
              <input className="input" value={agentDraft.name} onChange={(event) => setAgentDraft({ ...agentDraft, name: event.target.value })} />
            </Field>
            <Field label="模型">
              <select className="select" value={agentDraft.modelId} onChange={(event) => setAgentDraft({ ...agentDraft, modelId: event.target.value })}>
                <option value="">跟随系统默认模型</option>
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

function MessageBubble({ message }) {
  const html = DOMPurify.sanitize(marked.parse(message.content || ''));
  if (message.role === 'assistant') {
    return (
      <div className="message assistant">
        <div className="markdown-body" dangerouslySetInnerHTML={{ __html: html }} />
        <MessageOutcome message={message} />
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
