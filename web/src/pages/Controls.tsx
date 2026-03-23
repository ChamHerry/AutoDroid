import { useState } from 'react'
import { tapPoint, swipe, pressKey, launchApp, getCurrentApp, getScreenshotUrl } from '../api'

export default function Controls() {
  const [msg, setMsg] = useState('')
  const [msgType, setMsgType] = useState<'success' | 'error'>('success')
  const [screenshotUrl, setScreenshotUrl] = useState('')

  const showMsg = (text: string, type: 'success' | 'error' = 'success') => {
    setMsg(text)
    setMsgType(type)
    setTimeout(() => setMsg(''), 2500)
  }

  const refreshScreenshot = () => setScreenshotUrl(getScreenshotUrl())

  // Tap
  const [tapX, setTapX] = useState('500')
  const [tapY, setTapY] = useState('1000')
  const doTap = async () => {
    try {
      await tapPoint(+tapX, +tapY)
      showMsg(`已点击 (${tapX}, ${tapY})`)
      setTimeout(refreshScreenshot, 500)
    } catch (e) { showMsg(`失败: ${e}`, 'error') }
  }

  // Swipe
  const [sx1, setSx1] = useState('500')
  const [sy1, setSy1] = useState('1500')
  const [sx2, setSx2] = useState('500')
  const [sy2, setSy2] = useState('500')
  const [dur, setDur] = useState('300')
  const doSwipe = async () => {
    try {
      await swipe(+sx1, +sy1, +sx2, +sy2, +dur)
      showMsg('滑动完成')
      setTimeout(refreshScreenshot, 500)
    } catch (e) { showMsg(`失败: ${e}`, 'error') }
  }

  // Keys
  const doKey = async (action: string) => {
    try {
      await pressKey(action)
      showMsg(`已执行: ${action}`)
      setTimeout(refreshScreenshot, 500)
    } catch (e) { showMsg(`失败: ${e}`, 'error') }
  }

  // Launch
  const [pkg, setPkg] = useState('')
  const doLaunch = async () => {
    if (!pkg.trim()) return
    try {
      await launchApp(pkg.trim())
      showMsg(`已启动: ${pkg}`)
      setTimeout(refreshScreenshot, 1000)
    } catch (e) { showMsg(`失败: ${e}`, 'error') }
  }

  // Current app
  const [currentApp, setCurrentApp] = useState('')
  const doGetCurrent = async () => {
    try {
      const r = await getCurrentApp()
      setCurrentApp(r.packageName)
    } catch (e) { showMsg(`失败: ${e}`, 'error') }
  }

  return (
    <div>
      {msg && <div className={`toast ${msgType === 'error' ? 'toast-error' : ''}`}>{msg}</div>}

      <div className="control-grid">
        {/* Screenshot preview */}
        <div className="control-section" style={{ gridColumn: '1 / -1' }}>
          <h3>屏幕预览</h3>
          <button className="btn" onClick={refreshScreenshot} style={{ marginBottom: 8 }}>截图</button>
          {screenshotUrl && (
            <div style={{ textAlign: 'center' }}>
              <img src={screenshotUrl} alt="截图" style={{ maxHeight: '50vh', borderRadius: 4 }} />
            </div>
          )}
        </div>

        {/* Tap */}
        <div className="control-section">
          <h3>坐标点击</h3>
          <div className="control-row">
            <span className="prop-key">X</span>
            <input className="control-input" value={tapX} onChange={e => setTapX(e.target.value)} />
            <span className="prop-key">Y</span>
            <input className="control-input" value={tapY} onChange={e => setTapY(e.target.value)} />
            <button className="btn" onClick={doTap}>点击</button>
          </div>
        </div>

        {/* Swipe */}
        <div className="control-section">
          <h3>滑动操作</h3>
          <div className="control-row">
            <span className="prop-key">起点</span>
            <input className="control-input" placeholder="X1" value={sx1} onChange={e => setSx1(e.target.value)} />
            <input className="control-input" placeholder="Y1" value={sy1} onChange={e => setSy1(e.target.value)} />
          </div>
          <div className="control-row">
            <span className="prop-key">终点</span>
            <input className="control-input" placeholder="X2" value={sx2} onChange={e => setSx2(e.target.value)} />
            <input className="control-input" placeholder="Y2" value={sy2} onChange={e => setSy2(e.target.value)} />
          </div>
          <div className="control-row">
            <span className="prop-key">时长</span>
            <input className="control-input" value={dur} onChange={e => setDur(e.target.value)} />
            <span style={{ color: 'var(--text2)', fontSize: 12 }}>ms</span>
            <button className="btn" onClick={doSwipe}>滑动</button>
          </div>
        </div>

        {/* Key buttons */}
        <div className="control-section">
          <h3>全局按键</h3>
          <div className="control-row" style={{ flexWrap: 'wrap' }}>
            <button className="key-btn" onClick={() => doKey('back')}>返回</button>
            <button className="key-btn" onClick={() => doKey('home')}>主页</button>
            <button className="key-btn" onClick={() => doKey('recents')}>最近任务</button>
            <button className="key-btn" onClick={() => doKey('notifications')}>通知栏</button>
          </div>
        </div>

        {/* Launch app */}
        <div className="control-section">
          <h3>启动应用</h3>
          <div className="control-row">
            <input
              className="control-input control-input-wide"
              placeholder="包名 (如 com.android.settings)"
              value={pkg}
              onChange={e => setPkg(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && doLaunch()}
            />
            <button className="btn" onClick={doLaunch}>启动</button>
          </div>
          <div className="control-row" style={{ marginTop: 8 }}>
            <button className="btn" onClick={doGetCurrent}
              style={{ background: 'var(--surface2)', color: 'var(--text)' }}>
              获取当前应用
            </button>
            {currentApp && <span style={{ color: 'var(--text2)', fontSize: 13 }}>{currentApp}</span>}
          </div>
        </div>
      </div>
    </div>
  )
}
