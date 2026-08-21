# Android Vibe Design -- MVP

## 1. 产品定位

一款运行在 Android 端、面向非技术用户的开源 Vibe Design 工具。用户无需掌握编程知识或配置传统开发环境，只需通过自然语言与 AI 对话，即可在手机上生成、修改和实时预览移动端 UI。

产品专注于移动界面设计、交互验证和轻量应用原型制作，并支持将设计直接导出为可安装、可分享的 APK。它不试图替代完整的 Android 原生开发体系，而是希望将从想法到可体验 App 的过程尽可能缩短，让普通用户下载后即可开始创作。

---

## 2. 用户设计模式 -- dev服务 + Agent修改前端代码

主应用启动 dev 服务，用户点击”浏览“时对设计进行浏览

```text
React/Vue 源码
      ↓
主应用启动 Vite Dev Server
http://127.0.0.1:<port>
      ↓
主应用内 WebView
实时预览 + HMR
```

**主应用提供 agent对话界面 和 ai悬浮窗**

---

## 3. apk构建方式 -- 预编译 Android WebView 壳 APK

APK 不是现场用 Gradle 编译，而是改造模板，使用内置预编译的 Android WebView 壳 APK

导出时 `ApkBuilder` 把模板 APK 当作 ZIP 重写：

- 修改二进制 `AndroidManifest.xml`：

  - 包名
  - versionCode/versionName
  - 权限
  - 所需组件
  
- 修改 `resources.arsc` 中的应用名称和图标引用

- 替换启动图标、启动页

- 写入 `config.json`

- 删除不需要的 ABI 和运行时

- 注入前端产物

所以手机端不需要 Android SDK、Gradle、AAPT2 或 D8；这些工作已经体现在模板 APK 中。

### 预编译 Android WebView 壳 APK

```text
webview_shell.apk
├─ AndroidManifest.xml
├─ classes.dex
│  ├─ ShellActivity
│  ├─ ShellModeManager
│  ├─ WebViewManager
│  ├─ LocalHttpServer
│  ├─ ProductionFrontendShellMode
│  ├─ DevelopmentFrontendShellMode
│  ├─ JavaScript Bridge
│  └─ 其他运行时能力
├─ resources.arsc
├─ res/
│  ├─ drawable/
│  ├─ mipmap/
│  ├─ layout/
│  ├─ xml/
│  └─ values/
├─ assets/
│  └─ app_config.json（模板配置或占位）
└─ lib/
   └─ 必需的基础 native 库
```

### Production APK

```text
React / Vue 源码
        ↓
主应用准备 Node.js 环境
npm / pnpm / yarn install
npm run build
        ↓
dist / build / out
HTML + JS + CSS + 图片
        ↓
写入预编译的 webview_shell.apk
assets/frontend_app/
        ↓
修改包名、名称、图标、配置
ZipAlign → 签名 → APK
        ↓
安装后解压资源
启动 127.0.0.1 本地静态服务器（Kotlin 实现的轻量 HTTP Server）
        ↓
WebView 加载 React / Vue 页面
```

---

## 4. Agent形式 -- Android端应用内置Agent

Android端应用内置一个基本可用的ReAct Agent，填入ai api key即可使用，以便于完全没有电脑编程基础的用户使用
