import { useRef, useEffect, useCallback } from 'react'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import '@xterm/xterm/css/xterm.css'
import { execShell } from '../api'

function commonPrefix(strs: string[]): string {
  if (strs.length === 0) return ''
  let prefix = strs[0]
  for (let i = 1; i < strs.length; i++) {
    while (!strs[i].startsWith(prefix)) {
      prefix = prefix.slice(0, -1)
      if (!prefix) return ''
    }
  }
  return prefix
}

/** Ask the device shell for tab-completion candidates (file/dir names). */
async function getCompletions(partial: string): Promise<string[]> {
  const words = partial.split(/\s+/)
  const last = words[words.length - 1] || ''

  // Determine the directory to list and the prefix to match
  const slashIdx = last.lastIndexOf('/')
  const dir = slashIdx >= 0 ? last.slice(0, slashIdx + 1) || '/' : '.'
  const prefix = slashIdx >= 0 ? last.slice(slashIdx + 1) : last

  try {
    // List directory, append / to subdirs
    const escaped = dir.replace(/'/g, "'\\''")
    const result = await execShell(
      `ls -1p '${escaped}' 2>/dev/null | grep "^${prefix.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}"`,
    )
    if (result.code === 0 && result.stdout.trim()) {
      const dirPrefix = slashIdx >= 0 ? dir : ''
      return result.stdout.trim().split('\n').map(name => dirPrefix + name)
    }
  } catch { /* ignore */ }
  return []
}

export default function Shell() {
  const containerRef = useRef<HTMLDivElement>(null)
  const termRef = useRef<Terminal | null>(null)
  const fitRef = useRef<FitAddon | null>(null)
  const lineRef = useRef('')
  const historyRef = useRef<string[]>([])
  const histIdxRef = useRef(-1)
  const runningRef = useRef(false)

  const prompt = useCallback(() => {
    termRef.current?.write('\r\n\x1b[32m$ \x1b[0m')
  }, [])

  const runCommand = useCallback(async (cmd: string) => {
    const term = termRef.current
    if (!term || !cmd.trim()) { prompt(); return }

    runningRef.current = true
    // Add to history
    const h = historyRef.current
    if (h[0] !== cmd) historyRef.current = [cmd, ...h].slice(0, 100)
    histIdxRef.current = -1

    try {
      const result = await execShell(cmd.trim())

      if (result.stdout) {
        const lines = result.stdout.replace(/\r?\n$/, '').split('\n')
        for (const line of lines) {
          term.write('\r\n' + line)
        }
      }
      if (result.stderr) {
        const lines = result.stderr.replace(/\r?\n$/, '').split('\n')
        for (const line of lines) {
          term.write('\r\n\x1b[31m' + line + '\x1b[0m')
        }
      }
      if (result.code !== 0) {
        term.write(`\r\n\x1b[90m[exit code: ${result.code}]\x1b[0m`)
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      term.write('\r\n\x1b[31m' + msg + '\x1b[0m')
    } finally {
      runningRef.current = false
      prompt()
    }
  }, [prompt])

  useEffect(() => {
    if (!containerRef.current) return

    const term = new Terminal({
      cursorBlink: true,
      fontSize: 14,
      fontFamily: "'JetBrains Mono', 'Fira Code', 'SF Mono', Menlo, monospace",
      theme: {
        background: '#0d1117',
        foreground: '#c9d1d9',
        cursor: '#58a6ff',
        selectionBackground: '#264f78',
        black: '#0d1117',
        red: '#ff7b72',
        green: '#7ee787',
        yellow: '#d29922',
        blue: '#58a6ff',
        magenta: '#bc8cff',
        cyan: '#39c5cf',
        white: '#c9d1d9',
        brightBlack: '#484f58',
        brightRed: '#ffa198',
        brightGreen: '#56d364',
        brightYellow: '#e3b341',
        brightBlue: '#79c0ff',
        brightMagenta: '#d2a8ff',
        brightCyan: '#56d4dd',
        brightWhite: '#f0f6fc',
      },
      allowProposedApi: true,
    })

    const fit = new FitAddon()
    term.loadAddon(fit)
    term.open(containerRef.current)
    fit.fit()

    termRef.current = term
    fitRef.current = fit

    // Welcome message
    term.writeln('\x1b[36m AutoDroid Shell\x1b[0m')
    term.writeln('\x1b[90m 输入命令并回车执行 | 上下箭头浏览历史 | Ctrl+L 清屏\x1b[0m')
    prompt()

    // Handle input
    term.onData((data) => {
      if (runningRef.current) return

      switch (data) {
        case '\r': { // Enter
          const cmd = lineRef.current
          lineRef.current = ''
          runCommand(cmd)
          break
        }
        case '\x7f': { // Backspace
          if (lineRef.current.length > 0) {
            lineRef.current = lineRef.current.slice(0, -1)
            term.write('\b \b')
          }
          break
        }
        case '\x0c': { // Ctrl+L
          term.clear()
          break
        }
        case '\x03': { // Ctrl+C
          lineRef.current = ''
          term.write('^C')
          prompt()
          break
        }
        case '\x1b[A': { // Arrow Up
          const h = historyRef.current
          if (h.length > 0) {
            const idx = Math.min(histIdxRef.current + 1, h.length - 1)
            histIdxRef.current = idx
            // Clear current line
            term.write('\r\x1b[32m$ \x1b[0m\x1b[K')
            lineRef.current = h[idx]
            term.write(lineRef.current)
          }
          break
        }
        case '\x1b[B': { // Arrow Down
          const h = historyRef.current
          if (histIdxRef.current > 0) {
            const idx = histIdxRef.current - 1
            histIdxRef.current = idx
            term.write('\r\x1b[32m$ \x1b[0m\x1b[K')
            lineRef.current = h[idx]
            term.write(lineRef.current)
          } else if (histIdxRef.current === 0) {
            histIdxRef.current = -1
            term.write('\r\x1b[32m$ \x1b[0m\x1b[K')
            lineRef.current = ''
          }
          break
        }
        case '\t': { // Tab - autocomplete
          const current = lineRef.current
          if (!current) break
          getCompletions(current).then(matches => {
            if (runningRef.current) return
            if (matches.length === 1) {
              // Single match — complete it
              const words = current.split(/\s+/)
              const last = words[words.length - 1] || ''
              const completion = matches[0].slice(last.length)
              if (completion) {
                lineRef.current += completion
                term.write(completion)
              }
            } else if (matches.length > 1) {
              // Multiple matches — find common prefix and show candidates
              const words = current.split(/\s+/)
              const last = words[words.length - 1] || ''
              const prefix = commonPrefix(matches).slice(last.length)
              if (prefix) {
                lineRef.current += prefix
                term.write(prefix)
              } else {
                // Show all candidates
                term.write('\r\n' + matches.join('  '))
                term.write('\r\n\x1b[32m$ \x1b[0m' + lineRef.current)
              }
            }
          })
          break
        }
        default: {
          // Printable characters
          if (data >= ' ') {
            lineRef.current += data
            term.write(data)
          }
          break
        }
      }
    })

    // Resize handler
    const onResize = () => fit.fit()
    window.addEventListener('resize', onResize)

    return () => {
      window.removeEventListener('resize', onResize)
      term.dispose()
    }
  }, [prompt, runCommand])

  return (
    <div
      ref={containerRef}
      style={{
        height: 'calc(100vh - 120px)',
        borderRadius: 8,
        overflow: 'hidden',
        border: '1px solid var(--border)',
      }}
    />
  )
}
