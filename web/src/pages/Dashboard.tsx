import { useEffect, useState } from 'react'
import { getStatus, getDeviceInfo, type StatusData, type DeviceInfo } from '../api'

export default function Dashboard() {
  const [status, setStatus] = useState<StatusData | null>(null)
  const [device, setDevice] = useState<DeviceInfo | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const refresh = async () => {
    setLoading(true)
    setError('')
    try {
      const [s, d] = await Promise.all([getStatus(), getDeviceInfo()])
      setStatus(s)
      setDevice(d)
    } catch (e) {
      setError(e instanceof Error ? e.message : '连接失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { refresh() }, [])

  if (loading) return <div className="loading">正在连接设备...</div>
  if (error) return <div className="error">错误: {error}<br /><button className="btn" style={{ marginTop: 12 }} onClick={refresh}>重试</button></div>

  return (
    <div>
      <div className="card">
        <div className="card-title">服务状态</div>
        <div className="grid">
          <div className="stat">
            <div className="stat-label">运行状态</div>
            <div className="stat-value">
              <span className={`badge ${status?.status === 'running' ? 'badge-green' : 'badge-red'}`}>
                {status?.status === 'running' ? '运行中' : status?.status ?? '未知'}
              </span>
            </div>
          </div>
          <div className="stat">
            <div className="stat-label">版本</div>
            <div className="stat-value">{status?.version ?? '-'}</div>
          </div>
          <div className="stat">
            <div className="stat-label">运行时长</div>
            <div className="stat-value">{formatUptime(status?.uptime ?? 0)}</div>
          </div>
        </div>
      </div>

      {device && (
        <div className="card">
          <div className="card-title">设备信息</div>
          <div className="grid">
            <Stat label="品牌" value={device.brand} />
            <Stat label="型号" value={device.model} />
            <Stat label="Android SDK" value={device.sdkVersion} />
            <Stat label="屏幕分辨率" value={`${device.screenWidth} x ${device.screenHeight}`} />
            <Stat label="电池电量" value={`${device.batteryLevel}%`} />
          </div>
        </div>
      )}

      {device && (
        <div className="card">
          <div className="card-title">全部属性</div>
          {Object.entries(device).map(([k, v]) => (
            <div className="prop-row" key={k}>
              <span className="prop-key">{k}</span>
              <span className="prop-val">{String(v)}</span>
            </div>
          ))}
        </div>
      )}

      <button className="btn" onClick={refresh} style={{ marginTop: 8 }}>刷新</button>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: unknown }) {
  return (
    <div className="stat">
      <div className="stat-label">{label}</div>
      <div className="stat-value">{String(value)}</div>
    </div>
  )
}

function formatUptime(ms: number): string {
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}秒`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}分${s % 60}秒`
  const h = Math.floor(m / 60)
  return `${h}时${m % 60}分`
}
