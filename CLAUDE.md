# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Android电子书阅读器，类似Apple Books风格，支持EPUB、PDF、TXT格式。Kotlin + Jetpack Compose，MVVM架构。

## 构建命令

```bash
# 编译检查（最快验证代码是否正确）
./gradlew compileDebugKotlin

# 构建Debug APK
./gradlew assembleDebug
# APK输出: app/build/outputs/apk/debug/app-debug.apk

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk

# 构建Release APK
./gradlew assembleRelease
```

Windows环境下用 `gradlew.bat` 替代 `./gradlew`。

## 架构

三层架构，包名 `com.ebook.reader`：

```
data/          → Room数据库(Entity/DAO)、DataStore、Repository实现
domain/        → 数据模型(Book/Bookmark/Note/ReadingRecord)、Repository接口
ui/            → Compose页面 + ViewModel，按功能分包(home/reader/statistics)
di/            → Hilt AppModule，提供所有依赖
util/          → 工具类
util/parser/   → 电子书解析器（核心模块）
```

### 数据流

```
UI (Compose) ← ViewModel ← Repository ← Room DAO / DataStore
                                    ↑
                              BookParser (EPUB/PDF/TXT)
```

### 导航

单Activity架构：`MainActivity` → `NavGraph` → `HomeScreen` / `StatisticsScreen` / `ReaderScreen`

路由定义在 `ui/navigation/Screen.kt`，NavGraph 在 `ui/navigation/NavGraph.kt`。

### 电子书解析器

`util/parser/` 下的核心模块：

- `BookParser` — 接口，定义 parse/getChapterHtml/getPageContent 等方法
- `BookParserFactory` — 根据 BookFormat 创建对应解析器
- `EpubParser` — 自实现ZIP解析，图片转Base64内嵌到HTML
- `PdfParser` — Android PdfRenderer，按需渲染+LRU缓存（最多3页），JPEG压缩
- `TxtParser` — 纯文本包装为HTML

**重要**：EPUB/PDF的图片通过WebView渲染HTML（含Base64 data URI），不是Compose原生渲染。

### 阅读器手势

`ReaderScreen.kt` 使用单个 `awaitEachGesture` 处理拖拽和点击：
- 左侧30%点击 → 上一页
- 右侧30%点击 → 下一页
- 中间40% → 通过WebView JS注入 + `@JavascriptInterface` 回调触发菜单
- 拖拽 → 跟随手势滑动，松手后判断方向翻页

## 关键技术决策

- **Hilt** 依赖注入，所有 ViewModel 用 `@HiltViewModel`
- **Room** 数据库，`fallbackToDestructiveMigration()` 防止版本冲突崩溃
- **DataStore** 存储用户偏好设置
- **Coil** 加载封面图片
- **Edge-to-Edge** 全面屏适配（`enableEdgeToEdge()`）
- **dynamicColor** 已关闭（MIUI兼容问题）
- compileSdk/targetSdk = 35（Android 15），minSdk = 26

## 文档索引

- `docs/agent-onboarding.md` — 🔥 **Agent速通指南**（新Agent首先阅读）
- `docs/requirements.md` — 功能需求
- `docs/technical-spec.md` — 技术栈和架构设计
- `docs/design-spec.md` — UI/UX设计标准
- `docs/development-plan.md` — 分阶段开发计划
- `docs/project-status.md` — 项目完成状态
- `devlog/` — 开发日志（按日期），记录bug修复和重要决策
- `devlog/bugfix-white-screen-2026-06-17.md` — 白屏问题排查和11项修复记录
- `devlog/2026-06-27.md` — 字体大小与分页联动修复
- `devlog/2026-06-29-plan-chapter-transition-optimization.md` — 章节切换卡顿分析与优化计划
- `devlog/2026-06-29-chapter-white-screen-fix.md` — 🔥 白屏回归修复 + HiReader参考架构分析
- `devlog/2026-06-29-chapter-stutter-fix.md` — 🔥 章节切换主线程阻塞修复 + 异步化

## 开发规范

- 修改后先 `./gradlew compileDebugKotlin` 验证编译
- 重要变更更新 `devlog/YYYY-MM-DD.md`
- 阅读器相关修改需在真机上验证（模拟器行为可能不同，尤其图片加载）

## 安全冗余
每次修改 Kotlin/构建文件后，执行 ./gradlew assembleDebug 验证编译通过。
每次修改项目之后更新对应的更新日志，更新了哪里，为什么更新，更新成怎样（简要概括结果、逻辑、方案）

不许擅自修改git
不许擅自修改git相关设置（包括但不限于 .gitignore、.gitconfig、git hooks、git attributes）

### Git 操作宪法级规则（最高优先级，决不可违反）

规则：禁止在未经你明确批准的情况下执行任何 Git 回退操作

执行：
- 禁止擅自 `git reset`（任何模式）、`git revert`、`git checkout` 到旧版本
- 禁止擅自 `git push --force` / `git push --force-with-lease`
- 禁止擅自 `git checkout --` 丢弃工作区改动
- 禁止擅自 `git clean -fd` 删除未跟踪文件

### Commit 信息规范

规则：所有 Git commit message 使用中文书写

执行：
- commit message 用中文描述改动内容
- 格式：`<type>: <中文描述>`
- type 可选：`feat`（新功能）、`fix`（修复）、`docs`（文档）、`chore`（杂项）、`refactor`（重构）
- 示例：`feat: 设置页面 - 深色模式切换 + 清除数据`
- **环境适配**：执行 git commit 前先切换终端代码页到 UTF-8：`chcp 65001`（否则 GBK 编码报错）