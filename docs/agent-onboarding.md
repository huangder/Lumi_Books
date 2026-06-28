# Agent Onboarding — 电子书阅读器项目速通指南

> 让新 Agent 在 5 分钟内理解这个项目的全部关键信息，能立刻上手修改代码。

---

## 1. 一句话概述

Android 电子书阅读器（"文阅"），Apple Books 风格，Kotlin + Jetpack Compose + MVVM，支持 EPUB/PDF/TXT，用 **WebView + CSS columns** 做阅读器渲染。

---

## 2. 项目骨架（关键目录树）

```
app/src/main/java/com/ebook/reader/
├── MainActivity.kt                    ← 唯一 Activity，enableEdgeToEdge()
├── di/AppModule.kt                    ← Hilt 模块，提供所有 DAO/Repository
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt            ← Room DB，fallbackToDestructiveMigration()
│   │   ├── dao/                       ← BookDao, ReadingRecordDao, BookmarkDao, NoteDao
│   │   └── entity/                    ← BookEntity, ReadingRecordEntity, BookmarkEntity, NoteEntity
│   ├── repository/                    ← RepositoryImpl（Book, Reading, Bookmark, Note）
│   └── datastore/SettingsDataStore.kt ← DataStore：字体大小、主题偏好
├── domain/
│   ├── model/                         ← Book, ReadingRecord, Bookmark, Note（纯数据类）
│   └── repository/                    ← Repository 接口
├── ui/
│   ├── navigation/
│   │   ├── Screen.kt                 ← 路由定义（Home, Reader, Statistics）
│   │   └── NavGraph.kt               ← NavHost，嵌套导航图
│   ├── home/HomeScreen.kt            ← 首页书籍网格
│   ├── statistics/StatisticsScreen.kt ← 阅读统计页
│   └── reader/
│       ├── ReaderScreen.kt           ← 🔥 核心文件（~1000行）：WebView、JS桥、分页、手势、菜单
│       ├── ReaderViewModel.kt        ← 阅读器状态管理、章节导航、预加载缓存
│       ├── BookmarkScreen.kt         ← 书签列表
│       ├── ThemeSettingsSheet.kt     ← 字体/主题设置 BottomSheet
│       ├── PdfViewerScreen.kt        ← PDF 专用阅读器（独立 LazyColumn 方案）
│       └── ReaderTopBar.kt           ← 阅读器顶栏
├── util/
│   ├── parser/
│   │   ├── BookParser.kt             ← 解析器接口
│   │   ├── BookParserFactory.kt      ← 简单工厂（按格式创建解析器）
│   │   ├── EpubParser.kt            ← EPUB：自实现ZIP解析，图片Base64内嵌
│   │   ├── PdfParser.kt             ← PDF：PdfRenderer按需渲染，LRU缓存3页
│   │   └── TxtParser.kt             ← TXT：按章节标题或3000字分块
│   └── PageCalculator.kt            ← StaticLayout 页面预估工具（当前未被阅读器使用）
└── service/                          ← 后台服务（如有）
```

---

## 3. 核心架构：阅读器是如何工作的

### 3.1 渲染流程（最重要，理解这个才能改代码）

```
用户打开书
  → ReaderViewModel.loadBook()
    → BookParserFactory.createParser() → parser.parse()  [一次性解析全书]
    → loadChapterContent(chapterIndex)
      → 检查 preloadCache，miss 则调 parser.getChapterHtml()
      → 更新 uiState.chapterHtml
        → HtmlContent Composable 检测 chapterHtml 变化
          → WebView.loadDataWithBaseURL(html)  [⚠️ 全量重载，慢]
            → onPageFinished 回调
              → 延迟 300ms
              → 注入分页 JavaScript (buildPaginationJs)
                → JS 设置 CSS column-width = viewport 宽度
                → 计算 total = Math.ceil(scrollWidth / vw)
                → 调用 AndroidBridge.onPageChanged(0, total)
```

### 3.2 分页机制

**不使用原生 Compose 分页，而是用 WebView + CSS columns 取巧：**

```javascript
wrap.style.columnWidth = vw + 'px';      // 每列宽度 = 屏幕宽度
wrap.style.columnFill = 'auto';          // 自动填充列
wrap.style.height = vh + 'px';           // 限制高度
// 翻页 = CSS 平移
wrap.style.transform = 'translateX(' + (-cur * vw) + 'px)';
```

### 3.3 手势处理（全在 JS 端）

| 手势 | 区域 | 行为 | 代码路径 |
|------|------|------|----------|
| 点击左侧 30% | x < 0.3vw | 上一页 / 上一章 | `tapFlip(-1)` / `AndroidBridge.onChapterFlipReady(-1)` |
| 点击中间 40% | 0.3~0.7vw | 切换菜单 | `AndroidBridge.onCenterTap()` |
| 点击右侧 30% | x > 0.7vw | 下一页 / 下一章 | `tapFlip(1)` / `AndroidBridge.onChapterFlipReady(1)` |
| 水平拖拽 | 任意 | 跟随手指滑动，松手判断翻页 | `aDrag(dx)` → `dEnd()` |

### 3.4 JS ↔ Kotlin 桥接

所有桥接通过 `ReaderJsBridge` 类（6 个 `@JavascriptInterface` 方法），注册为 `window.AndroidBridge`：

| JS 调用 | Kotlin 处理 | 实际上做什么 |
|---------|-----------|------------|
| `onPageFlip(dir)` | 空函数 | 翻页动画全在 JS 侧，不需要 Kotlin |
| `onPageChanged(page, total)` | `viewModel.onPageChanged()` | 更新页码、保存进度、触发预加载 |
| `onChapterFlip(dir)` | 空函数 | 未使用 |
| `onChapterFlipReady(dir)` | `handleChapterFlipReady` | 🔥 章节切换核心流程 |
| `onCenterTap()` | `viewModel.toggleMenu()` | 显示/隐藏菜单 |
| `onPaginationComplete()` | `handlePaginationComplete` | 分页完成信号、恢复阅读位置 |

### 3.5 章节切换流程（当前导致卡顿的路径）

```
JS 检测到章节边界 → AndroidBridge.onChapterFlipReady(direction)
  → isChapterAnimating = true; window.__animating = true (阻止触摸)
  → 尝试 usePreRendered(targetIdx)
      ✅ 命中：直接 swap DOM，调用 viewModel.nextChapter()，完成
      ❌ 未命中（走下面的流程）：
        → flipOffset.animateTo(±1f)         [Compose 动画 ~300ms]
        → viewModel.nextChapter()           [换章 → chapterHtml 变 → WebView loadDataWithBaseURL]
        → 等待 onPaginationComplete         [CompletableDeferred, 超时 500ms]
        → flipOffset.snapTo(±1f) + animateTo(0f)  [滑入 ~300ms]
  → 重置动画状态
```

---

## 4. 三层预加载机制

| 层 | 位置 | 触发条件 | 存储什么 | 消费方式 |
|---|------|---------|---------|---------|
| **ViewModel 缓存** | `ReaderViewModel.preloadCache` | 进度 > 40% 或 在第 0-1 页 | 原始 HTML 字符串 | `getChapterHtml()` 消费即删 |
| **JS DOM 预渲染** | WebView `window.preRendered` | 距边界 ≤ 2 页 | 已排版的 DOM wrapper | `usePreRendered()` 即时 swap |
| **PdfParser LRU** | `PdfParser` LinkedHashMap(3) | 首次访问页面 | 渲染好的 Bitmap HTML | `getChapterHtml()` 检查 |

**预渲染黑洞**（当前方案的弱点）：
- 短章节（3-4 页）：用户翻 1 页就到边界，预渲染来不及
- PDF：LaunchedEffect 显式跳过预渲染（`format.name == "PDF"` 时 return）
- 快速连续翻页：预渲染的 Base64 注入可能还没完成用户已经翻过去了

---

## 5. 关键技术细节

### 5.1 图片处理
- **EPUB**：解析时一次性把所有图片转 Base64 内嵌到 HTML（`EpubParser.processHtml()`）
  - SVG 显式跳过（WebView 原生支持）
  - MIME 检测：先看扩展名，再看 magic bytes
- **PDF**：每页按需渲染为 JPEG（quality 85），Base64 嵌入 `<img>`，LRU 缓存 3 页
- **TXT**：无图片

### 5.2 数据持久化
- **书籍列表** → Room `books` 表
- **阅读进度** → `Book.readingProgress: Float`（0~1），公式：`(chapterIndex + pageIndex/totalPages) / chapterCount`
- **阅读记录** → Room `reading_records` 表，每天每本书一条
- **书签/笔记** → Room `bookmarks` / `notes` 表
- **用户偏好** → DataStore（字体大小、主题）

### 5.3 主题系统
- 4 种主题：`day`（白）、`night`（黑）、`sepia`（护眼米色）、`green`（绿色）
- 切换方式：通过 `evaluateJavascript` 动态注入 CSS `!important` 覆盖，不重新加载 HTML
- 字体变化：注入 CSS + 调用 `window.repaginate(true)` 重新计算页数

### 5.4 ReaderScreen 动画
- `SlideCoverAnimation`（EPUB/TXT）：双层视差滑动，顶层有阴影和圆角
- `SimpleSlideAnimation`（PDF）：简单 easeOutCubic 跟手滑动
- 章节切换时用 Compose `Animatable(0f)` 控制 WebView 的 `graphicsLayer.translationX`

---

## 6. 构建与验证

```bash
# Windows 环境
gradlew.bat compileDebugKotlin     # 编译检查
gradlew.bat assembleDebug          # 构建 APK

# APK 位置
app/build/outputs/apk/debug/app-debug.apk

# 安装
adb install app/build/outputs/apk/debug/app-debug.apk
```

- compileSdk / targetSdk = 35, minSdk = 26
- `dynamicColor` 已关闭（MIUI 兼容问题）
- Room 使用 `fallbackToDestructiveMigration()`

---

## 7. 已知问题与注意事项

### 7.1 当前问题
1. **章节切换卡顿**（2026-06-29 分析）：WebView `loadDataWithBaseURL` 全量重载是瓶颈
2. **进度保存过于频繁**：每次 `onPageChanged` 都写 Room（EPUB 未做节流）
3. **`window.__animating` 竞态**：章节切换异常时可能不重置，导致触摸永久阻塞
4. **`runBlocking` 在主线程**：`saveAndPause()` 用 `runBlocking` 写数据库（应改为协程）
5. **PDF 每页一个"章节"**：`PdfParser` 把每页当独立章节，但禁用预渲染
6. **PdfParser 不 close**：ViewModel `onCleared()` 没有调用 `parser.close()`

### 7.2 不要做的事
- ❌ 不要动 CSS columns 分页方案为原生分页（工作量巨大）
- ❌ 不要在 `saveProgress()` 中做复杂计算（调用太频繁）
- ❌ 不要移除 `fallbackToDestructiveMigration()`（数据库版本升级会崩溃）
- ❌ 不要启用 `dynamicColor`（MIUI 上会白屏）

### 7.3 可以安全做的事
- ✅ 调整预渲染触发阈值（LaunchedEffect 中的 `total - 2`）
- ✅ 增加 preloadCache 容量
- ✅ 修改 JS `buildPaginationJs()` 中的动画参数
- ✅ 添加 WebView 加载完成的更精确检测替代 300ms 硬延迟
- ✅ 优化 `saveProgress` 节流

---

## 8. 阅读器核心文件速查表

| 想改什么 | 去哪个文件 | 找什么 |
|---------|-----------|--------|
| 翻页动画 | `ReaderScreen.kt` | `buildPaginationJs()` 中的 `flipTo()` / `tapFlip()` |
| 章节切换动画 | `ReaderScreen.kt` | `handleChapterFlipReady` lambda |
| 章节预渲染 | `ReaderScreen.kt` | `LaunchedEffect` 约 471 行 |
| 分页逻辑 | `ReaderScreen.kt` | `buildPaginationJs()` 中的 `init()` / `repaginate()` |
| 触摸手势 | `ReaderScreen.kt` | `buildPaginationJs()` 中的 touchstart/touchmove/touchend |
| 主题/字体 | `ReaderScreen.kt` | `HtmlContent` 的 `update` 块 |
| ViewModel 状态 | `ReaderViewModel.kt` | `ReaderUiState` 数据类 |
| 章节导航 | `ReaderViewModel.kt` | `nextChapter()` / `previousChapter()` / `setChapter()` |
| 预加载缓存 | `ReaderViewModel.kt` | `preloadCache` / `preloadAdjacentChapters()` |
| EPUB 解析 | `EpubParser.kt` | `extractChapters()` / `processHtml()` |
| PDF 渲染 | `PdfParser.kt` | `getChapterHtml()` / LRU 缓存 |
| 封面动画 | `PageFlipAnimation.kt` | `SlideCoverAnimation` / `SimpleSlideAnimation` |
| 进度公式 | `ReaderViewModel.kt` | `saveProgress()` 中的进度计算 |
| 书签 | `BookmarkScreen.kt` | 独立 composable |
| 目录 | `ReaderScreen.kt` | `TocOverlay` composable |

---

## 9. 文档索引

| 文档 | 内容 |
|------|------|
| `CLAUDE.md` | 项目概述、构建命令、架构简述 |
| `docs/requirements.md` | 功能需求列表 |
| `docs/technical-spec.md` | 技术栈选型、架构设计 |
| `docs/design-spec.md` | UI/UX 设计标准 |
| `docs/development-plan.md` | 分阶段开发计划 |
| `docs/project-status.md` | 项目完成状态报告 |
| `docs/agent-onboarding.md` | 本文档 |
| `devlog/2026-06-17.md` | 初始开发日志 |
| `devlog/2026-06-27.md` | 字体分页联动修复 |
| `devlog/2026-06-27-plan-font-bg-fix.md` | 背景色/字号/章节切换修复计划 |
| `devlog/bugfix-white-screen-2026-06-17.md` | 白屏问题 11 项修复记录 |

---

**最后更新**：2026-06-29 | **维护者**：Agent 协作
