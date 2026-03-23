[English](README.md) | [中文](README_zh.md)

# AutoDroid

Android 自动化平台，通过 HTTP REST API 控制设备。支持点击、滑动、UI 树检查、Shell 命令执行、屏幕截图等操作。

## 截图展示

| Android 应用 | Web 仪表盘 |
|:-----------:|:----------:|
| ![应用仪表盘](docs/screenshots/dashboard.png) | ![Web 仪表盘](docs/screenshots/web_dashboard.png) |

| 界面检查 | 操控面板 | 终端 |
|:-------:|:-------:|:----:|
| ![界面检查](docs/screenshots/web_inspector.png) | ![操控面板](docs/screenshots/web_controls.png) | ![终端](docs/screenshots/web_terminal.png) |

## 功能特性

- **UI 自动化** — 通过选择器查找元素，点击、输入文本、滚动、等待元素出现
- **屏幕截图** — JPEG 格式，可配置画质/缩放比例，500ms 智能缓存
- **无障碍树** — 完整的多窗口 UI 层级结构导出为 JSON
- **Shell 执行** — 执行任意 Shell 命令，捕获 stdout/stderr
- **手势控制** — 点击、滑动、长按、多点手势、硬件按键
- **文件操作** — 在沙箱内列出、读取、写入、删除文件
- **事件流** — 通过 Server-Sent Events (SSE) 实时推送无障碍和按键事件
- **Web 控制台** — 内置 React Web UI，用于设备检查和控制
- **Token 认证** — 双范围（只读/完全）Token 认证 + 限流保护

## 快速开始

### 环境要求

- Android 设备（SDK 26+，Android 8.0+）
- Android Studio 或 Gradle
- Node.js 18+（用于 Web 前端）
- ADB 已连接设备

### 构建与安装

```bash
# 构建 Web 前端
cd web && npm install && npm run build && cd ..

# 构建并安装 APK
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 设备端设置

1. 启动 **AutoDroid** 应用
2. 按提示授予**无障碍服务**权限
3. 记录仪表盘上显示的 **API Token**
4. 设置端口转发：`adb forward tcp:8080 tcp:8080`

### 第一个 API 调用

```bash
# 检查服务器状态（无需认证）
curl http://127.0.0.1:8080/api/status

# 获取设备信息
curl -H "Authorization: Bearer YOUR_TOKEN" http://127.0.0.1:8080/api/device/info

# 截图
curl -H "Authorization: Bearer YOUR_TOKEN" http://127.0.0.1:8080/api/screenshot -o screen.jpg

# 点击坐标
curl -H "Authorization: Bearer YOUR_TOKEN" -X POST \
  http://127.0.0.1:8080/api/actions/click -d '{"x":500,"y":500}'

# 导出 UI 树
curl -H "Authorization: Bearer YOUR_TOKEN" http://127.0.0.1:8080/api/ui/dump

# 执行 Shell 命令
curl -H "Authorization: Bearer YOUR_TOKEN" -X POST \
  http://127.0.0.1:8080/api/shell/exec -d '{"command":"ls /sdcard"}'
```

## API 参考

所有响应使用统一信封格式：`{"success": true/false, "data": ..., "timestamp": ...}`

### 状态与设备

| 方法 | 端点 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/status` | 无 | 服务器版本、运行时间、服务状态 |
| GET | `/api/device/info` | READ | 设备型号、Android 版本、屏幕尺寸、电量 |

### UI 自动化

| 方法 | 端点 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/ui/dump` | READ | 完整无障碍树（所有窗口） |
| POST | `/api/ui/find` | FULL | 通过选择器查找元素 |
| POST | `/api/ui/click` | FULL | 通过选择器点击元素 |
| POST | `/api/ui/input` | FULL | 向元素输入文本 |
| POST | `/api/ui/scroll` | FULL | 滚动元素 |
| POST | `/api/ui/wait` | FULL | 等待元素出现 |

### 操作

| 方法 | 端点 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/actions/click` | FULL | 坐标点击 `{x, y}` |
| POST | `/api/actions/swipe` | FULL | 滑动手势 `{x1, y1, x2, y2, duration}` |
| POST | `/api/actions/gesture` | FULL | 多点手势 |
| POST | `/api/actions/key` | FULL | 硬件按键（home、back、recents 等） |

### 屏幕截图

| 方法 | 端点 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/screenshot` | READ | JPEG 截图。参数：`quality`(1-100)、`scale`(0.1-1.0) |

### Shell 与文件

| 方法 | 端点 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/shell/exec` | FULL | 执行 Shell 命令 `{command}` |
| GET | `/api/files/list` | READ | 列出目录。参数：`path` |
| GET | `/api/files/read` | READ | 读取文件内容。参数：`path` |
| POST | `/api/files/write` | FULL | 写入文件 `{path, content, append?}` |
| DELETE | `/api/files/delete` | FULL | 删除文件。参数：`path` |

### 事件与日志

| 方法 | 端点 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/events/stream` | READ | 无障碍事件 SSE 流 |
| GET | `/api/logs` | READ | 服务器日志。参数：`limit`、`offset` |
| GET | `/api/logs/stream` | READ | 日志事件 SSE 流 |
| DELETE | `/api/logs` | FULL | 清空日志 |

### 认证

| 方法 | 端点 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/auth/rotate-tokens` | FULL | 轮换 API Token（旧 Token 立即失效） |

## 架构

```
浏览器/客户端 --> HTTP REST API (端口 8080) --> Controllers --> Adapters --> Android 系统 API
                                                    |
                                           StaticController --> assets/web/ (React SPA)
```

- **HTTP 服务器** — 自实现 raw-socket 服务器，协程并发模型，Express 风格路由，中间件管道（CORS、Auth、Logger）
- **Controllers** — 薄请求/响应层，11 个 controller 覆盖不同 API 领域
- **Adapters** — 5 个单例 adapter（Automator、App、Device、Shell、Event），通过 Hilt DI 注入，封装 Android API
- **Automator 引擎** — 独立 Gradle 模块，负责 UI 树遍历、选择器解析、手势分发
- **Web 前端** — React 19 + TypeScript + Vite，构建到 `assets/web/` 作为静态文件服务

## 安全

- **Token 认证** — 双范围 Token（READ 用于 GET，FULL 用于写操作）。128 位 SecureRandom 生成，存储于 EncryptedSharedPreferences (AES-256-GCM)
- **限流** — 5 次认证失败触发指数退避（1s-32s），按 IP 追踪
- **路径遍历防护** — 所有文件操作使用 canonical path 校验
- **Header 注入防护** — 响应头中的 CR/LF 字符被过滤
- **URL 规范化** — 路径在认证检查前完成解码和规范化
- **CORS** — 可配置，默认允许私有 IP 范围

> **注意**：HTTP 为明文传输。请仅在受信任的网络中使用，或通过 `adb forward` 本地访问。

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin 2.1, Coroutines 1.9 |
| 依赖注入 | Hilt 2.53 |
| 测试 | JUnit5, MockK |
| 前端 | React 19, TypeScript 5.9, Vite 8 |
| 最低 SDK | 26 (Android 8.0) |
| 目标 SDK | 35 |

## 开发

```bash
# 运行 automator 单元测试（62 个测试）
./gradlew :automator:test

# 运行 app 单元测试
./gradlew :app:testDebugUnitTest

# Web 前端开发服务器（代理 /api 到 localhost:8080）
cd web && npm run dev

# TypeScript 类型检查
cd web && npx tsc --noEmit
```

## 许可证

[Apache License 2.0](LICENSE)
