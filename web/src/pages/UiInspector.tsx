import { useState, useRef, useCallback, useEffect, memo } from 'react'
import { getUiDump, getScreenshotUrl, type UiNode } from '../api'

// Indexed node: UiNode + path string (e.g. "0.2.1") for tree state management
interface IndexedNode {
  node: UiNode
  path: string
  parentPath: string | null
  depth: number
  label: string // short class name
}

function indexTree(root: UiNode): IndexedNode[] {
  const result: IndexedNode[] = []
  const walk = (node: UiNode, path: string, parentPath: string | null, depth: number) => {
    const label = node.className?.split('.')?.pop() ?? ''
    result.push({ node, path, parentPath, depth, label })
    node.children?.forEach((child, i) => walk(child, `${path}.${i}`, path, depth + 1))
  }
  walk(root, '0', null, 0)
  return result
}

function getAncestorPaths(path: string): string[] {
  const parts = path.split('.')
  const paths: string[] = []
  for (let i = 1; i < parts.length; i++) {
    paths.push(parts.slice(0, i).join('.'))
  }
  return paths
}

export default function UiInspector() {
  const [tree, setTree] = useState<UiNode | null>(null)
  const [allIndexed, setAllIndexed] = useState<IndexedNode[]>([])
  const [selectedPath, setSelectedPath] = useState<string | null>(null)
  const [hoveredPath, setHoveredPath] = useState<string | null>(null)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [screenshotUrl, setScreenshotUrl] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [screenSize, setScreenSize] = useState({ w: 0, h: 0 })
  const [search, setSearch] = useState('')
  const imgRef = useRef<HTMLImageElement>(null)
  const treeRefs = useRef<Map<string, HTMLDivElement>>(new Map())

  const selectedIndexed = allIndexed.find(n => n.path === selectedPath) ?? null
  const hoveredIndexed = allIndexed.find(n => n.path === hoveredPath) ?? null

  const dump = async () => {
    setLoading(true)
    setError('')
    setSelectedPath(null)
    setHoveredPath(null)
    setSearch('')
    try {
      const root = await getUiDump()
      setTree(root)
      // Extract screen size from root bounds
      const rb = root.bounds
      setScreenSize({ w: rb.right - rb.left, h: rb.bottom - rb.top })
      const indexed = indexTree(root)
      setAllIndexed(indexed)
      // Auto-expand first 2 levels
      const initialExpanded = new Set<string>()
      indexed.forEach(n => { if (n.depth < 2) initialExpanded.add(n.path) })
      setExpanded(initialExpanded)
      setScreenshotUrl(getScreenshotUrl())
    } catch (e) {
      setError(e instanceof Error ? e.message : '获取失败')
    } finally {
      setLoading(false)
    }
  }

  const selectNode = useCallback((path: string) => {
    setSelectedPath(path)
    // Expand all ancestors
    const ancestors = getAncestorPaths(path)
    setExpanded(prev => {
      const next = new Set(prev)
      ancestors.forEach(a => next.add(a))
      return next
    })
    // Scroll into view after render
    requestAnimationFrame(() => {
      treeRefs.current.get(path)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    })
  }, [])

  const onScreenshotClick = useCallback((e: React.MouseEvent<HTMLDivElement>) => {
    if (!imgRef.current || !allIndexed.length || !screenSize.w) return
    const rect = imgRef.current.getBoundingClientRect()
    // Map click position to screen coordinates (same system as bounds)
    const scaleX = screenSize.w / rect.width
    const scaleY = screenSize.h / rect.height
    const x = (e.clientX - rect.left) * scaleX
    const y = (e.clientY - rect.top) * scaleY

    let best: IndexedNode | null = null
    let bestArea = Infinity
    for (const indexed of allIndexed) {
      const b = indexed.node.bounds
      if (x >= b.left && x <= b.right && y >= b.top && y <= b.bottom) {
        const area = (b.right - b.left) * (b.bottom - b.top)
        if (area < bestArea) {
          bestArea = area
          best = indexed
        }
      }
    }
    if (best) selectNode(best.path)
  }, [screenSize, allIndexed, selectNode])

  const toggleExpand = useCallback((path: string) => {
    setExpanded(prev => {
      const next = new Set(prev)
      if (next.has(path)) next.delete(path)
      else next.add(path)
      return next
    })
  }, [])

  const onHover = useCallback((path: string | null) => {
    setHoveredPath(path)
  }, [])

  // Search: find matching paths
  const searchLower = search.toLowerCase()
  const matchingPaths = search
    ? new Set(allIndexed
        .filter(n => {
          const nd = n.node
          return (nd.text?.toLowerCase().includes(searchLower)) ||
            (nd.id?.toLowerCase().includes(searchLower)) ||
            (nd.className?.toLowerCase().includes(searchLower)) ||
            (nd.desc?.toLowerCase().includes(searchLower))
        })
        .map(n => n.path))
    : null

  // Auto-expand ancestors of matching nodes when search changes
  useEffect(() => {
    if (!matchingPaths || matchingPaths.size === 0) return
    setExpanded(prev => {
      const next = new Set(prev)
      matchingPaths.forEach(p => getAncestorPaths(p).forEach(a => next.add(a)))
      return next
    })
  }, [search]) // eslint-disable-line react-hooks/exhaustive-deps

  // Breadcrumb: ancestor chain of selected node
  const breadcrumb: IndexedNode[] = []
  if (selectedPath) {
    const chain = [selectedPath, ...getAncestorPaths(selectedPath)]
    chain.reverse()
    chain.forEach(p => {
      const n = allIndexed.find(x => x.path === p)
      if (n) breadcrumb.push(n)
    })
  }

  // Build children lookup for tree rendering
  const childrenByPath = useRef<Map<string, IndexedNode[]>>(new Map())
  useEffect(() => {
    const map = new Map<string, IndexedNode[]>()
    allIndexed.forEach(n => {
      if (n.parentPath !== null) {
        const siblings = map.get(n.parentPath) ?? []
        siblings.push(n)
        map.set(n.parentPath, siblings)
      }
    })
    childrenByPath.current = map
  }, [allIndexed])

  return (
    <div>
      <div style={{ marginBottom: 12, display: 'flex', gap: 12, alignItems: 'center' }}>
        <button className="btn" onClick={dump} disabled={loading}>
          {loading ? '获取中...' : '抓取界面'}
        </button>
        {tree && (
          <button className="btn" onClick={() => setScreenshotUrl(getScreenshotUrl())}
            style={{ background: 'var(--surface2)', color: 'var(--text)' }}>
            刷新截图
          </button>
        )}
        {error && <span className="error" style={{ padding: '6px 12px' }}>{error}</span>}
      </div>

      {tree ? (
        <div className="inspector-3col">
          {/* Left: Screenshot */}
          <div className="screenshot-panel">
            <div className="screenshot-wrapper" onClick={onScreenshotClick}>
              <img
                ref={imgRef}
                src={screenshotUrl}
                alt="screenshot"
                className="screenshot-img"

              />
              {imgRef.current && screenSize.w > 0 && (
                <BoundsOverlay
                  node={(hoveredIndexed ?? selectedIndexed)?.node ?? null}
                  imgEl={imgRef.current}
                  screenW={screenSize.w}
                  screenH={screenSize.h}
                  color={hoveredIndexed ? 'rgba(108,140,255,0.4)' : 'rgba(76,175,128,0.4)'}
                />
              )}
            </div>
          </div>

          {/* Middle: Search + Node tree */}
          <div className="tree-panel">
            <input
              className="tree-search"
              type="text"
              placeholder="搜索节点 (文本、ID、类名...)"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
            <div className="tree-container">
              {allIndexed.filter(n => n.parentPath === null).map(rootNode => (
                <TreeNode
                  key={rootNode.path}
                  indexed={rootNode}
                  childrenByPath={childrenByPath.current}
                  expanded={expanded}
                  selectedPath={selectedPath}
                  matchingPaths={matchingPaths}
                  onToggle={toggleExpand}
                  onSelect={selectNode}
                  onHover={onHover}
                  treeRefs={treeRefs.current}
                />
              ))}
            </div>
          </div>

          {/* Right: Breadcrumb + Properties */}
          <div className="props-panel">
            {breadcrumb.length > 0 && (
              <Breadcrumb items={breadcrumb} onSelect={selectNode} />
            )}
            <PropsPanel node={selectedIndexed?.node ?? null} />
          </div>
        </div>
      ) : (
        <div className="card">
          <p style={{ color: 'var(--text2)' }}>
            点击「抓取界面」获取当前屏幕截图和无障碍节点树
          </p>
        </div>
      )}
    </div>
  )
}

// ── Bounds overlay on screenshot ──
// Maps accessibility bounds (in screen pixels) to the displayed image coordinates.
// Handles portrait/landscape: bounds are in the dump's screen orientation,
// which matches the screenshot since both are captured together.
function BoundsOverlay({
  node, imgEl, screenW, screenH, color,
}: {
  node: UiNode | null
  imgEl: HTMLImageElement
  screenW: number
  screenH: number
  color: string
}) {
  if (!node) return null
  const rect = imgEl.getBoundingClientRect()
  // Map from screen coordinates (bounds) → displayed image coordinates
  const scaleX = rect.width / screenW
  const scaleY = rect.height / screenH
  const b = node.bounds

  return (
    <div
      className="bounds-highlight"
      style={{
        left: b.left * scaleX,
        top: b.top * scaleY,
        width: (b.right - b.left) * scaleX,
        height: (b.bottom - b.top) * scaleY,
        background: color,
        border: `2px solid ${color.replace('0.4', '0.9')}`,
      }}
    />
  )
}

// ── Breadcrumb ──
function Breadcrumb({ items, onSelect }: { items: IndexedNode[]; onSelect: (path: string) => void }) {
  return (
    <div className="breadcrumb">
      {items.map((item, i) => (
        <span key={item.path}>
          {i > 0 && <span className="breadcrumb-sep">&gt;</span>}
          <span
            className={`breadcrumb-item${i === items.length - 1 ? ' current' : ''}`}
            onClick={() => onSelect(item.path)}
          >
            {item.label || '?'}
          </span>
        </span>
      ))}
    </div>
  )
}

// ── Tree node (recursive, memoized to avoid full-tree re-renders) ──
interface TreeNodeProps {
  indexed: IndexedNode
  childrenByPath: Map<string, IndexedNode[]>
  expanded: Set<string>
  selectedPath: string | null
  matchingPaths: Set<string> | null
  onToggle: (path: string) => void
  onSelect: (path: string) => void
  onHover: (path: string | null) => void
  treeRefs: Map<string, HTMLDivElement>
}

const TreeNode = memo(function TreeNode({
  indexed, childrenByPath, expanded, selectedPath, matchingPaths,
  onToggle, onSelect, onHover, treeRefs,
}: TreeNodeProps) {
  const children = childrenByPath.get(indexed.path)
  const hasChildren = children && children.length > 0
  const isExpanded = expanded.has(indexed.path)
  const isSelected = selectedPath === indexed.path
  const isMatch = matchingPaths?.has(indexed.path) ?? false
  const node = indexed.node

  // If searching and this node (and its subtree) has no matches, hide it
  if (matchingPaths && !isMatch && !hasDescendantMatch(indexed.path, childrenByPath, matchingPaths)) {
    return null
  }

  return (
    <div className={indexed.depth > 0 ? 'tree-node' : ''}>
      <div
        ref={el => { if (el) treeRefs.set(indexed.path, el); else treeRefs.delete(indexed.path) }}
        className={`tree-label${isSelected ? ' selected' : ''}${isMatch && matchingPaths ? ' search-match' : ''}`}
        onClick={() => onSelect(indexed.path)}
        onMouseEnter={() => onHover(indexed.path)}
        onMouseLeave={() => onHover(null)}
      >
        <span
          className="tree-toggle"
          onClick={(e) => { e.stopPropagation(); if (hasChildren) onToggle(indexed.path) }}
        >
          {hasChildren ? (isExpanded ? '\u25BC' : '\u25B6') : '\u00B7'}
        </span>
        <span className="tree-class">{indexed.label}</span>
        {node.text && <span className="tree-text">"{truncate(node.text, 20)}"</span>}
        {node.id && <span className="tree-id">#{node.id.split('/').pop()}</span>}
      </div>
      {isExpanded && hasChildren && children.map(child => (
        <TreeNode
          key={child.path}
          indexed={child}
          childrenByPath={childrenByPath}
          expanded={expanded}
          selectedPath={selectedPath}
          matchingPaths={matchingPaths}
          onToggle={onToggle}
          onSelect={onSelect}
          onHover={onHover}
          treeRefs={treeRefs}
        />
      ))}
    </div>
  )
}, (prev, next) => {
  // Custom comparator: only re-render when state affecting this specific node changes.
  // Avoids full-tree re-renders caused by new Set/Map references on every state update.
  const path = prev.indexed.path
  return (
    prev.indexed === next.indexed &&
    prev.expanded.has(path) === next.expanded.has(path) &&
    prev.selectedPath === next.selectedPath &&
    prev.matchingPaths === next.matchingPaths &&
    prev.onToggle === next.onToggle &&
    prev.onSelect === next.onSelect &&
    prev.onHover === next.onHover
  )
})

function hasDescendantMatch(path: string, childrenByPath: Map<string, IndexedNode[]>, matching: Set<string>): boolean {
  const children = childrenByPath.get(path)
  if (!children) return false
  for (const child of children) {
    if (matching.has(child.path) || hasDescendantMatch(child.path, childrenByPath, matching)) return true
  }
  return false
}

// ── Props panel with code generation ──
type CodeLang = 'curl' | 'python' | 'javascript' | 'nodejs'

function PropsPanel({ node }: { node: UiNode | null }) {
  const [codeLang, setCodeLang] = useState<CodeLang>('curl')
  const [copied, setCopied] = useState(false)

  if (!node) {
    return (
      <>
        <h3>节点属性</h3>
        <p style={{ color: 'var(--text2)', fontSize: 13 }}>点击截图或节点树选中元素</p>
      </>
    )
  }

  const b = node.bounds
  const bp = node.boundsInParent
  const props: [string, string][] = [
    ['类名', node.className ?? ''],
    ['文本', node.text ?? ''],
    ['描述', node.desc ?? ''],
    ['ID', node.id ?? ''],
    ['包名', node.packageName ?? ''],
    ['位置', `[${b.left}, ${b.top}, ${b.right}, ${b.bottom}]`],
    ['父内位置', bp ? `[${bp.left}, ${bp.top}, ${bp.right}, ${bp.bottom}]` : '-'],
    ['尺寸', `${b.right - b.left} x ${b.bottom - b.top}`],
    ['深度', String(node.depth ?? '-')],
    ['兄弟索引', String(node.indexInParent ?? '-')],
    ['绘制顺序', String(node.drawingOrder ?? '-')],
    ['可点击', node.clickable ? '是' : '否'],
    ['可长按', node.longClickable ? '是' : '否'],
    ['可滚动', node.scrollable ? '是' : '否'],
    ['已启用', node.enabled ? '是' : '否'],
    ['已选中', node.checked ? '是' : '否'],
    ['已聚焦', node.focused ? '是' : '否'],
    ['已选择', node.selected ? '是' : '否'],
    ['可编辑', node.editable ? '是' : '否'],
    ['可见', node.visibleToUser ? '是' : '否'],
    ['子节点数', String(node.childCount ?? node.children?.length ?? 0)],
  ]
  if (node.windowType) {
    props.push(['窗口类型', node.windowType])
    props.push(['窗口层级', String(node.windowLayer ?? '-')])
    props.push(['窗口标题', node.windowTitle ?? '-'])
  }

  const selector = buildSelector(node)
  const selectorJson = JSON.stringify(selector)
  const codes = generateCodes(selector, selectorJson)

  const copyCode = (code: string) => {
    navigator.clipboard.writeText(code).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }

  const langLabels: { key: CodeLang; label: string }[] = [
    { key: 'curl', label: 'cURL' },
    { key: 'python', label: 'Python' },
    { key: 'javascript', label: 'JS Fetch' },
    { key: 'nodejs', label: 'Node.js' },
  ]

  return (
    <>
      <h3>节点属性</h3>
      {props.map(([k, v]) => (
        <div className="prop-row" key={k}>
          <span className="prop-key">{k}</span>
          <span className="prop-val" title={v}>{v || '-'}</span>
        </div>
      ))}

      <h3 style={{ marginTop: 16 }}>代码示例</h3>
      <div className="code-tabs">
        {langLabels.map(({ key, label }) => (
          <button
            key={key}
            className={`code-tab${codeLang === key ? ' active' : ''}`}
            onClick={() => setCodeLang(key)}
          >
            {label}
          </button>
        ))}
      </div>
      <div className="code-block">
        <pre>{codes[codeLang]}</pre>
        <button className="code-copy" onClick={() => copyCode(codes[codeLang])}>
          {copied ? '已复制' : '复制'}
        </button>
      </div>
    </>
  )
}

function buildSelector(node: UiNode): Record<string, unknown> {
  const s: Record<string, unknown> = {}
  // Combine multiple conditions for precise matching
  if (node.id) s.id = node.id
  if (node.text) s.text = node.text
  if (node.desc) s.desc = node.desc
  // Add className only if no text/id/desc (too generic on its own)
  if (!s.id && !s.text && !s.desc && node.className) {
    s.className = node.className
  }
  // If still empty, use bounds as last resort
  if (Object.keys(s).length === 0) {
    const b = node.bounds
    s.bounds = [b.left, b.top, b.right, b.bottom]
  }
  return s
}

function generateCodes(
  selector: Record<string, unknown>,
  selectorJson: string,
): Record<CodeLang, string> {
  const host = 'http://DEVICE_IP:8080'

  const curl = `# 查找并点击元素
curl -X POST ${host}/api/ui/click \\
  -H "Content-Type: application/json" \\
  -d '${JSON.stringify({ selector, timeout: 5000 })}'

# 查找元素
curl -X POST ${host}/api/ui/find \\
  -H "Content-Type: application/json" \\
  -d '${JSON.stringify({ selector, max: 10 })}'

# 输入文本
curl -X POST ${host}/api/ui/input \\
  -H "Content-Type: application/json" \\
  -d '${JSON.stringify({ selector, text: "输入内容", timeout: 5000 })}'`

  const python = `import requests

BASE = "${host}"

# 查找并点击元素
resp = requests.post(f"{BASE}/api/ui/click", json={
    "selector": ${selectorJson},
    "timeout": 5000
})
print(resp.json())

# 查找元素
resp = requests.post(f"{BASE}/api/ui/find", json={
    "selector": ${selectorJson},
    "max": 10
})
nodes = resp.json()["data"]

# 输入文本
requests.post(f"{BASE}/api/ui/input", json={
    "selector": ${selectorJson},
    "text": "输入内容",
    "timeout": 5000
})`

  const javascript = `const BASE = "${host}";

// 查找并点击元素
const res = await fetch(\`\${BASE}/api/ui/click\`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    selector: ${selectorJson},
    timeout: 5000
  })
});
console.log(await res.json());

// 查找元素
const found = await fetch(\`\${BASE}/api/ui/find\`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    selector: ${selectorJson},
    max: 10
  })
}).then(r => r.json());

// 输入文本
await fetch(\`\${BASE}/api/ui/input\`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    selector: ${selectorJson},
    text: "输入内容",
    timeout: 5000
  })
});`

  const nodejs = `const http = require("http");

const BASE = "${host}";

function post(path, body) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const url = new URL(path, BASE);
    const req = http.request(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(data),
      },
    }, (res) => {
      let buf = "";
      res.on("data", (d) => buf += d);
      res.on("end", () => resolve(JSON.parse(buf)));
    });
    req.on("error", reject);
    req.write(data);
    req.end();
  });
}

// 查找并点击元素
const result = await post("/api/ui/click", {
  selector: ${selectorJson},
  timeout: 5000
});
console.log(result);

// 查找元素
const nodes = await post("/api/ui/find", {
  selector: ${selectorJson},
  max: 10
});

// 输入文本
await post("/api/ui/input", {
  selector: ${selectorJson},
  text: "输入内容",
  timeout: 5000
});`

  return { curl, python, javascript, nodejs }
}

function truncate(s: string, max: number) {
  return s.length > max ? s.slice(0, max) + '...' : s
}
