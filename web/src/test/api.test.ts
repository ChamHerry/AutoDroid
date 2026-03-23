import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setToken, clearToken } from '../auth'

// Mock fetch globally
const mockFetch = vi.fn()
vi.stubGlobal('fetch', mockFetch)

// Prevent reload in tests
Object.defineProperty(window, 'location', {
  value: { reload: vi.fn() },
  writable: true,
})

describe('api', () => {
  beforeEach(async () => {
    localStorage.clear()
    mockFetch.mockReset()
    // Re-import to get fresh module
    vi.resetModules()
  })

  it('getScreenshotUrl includes token in query params', async () => {
    setToken('my-secret')
    const { getScreenshotUrl } = await import('../api')
    const url = getScreenshotUrl()
    expect(url).toContain('token=my-secret')
    expect(url).toContain('/api/screenshot')
  })

  it('getScreenshotUrl works without token', async () => {
    clearToken()
    const { getScreenshotUrl } = await import('../api')
    const url = getScreenshotUrl()
    expect(url).toContain('/api/screenshot')
    expect(url).not.toContain('token=')
  })

  it('request adds Authorization header when token exists', async () => {
    setToken('test-token')
    mockFetch.mockResolvedValueOnce({
      status: 200,
      json: () => Promise.resolve({ success: true, data: { version: '1.0.0' } }),
    })

    const { getStatus } = await import('../api')
    await getStatus()

    expect(mockFetch).toHaveBeenCalledOnce()
    const [, init] = mockFetch.mock.calls[0]
    expect(init.headers).toHaveProperty('Authorization', 'Bearer test-token')
  })

  it('request throws on error response', async () => {
    setToken('test-token')
    mockFetch.mockResolvedValueOnce({
      status: 200,
      json: () => Promise.resolve({ success: false, error: 'Something failed' }),
    })

    const { getStatus } = await import('../api')
    await expect(getStatus()).rejects.toThrow('Something failed')
  })

  it('request clears token on 401', async () => {
    setToken('bad-token')
    mockFetch.mockResolvedValueOnce({
      status: 401,
      json: () => Promise.resolve({ success: false, error: 'Unauthorized' }),
    })

    const { getStatus } = await import('../api')
    await expect(getStatus()).rejects.toThrow('认证失败')
    expect(localStorage.getItem('autodroid_token')).toBeNull()
  })
})
