import { ArrowLeft, Brain, LogOut, ShieldCheck } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { createApiClient } from './api/client.js';
import { ToastHost } from './components/ui.jsx';
import { adminNavGroups, adminPages, userNavGroups } from './config/navigation.jsx';
import { AgentApprovalsPage, AgentsPage, DatasourcesPage, ModelsPage, RolesPage, SkillsPage, UsersPage } from './pages/AdminPages.jsx';
import { AuditPage } from './pages/AuditPage.jsx';
import { AgentEvalPage } from './pages/AgentEvalPage.jsx';
import { ChatPage } from './pages/ChatPage.jsx';
import { ExpertsPage } from './pages/ExpertsPage.jsx';
import { FilesPage } from './pages/FilesPage.jsx';
import { KnowledgePage } from './pages/KnowledgePage.jsx';
import { LoginScreen } from './pages/LoginScreen.jsx';
import { MemoryPage } from './pages/MemoryPage.jsx';
import { QualityPage } from './pages/QualityPage.jsx';
import { ResourceCenterPage } from './pages/ResourceCenterPage.jsx';
import { StatsPage } from './pages/StatsPage.jsx';
import { TracePage } from './pages/TracePage.jsx';

function readJson(key, fallback) {
  try {
    return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback));
  } catch {
    return fallback;
  }
}

export default function App() {
  const [token, setToken] = useState(localStorage.getItem('accessToken') || '');
  const [refreshToken, setRefreshToken] = useState(localStorage.getItem('refreshToken') || '');
  const [user, setUser] = useState(readJson('userInfo', {}));
  const [page, setPage] = useState('chat');
  const [consoleMode, setConsoleMode] = useState('agent');
  const [traceUserId, setTraceUserId] = useState('');
  const [notice, setNotice] = useState('');
  const [toasts, setToasts] = useState([]);

  const toast = useCallback((message, type = 'info') => {
    const id = `${Date.now()}-${Math.random()}`;
    setToasts((items) => [...items, { id, message, type }]);
    window.setTimeout(() => setToasts((items) => items.filter((item) => item.id !== id)), 3600);
  }, []);

  const logout = useCallback(() => {
    setToken('');
    setRefreshToken('');
    setUser({});
    setConsoleMode('agent');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userInfo');
    localStorage.removeItem('chatSessionId');
  }, []);

  const api = useMemo(() => createApiClient(token, logout), [token, logout]);
  const admin = Array.isArray(user.roles) && user.roles.includes('ADMIN');

  const saveAuth = (auth) => {
    const nextUser = {
      userId: auth.userId,
      username: auth.username,
      nickname: auth.nickname,
      tenantId: auth.tenantId,
      roles: auth.roles || []
    };
    setToken(auth.accessToken);
    setRefreshToken(auth.refreshToken);
    setUser(nextUser);
    localStorage.setItem('accessToken', auth.accessToken);
    localStorage.setItem('refreshToken', auth.refreshToken);
    localStorage.setItem('userInfo', JSON.stringify(nextUser));
    setPage('chat');
    setConsoleMode('agent');
    toast('登录成功', 'success');
  };

  if (!token || !refreshToken) {
    return <LoginScreen api={api} onAuth={saveAuth} notice={notice} setNotice={setNotice} />;
  }

  const goPage = (key) => {
    if (adminPages.includes(key) && !admin) {
      setNotice('当前账号没有管理权限');
      return;
    }
    setNotice('');
    if (key === 'traces') {
      setTraceUserId('');
    }
    setPage(key);
  };

  const openUserTraces = (userId) => {
    setTraceUserId(userId || '');
    setPage('traces');
    setNotice('');
  };

  const enterAdminConsole = () => {
    setConsoleMode('admin');
    setTraceUserId('');
    setPage('traces');
    setNotice('');
  };

  const leaveAdminConsole = () => {
    setConsoleMode('agent');
    setPage('chat');
    setNotice('');
  };

  const visibleGroups = consoleMode === 'admin' && admin ? adminNavGroups : userNavGroups;
  const activeItem = visibleGroups.flatMap((group) => group.items).find((item) => item.key === page);

  return (
    <>
      <div className="app-shell">
        <aside className="sidebar">
          <div className="brand">
            <div className="brand-mark"><Brain size={20} /></div>
            <div>
              <div className="brand-title">Data Agent</div>
              <div className="brand-subtitle">{consoleMode === 'admin' ? 'Agent Control Plane' : 'Java Agent Harness'}</div>
            </div>
          </div>
          {visibleGroups.map((group) => (
            <div className="nav-group" key={group.label}>
              <div className="nav-label">{group.label}</div>
              {group.items.map((item) => {
                const Icon = item.icon;
                return (
                  <button key={item.key} className={`nav-button ${page === item.key ? 'active' : ''}`} onClick={() => goPage(item.key)}>
                    <Icon size={18} />
                    <span>{item.label}</span>
                  </button>
                );
              })}
            </div>
          ))}
        </aside>
        <main className="main">
          <header className="topbar">
            <div className="topbar-title">
              <span>{activeItem?.label || '工作台'}</span>
              <small>{consoleMode === 'admin' ? 'Runtime · Capabilities · Governance' : 'Reference App · 由 Agent Harness 驱动'}</small>
            </div>
            <div className="toolbar">
              {notice && <span className="notice-text">{notice}</span>}
              {admin && consoleMode === 'agent' && <button className="btn" onClick={enterAdminConsole}><ShieldCheck size={16} />Control Plane</button>}
              {consoleMode === 'admin' && <button className="btn" onClick={leaveAdminConsole}><ArrowLeft size={16} />返回我的 Agent</button>}
              <div className="account-chip">
                <strong>{user.nickname || user.username}</strong>
                <span>{user.tenantId || 'default'}</span>
                {admin && <span className="badge amber">ADMIN</span>}
              </div>
              <button className="btn ghost" onClick={logout}><LogOut size={16} />退出</button>
            </div>
          </header>
          <section className="content">
            {page === 'chat' && <ChatPage api={api} token={token} toast={toast} onOpenExperts={() => goPage('experts')} />}
            {page === 'experts' && <ExpertsPage api={api} toast={toast} />}
            {page === 'memory' && <MemoryPage api={api} toast={toast} />}
            {page === 'knowledge' && <KnowledgePage api={api} toast={toast} />}
            {page === 'files' && <FilesPage api={api} toast={toast} />}
            {page === 'users' && admin && <UsersPage api={api} toast={toast} />}
            {page === 'roles' && admin && <RolesPage api={api} toast={toast} />}
            {page === 'models' && admin && <ModelsPage api={api} toast={toast} />}
            {page === 'skills' && admin && <SkillsPage api={api} toast={toast} />}
            {page === 'agents' && admin && <AgentsPage api={api} toast={toast} onOpenTraces={openUserTraces} />}
            {page === 'datasources' && admin && <DatasourcesPage api={api} toast={toast} />}
            {page === 'approvals' && admin && <AgentApprovalsPage api={api} toast={toast} />}
            {page === 'resources' && admin && <ResourceCenterPage api={api} toast={toast} />}
            {page === 'stats' && admin && <StatsPage api={api} />}
            {page === 'quality' && admin && <QualityPage api={api} toast={toast} />}
            {page === 'agent-evals' && admin && <AgentEvalPage api={api} toast={toast} />}
            {page === 'traces' && admin && <TracePage key={traceUserId || 'all-users'} api={api} initialUserId={traceUserId} />}
            {page === 'audit' && admin && <AuditPage api={api} />}
          </section>
        </main>
      </div>
      <ToastHost toasts={toasts} onClose={(id) => setToasts((items) => items.filter((item) => item.id !== id))} />
    </>
  );
}
