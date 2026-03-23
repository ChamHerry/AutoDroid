import { getToken, clearToken } from './auth'

const BASE = '/api'

export class ApiError extends Error {
  status: number
  errorCode?: string
  constructor(message: string, status: number, errorCode?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = errorCode
  }
}

function authHeaders(): Record<string, string> {
  const token = getToken()
  return token ? { 'Authorization': `Bearer ${token}` } : {}
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = { ...authHeaders(), ...init?.headers }
  const res = await fetch(`${BASE}${path}`, { ...init, headers })
  if (res.status === 401 || res.status === 403) {
    clearToken()
    window.location.reload()
    throw new ApiError('认证失败', res.status)
  }
  const json = await res.json()
  if (!json.success) throw new ApiError(json.error ?? 'Request failed', res.status, json.errorCode)
  return json.data as T
}

function post<T>(path: string, body: Record<string, unknown>) {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

// ── Types ──

export interface StatusData {
  version: string
  uptime: number
  status: string
  accessibilityServiceConnected: boolean
}

export interface DeviceInfo {
  model: string
  brand: string
  sdkVersion: number
  screenWidth: number
  screenHeight: number
  batteryLevel: number
  [key: string]: unknown
}

export interface UiNode {
  text: string | null
  desc: string | null
  id: string | null
  className: string | null
  packageName: string | null
  clickable: boolean
  longClickable: boolean
  scrollable: boolean
  enabled: boolean
  checked: boolean
  focused: boolean
  selected: boolean
  editable: boolean
  visibleToUser: boolean
  depth?: number
  indexInParent?: number
  drawingOrder?: number
  childCount: number
  bounds: { left: number; top: number; right: number; bottom: number }
  boundsInParent: { left: number; top: number; right: number; bottom: number }
  // multi-window dump extras
  windowType?: string
  windowLayer?: number
  windowTitle?: string | null
  children?: UiNode[]
}

export interface ShellResult {
  code: number
  stdout: string
  stderr: string
}

// ── Dashboard ──

export function getStatus() {
  return request<StatusData>('/status')
}

export function getDeviceInfo() {
  return request<DeviceInfo>('/device/info')
}

// ── Inspector ──

export function getUiDump() {
  return request<UiNode>('/ui/dump')
}

/**
 * Returns a screenshot URL with the auth token as a query parameter.
 *
 * SECURITY NOTE: This places the long-lived auth token in the URL, which means
 * it may be logged in server access logs, browser history, Referer headers, and
 * network intermediary logs. This is a known limitation because <img src="...">
 * does not support Authorization headers. Mitigations:
 * - The server binds to a private/trusted network by default.
 * - Use `adb forward` for remote access rather than exposing the port directly.
 * - Consider rotating tokens periodically via POST /api/auth/rotate-tokens.
 */
export function getScreenshotUrl() {
  const token = getToken()
  const params = new URLSearchParams({ t: String(Date.now()) })
  if (token) params.set('token', token)
  return `${BASE}/screenshot?${params}`
}

// ── Actions ──

export function tapPoint(x: number, y: number) {
  return post<{ clicked: boolean }>('/actions/click', { x, y })
}

export function swipe(x1: number, y1: number, x2: number, y2: number, duration = 300) {
  return post<{ swiped: boolean }>('/actions/swipe', { x1, y1, x2, y2, duration })
}

export function pressKey(action: string) {
  return post<{ performed: boolean }>('/actions/key', { action })
}

export function inputText(selector: Record<string, unknown>, text: string, timeout = 5000) {
  return post<{ inputSet: boolean }>('/ui/input', { selector, text, timeout })
}

export function launchApp(packageName: string) {
  return post<{ launched: boolean }>('/app/launch', { packageName })
}

export function getCurrentApp() {
  return request<{ packageName: string }>('/app/current')
}

// ── Shell ──

export function execShell(command: string, root = false) {
  return post<ShellResult>('/shell/exec', { command, root })
}

// ── Auth ──

export interface RotateTokensResult {
  fullToken: string
  readToken: string
  message: string
  warning: string
}

/**
 * Rotate both FULL and READ API tokens. Requires FULL token auth.
 * The caller must save the new fullToken immediately — old tokens
 * are invalidated on the server upon return.
 */
export function rotateTokens() {
  return post<RotateTokensResult>('/auth/rotate-tokens', {})
}
