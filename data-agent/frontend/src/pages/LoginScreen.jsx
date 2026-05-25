import { Brain } from 'lucide-react';
import { useState } from 'react';

export function LoginScreen({ api, onAuth, notice, setNotice }) {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ username: '', password: '', nickname: '', email: '', tenantId: '' });

  const submit = async (event) => {
    event.preventDefault();
    const endpoint = mode === 'login'
      ? '/api/v1/auth/login'
      : mode === 'bootstrap'
        ? '/api/v1/auth/bootstrap-admin'
        : '/api/v1/auth/register';
    const payload = mode === 'login'
      ? { username: form.username, password: form.password }
      : { username: form.username, password: form.password, nickname: form.nickname, email: form.email, tenantId: form.tenantId };
    const res = await api.post(endpoint, payload);
    if (res.success && res.data) {
      onAuth(res.data);
      return;
    }
    setNotice(res.message || '操作失败');
  };

  return (
    <div className="login-page">
      <form className="login-panel" onSubmit={submit}>
        <div className="brand" style={{ border: 0, padding: 0 }}>
          <div className="brand-mark"><Brain size={20} /></div>
          <div>
            <div style={{ color: '#0f172a', fontWeight: 800, fontSize: 20 }}>Data Agent</div>
            <div style={{ color: '#64748b', fontSize: 13 }}>企业级数据分析智能体</div>
          </div>
        </div>
        <div className="tabs">
          <button type="button" className={`tab ${mode === 'login' ? 'active' : ''}`} onClick={() => setMode('login')}>登录</button>
          <button type="button" className={`tab ${mode === 'register' ? 'active' : ''}`} onClick={() => setMode('register')}>注册</button>
          <button type="button" className={`tab ${mode === 'bootstrap' ? 'active' : ''}`} onClick={() => setMode('bootstrap')}>初始化管理员</button>
        </div>
        <div className="form-stack">
          <input className="input" placeholder="用户名" value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} />
          <input className="input" type="password" placeholder="密码" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} />
          {mode !== 'login' && (
            <>
              <input className="input" placeholder="昵称" value={form.nickname} onChange={(event) => setForm({ ...form, nickname: event.target.value })} />
              <input className="input" placeholder="邮箱，可选" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} />
              <input className="input" placeholder="租户 ID，可选" value={form.tenantId} onChange={(event) => setForm({ ...form, tenantId: event.target.value })} />
            </>
          )}
          {mode === 'bootstrap' && <div className="notice-text">仅系统没有任何管理员时可用；已有管理员后会自动拒绝。</div>}
          <button className="btn primary" type="submit">{mode === 'login' ? '登录' : mode === 'bootstrap' ? '创建首个管理员' : '创建普通账号'}</button>
          {notice && <div style={{ color: '#b45309', fontSize: 13 }}>{notice}</div>}
        </div>
      </form>
    </div>
  );
}
