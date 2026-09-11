import { Activity, ArrowRight, BookOpen, BrainCircuit, FolderKanban, FileText, GitBranch, MessageSquare, RefreshCw, ShieldCheck, Sparkles, Users, Wrench } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Badge, Metric, PageHeader } from '../components/ui.jsx';
import { formatTime, truncate } from '../utils/format.js';

export function OverviewPage({ api, user, admin, goPage, toast }) {
  const [data, setData] = useState({
    quota: '-',
    knowledge: 0,
    files: 0,
    sessions: 0,
    memories: 0,
    traces: 0,
    resources: 0,
    recentTraces: [],
    recentFiles: [],
    recentKnowledge: []
  });

  const load = async () => {
    const [quota, knowledge, files, sessions, memories, traces, resources] = await Promise.all([
      api.get('/api/v1/token/remaining-quota').catch(() => ({})),
      api.get('/api/v1/resources/summary').catch(() => ({})),
      api.get('/api/v1/files/list').catch(() => ({})),
      api.get('/api/v1/analysis/sessions').catch(() => ({})),
      api.get('/api/v1/memories/stats').catch(() => ({})),
      api.get('/api/v1/agent-traces?limit=5').catch(() => ({})),
      api.get('/api/v1/resources/assets?limit=5').catch(() => ({}))
    ]);
    setData({
      quota: quota.remainingTokens === -1 ? '不限' : quota.remainingTokens ?? '-',
      knowledge: knowledge.knowledgeCount || 0,
      files: (files.files || []).length,
      sessions: (sessions.sessions || []).length,
      memories: memories.totalMemories || 0,
      traces: (traces.traces || []).length,
      resources: resources.summary?.totalAssets || (resources.assets || []).length,
      recentTraces: (traces.traces || []).slice(0, 5),
      recentFiles: (files.files || []).slice(0, 5),
      recentKnowledge: (resources.assets || []).filter((item) => item.resourceType === 'KNOWLEDGE').slice(0, 5)
    });
  };

  useEffect(() => { load(); }, []);

  return (
    <>
      <PageHeader title="工作台" desc="平台运行概览、资源状态和常用入口" actions={<button className="btn" onClick={() => { load(); toast('已刷新工作台', 'success'); }}><RefreshCw size={16} />刷新</button>} />
      <div className="grid grid-4">
        <Metric label="健康状态" value="UP" hint="后端服务可用" />
        <Metric label="剩余额度" value={data.quota} hint="当前账号 Token" />
        <Metric label="知识文档" value={data.knowledge} />
        <Metric label="文件数量" value={data.files} />
      </div>
      <div className="grid grid-4" style={{ marginTop: 16 }}>
        <Metric label="会话数" value={data.sessions} />
        <Metric label="记忆数" value={data.memories} />
        <Metric label="执行轨迹" value={data.traces} />
        <Metric label="资料总数" value={data.resources} />
      </div>
      <div className="dashboard-band" style={{ marginTop: 16 }}>
        <div className="dashboard-band-head">
          <div>
            <strong>常用入口</strong>
            <p>从这里直接进入主流程页面，不再绕路。</p>
          </div>
          <Badge tone="blue">主流程</Badge>
        </div>
        <div className="quick-grid">
          <button className="quick-card" onClick={() => goPage('chat')}><MessageSquare size={18} /><strong>开始对话</strong><span>进入 Agent 分析入口</span></button>
          <button className="quick-card" onClick={() => goPage('resources')}><FolderKanban size={18} /><strong>资料中心</strong><span>文件、知识、RAG 状态</span></button>
          <button className="quick-card" onClick={() => goPage('knowledge')}><BookOpen size={18} /><strong>知识库</strong><span>检索、上传、文本录入</span></button>
          <button className="quick-card" onClick={() => goPage('files')}><FileText size={18} /><strong>文件</strong><span>解析状态和内容详情</span></button>
          <button className="quick-card" onClick={() => goPage('memory')}><BrainCircuit size={18} /><strong>记忆中心</strong><span>短期、长期、画像</span></button>
          <button className="quick-card" onClick={() => goPage('traces')}><GitBranch size={18} /><strong>执行追踪</strong><span>编排决策和任务轨迹</span></button>
          {admin && <button className="quick-card" onClick={() => goPage('users')}><Users size={18} /><strong>用户管理</strong><span>账号、角色、配额</span></button>}
          {admin && <button className="quick-card" onClick={() => goPage('audit')}><ShieldCheck size={18} /><strong>审计日志</strong><span>操作和安全记录</span></button>}
        </div>
      </div>
      <div className="grid grid-2" style={{ marginTop: 16 }}>
        <div className="card panel-card">
          <div className="section-title">当前身份</div>
          <p className="panel-primary">{user.nickname || user.username}</p>
          <p className="panel-muted">{user.tenantId || 'default'} · {(user.roles || []).join(', ') || 'USER'}</p>
          <div className="mini-status-row">
            <span><Activity size={14} />在线</span>
            <span><Sparkles size={14} />Agent</span>
            <span><Wrench size={14} />工具</span>
          </div>
        </div>
        <div className="card panel-card">
          <div className="section-title">最近状态</div>
          <div className="mini-list">
            <MiniRow title="最近轨迹" items={data.recentTraces} onOpen={() => goPage('traces')} render={(item) => (
              <>
                <strong>{truncate(item.question || '-', 48)}</strong>
                <span>{item.selectedAgent || '-'} · {formatTime(item.createdAt)}</span>
              </>
            )} />
            <MiniRow title="最近知识" items={data.recentKnowledge} onOpen={() => goPage('resources')} render={(item) => (
              <>
                <strong>{truncate(item.name || item.title || item.content || '-', 48)}</strong>
                <span>{item.status || '可用'} · {formatTime(item.updatedAt || item.createdAt)}</span>
              </>
            )} />
          </div>
        </div>
      </div>
    </>
  );
}

function MiniRow({ title, items, onOpen, render }) {
  return (
    <div className="mini-row">
      <div className="mini-row-head">
        <strong>{title}</strong>
        <button className="text-button" onClick={onOpen}><ArrowRight size={14} />查看</button>
      </div>
      <div className="mini-row-list">
        {items.length ? items.map((item, index) => (
          <div className="mini-row-item" key={`${title}-${index}`}>
            {render(item)}
          </div>
        )) : <span className="panel-muted">暂无记录</span>}
      </div>
    </div>
  );
}
