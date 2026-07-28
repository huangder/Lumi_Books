<!-- Language: zh | Audience: dev -->

<div align="center">

🌐 **中文** · [English](README.dev.en.md) · [日本語](README.dev.ja.md) · [한국어](README.dev.ko.md)

[📖 用户版](README.md) · ⚙️ **开发者版**

</div>

---

# Lumi — 开发者指引

> Android 本地电子书阅读器的技术文档。面向贡献者与二次开发者。

[![Version](https://img.shields.io/badge/version-1.6.00-coral)](https://github.com/huangder/Lumi_Books/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-blue)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple)](https://kotlinlang.org)

---

## 📋 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.3.10 |
| UI 框架 | Jetpack Compose (BOM) | 2026.06.01 |
| 架构 | MVVM + Repository | — |
| 依赖注入 | Hilt | 2.58 |
| 数据库 | Room | 2.8.4 |
| 偏好存储 | DataStore Preferences | 1.1.1 |
| 导航 | Navigation Compose | 2.8.9 |
| 图片加载 | Coil | 2.7.0 |
| 异步 | Kotlin Coroutines | 1.9.0 |
| EPUB 渲染 | Android WebKit | 1.16.0 |
| HTML 解析 | Jsoup | 1.22.2 |
| PDF 解析 | pdfbox-android | 2.0.27.0 |
| 网络 | OkHttp | 4.12.0 |
| Markdown | CommonMark | 0.24.0 |
| 毛玻璃效果 | Haze | 1.1.1 |
| 液态玻璃 | Backdrop | 1.0.6 |
| 后台任务 | WorkManager | 2.10.0 |
| 调色板 | Palette KTX | 1.0.0 |
| 构建工具 | AGP | 8.13.2 |
| 注解处理 | KSP | 2.3.10 |
| 最低 SDK | Android 8.0 (API 26) | — |
| 目标 SDK | Android 15 (API 35) | — |
| 编译 SDK | Android 16 (API 36) | — |
| JVM 目标 | Java 17 | — |

---

## 🏗 架构概览

Lumi 采用 **MVVM + Repository** 架构，结合 Hilt 依赖注入：

```
┌─────────────────────────────────────────────┐
│  UI Layer (Compose Screens + ViewModels)     │
│  ui/home/  ui/reader/  ui/settings/  ...     │
├─────────────────────────────────────────────┤
│  Domain Layer (Models)                        │
│  domain/model/  Book, Bookmark, Note, ...    │
├─────────────────────────────────────────────┤
│  Data Layer (Repository + DAO + DataStore)   │
│  data/local/  data/repository/               │
│  data/sync/   WebdavSyncManager              │
├─────────────────────────────────────────────┤
│  Util Layer (Parsers, Helpers)               │
│  util/parser/  EPUB, PDF, TXT               │
│  util/  LocaleHelper, FontManager, ...       │
└─────────────────────────────────────────────┘
```

- **Single Activity + Navigation Compose**：`MainActivity` 承载主要 Compose 导航；设置/引导等使用独立 Activity 以支持原生转场动画。
- **Hilt**：`@HiltAndroidApp` → `@HiltViewModel` → `@Inject constructor`。
- **Room**：`BookDao`、`BookmarkDao`、`ReadingRecordDao`，数据库迁移使用 `fallbackToDestructiveMigration()`。
- **DataStore**：存储用户偏好（主题、阅读设置、TTS 配置等）。
- **阅读器引擎**：EPUB 支持两种模式 —— Canvas + StaticLayout（统一点阵排版）和 WebView（书版 CSS 排版保留）。

---

## 📁 项目结构

```
android_books/
├── LICENSE                     # MIT 许可证与第三方组件声明
├── README.md                   # 用户版说明
├── README.dev.md               # 开发者版说明（本文件）
├── build.gradle.kts            # 顶层构建配置
├── settings.gradle.kts         # 项目设置
│
├── app/                        # Android 应用代码
│   ├── build.gradle.kts        # 应用构建配置
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/             # 内嵌 HTML、字体、changelog
│       ├── java/com/huangder/lumibooks/
│       │   ├── EBookReaderApp.kt       # Application 类
│       │   ├── MainActivity.kt         # 主 Activity
│       │   ├── data/                   # 数据层
│       │   │   ├── local/              # Room DAO/Entity、DataStore
│       │   │   ├── repository/         # Repository 实现
│       │   │   └── sync/              # WebdavSyncManager
│       │   ├── domain/model/           # 领域模型
│       │   ├── di/                     # Hilt Module
│       │   ├── ui/                     # 表现层
│       │   │   ├── home/               # 首页 + 书架
│       │   │   ├── reader/             # 阅读器（核心）
│       │   │   │   └── engine/         # Canvas 排版引擎
│       │   │   ├── statistics/         # 阅读统计
│       │   │   ├── settings/           # 设置
│       │   │   ├── bookshelf/          # 书架组件
│       │   │   ├── components/         # 通用 Compose 组件
│       │   │   ├── welcome/            # 引导页
│       │   │   └── navigation/         # 导航图
│       │   └── util/                   # 工具类
│       │       ├── parser/             # EPUB/PDF/TXT 解析器
│       │       ├── LocaleHelper.kt     # 多语言切换
│       │       └── ...
│       └── res/                        # 资源文件（8 个语言目录）
│
├── devlog/                     # 开发日志（按日期）
├── devdocs/                    # 项目文档
│   ├── requirements.md         # 需求文档
│   ├── technical-spec.md       # 技术规范
│   ├── design-spec.md          # 设计规范
│   ├── ui-design-spec.md       # UI 设计实现
│   ├── project-status.md       # 项目状态
│   ├── development-plan.md     # 开发计划
│   └── CHANGELOG.md            # 更新日志（v1.0.01.124）
│
├── docs/                       # 项目网站 (GitHub Pages → huangder.top)
│   ├── index.html              # 首页
│   ├── features.html           # 功能特性
│   ├── tech.html               # 技术规格
│   ├── privacy.html            # 隐私政策
│   └── ...
│
└── .github/workflows/          # CI（GitHub Pages 部署）
```

---

## 🔨 构建

### 前置条件
- **Android Studio**（最新稳定版推荐）
- **JDK 17**
- **Android SDK 36**（compileSdk）及 35（targetSdk）build-tools

### 步骤

```bash
# 1. 克隆仓库
git clone https://github.com/huangder/Lumi_Books.git
cd Lumi_Books

# 2. 用 Android Studio 打开项目，等待 Gradle 同步完成

# 3. 连接设备或启动模拟器（API 26+），点击运行

# 或者命令行构建：
./gradlew assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk

# Release 构建（需配置签名）：
./gradlew assembleRelease
# APK 输出: app/build/outputs/apk/release/
```

### 签名配置

Release 构建需要在 `app/` 下放置 `keystore.properties` 或在 `build.gradle.kts` 中配置签名信息。默认 release buildType 关闭了 minify（`isMinifyEnabled = false`）。

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request。

- **Bug 报告**：通过 [GitHub Issues](https://github.com/huangder/Lumi_Books/issues) 提交，附上设备型号、Android 版本和复现步骤。
- **功能建议**：在 Issues 中发起讨论。
- **代码贡献**：
  1. Fork 仓库
  2. 创建功能分支（`feature/xxx` 或 `fix/xxx`）
  3. 开发并确认 `./gradlew compileDebugKotlin` 通过
  4. 提交 PR 到 `main` 分支
- **Commit 规范**：遵循 Conventional Commits —— `feat:` / `fix:` / `refactor:` / `docs:` / `chore:` / `perf:`。
- **代码风格**：遵循 Kotlin 官方代码风格与项目现有模式。

> **注意**：`devdocs/` 和 `devlog/` 中的详细开发文档目前主要为中文。欢迎贡献其他语言的翻译。

---

## 📚 项目文档索引

| 文档 | 路径 |
|------|------|
| 更新日志 (v1.0.01) | [devdocs/CHANGELOG.md](devdocs/CHANGELOG.md) |
| 线上更新日志 | [huangder.top/changelog](https://huangder.top/changelog.html) |
| 需求文档 | [devdocs/requirements.md](devdocs/requirements.md) |
| 技术规范 | [devdocs/technical-spec.md](devdocs/technical-spec.md) |
| 设计规范 | [devdocs/design-spec.md](devdocs/design-spec.md) |
| UI 设计实现 | [devdocs/ui-design-spec.md](devdocs/ui-design-spec.md) |
| 项目状态 | [devdocs/project-status.md](devdocs/project-status.md) |
| 开发计划 | [devdocs/development-plan.md](devdocs/development-plan.md) |
| 开发日志 | [devlog/](devlog/) |
| 应用内更新日志 | [app/src/main/assets/changelog.md](app/src/main/assets/changelog.md) |

---

## 📜 许可证

Lumi 原创代码采用 [MIT License](LICENSE) 开源。

第三方依赖及改编代码继续遵循各自许可证：

| 组件 | 许可证 | 备注 |
|------|--------|------|
| [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) | Apache 2.0 | 液态玻璃效果，有改编 |
| [Haze](https://github.com/chrisbanes/haze) | Apache 2.0 | 毛玻璃效果 |
| [PDFBox Android](https://github.com/TomRoush/PdfBox-Android) | Apache 2.0 | PDF 文字提取 |
| [CommonMark](https://github.com/commonmark/commonmark-java) | BSD 2-Clause | Markdown 解析 |
| [Jsoup](https://jsoup.org/) | MIT | HTML 解析 |
| [Coil](https://coil-kt.github.io/coil/) | Apache 2.0 | 图片加载 |
| [OkHttp](https://square.github.io/okhttp/) | Apache 2.0 | HTTP 客户端 |

完整第三方许可文本见 [app/src/main/assets/licenses/](app/src/main/assets/licenses/)。

© 2026 Huangder

---

## 🔗 链接

- 🌐 官网：[huangder.top](https://huangder.top)
- 📦 GitHub：[github.com/huangder/Lumi_Books](https://github.com/huangder/Lumi_Books)
- 📧 邮箱：huangder0104@126.com
