<!-- Language: zh | Audience: dev -->

<div align="center">

<img src="docs/readme-icon.png" alt="Lumi" width="96">

# Lumi 开发者指引

Android 本地电子书阅读器的技术说明，面向贡献者和二次开发者。

[![Version](https://img.shields.io/badge/version-2.3.11-coral)](https://github.com/huangder/Lumi_Books/releases)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-blue)](https://developer.android.com)

**中文** · [English](README.dev.en.md) · [日本語](README.dev.ja.md) · [한국어](README.dev.ko.md)

[用户版](README.md) · **开发者版**

</div>

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.3.10 |
| 构建 | Android Gradle Plugin | 8.13.2 |
| UI | Jetpack Compose（BOM） | 2026.06.01 |
| 依赖注入 | Hilt | 2.58 |
| 数据库 | Room | 2.8.4 |
| 偏好存储 | DataStore Preferences | 1.1.1 |
| 导航 | Navigation Compose | 2.8.9 |
| 协程 | kotlinx-coroutines-android | 1.9.0 |
| 后台任务 | WorkManager | 2.10.0 |
| EPUB 书版渲染 | AndroidX WebKit | 1.16.0 |
| HTML 解析 | Jsoup | 1.22.2 |
| PDF 解析 | pdfbox-android | 2.0.27.0 |
| Markdown | CommonMark | 0.24.0 |
| 网络 | OkHttp | 4.12.0 |
| 图片加载 | Coil | 2.7.0 |
| 模糊与玻璃效果 | Haze 1.1.1，Backdrop（`third_party/backdrop`） | — |
| SDK | minSdk 26，compileSdk 37，targetSdk 37 | — |
| JVM | Java 17 | — |

## 架构

应用采用 MVVM + Repository 架构，依赖注入使用 Hilt。

```
ui/        Compose 界面与 ViewModel：home、reader、settings、statistics、bookshelf 等
domain/    领域模型与仓库接口
data/      Room、DataStore、仓库实现、WebDAV / OPDS 等远程数据源、备份与同步
util/      解析器（EPUB、PDF、TXT、Markdown、Word、Kindle）、排版与缓存工具
tts/       系统语音与外部语音接口
widget/    桌面小组件
edition/   公开版与官方版之间的功能接口
```

- **界面**：`MainActivity` 通过 Navigation Compose（`ui/navigation/NavGraph.kt`）承载主要页面。欢迎页、全屏听书、听书设置等页面是独立的 Activity。
- **依赖注入**：`EBookReaderApp` 标注 `@HiltAndroidApp`，ViewModel 使用 `@HiltViewModel`，模块位于 `di/`。
- **数据库**：Room 当前版本为 28，所有迁移在 `di/AppModule.kt` 中通过 `addMigrations` 注册。修改表结构时需要同时增加版本号和迁移，不要依赖破坏性迁移。
- **偏好与密钥**：用户设置保存在 DataStore。WebDAV、翻译、语音和 MinerU 的 Token 等凭据通过 Android KeyStore 加密保存。
- **阅读器**：EPUB 书版排版走 WebView，保留出版方的 CSS 与字体；阅读器排版走 Canvas + StaticLayout，使用统一的字体与版式。

## 项目结构

```
android_books/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/            公共代码与资源
│       │   ├── java/com/huangder/lumibooks/
│       │   ├── assets/      内置 HTML、字体、词典、更新日志
│       │   └── res/         资源（7 种界面语言）
│       ├── oss/             公开版的功能实现
│       ├── premium/         官方版私有源码（被 Git 忽略，独立仓库，见下文）
│       ├── test/            本地单元测试
│       ├── androidTest/     仪器测试
│       └── debug/           调试入口
│
├── benchmark/               启动与滚动性能测试
├── third_party/             随仓库维护的第三方模块（Backdrop、字幕视图、Word 转换等）
├── tools/                   EPUB 与阅读器的探针、测试脚本和辅助工具
├── devdocs/                 开发文档
├── docs/                    项目网站（GitHub Pages）
└── .github/                 Pages 部署工作流与 Issue 模板
```

## 构建

### 环境

- Android Studio（最新稳定版），或命令行
- JDK 17
- Android SDK：compileSdk 37 及对应的 build-tools
- 命令行构建时设置 `ANDROID_HOME`；Windows 下使用 `gradlew.bat`

### 公开版

只克隆本仓库时，构建结果就是公开版：

```bash
./gradlew :app:assembleDebug
# APK：app/build/oss/outputs/apk/debug/
```

常用的检查命令：

```bash
./gradlew :app:compileDebugKotlin                 # Kotlin 编译
./gradlew :app:testDebugUnitTest                  # 本地单元测试
./gradlew :app:compileDebugAndroidTestKotlin      # 仪器测试的编译检查
```

### 官方版与版本边界

公开仓库和官方版共用同一套代码。两者的差异通过 `edition/` 中的接口隔离：公开仓库提供可独立编译的实现，官方版的私有实现放在 `app/src/premium/`。该目录被公开仓库忽略，拥有私有仓库权限的开发者可以按需克隆：

```bash
git clone --branch main https://github.com/huangder/Lumi_Books-premium.git app/src/premium
```

当 `app/src/premium/java` 存在时，默认构建会切换为官方版，产物位于 `app/build/official/`。要在这种环境下验证公开版，可以加上 `-Plumi.oss=true`：

```bash
./gradlew '-Plumi.oss=true' :app:assembleDebug
```

两种版本使用不同的构建目录，避免 KSP 和 Hilt 生成的代码混用。修改公共接口、数据格式或构建配置时，需要同时确认公开版和官方版都能编译。更详细的规则见 [devdocs/editions.md](devdocs/editions.md)。

### 签名与发布

- release 构建开启了 R8 混淆和资源压缩（`isMinifyEnabled = true`）。
- 仓库中没有提供签名配置。发布前需要在 `app/build.gradle.kts` 中为 release 配置自己的 `signingConfig`。
- 启动诊断等特殊构建类型见 `app/build.gradle.kts` 中的 `buildTypes`。

## 贡献

欢迎提交 Issue 和 Pull Request。

- **Bug 报告**：通过 [GitHub Issues](https://github.com/huangder/Lumi_Books/issues) 提交，附上设备型号、Android 版本和复现步骤。
- **功能建议**：在 Issues 中发起讨论。
- **代码贡献**：
  1. Fork 仓库
  2. 创建功能分支（`feature/xxx` 或 `fix/xxx`）
  3. 开发，并确认 `./gradlew :app:compileDebugKotlin` 通过
  4. 提交 PR 到 `main` 分支
- **Commit 规范**：遵循 Conventional Commits，即 `feat:`、`fix:`、`refactor:`、`docs:`、`chore:`、`perf:`。
- **代码风格**：遵循 Kotlin 官方风格，并沿用项目中已有的模式。

`devdocs/` 中的详细文档目前主要为中文，欢迎贡献其他语言的翻译。

## 文档索引

| 文档 | 路径 |
|------|------|
| 应用内更新日志 | [app/src/main/assets/changelog.md](app/src/main/assets/changelog.md) |
| 线上更新日志 | [huangder.top/changelog](https://huangder.top/changelog.html) |
| 版本与构建边界 | [devdocs/editions.md](devdocs/editions.md) |
| 需求文档 | [devdocs/requirements.md](devdocs/requirements.md) |
| 技术规范 | [devdocs/technical-spec.md](devdocs/technical-spec.md) |
| 设计规范 | [devdocs/design-spec.md](devdocs/design-spec.md) |
| 现行 UI 与动效规范 | [devdocs/lumi-ui-motion-design-system.md](devdocs/lumi-ui-motion-design-system.md) |
| 项目状态 | [devdocs/project-status.md](devdocs/project-status.md) |
| 开发计划 | [devdocs/development-plan.md](devdocs/development-plan.md) |

## 许可证

Lumi 原创代码采用 [Apache License 2.0](LICENSE) 开源。

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

完整的第三方声明见 [NOTICE](NOTICE)，许可证文本位于 [app/src/main/assets/licenses/](app/src/main/assets/licenses/)。

© 2026 Huangder

## 链接

- 官网：[huangder.top](https://huangder.top)
- GitHub：[github.com/huangder/Lumi_Books](https://github.com/huangder/Lumi_Books)
- 邮箱：huangder0104@126.com
