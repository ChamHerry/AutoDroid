# AutoDroid Architecture Specification

> **Version**: 2.0
> **Date**: 2026-03-23
> **Status**: Draft
> **Author**: Architecture Team

---

## 1. Executive Summary

本文档定义 AutoDroid（原 Auto.js Next）的完整技术架构。项目目标是构建一个基于 **Node.js 22 LTS (V8 引擎)** + **HTTP REST API** 双通道的 Android 自动化平台。**Kotlin** 作为核心适配层对接 Android 系统 API，同时通过内嵌 HTTP Server 对外暴露 REST API，支持外部工具（VS Code、Python、AI Agent 等）远程控制设备。

### 1.1 Design Goals

| 目标 | 描述 |
|------|------|
| **高性能** | V8 JIT 编译，脚本执行性能比 Rhino 提升 100x+ |
| **现代 JS** | ES2024 完整支持，async/await 原生异步 |
| **npm 生态** | 直接使用 200 万+ npm 包 |
| **低延迟** | 脚本调用 Android API 端到端 <100ms |
| **可维护** | 清晰分层，Kotlin 适配层可独立演进 |
| **安全** | V8 字节码加密，脚本代码保护 |
| **可更新** | V8/Node.js 引擎可独立升级 |
| **开放接入** | HTTP REST API 支持外部工具链和 AI Agent 远程控制 |

### 1.2 Non-Goals

- 不兼容旧版 Auto.js Rhino API（不做向后兼容）
- 不支持 iOS（仅 Android 平台）
- 不内置代码编辑器 IDE（初期通过 VS Code Remote 或 Web IDE 替代）

---

## 2. Architecture Overview

### 2.1 Dual-Channel Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│  Channel A: User Scripts            Channel B: External Clients    │
│  (Node.js ES2024, .mjs/.js)         (VS Code, Python, AI Agent)    │
├─────────────────────┬──────────────────────────────────────────────┤
│  Node.js Runtime    │  HTTP REST API Server                        │
│  (V8 + libuv)       │  (Kotlin, port 8080)                         │
│  脚本管理 + N-API    │  Router + Middleware + Controllers            │
├═══ JNI + N-API ═════┴══════════ Direct Call ═══════════════════════┤
│  Kotlin Adapter Layer                                              │
│  AutomatorAdapter, AppAdapter, DeviceAdapter, ShellAdapter, ...    │
│  Coroutines 异步 + Hilt DI                                         │
├────────────────────────────────────────────────────────────────────┤
│  Android System Layer                                              │
│  AccessibilityService + WindowManager + MediaProjection + System   │
└────────────────────────────────────────────────────────────────────┘
```

**双通道设计**:
- **Channel A (嵌入式)**: 脚本运行在 App 内的 Node.js 进程中，通过 JNI + N-API 同进程调用 Adapter，延迟 ~0.1ms
- **Channel B (HTTP API)**: 外部客户端通过 HTTP REST API 远程调用 Adapter，支持跨设备控制，延迟 ~1-5ms

### 2.2 Module Structure

```
AutoDroid/                         # Root (com.autodroid)
├── app/                           # Android Application (Kotlin)
│   ├── src/main/
│   │   ├── kotlin/com/autodroid/
│   │   │   ├── app/               # Application, MainActivity
│   │   │   ├── service/           # AccessibilityService, ForegroundService
│   │   │   ├── adapter/           # Kotlin Adapter Layer
│   │   │   │   ├── AutomatorAdapter.kt   # UI 自动化 (find/click/swipe)
│   │   │   │   ├── AppAdapter.kt         # 应用操作 (launch/toast)
│   │   │   │   ├── DeviceAdapter.kt      # 设备信息
│   │   │   │   ├── ShellAdapter.kt       # Shell 命令
│   │   │   │   ├── UIAdapter.kt          # 悬浮窗/对话框
│   │   │   │   ├── EventAdapter.kt       # 系统事件转发
│   │   │   │   └── StorageAdapter.kt     # 本地存储
│   │   │   ├── server/            # HTTP REST API Server (Channel B)
│   │   │   │   ├── HttpServer.kt         # 轻量 HTTP 服务器 (raw socket)
│   │   │   │   ├── Router.kt             # Express-like 路由 (支持 :param)
│   │   │   │   ├── Middleware.kt          # CORS + Logger 中间件
│   │   │   │   ├── AdapterContainer.kt   # Adapter DI 容器
│   │   │   │   ├── ApiRoutes.kt          # 路由注册入口
│   │   │   │   ├── Request.kt / Response.kt
│   │   │   │   └── controller/           # REST 控制器
│   │   │   │       ├── StatusController.kt    # GET  /api/status
│   │   │   │       ├── ActionController.kt    # POST /api/actions/*
│   │   │   │       ├── UiController.kt        # POST /api/ui/*
│   │   │   │       ├── AppController.kt       # POST /api/app/*
│   │   │   │       ├── DeviceController.kt    # GET  /api/device/*
│   │   │   │       ├── ShellController.kt     # POST /api/shell/*
│   │   │   │       ├── FileController.kt      # GET/POST /api/files/*
│   │   │   │       ├── LogController.kt       # GET  /api/logs/*
│   │   │   │       └── ScriptController.kt    # POST /api/scripts/* (TODO)
│   │   │   ├── project/           # 项目管理
│   │   │   │   ├── ProjectConfig.kt      # project.json 配置模型
│   │   │   │   └── ProjectManager.kt     # 项目 CRUD + zip 导入
│   │   │   ├── bridge/            # JNI Bridge (Channel A)
│   │   │   │   └── TypeConverter.kt
│   │   │   ├── ui/                # App UI (Jetpack Compose)
│   │   │   │   ├── MainScreen.kt         # 底部导航 (Dashboard/Logs)
│   │   │   │   ├── DashboardScreen.kt    # 服务状态仪表盘
│   │   │   │   ├── ConsoleScreen.kt      # 日志控制台
│   │   │   │   ├── ConsoleRepository.kt  # 日志数据层
│   │   │   │   ├── ScriptListScreen.kt   # 脚本列表
│   │   │   │   ├── ProjectDetailScreen.kt
│   │   │   │   ├── FileEditorScreen.kt
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   ├── PermissionGuideScreen.kt
│   │   │   │   └── Theme.kt
│   │   │   └── di/                # Hilt DI modules
│   │   │       └── AppModule.kt
│   │   ├── res/
│   │   │   └── xml/accessibility_service_config.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── automator/                     # UI Automation Engine (Kotlin library)
│   ├── src/main/kotlin/com/autodroid/automator/
│   │   ├── UiObject.kt                  # AccessibilityNodeInfo 封装
│   │   ├── UiGlobalSelector.kt          # 选择器引擎
│   │   ├── SelectorParser.kt            # JSON → Selector 解析
│   │   ├── GlobalActionAutomator.kt     # 全局手势/按键
│   │   ├── filter/Filter.kt             # 节点过滤器
│   │   └── search/SearchAlgorithm.kt    # DFS / BFS 搜索
│   ├── src/test/                         # 62 个测试，100% 通过
│   └── build.gradle.kts
├── native/                        # N-API native addon (C++, 待实现)
│   └── src/
└── docs/
    ├── specs/                     # 架构规范文档
    ├── plans/                     # 开发路线图
    └── architecture-diagrams/     # drawio 架构图
```

---

## 3. Layer 1: User Script Layer

### 3.1 Script API Design

用户脚本运行在标准 Node.js 环境中，通过 `@autojs/*` SDK 包访问自动化能力。所有 Android 交互均为 **async** 操作。

```javascript
// example.mjs
import { selector, click, setText, swipe, gesture } from "@autojs/automator";
import { launchApp, toast, getClipboard } from "@autojs/app";
import { floaty, dialog } from "@autojs/ui";
import { captureScreen, findImage, ocr } from "@autojs/media";
import { onKey, onNotification } from "@autojs/events";
import { getDeviceInfo } from "@autojs/device";

// 启动应用
await launchApp("com.tencent.mm");

// 查找并点击元素 (async + timeout)
const btn = await selector({ text: "发现" }).findOne(5000);
await btn.click();

// 文本输入
const input = await selector({ className: "EditText" }).findOne();
await input.setText("Hello World");

// 手势操作
await swipe(500, 1500, 500, 500, 300);
await gesture(0, 500, [[500, 500], [600, 600], [700, 500]]);

// 截图 + OCR
const img = await captureScreen();
const text = await ocr(img, { region: [0, 0, 500, 200] });

// 悬浮窗 (返回操控句柄)
const win = await floaty.rawWindow({ x: 100, y: 100, width: 200, height: 100 });
win.on("click", () => toast("Clicked!"));

// 事件监听
onKey("volume_up", () => {
  console.log("Volume up pressed");
  process.exit(0);
});

// npm 包直接使用
import axios from "axios";
const { data } = await axios.get("https://api.example.com/data");
```

### 3.2 SDK Package Specifications

#### @autojs/automator

```typescript
// Type definitions
interface SelectorOptions {
  text?: string;
  textContains?: string;
  textStartsWith?: string;
  textMatches?: string | RegExp;
  id?: string;
  idContains?: string;
  className?: string;
  desc?: string;
  descContains?: string;
  packageName?: string;
  clickable?: boolean;
  scrollable?: boolean;
  enabled?: boolean;
  bounds?: [number, number, number, number];
  boundsInside?: [number, number, number, number];
  depth?: number;
  indexInParent?: number;
  algorithm?: "dfs" | "bfs";
}

interface UiNode {
  // Properties
  readonly text: string;
  readonly desc: string;
  readonly id: string;
  readonly className: string;
  readonly packageName: string;
  readonly bounds: { left: number; top: number; right: number; bottom: number };
  readonly depth: number;
  readonly clickable: boolean;
  readonly scrollable: boolean;
  readonly enabled: boolean;
  readonly childCount: number;

  // Actions (all async)
  click(): Promise<boolean>;
  longClick(): Promise<boolean>;
  setText(text: string): Promise<boolean>;
  scrollForward(): Promise<boolean>;
  scrollBackward(): Promise<boolean>;
  focus(): Promise<boolean>;
  select(): Promise<boolean>;
  copy(): Promise<boolean>;
  paste(): Promise<boolean>;

  // Tree navigation
  parent(): Promise<UiNode | null>;
  child(index: number): Promise<UiNode | null>;
  children(): Promise<UiNode[]>;
  find(options: SelectorOptions): Promise<UiNode[]>;
  findOne(timeout?: number): Promise<UiNode>;
}

interface SelectorChain {
  findOne(timeout?: number): Promise<UiNode>;
  find(max?: number): Promise<UiNode[]>;
  exists(): Promise<boolean>;
  waitFor(timeout?: number): Promise<UiNode>;
  untilGone(timeout?: number): Promise<void>;
}

// Exports
export function selector(options: SelectorOptions): SelectorChain;
export function click(target: SelectorOptions | number, y?: number): Promise<boolean>;
export function longClick(target: SelectorOptions | number, y?: number): Promise<boolean>;
export function setText(target: SelectorOptions, text: string): Promise<boolean>;
export function swipe(x1: number, y1: number, x2: number, y2: number, duration: number): Promise<boolean>;
export function gesture(delay: number, duration: number, points: [number, number][]): Promise<boolean>;
export function back(): Promise<boolean>;
export function home(): Promise<boolean>;
export function recents(): Promise<boolean>;
export function notifications(): Promise<boolean>;
export function scrollDown(target?: SelectorOptions): Promise<boolean>;
export function scrollUp(target?: SelectorOptions): Promise<boolean>;
```

#### @autojs/app

```typescript
export function launchApp(packageName: string): Promise<void>;
export function openUrl(url: string): Promise<void>;
export function toast(message: string, duration?: "short" | "long"): void;
export function getClipboard(): Promise<string>;
export function setClipboard(text: string): Promise<void>;
export function startActivity(intent: IntentOptions): Promise<void>;
export function sendBroadcast(intent: IntentOptions): Promise<void>;
export function getInstalledApps(): Promise<AppInfo[]>;
export function getAppName(packageName: string): Promise<string>;
export function isAppInstalled(packageName: string): Promise<boolean>;
export function uninstall(packageName: string): Promise<void>;
```

#### @autojs/ui

```typescript
export namespace floaty {
  function window(options: FloatyWindowOptions): Promise<FloatyWindow>;
  function rawWindow(options: RawWindowOptions): Promise<RawWindow>;
  function closeAll(): void;
}

export namespace dialog {
  function alert(title: string, message?: string): Promise<void>;
  function confirm(title: string, message?: string): Promise<boolean>;
  function prompt(title: string, defaultValue?: string): Promise<string | null>;
  function select(title: string, items: string[]): Promise<number>;
  function multiSelect(title: string, items: string[], selected?: number[]): Promise<number[]>;
}
```

#### @autojs/media

```typescript
export function captureScreen(): Promise<Image>;
export function findImage(source: Image, template: Image, options?: FindImageOptions): Promise<Point | null>;
export function findColor(image: Image, color: number, options?: FindColorOptions): Promise<Point | null>;
export function ocr(image: Image, options?: OcrOptions): Promise<OcrResult[]>;
export function readImage(path: string): Promise<Image>;
export function saveImage(image: Image, path: string, format?: "png" | "jpg"): Promise<void>;
```

#### @autojs/events

```typescript
export function onKey(key: string, callback: (event: KeyEvent) => void): Disposable;
export function onNotification(callback: (notification: Notification) => void): Disposable;
export function onToast(callback: (toast: ToastEvent) => void): Disposable;
export function onAccessibilityEvent(callback: (event: A11yEvent) => void): Disposable;
```

#### @autojs/device

```typescript
export function getDeviceInfo(): DeviceInfo;
export function getScreenWidth(): number;
export function getScreenHeight(): number;
export function getBatteryLevel(): number;
export function isScreenOn(): boolean;
export function wakeUp(): Promise<void>;
export function vibrate(ms: number): void;
export function getBrightness(): number;
export function setBrightness(value: number): void;
```

#### @autojs/shell

```typescript
export function exec(command: string): Promise<ShellResult>;
export function execRoot(command: string): Promise<ShellResult>;
export function isRootAvailable(): Promise<boolean>;

interface ShellResult {
  code: number;
  stdout: string;
  stderr: string;
}
```

#### @autojs/storage

```typescript
export function getPreferences(name?: string): Preferences;
export function getDatabase(name: string): Database;

interface Preferences {
  get<T>(key: string, defaultValue?: T): T;
  set(key: string, value: any): void;
  remove(key: string): void;
  clear(): void;
  keys(): string[];
}
```

### 3.3 Script Execution Modes

| 模式 | 触发方式 | 说明 |
|------|---------|------|
| **Module (ESM)** | `.mjs` 文件 | 默认模式，支持 `import/export` |
| **CommonJS** | `.cjs` / `.js` 文件 | 兼容模式，支持 `require()` |
| **REPL** | 交互式控制台 | 调试用，逐行执行 |

---

## 4. Layer 2: Node.js Runtime Layer

### 4.1 Node.js Embedding

使用 [Node.js Embedder API](https://nodejs.org/api/embedding.html) 将 Node.js 22 LTS 交叉编译为 `libnode.so`，嵌入 Android App。

#### Build Configuration

```bash
# Cross-compile Node.js 22 LTS for Android arm64
export ANDROID_NDK_HOME=/path/to/ndk
export CC_target=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang
export CXX_target=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang++

./configure \
  --dest-cpu=arm64 \
  --dest-os=android \
  --cross-compiling \
  --shared \
  --without-inspector \
  --without-intl \
  --without-npm \
  --without-corepack \
  --openssl-no-asm \
  --partly-static

make -j$(nproc)
# Output: out/Release/lib.target/libnode.so (~15-20MB stripped)
```

#### Supported ABIs

| ABI | 优先级 | 说明 |
|-----|--------|------|
| `arm64-v8a` | P0 | 主流 Android 设备 (>95% 2024+) |
| `x86_64` | P1 | 模拟器调试 |
| `armeabi-v7a` | P2 | 旧设备（可选支持） |

### 4.2 Node.js Lifecycle Management

```cpp
// node_runtime.cpp - Node.js 生命周期管理

class NodeRuntime {
public:
    // 初始化 Node.js 环境 (App.onCreate 时调用)
    static bool Initialize(const char* execPath, const char* scriptDir);

    // 执行脚本
    static int ExecuteScript(const char* scriptPath, ScriptCallback* callback);

    // 停止指定脚本
    static void StopScript(int scriptId);

    // 停止所有脚本
    static void StopAllScripts();

    // 销毁 Node.js 环境 (App.onDestroy 时调用)
    static void Shutdown();

private:
    static node::MultiIsolatePlatform* platform_;
    static std::vector<std::unique_ptr<ScriptWorker>> workers_;
};
```

### 4.3 Script Isolation

每个脚本运行在独立的 **Worker Thread** 中，实现进程内隔离：

```
Main Thread (libuv event loop)
  ├─ Worker 1: script_a.mjs  (独立 V8 Isolate)
  ├─ Worker 2: script_b.mjs  (独立 V8 Isolate)
  └─ Worker 3: script_c.mjs  (独立 V8 Isolate)
```

- 每个 Worker 有独立的 V8 Isolate（内存隔离）
- 脚本崩溃不影响其他脚本
- 可通过 `worker.terminate()` 强制停止
- 共享 Adapter 连接池（通过 SharedArrayBuffer）

### 4.4 Event Bridge Design

Android 系统事件 → Node.js EventEmitter 的单向事件流：

```
Android (Kotlin)                     Node.js (JS)
─────────────────                    ─────────────
AccessibilityEvent ──→ JNI ──→ N-API ──→ events.emit("a11y", data)
KeyEvent           ──→ JNI ──→ N-API ──→ events.emit("key", data)
Notification       ──→ JNI ──→ N-API ──→ events.emit("notification", data)
Sensor data        ──→ JNI ──→ N-API ──→ events.emit("sensor", data)
```

实现机制：
- Kotlin 侧通过 JNI 调用 C++ bridge
- C++ bridge 通过 `napi_threadsafe_function` 安全地在 Node.js 事件循环中发射事件
- 使用 Ring Buffer 缓冲高频事件（如传感器数据），避免阻塞

---

## 5. IPC Bridge Layer (Dual Channel)

### 5.1 Channel A: JNI + N-API Bridge (嵌入式脚本)

```
┌─────────────────────────────────────────────────────────┐
│                    Same Process                         │
│                                                         │
│  Node.js (V8)          C++ Bridge          Kotlin (JVM) │
│  ┌──────────┐    ┌─────────────────┐    ┌────────────┐ │
│  │ N-API    │◄──►│ bridge.cpp      │◄──►│ JNI calls  │ │
│  │ functions│    │ - type mapping  │    │ - Adapter  │ │
│  │          │    │ - thread safety │    │   methods  │ │
│  │          │    │ - error convert │    │            │ │
│  └──────────┘    └─────────────────┘    └────────────┘ │
│                                                         │
│  JSValue ←→ C++ variant ←→ jobject                     │
└─────────────────────────────────────────────────────────┘
```

### 5.2 Channel B: HTTP REST API (外部控制)

```
┌──────────────────────────────────────────────────────────────┐
│  External Client                 Android App (port 8080)     │
│  ┌───────────┐    HTTP/JSON    ┌──────────────────────────┐  │
│  │ VS Code   │ ──────────────→ │ HttpServer               │  │
│  │ Python    │                 │  ├─ CorsMiddleware       │  │
│  │ AI Agent  │ ←────────────── │  ├─ LoggerMiddleware     │  │
│  │ curl      │    JSON resp    │  └─ Router               │  │
│  └───────────┘                 │      ├─ /api/actions/*   │  │
│                                │      ├─ /api/ui/*        │  │
│                                │      ├─ /api/app/*       │  │
│                                │      ├─ /api/device/*    │  │
│                                │      ├─ /api/shell/*     │  │
│                                │      ├─ /api/files/*     │  │
│                                │      ├─ /api/logs/*      │  │
│                                │      └─ /api/scripts/*   │  │
│                                └──────────┬───────────────┘  │
│                                           │ direct call      │
│                                ┌──────────▼───────────────┐  │
│                                │ AdapterContainer          │  │
│                                │  ├─ AutomatorAdapter     │  │
│                                │  ├─ AppAdapter           │  │
│                                │  ├─ DeviceAdapter        │  │
│                                │  └─ ShellAdapter         │  │
│                                └──────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### 5.3 Channel Comparison

| 因素 | Channel A (JNI) | Channel B (HTTP) |
|------|-----------------|-----------------|
| **延迟** | ~0.1ms | ~1-5ms |
| **序列化** | 不需要（直接引用） | JSON |
| **使用场景** | 嵌入式脚本执行 | 外部工具/AI Agent 远程控制 |
| **依赖** | libnode.so + C++ bridge | 仅 Kotlin |
| **开发复杂度** | 高 (JNI + N-API) | 低 (纯 Kotlin) |
| **已实现** | 部分 | **完整** |
| **跨设备** | 否 | 是 (WiFi/USB 转发) |

Channel B 已完整实现，可作为当前主要开发和调试接口。Channel A 为最终目标架构。

### 5.3 Type Mapping

| JavaScript Type | C++ Intermediate | Kotlin/JVM Type |
|----------------|-----------------|----------------|
| `string` | `std::string` | `String` |
| `number` | `double` | `Double` / `Int` |
| `boolean` | `bool` | `Boolean` |
| `null` / `undefined` | `nullptr` | `null` |
| `object` (plain) | `std::map<string, variant>` | `Map<String, Any?>` |
| `Array` | `std::vector<variant>` | `List<Any?>` |
| `Buffer` | `void*` + `size_t` | `ByteArray` |
| `Promise` | `napi_deferred` | `suspend fun` (Coroutine) |
| `UiNode` (proxy) | handle ID (int) | `AccessibilityNodeInfo` |

### 5.4 Async Call Flow

JavaScript `async/await` ↔ Kotlin `suspend fun` 的映射：

```
JS:     const result = await click({ text: "OK" });
          ↓
N-API:  napi_create_promise() → deferred
          ↓ (async)
JNI:    Call AutomatorAdapter.click(selector)
          ↓
Kotlin: suspend fun click(selector): Boolean {
            val node = withContext(Dispatchers.Default) {
                uiGlobalSelector.findOne(rootNode)
            }
            return node.performAction(ACTION_CLICK)
        }
          ↓ (result)
JNI:    Return result to C++
          ↓
N-API:  napi_resolve_deferred(deferred, result)
          ↓
JS:     Promise resolves → result = true
```

### 5.5 Thread Safety

```
Thread Model:
─────────────
Node.js Main Thread (libuv)  ←→  C++ Bridge  ←→  JNI Calls
Worker Thread 1              ←→  C++ Bridge  ←→  JNI Calls
Worker Thread 2              ←→  C++ Bridge  ←→  JNI Calls
                                                      ↕
                                              Kotlin Coroutine Pool
                                              (Dispatchers.Default)

Rules:
1. N-API 调用必须在创建它的线程上执行
2. 跨线程回调使用 napi_threadsafe_function
3. Kotlin Adapter 方法均为 suspend fun，线程安全
4. AccessibilityNodeInfo 访问限制在主线程或指定线程
```

### 5.6 HTTP REST API Reference (Channel B)

HTTP Server 基于原生 `ServerSocket` 实现，无第三方框架依赖。支持 Express-like 路由和中间件。

#### Server Architecture

```
HttpServer (port 8080)
  ├─ CorsMiddleware     → Access-Control-Allow-Origin: *
  ├─ LoggerMiddleware   → 请求日志 → ConsoleRepository
  └─ Router
       ├─ Route matching (Regex + named params :id)
       └─ Controller handlers (suspend fun)
```

#### API Endpoints

| Method | Path | Controller | Description |
|--------|------|-----------|-------------|
| GET | `/api/status` | StatusController | 服务状态和版本 |
| POST | `/api/actions/click` | ActionController | 坐标点击 `{x, y}` |
| POST | `/api/actions/longClick` | ActionController | 坐标长按 `{x, y}` |
| POST | `/api/actions/swipe` | ActionController | 滑动 `{x1, y1, x2, y2, duration}` |
| POST | `/api/actions/key` | ActionController | 全局按键 `{action: "back"\|"home"\|...}` |
| POST | `/api/actions/gesture` | ActionController | 手势 `{delay, duration, points}` |
| GET | `/api/ui/dump` | UiController | Dump 完整无障碍树 (JSON) |
| POST | `/api/ui/find` | UiController | 按选择器查找节点 `{selector, max}` |
| POST | `/api/ui/click` | UiController | 按选择器点击 `{selector, timeout}` |
| POST | `/api/ui/input` | UiController | 按选择器输入文本 `{selector, text}` |
| POST | `/api/ui/scroll` | UiController | 按选择器滚动 `{selector, direction}` |
| POST | `/api/ui/wait` | UiController | 等待元素出现 `{selector, timeout}` |
| POST | `/api/app/launch` | AppController | 启动应用 |
| POST | `/api/app/toast` | AppController | 显示 Toast |
| GET | `/api/device/info` | DeviceController | 设备信息 |
| POST | `/api/shell/exec` | ShellController | 执行 Shell 命令 |
| GET | `/api/files/*` | FileController | 文件读取 |
| POST | `/api/files/*` | FileController | 文件写入 |
| GET | `/api/logs` | LogController | 获取日志 |
| POST | `/api/scripts/run` | ScriptController | 执行脚本 (TODO) |
| POST | `/api/scripts/stop` | ScriptController | 停止脚本 (TODO) |

#### Example: External Client Usage

```bash
# 检查服务状态
curl http://device:8080/api/status

# 点击坐标
curl -X POST http://device:8080/api/actions/click \
  -H "Content-Type: application/json" \
  -d '{"x": 500, "y": 1000}'

# 按选择器查找并点击
curl -X POST http://device:8080/api/ui/click \
  -H "Content-Type: application/json" \
  -d '{"selector": {"text": "Settings"}, "timeout": 5000}'

# Dump UI 树
curl http://device:8080/api/ui/dump

# 执行 Shell 命令
curl -X POST http://device:8080/api/shell/exec \
  -H "Content-Type: application/json" \
  -d '{"command": "ls /sdcard/"}'
```

---

## 5.7 Project Management

项目管理模块提供脚本项目的组织和生命周期管理。

#### project.json Schema

```json
{
  "name": "my-project",
  "version": "1.0.0",
  "main": "main.js",
  "description": "",
  "icon": null,
  "permissions": [],
  "build": {
    "encrypt": false,
    "output": "dist/"
  },
  "config": {
    "loopTimes": 1,
    "delay": 0,
    "interval": 0
  }
}
```

#### ProjectManager API

- `listProjects()` → 列出 `files/projects/` 下所有项目
- `listScripts()` → 列出 `files/scripts/` 下单文件脚本
- `createProject(name)` → 创建项目目录 + project.json + main.js
- `importProject(source)` → 从目录或 .zip 导入项目
- `deleteProject(dir)` → 删除项目

---

## 6. Layer 3: Kotlin Adapter Layer

### 6.1 Adapter Interface Contract

每个 Adapter 遵循统一的接口规范：

```kotlin
// Base adapter interface
interface Adapter {
    fun initialize(context: Context)
    fun dispose()
}

// All Android-interacting methods are suspend functions
// All methods return serializable results for JNI transport
```

### 6.2 AutomatorAdapter

核心自动化操作适配器，对接 AccessibilityService。

```kotlin
@Singleton
class AutomatorAdapter @Inject constructor(
    private val accessibilityBridge: AccessibilityBridge,
    private val globalActionAutomator: GlobalActionAutomator,
    private val screenMetrics: ScreenMetrics,
) : Adapter {

    // ── Node Selection ──

    suspend fun findOne(selectorJson: String, timeout: Long): NodeHandle? =
        withTimeout(timeout) {
            val selector = SelectorParser.parse(selectorJson)
            val root = accessibilityBridge.getRootInActiveWindow()
                ?: throw AutomatorException("No active window")
            val node = selector.findOneOf(UiObject.createRoot(root))
            node?.let { nodePool.register(it) }  // Return handle ID
        }

    suspend fun find(selectorJson: String, max: Int): List<NodeHandle> {
        val selector = SelectorParser.parse(selectorJson)
        val root = accessibilityBridge.getRootInActiveWindow()
            ?: return emptyList()
        return selector.findOf(UiObject.createRoot(root), max)
            .map { nodePool.register(it) }
    }

    // ── Node Actions ──

    suspend fun click(handle: NodeHandle): Boolean {
        val node = nodePool.get(handle) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    suspend fun setText(handle: NodeHandle, text: String): Boolean {
        val node = nodePool.get(handle) ?: return false
        val args = Bundle().apply { putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
        )}
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ── Coordinate Actions ──

    suspend fun clickPoint(x: Int, y: Int): Boolean {
        val scaledX = screenMetrics.scaleX(x)
        val scaledY = screenMetrics.scaleY(y)
        return globalActionAutomator.click(scaledX, scaledY)
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean =
        globalActionAutomator.swipe(
            screenMetrics.scaleX(x1), screenMetrics.scaleY(y1),
            screenMetrics.scaleX(x2), screenMetrics.scaleY(y2),
            duration
        )

    suspend fun gesture(delay: Long, duration: Long, points: List<IntArray>): Boolean =
        globalActionAutomator.gesture(delay, duration, *points.toTypedArray())

    // ── Global Actions ──

    suspend fun back(): Boolean = globalActionAutomator.back()
    suspend fun home(): Boolean = globalActionAutomator.home()
    suspend fun recents(): Boolean = globalActionAutomator.recents()
    suspend fun notifications(): Boolean = globalActionAutomator.notifications()

    // ── Node Property Access ──

    fun getNodeInfo(handle: NodeHandle): Map<String, Any?> {
        val node = nodePool.get(handle) ?: return emptyMap()
        return mapOf(
            "text" to node.text?.toString(),
            "desc" to node.contentDescription?.toString(),
            "id" to node.viewIdResourceName,
            "className" to node.className?.toString(),
            "bounds" to node.boundsInScreen().let {
                mapOf("left" to it.left, "top" to it.top,
                      "right" to it.right, "bottom" to it.bottom)
            },
            "clickable" to node.isClickable,
            "scrollable" to node.isScrollable,
            "enabled" to node.isEnabled,
            "childCount" to node.childCount,
            "depth" to node.depth(),
        )
    }

    // ── Node Lifecycle ──

    fun releaseNode(handle: NodeHandle) = nodePool.release(handle)
    fun releaseAllNodes() = nodePool.releaseAll()
}
```

### 6.3 UIAdapter

悬浮窗和对话框，使用 Jetpack Compose 渲染。

```kotlin
@Singleton
class UIAdapter @Inject constructor(
    private val uiHandler: Handler,
    private val context: Context,
) : Adapter {

    private val windows = ConcurrentHashMap<Int, FloatyWindowHandle>()

    suspend fun createRawWindow(config: WindowConfig): Int = withContext(Dispatchers.Main) {
        ensureOverlayPermission()
        val windowId = nextWindowId()
        val window = RawFloatyWindow(context, config)
        val wm = context.getSystemService(WindowManager::class.java)
        wm.addView(window.rootView, window.layoutParams)
        windows[windowId] = FloatyWindowHandle(window, wm)
        windowId
    }

    suspend fun updateWindow(windowId: Int, updates: Map<String, Any>) {
        withContext(Dispatchers.Main) {
            val handle = windows[windowId] ?: return@withContext
            updates["x"]?.let { handle.window.updateX(it as Int) }
            updates["y"]?.let { handle.window.updateY(it as Int) }
            updates["width"]?.let { handle.window.updateWidth(it as Int) }
            updates["height"]?.let { handle.window.updateHeight(it as Int) }
        }
    }

    suspend fun closeWindow(windowId: Int) { /* ... */ }
    fun closeAll() { /* ... */ }

    suspend fun showDialog(config: DialogConfig): Any? = suspendCancellableCoroutine { cont ->
        uiHandler.post {
            MaterialAlertDialogBuilder(context).apply {
                setTitle(config.title)
                setMessage(config.message)
                setPositiveButton("OK") { _, _ -> cont.resume(true) }
                setNegativeButton("Cancel") { _, _ -> cont.resume(false) }
                setOnCancelListener { cont.resume(null) }
            }.show()
        }
    }
}
```

### 6.4 MediaAdapter

截图和图像处理。

```kotlin
@Singleton
class MediaAdapter @Inject constructor(
    private val context: Context,
) : Adapter {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null

    suspend fun requestCapture(): Boolean { /* Request MediaProjection permission */ }

    suspend fun captureScreen(): ByteArray {
        val projection = mediaProjection ?: throw MediaException("Capture not initialized")
        return suspendCancellableCoroutine { cont ->
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                cont.resume(bytes)
            }, uiHandler)
        }
    }

    suspend fun ocr(imageBytes: ByteArray, region: IntArray?): List<OcrResult> {
        // MLKit or PaddleOCR integration
        val inputImage = InputImage.fromByteArray(imageBytes, width, height, 0, FORMAT_NV21)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return suspendCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { text -> cont.resume(text.toOcrResults()) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }

    suspend fun findImage(
        sourceBytes: ByteArray, templateBytes: ByteArray,
        threshold: Double = 0.9, region: IntArray? = null
    ): IntArray? {
        // OpenCV template matching
        return withContext(Dispatchers.Default) {
            val source = Mat().apply { /* decode sourceBytes */ }
            val template = Mat().apply { /* decode templateBytes */ }
            val result = Mat()
            Imgproc.matchTemplate(source, template, result, Imgproc.TM_CCOEFF_NORMED)
            val mmr = Core.minMaxLoc(result)
            if (mmr.maxVal >= threshold) {
                intArrayOf(mmr.maxLoc.x.toInt(), mmr.maxLoc.y.toInt())
            } else null
        }
    }
}
```

### 6.5 EventAdapter

系统事件到 Node.js 的转发。

```kotlin
@Singleton
class EventAdapter @Inject constructor(
    private val accessibilityBridge: AccessibilityBridge,
) : Adapter, AccessibilityDelegate {

    // Native callback (C++ bridge 注册)
    private var nativeEventCallback: Long = 0

    fun registerNativeCallback(callbackPtr: Long) {
        nativeEventCallback = callbackPtr
    }

    override fun onAccessibilityEvent(service: AccessibilityService, event: AccessibilityEvent): Boolean {
        if (nativeEventCallback != 0L) {
            val eventData = mapOf(
                "type" to event.eventType,
                "packageName" to event.packageName?.toString(),
                "className" to event.className?.toString(),
                "text" to event.text?.map { it.toString() },
                "time" to event.eventTime,
            )
            NativeBridge.emitEvent(nativeEventCallback, "accessibility", eventData)
        }
        return false
    }

    // Key events
    fun onKeyEvent(keyCode: Int, action: Int) {
        if (nativeEventCallback != 0L) {
            NativeBridge.emitEvent(nativeEventCallback, "key", mapOf(
                "keyCode" to keyCode, "action" to action
            ))
        }
    }
}
```

### 6.6 Other Adapters (Summary)

| Adapter | Key Methods | Android API |
|---------|------------|-------------|
| **AppAdapter** | `launchApp`, `toast`, `getClipboard`, `startActivity` | PackageManager, Context |
| **DeviceAdapter** | `getScreenSize`, `getBattery`, `vibrate`, `setBrightness` | DisplayManager, BatteryManager |
| **ShellAdapter** | `exec`, `execRoot` | ProcessBuilder, `su` |
| **StorageAdapter** | `get/set/remove` (prefs), `query` (db) | SharedPreferences, Room |
| **PermissionAdapter** | `checkA11y`, `requestOverlay`, `enableByRoot` | Settings, AccessibilityManager |
| **ScheduleAdapter** | `schedule`, `cancel`, `listTasks` | WorkManager, AlarmManager |

---

## 7. Layer 4: Android System Integration

### 7.1 AccessibilityService

```kotlin
class AutojsAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AutojsAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Dispatch to EventAdapter → Node.js
        EventAdapter.instance?.onAccessibilityEvent(this, event)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        EventAdapter.instance?.onKeyEvent(event.keyCode, event.action)
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
```

### 7.2 Foreground Service

保持 Node.js 运行时在后台存活：

```kotlin
class NodejsForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Auto.js Next running")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }
}
```

### 7.3 Required Permissions

```xml
<manifest>
    <!-- Core -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

    <!-- Storage -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />

    <!-- Network -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Media -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <!-- Device -->
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <!-- Services -->
    <service
        android:name=".service.AutojsAccessibilityService"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
        android:exported="false">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
        <meta-data
            android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config" />
    </service>

    <service
        android:name=".service.NodejsForegroundService"
        android:foregroundServiceType="specialUse"
        android:exported="false" />
</manifest>
```

---

## 8. Performance Budget

### 8.1 Latency Targets

| Operation | Target | Breakdown |
|-----------|--------|-----------|
| `click({ text: "OK" })` | <100ms | find: 1-10ms, IPC: 0.2ms, action: 5-50ms |
| `selector().findOne()` | <50ms | DFS/BFS: 1-10ms, IPC: 0.2ms |
| `captureScreen()` | <200ms | MediaProjection capture + encode |
| `ocr(image)` | <500ms | MLKit processing |
| `findImage(src, tpl)` | <300ms | OpenCV template matching |
| Event delivery (a11y → JS) | <5ms | JNI + threadsafe_function |
| Script cold start | <2s | Node.js init + module load |

### 8.2 Memory Budget

| Component | Target |
|-----------|--------|
| libnode.so (stripped) | 15-20 MB |
| V8 Heap per script | 64-128 MB (configurable) |
| Node.js base memory | ~30 MB |
| Kotlin Adapter Layer | ~10 MB |
| Total App (idle) | ~80 MB |
| Total App (1 script running) | ~120 MB |

### 8.3 APK Size Budget

| Component | Size |
|-----------|------|
| libnode.so (arm64) | ~18 MB |
| Kotlin App + Adapters | ~5 MB |
| Bundled SDK (@autojs/*) | ~2 MB |
| OpenCV (optional) | ~8 MB |
| Other dependencies | ~3 MB |
| **Total APK** | **~36 MB** |
| **AAB (arm64 only)** | **~25 MB** |

---

## 9. Security

### 9.1 Script Encryption

```
Source (.mjs) → V8 Bytecode Compilation → AES-256-GCM Encrypt → .ajsb file

Key derivation:
  masterKey = PBKDF2(deviceId + appSignature, salt, 100000, 256)
  scriptKey = HKDF(masterKey, scriptHash, 256)
```

### 9.2 Sandbox

- V8 Isolate 提供内存隔离
- Node.js `--experimental-permission` flag 限制文件/网络访问
- Adapter 层可实现 API 级别的权限控制：

```kotlin
class PermissionGuard {
    fun checkPermission(scriptId: Int, api: String): Boolean {
        val policy = getScriptPolicy(scriptId)
        return policy.isAllowed(api)
    }
}
```

### 9.3 Trust Boundary

```
Untrusted:  User Scripts (Layer 1)
            ↕ Permission Check
Trusted:    @autojs/* SDK (Layer 1, bundled)
            Node.js Runtime (Layer 2)
            ↕ JNI Boundary
Trusted:    Kotlin Adapters (Layer 3)
            Android System (Layer 4)
```

---

## 10. Testing Strategy

### 10.1 Unit Tests

| Layer | Framework | Scope |
|-------|-----------|-------|
| SDK (TypeScript) | Jest / Vitest | API contract, type safety |
| Native Bridge (C++) | Google Test | Type mapping, memory safety |
| Kotlin Adapters | JUnit 5 + MockK | Adapter logic, coroutine behavior |
| Automator | JUnit 5 | Selector building, filter logic |

### 10.2 Integration Tests

| Test | Tool | Scope |
|------|------|-------|
| JS → Kotlin round-trip | Custom harness | IPC correctness, async flow |
| Accessibility operations | UI Automator | Real device click/find/scroll |
| Floating window | Espresso | Window creation, touch events |
| Script lifecycle | Custom | Start/stop/crash recovery |

### 10.3 E2E Tests

- 在真实设备上运行示例脚本
- 验证常见自动化场景（启动应用、点击、输入、滑动）
- 性能基准测试（延迟、内存、CPU）

---

## 11. Build & CI

### 11.1 Build Pipeline

```
1. Compile Node.js 22 LTS → libnode.so (arm64, x86_64)
   (cached, only rebuild on Node.js version upgrade)

2. Build @autojs/* SDK packages (TypeScript → JS)
   npm run build --workspaces

3. Build Native Bridge (C++ → .so)
   cmake + NDK

4. Build Android App (Kotlin)
   ./gradlew assembleRelease

5. Bundle: APK = App + libnode.so + SDK + Native Bridge
```

### 11.2 CI/CD

```yaml
# GitHub Actions
- Node.js cross-compile (weekly cache)
- SDK tests (every PR)
- Native bridge tests (every PR)
- Kotlin tests (every PR)
- Integration tests (nightly, real device via Firebase Test Lab)
- Release build (on tag)
```

---

## 12. Migration & Compatibility

### 12.1 Not Compatible with Auto.js v4 API

本项目**不提供**与旧版 Auto.js Rhino API 的兼容。原因：

1. 编程模型根本不同（同步阻塞 vs async/await）
2. API 命名和参数完全重新设计
3. 模块系统不同（Rhino require vs Node.js ESM）

### 12.2 Migration Guide (Future)

将提供迁移工具和文档，帮助用户将旧脚本迁移到新 API：

```
旧 API (Rhino):                    新 API (Node.js):
─────────────────                   ──────────────────
auto();                             // 自动启用
click("OK");                        await click({ text: "OK" });
id("input").findOne().setText("x")  const n = await selector({id:"input"}).findOne();
                                    await n.setText("x");
toast("done");                      toast("done");
threads.start(fn)                   import { Worker } from "worker_threads";
```

---

## 13. Milestones

| Phase | Target | Status | Deliverables |
|-------|--------|--------|-------------|
| **P0: Automator Engine** | Month 1 | ✅ Done | UiObject, Selector, Filter, Search (DFS/BFS), 62 tests |
| **P1: Adapter Layer** | Month 2 | ✅ Done | AutomatorAdapter, AppAdapter, DeviceAdapter, ShellAdapter, EventAdapter, StorageAdapter, UIAdapter |
| **P2: HTTP REST API** | Month 3 | ✅ Done | HttpServer, Router, Middleware, 9 Controllers, AdapterContainer |
| **P3: App Shell** | Month 3-4 | 🔧 In Progress | Compose UI (Dashboard + Logs), Project Management, AccessibilityService |
| **P4: Node.js Integration** | Month 5-6 | 📋 Planned | Node.js 22 cross-compile, JNI bridge, N-API, @autojs/* SDK |
| **P5: Advanced** | Month 7-8 | 📋 Planned | Screenshot, OCR, image matching, encryption |
| **P6: Polish** | Month 9-10 | 📋 Planned | Performance optimization, testing, documentation |

---

## 14. Technology Stack Summary

| Layer | Technology | Version | Status |
|-------|-----------|---------|--------|
| Script Runtime | Node.js | 22 LTS | 📋 Planned |
| JS Engine | V8 | 12.4+ | 📋 Planned |
| Script Language | JavaScript / TypeScript | ES2024 | 📋 Planned |
| SDK Build | TypeScript + tsup | 5.x | 📋 Planned |
| Native Bridge | C++ (N-API + JNI) | C++17 | 📋 Planned |
| HTTP Server | Kotlin (raw socket) | - | ✅ Done |
| App Language | Kotlin | 2.0+ | ✅ Done |
| UI Framework | Jetpack Compose | latest | ✅ Done |
| Async | Kotlin Coroutines + Flow | 1.9+ | ✅ Done |
| DI | Hilt | latest | ✅ Done |
| Image Processing | OpenCV Android | 4.9+ | 📋 Planned |
| OCR | MLKit / PaddleOCR | latest | 📋 Planned |
| Build System | Gradle (KTS) + CMake | 8.x | ✅ Done |
| Min Android API | 26 (Android 8.0) | - | ✅ Done |
| Target API | 35 (Android 15) | - | ✅ Done |

---

## Appendix A: Reference Architecture Diagram

See `architecture-diagrams/07-new-architecture-design.drawio` for the visual representation.

## Appendix B: Compared to Auto.js Pro v9

| Feature | Pro v9 | AutoDroid |
|---------|--------|-----------|
| Node.js | 16.x (EOL) | **22 LTS** (planned) |
| V8 | 9.4 | **12.4+** (planned) |
| ES | ES2021 | **ES2024** (planned) |
| Rhino compat | Yes (dual engine) | No (clean break) |
| UI Framework | Android XML | **Jetpack Compose** |
| Async model | Rhino threads + Node event loop | **Pure async/await** |
| DI | Manual | **Hilt** |
| Type safety | JS only | **TypeScript SDK** |
| External API | No | **HTTP REST API** |
| Open source | No | **Yes** |
