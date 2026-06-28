# 2026-06-29 分析计划：章节切换卡顿优化

## 问题描述

阅读器在章节内翻页流畅无缝（纯 CSS `translateX`，~16ms），但跨章节切换时有明显卡顿和延迟（~500-1100ms）。用户滑动到章末时，不是立刻看到下一章内容，而是卡住等待。

## 根因分析（3 个串联瓶颈）

### 瓶颈1：WebView `loadDataWithBaseURL()` 全量重载（主导因素）

换章时 ViewModel 更新 `chapterHtml` → Compose 重组 → WebView 必须执行 `loadDataWithBaseURL()`：

```
销毁旧 DOM → 解析新 HTML → 重算 CSS → Layout → Paint
    → 等 onPageFinished → 硬编码延迟 300ms → 注入分页 JS → 重算列布局
```

对于 Base64 内嵌图片的 EPUB 章节，HTML 可能数 MB，解析+排版需数百毫秒。

### 瓶颈2：预渲染覆盖不足

已有两层预加载，但触发条件太保守：
- **Kotlin `preloadCache`**：进度 > 40% 才预加载下一章。短章节（3-4 页）翻 1-2 页就到边界，来不及。
- **JS `preRenderChapter`**：距边界 ≤ 2 页才触发。同上问题。
- **PDF 完全禁用**：`LaunchedEffect` 显式 `if (format == "PDF") return`

### 瓶颈3：Compose 动画序列串行等待

预渲染 miss 时的完整流程（ReaderScreen.kt `handleChapterFlipReady`）：

```
flipOffset.animateTo(±1f)  ──300ms──→ viewModel.nextChapter()
  → WebView loadDataWithBaseURL → 等 CompletableDeferred(500ms超时)
  → flipOffset.animateTo(0f) ──300ms──→ 完成
```

最坏：**300 + 500 + 300 = 1100ms**，且 WebView 加载期间用户看到空白。

### PDF 特有问题

`PdfParser.getChapterHtml()` 按需渲染：`PdfRenderer.openPage()` → Bitmap → JPEG 压缩 → Base64。单页 200-500ms，LRU 缓存仅 3 页，顺序阅读命中率 0。

## 与其他电子书软件的核心差异

| 方面 | 本项目 | Apple Books / Kindle |
|------|--------|---------------------|
| 渲染引擎 | Android WebView（通用浏览器） | 原生排版引擎（专为电子书优化） |
| 分页方式 | CSS columns（hack） | 引擎级预分页 + 字形定位 |
| 章节切换 | `loadDataWithBaseURL` 全量重载 | 内存中预排版页面直接 swap |
| 图片处理 | Base64 内嵌 HTML（膨胀 33%） | 原生解码 + 文件缓存 |

**根本原因**：WebView 是通用浏览器引擎，`loadDataWithBaseURL` 设计目标是"加载网页"而非"毫秒级切换电子书章节"。CSS columns 取巧实现了分页，但换章时绕不开浏览器引擎的完整加载流水线。

## 优化方向（按优先级）

### 方向1：降低预渲染触发阈值（低风险，立即见效）
- JS 预渲染：从 `≤ 2 页` 改为 `≤ 5 页` 或进入章节即触发
- Kotlin 预加载：从 `> 40%` 改为 `> 20%` 或进入章节即触发
- 涉及文件：ReaderScreen.kt `LaunchedEffect` + ReaderViewModel.kt `preloadAdjacentChapters()`

### 方向2：消除/缩短 300ms 硬编码延迟
- 当前：`webView.postDelayed({ injectPaginationJs() }, 300)`
- 改进：用 `evaluateJavascript` 轮询 `document.readyState` 或用 `onPageCommitVisible` 替代 `onPageFinished`
- 涉及文件：ReaderScreen.kt `onPageFinished`

### 方向3：双 WebView 方案（大改动，根除问题）
- 当前：单 WebView，换章时销毁旧 DOM 加载新 DOM
- 改进：两个 WebView（一个显示，一个隐藏预加载相邻章节），切换时 Compose 层面 swap
- 类似 iOS `UIPageViewController` 的双页缓冲机制
- 涉及文件：ReaderScreen.kt（需重构 `HtmlContent` composable）

### 方向4：PDF 启用预渲染
- 当前：PDF 显式跳过预渲染
- 改进：至少预渲染相邻 2-3 页的 HTML
- 涉及文件：ReaderScreen.kt `LaunchedEffect`

### 方向5：Compose 动画与加载并行化
- 当前：`animateTo` → 等待加载 → `animateTo`（串行）
- 改进：加载在后台进行，动画直接过渡（或显示骨架/占位符）
- 涉及文件：ReaderScreen.kt `handleChapterFlipReady`

### 方向6：预加载缓存扩容
- 当前：preloadCache 只存相邻 1 章，one-shot 消费
- 改进：存前后各 2-3 章，非 one-shot（保留最近使用的 N 章）
- 涉及文件：ReaderViewModel.kt `preloadCache`

## 推荐执行路径

**第一阶段（低成本快速见效）**：
1. 方向1：降低预渲染阈值
2. 方向2：缩短 300ms 延迟
3. 方向6：扩容 preloadCache

**第二阶段（中等改动）**：
4. 方向4：PDF 启用预渲染
5. 方向5：动画与加载并行

**第三阶段（架构改动，根除问题）**：
6. 方向3：双 WebView 方案

## 验证方法

1. 打开 EPUB 书，快速连续翻页至章末 → 观察切换是否即时
2. 同一个 EPUB，分别在短章节（3-4 页）和长章节（10+ 页）的章末切换 → 对比延迟
3. 打开 PDF，连续翻页 → 观察页面加载延迟
4. 切换主题后翻到章末 → 确认预渲染的主题一致性
5. 快速来回翻越章节边界 → 确认无闪烁或 DOM 残留

## 相关文件

- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — 主战场：handleChapterFlipReady, LaunchedEffect, onPageFinished, buildPaginationJs
- `app/src/main/java/com/ebook/reader/ui/reader/ReaderViewModel.kt` — preloadCache, preloadAdjacentChapters, getChapterHtml
- `app/src/main/java/com/ebook/reader/util/parser/PdfParser.kt` — PDF 渲染性能
- `app/src/main/java/com/ebook/reader/util/parser/EpubParser.kt` — 图片 Base64 膨胀问题
- `docs/agent-onboarding.md` — 项目速通指南

---

**分析日期**：2026-06-29 | **状态**：分析完成，待实施
