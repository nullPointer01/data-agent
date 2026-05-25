import { Brain, LogOut } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { createApiClient } from './api/client.js';
import { ToastHost } from './components/ui.jsx';
import { adminPages, navGroups } from './config/navigation.jsx';
import { AgentsPage, DatasourcesPage, ModelsPage, RolesPage, SkillsPage, UsersPage } from './pages/AdminPages.jsx';
import { AdminRequestPage } from './pages/AdminRequestPage.jsx';
import { AuditPage } from './pages/AuditPage.jsx';
import { ChatPage } from './pages/ChatPage.jsx';
import { FeedbackPage } from './pages/FeedbackPage.jsx';
import { FilesPage } from './pages/FilesPage.jsx';
import { KnowledgePage } from './pages/KnowledgePage.jsx';
import { LoginScreen } from './pages/LoginScreen.jsx';
import { MemoryPage } from './pages/MemoryPage.jsx';
import { OverviewPage } from './pages/OverviewPage.jsx';
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
  const [page, setPage] = useState('overview');
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
    setPage('overview');
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
    setPage(key);
  };

  const visibleGroups = navGroups
    .map((group) => ({ ...group, items: group.admin && !admin ? [] : group.items }))
    .filter((group) => group.items.length > 0);

  return (
    <>
      <div className="app-shell">
        <aside className="sidebar">
          <div className="brand">
            <div className="brand-mark"><Brain size={20} /></div>
            <div>
              <div className="brand-title">Data Agent</div>
              <div className="brand-subtitle">Enterprise AI Platform</div>
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
            <div>
              <strong>{user.nickname || user.username}</strong>
              <span className="topbar-tenant">{user.tenantId || 'default'}</span>
              {admin && <span className="badge amber">ADMIN</span>}
            </div>
            <div className="toolbar">
              {notice && <span className="notice-text">{notice}</span>}
              <button className="btn" onClick={logout}><LogOut size={16} />退出</button>
            </div>
          </header>
          <section className="content">
            {page === 'overview' && <OverviewPage api={api} user={user} admin={admin} goPage={goPage} toast={toast} />}
            {page === 'chat' && <ChatPage api={api} token={token} toast={toast} />}
            {page === 'resources' && <ResourceCenterPage api={api} toast={toast} />}
            {page === 'memory' && <MemoryPage api={api} toast={toast} />}
            {page === 'knowledge' && <KnowledgePage api={api} toast={toast} />}
            {page === 'files' && <FilesPage api={api} toast={toast} />}
            {page === 'admin-request' && <AdminRequestPage api={api} user={user} admin={admin} toast={toast} goPage={goPage} />}
            {page === 'users' && admin && <UsersPage api={api} toast={toast} />}
            {page === 'roles' && admin && <RolesPage api={api} toast={toast} />}
            {page === 'models' && admin && <ModelsPage api={api} toast={toast} />}
            {page === 'skills' && admin && <SkillsPage api={api} toast={toast} />}
            {page === 'agents' && admin && <AgentsPage api={api} toast={toast} />}
            {page === 'datasources' && admin && <DatasourcesPage api={api} toast={toast} />}
            {page === 'stats' && admin && <StatsPage api={api} />}
            {page === 'quality' && admin && <QualityPage api={api} toast={toast} />}
            {page === 'traces' && admin && <TracePage api={api} />}
            {page === 'feedbacks' && admin && <FeedbackPage api={api} toast={toast} />}
            {page === 'audit' && admin && <AuditPage api={api} />}
          </section>
        </main>
      </div>
      <ToastHost toasts={toasts} onClose={(id) => setToasts((items) => items.filter((item) => item.id !== id))} />
    </>
  );
}
