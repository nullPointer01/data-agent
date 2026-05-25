import DOMPurify from 'dompurify';
import { Bot, Link2, Plus, RefreshCw, RotateCcw, Send, Square, Terminal, ThumbsDown, ThumbsUp, Trash2 } from 'lucide-react';
import { marked } from 'marked';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Badge, ConfirmDialog, EmptyState, LoadingState, PageHeader } from '../components/ui.jsx';
import { formatTime } from '../utils/format.js';

function pickEnabled(items) {
  return (items || []).filter((item) => item.enabled !== false);
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

export function ChatPage({ api, token, toast }) {
  const [sessions, setSessions] = useState([]);
  const [sessionId, setSessionId] = useState(localStorage.getItem('chatSessionId') || '');
  const [messages, setMessages] = useState([]);
  const [text, setText] = useState('');
  const [models, setModels] = useState([]);
  const [skills, setSkills] = useState([]);
  const [agents, setAgents] = useState([]);
  const [choice, setChoice] = useState({ modelId: '', skillId: '', agentId: '' });
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [confirm, setConfirm] = useState(null);
  const [feedbacks, setFeedbacks] = useState({});
  const abortRef = useRef(null);
  const scrollRef = useRef(null);

  const loadSessions = useCallback(async () => {
    const res = await api.get('/api/v1/analysis/sessions');
    setSessions(res.sessions || []);
  }, [api]);

  const loadMessages = useCallback(async (sid) => {
    if (!sid) {
      setMessages([]);
      return;
    }
    const res = await api.get(`/api/v1/analysis/session/${sid}/messages`);
    setMessages(res.messages || []);
  }, [api]);

  const createSession = useCallback(async () => {
    const res = await api.post('/api/v1/analysis/session', {});
    const sid = res.sessionId || res.data?.sessionId;
    if (sid) {
      setSessionId(sid);
      localStorage.setItem('chatSessionId', sid);
      setMessages([]);
      await loadSessions();
      toast('已创建新会话', 'success');
    }
  }, [api, loadSessions, toast]);

  const boot = useCallback(async () => {
    setLoading(true);
    try {
      const [modelRes, skillRes, agentRes] = await Promise.all([
        api.get('/api/v1/models/list').catch(() => ({ models: [] })),
        api.get('/api/v1/skills/list').catch(() => ({ skills: [] })),
        api.get('/api/v1/my/agents?enabledOnly=true').catch(() => ({ agents: [] }))
      ]);
      setModels(modelRes.models || []);
      setSkills(skillRes.skills || []);
      setAgents(agentRes.agents || []);
      await loadSessions();
      if (sessionId) {
        await loadMessages(sessionId);
      } else {
        await createSession();
      }
    } finally {
      setLoading(false);
    }
  }, [api, createSession, loadMessages, loadSessions, sessionId]);

  useEffect(() => { boot(); }, []);
  useEffect(() => { scrollRef.current?.scrollIntoView({ block: 'end' }); }, [messages]);

  const switchSession = async (sid) => {
    setSessionId(sid);
    localStorage.setItem('chatSessionId', sid);
    await loadMessages(sid);
  };

  const deleteSession = async (sid) => {
    await api.delete(`/api/v1/analysis/session/${sid}`);
    if (sid === sessionId) {
      localStorage.removeItem('chatSessionId');
      setSessionId('');
      setMessages([]);
    }
    setConfirm(null);
    await loadSessions();
    toast('会话已删除', 'success');
  };

  const send = async () => {
    if (processing && abortRef.current) {
      abortRef.current.abort();
      return;
    }
    const question = text.trim();
    if (!question) {
      return;
    }
    setText('');
    setMessages((items) => [...items, { role: 'user', content: question }, { role: 'assistant', content: '思考中...' }]);
    setProcessing(true);
    abortRef.current = new AbortController();
    try {
      const response = await fetch('/api/v1/analysis/analyze/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ question, sessionId, ...Object.fromEntries(Object.entries(choice).filter(([, value]) => value)) }),
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
      let responseSessionId = sessionId;
      let citations = [];
      let traceEvents = [];
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
            if (event.type === 'rag_context') {
              citations = Array.isArray(event.citations) ? event.citations : [];
            }
            if (event.type === 'token') {
              answer += event.content || '';
            }
            if (event.type === 'tool_call') {
              traceEvents = [...traceEvents, {
                type: 'tool_call',
                title: event.toolName || 'unknown',
                content: event.result || ''
              }];
            }
            if (event.type === 'execution_plan') {
              traceEvents = [...traceEvents, {
                type: 'execution_plan',
                title: event.title || '执行计划',
                content: event.content || ''
              }];
            }
            if (event.type === 'parallel_precheck') {
              traceEvents = [...traceEvents, {
                type: 'parallel_precheck',
                title: event.title || '并行预检',
                content: event.content || ''
              }];
            }
            if (event.type === 'reflection') {
              traceEvents = [...traceEvents, {
                type: 'reflection',
                title: '策略反思',
                content: event.content || ''
              }];
            }
            if (event.type === 'orchestration') {
              traceEvents = [...traceEvents, {
                type: 'orchestration',
                title: event.title || '编排轨迹',
                content: event.content || ''
              }];
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
          } catch {
            return;
          }
          setMessages((items) => [...items.slice(0, -1), {
            role: 'assistant',
            content: answer || '处理中...',
            question,
            sessionId: responseSessionId,
            traceId: responseTraceId,
            citations,
            traceEvents
          }]);
        });
      }
      await loadSessions();
    } catch (error) {
      setMessages((items) => [...items.slice(0, -1), { role: 'assistant', content: resolveStreamErrorMessage(error) }]);
    } finally {
      setProcessing(false);
      abortRef.current = null;
    }
  };

  const submitFeedback = async (message, rating) => {
    const feedbackKey = message.traceId || `${message.sessionId || sessionId}:${message.question || message.content}`;
    await api.post('/api/v1/agent-feedbacks', {
      sessionId: message.sessionId || sessionId,
      traceId: message.traceId,
      rating,
      question: message.question,
      answer: message.content
    });
    setFeedbacks((items) => ({ ...items, [feedbackKey]: rating }));
    toast(rating === 'UP' ? '已记录正向反馈' : '已记录改进反馈', 'success');
  };

  return (
    <>
      <PageHeader
        title="智能对话"
        desc="支持模型、技能、Agent、知识库和文件上下文的综合分析入口"
        actions={<><button className="btn" onClick={boot}><RefreshCw size={16} />刷新</button><button className="btn primary" onClick={createSession}><Plus size={16} />新对话</button></>}
      />
      <div className="chat-layout">
        <div className="chat-sidebar">
          <div className="panel-title"><strong>会话</strong><Badge tone="gray">{sessions.length}</Badge></div>
          <div className="session-list">
            {loading && <LoadingState label="加载会话" />}
            {!loading && sessions.length === 0 && <EmptyState title="暂无会话" desc="创建一个新会话后开始分析。" />}
            {!loading && sessions.map((session) => (
              <div className={`session-row ${session.sessionId === sessionId ? 'active' : ''}`} key={session.sessionId}>
                <button className="session-item" onClick={() => switchSession(session.sessionId)}>
                  <strong>{session.title || '新对话'}</strong>
                  <div className="muted">{session.messageCount || 0} 条 · {formatTime(session.lastAccessAt)}</div>
                </button>
                <button className="icon-button" onClick={() => setConfirm({ sid: session.sessionId, title: session.title || '新对话' })}><Trash2 size={15} /></button>
              </div>
            ))}
          </div>
        </div>
        <div className="chat-main">
          <div className="messages">
            {messages.length === 0 && <EmptyState title="开始一次分析" desc="选择模型、技能或 Agent 后输入问题。" actions={<Bot size={24} />} />}
            {messages.map((message, index) => <MessageBubble key={`${message.role}-${index}`} message={message} feedbacks={feedbacks} onFeedback={submitFeedback} />)}
            <div ref={scrollRef} />
          </div>
          <div className="composer">
            <div className="toolbar composer-controls">
              <select className="select" value={choice.modelId} onChange={(event) => setChoice({ ...choice, modelId: event.target.value })}>
                <option value="">默认模型</option>
                {pickEnabled(models).map((item) => <option key={item.modelId} value={item.modelId}>{item.name}</option>)}
              </select>
              <select className="select" value={choice.skillId} onChange={(event) => setChoice({ ...choice, skillId: event.target.value })}>
                <option value="">自由对话</option>
                {pickEnabled(skills).map((item) => <option key={item.skillId} value={item.skillId}>{item.name}</option>)}
              </select>
              <select className="select" value={choice.agentId} onChange={(event) => setChoice({ ...choice, agentId: event.target.value })}>
                <option value="">默认 Agent</option>
                {pickEnabled(agents).map((item) => <option key={item.agentId} value={item.agentId}>{item.name}</option>)}
              </select>
            </div>
            <div className="composer-input">
              <textarea className="textarea" rows={3} value={text} onChange={(event) => setText(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) send(); }} placeholder="输入问题，Ctrl/⌘ + Enter 发送" />
              <button className={`btn ${processing ? 'danger' : 'primary'}`} onClick={send}>{processing ? <Square size={16} /> : <Send size={16} />}{processing ? '停止' : '发送'}</button>
            </div>
          </div>
        </div>
      </div>
      {confirm && <ConfirmDialog title="删除会话" message={`确认删除「${confirm.title}」？`} danger confirmText="删除" onCancel={() => setConfirm(null)} onConfirm={() => deleteSession(confirm.sid)} />}
    </>
  );
}

function MessageBubble({ message, feedbacks, onFeedback }) {
  const html = DOMPurify.sanitize(marked.parse(message.content || ''));
  if (message.role === 'assistant') {
    const feedbackKey = message.traceId || `${message.sessionId || ''}:${message.question || message.content}`;
    const currentFeedback = feedbacks?.[feedbackKey];
    return (
      <div className="message assistant">
        <div className="markdown-body" dangerouslySetInnerHTML={{ __html: html }} />
        {message.traceEvents?.length > 0 && <TraceList events={message.traceEvents} />}
        {message.citations?.length > 0 && <CitationList citations={message.citations} />}
        {message.content && message.content !== '思考中...' && !message.content.startsWith('错误：') && (
          <div className="feedback-bar">
            <button className={`icon-button bordered ${currentFeedback === 'UP' ? 'active' : ''}`} onClick={() => onFeedback(message, 'UP')} title="回答有帮助">
              <ThumbsUp size={14} />
            </button>
            <button className={`icon-button bordered ${currentFeedback === 'DOWN' ? 'active danger' : ''}`} onClick={() => onFeedback(message, 'DOWN')} title="回答需要改进">
              <ThumbsDown size={14} />
            </button>
          </div>
        )}
      </div>
    );
  }
  return <div className="message user">{message.content || ''}</div>;
}

function TraceList({ events }) {
  return (
    <div className="trace-list">
      {events.map((item, index) => (
        <div className={`trace-item ${item.type}`} key={`${item.type}-${index}`}>
          <div className="trace-title">
            {item.type === 'reflection' ? <RotateCcw size={13} /> : <Terminal size={13} />}
            <span>{item.title}</span>
          </div>
          {item.content && <p>{item.content}</p>}
        </div>
      ))}
    </div>
  );
}

function renderCitationLocation(item) {
  const range = Number(item?.charEnd || 0) > Number(item?.charStart || 0) ? `字符 ${item.charStart}-${item.charEnd}` : '';
  return [item?.sectionPath, range].filter(Boolean).join(' · ');
}

function CitationList({ citations }) {
  return (
    <div className="citation-list">
      <div className="citation-title"><Link2 size={14} />引用来源</div>
      {citations.map((item) => (
        <div className="citation-item" key={`${item.referenceId}-${item.sourceId}`}>
          <Badge tone={item.sourceType === 'knowledge' ? 'blue' : 'gray'}>{item.referenceId}</Badge>
          <span>{item.sourceId || item.sourceType}</span>
          <small>score {Number(item.score || 0).toFixed(2)}</small>
          {renderCitationLocation(item) && <small>{renderCitationLocation(item)}</small>}
          <p>{item.snippet}</p>
        </div>
      ))}
    </div>
  );
}
