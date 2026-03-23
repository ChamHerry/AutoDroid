import { useState } from 'react'
import { getToken, setToken, clearToken } from './auth'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import UiInspector from './pages/UiInspector'
import Controls from './pages/Controls'
import Shell from './pages/Shell'
import './style.css'

type Page = 'dashboard' | 'inspector' | 'controls' | 'shell'

export default function App() {
  const [page, setPage] = useState<Page>('dashboard')
  const [authenticated, setAuthenticated] = useState(!!getToken())

  function handleLogin(token: string) {
    setToken(token)
    setAuthenticated(true)
  }

  function handleLogout() {
    clearToken()
    setAuthenticated(false)
  }

  if (!authenticated) {
    return <Login onLogin={handleLogin} />
  }

  return (
    <div className="app">
      <nav className="nav">
        <span className="nav-title">AutoDroid</span>
        <div className="nav-links">
          <button className={page === 'dashboard' ? 'active' : ''} onClick={() => setPage('dashboard')}>
            仪表盘
          </button>
          <button className={page === 'inspector' ? 'active' : ''} onClick={() => setPage('inspector')}>
            界面检查
          </button>
          <button className={page === 'controls' ? 'active' : ''} onClick={() => setPage('controls')}>
            操控面板
          </button>
          <button className={page === 'shell' ? 'active' : ''} onClick={() => setPage('shell')}>
            终端
          </button>
        </div>
        <button className="nav-logout" onClick={handleLogout} title="登出">
          退出
        </button>
      </nav>
      <main className="content">
        {page === 'dashboard' && <Dashboard />}
        {page === 'inspector' && <UiInspector />}
        {page === 'controls' && <Controls />}
        {page === 'shell' && <Shell />}
      </main>
    </div>
  )
}
