import { useState } from 'react'

export default function Login({ onLogin }: { onLogin: (token: string) => void }) {
  const [token, setToken] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!token.trim()) return

    setLoading(true)
    setError('')

    try {
      // Validate token by calling a protected endpoint
      const res = await fetch('/api/device/info', {
        headers: { 'Authorization': `Bearer ${token.trim()}` },
      })
      const json = await res.json()
      if (json.success) {
        onLogin(token.trim())
      } else {
        setError(json.error || '认证失败')
      }
    } catch {
      setError('无法连接到服务器')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={handleSubmit}>
        <div className="login-icon">🔐</div>
        <h1 className="login-title">AutoDroid</h1>
        <p className="login-desc">请输入 API Token 以继续</p>

        <input
          className="login-input"
          type="password"
          placeholder="输入 Token"
          value={token}
          onChange={e => setToken(e.target.value)}
          autoFocus
        />

        {error && <div className="login-error">{error}</div>}

        <button className="login-btn" type="submit" disabled={loading || !token.trim()}>
          {loading ? '验证中...' : '登录'}
        </button>

        <p className="login-hint">
          Token 可在设备端 AutoDroid 应用的仪表盘页面查看
        </p>
      </form>
    </div>
  )
}
