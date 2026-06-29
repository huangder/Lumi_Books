# 2026-06-29 章节切换白屏修复与 HiReader 架构分析

## HiReader 参考分析

参考项目 `HiReader-master` 使用**完全不同的渲染架构**：

| 维度 | HiReader | 本项目 |
|------|---------|--------|
| 渲染引擎 | Android Canvas (StaticLayout) | WebView (Blink) |
| 章节预加载 | 3槽容器(prev/cur/next)，始终在内存 | preloadCache + JS DOM 预渲染 |
| 章节切换 | swap引用 → 重绘Bitmap | loadDataWithBaseURL（全量重载） |
| 页数据 | 预计算 TxtPage 列表 | CSS columns 动态分页 |
| 动画 | Canvas 绘制 Bitmap | Compose translationX 平移 WebView |

### HiReader 核心机制

`chapterContainers = [prevChapter, curChapter, nextChapter]` — 3槽始终持有相邻章节。

换章时（skipNextChapter）：
1. `Collections.swap(chapterContainers, 0, 1)` — cur→prev, next→cur
2. `Collections.swap(chapterContainers, 1, 2)` — 新cur位置是原来的next
3. 清空新next槽位 + 异步加载新next
4. `openChapter()` — 直接重绘（内容已在内存中）

## 白屏回归根因分析

上一轮优化（动画并行化、预渲染阈值降低）引入了 4 个新 Bug：

### Bug 1: 预渲染命中后仍触发 loadDataWithBaseURL（**致命**）

```kotlin
// 原代码：hit 和 miss 都调用 nextChapter()
val used = usePreRendered(targetIdx) == true
if (!used) {
    viewModel.nextChapter()  // → 触发 loadDataWithBaseURL ✓（miss 路径需要）
} else {
    viewModel.nextChapter()  // → 触发 loadDataWithBaseURL ✗（hit 路径不需要！）
    // JS 侧刚 swap 完的 DOM 被 loadDataWithBaseURL 整体替换 → 白屏
}
```

### Bug 2: 动画并行化导致加载期间可见

`nextChapter()` 触发 `loadDataWithBaseURL` 清除旧内容后，WebView 短暂显示白色背景，而并行的滑出动画让用户看到这个过程。

### Bug 3: body visibility 时机不当

`init()` 中 `b.style.visibility = 'visible'` 在分页 JS 完成后立即执行，但 Compose 滑入动画可能尚未开始或进行中。

### Bug 4: 50ms 延迟不足

大章节（含大量 Base64 图片）50ms 不足以完成 layout，导致 `scrollWidth` 可能计算错误。

## 修复方案（5 项改动）

### 改动 1: ReaderViewModel 新增 `onChapterSwapped()`

**文件**: `ReaderViewModel.kt`

仅更新章节索引和进度，**不更新 `chapterHtml`**，避免触发 `HtmlContent.update` 中的 `loadDataWithBaseURL`。

```kotlin
fun onChapterSwapped(direction: Int) {
    val newIdx = (state.currentChapterIndex + direction).coerceIn(0, state.chapterCount - 1)
    _uiState.value = _uiState.value.copy(currentChapterIndex = newIdx, currentPageIndex = 0)
    saveProgress()
    preloadAdjacentChapters()
}
```

### 改动 2: handleChapterFlipReady 双向修复

**文件**: `ReaderScreen.kt`

- **Hit 路径**: `viewModel.nextChapter()` → `viewModel.onChapterSwapped()`（避免 loadDataWithBaseURL）
- **Miss 路径**: 恢复动画序列为"先滑出→再加载→等分页→瞬移→滑入→显示body"：
  1. 设置 `window.__chapterTransition = true` + 隐藏 body
  2. `flipOffset.animateTo(±1f, 250ms)` — 先滑出（旧内容仍在）
  3. `viewModel.nextChapter()` — WebView 不可见时才加载
  4. `withTimeoutOrNull(500L)` — 等待分页完成
  5. `snapTo(∓1f)` + `animateTo(0f, 250ms)` — 瞬移+滑入
  6. 设置 `window.__chapterTransition = false` + 显示 body

### 改动 3: buildPaginationJs 控制 body 可见性

**文件**: `ReaderScreen.kt` `buildPaginationJs` 函数

`init()` 中 body visibility 添加 `__chapterTransition` 检测：
```javascript
if (!window.__chapterTransition) b.style.visibility = 'visible';
```

### 改动 4: loadDataWithBaseURL 前注入 body background

**文件**: `ReaderScreen.kt` `HtmlContent.update`

```kotlin
val htmlWithBg = html.replace("<body", "<body style=\"background:$bgColor\"", ignoreCase = true)
webView.loadDataWithBaseURL(null, htmlWithBg, "text/html", "UTF-8", null)
```

### 改动 5: onPageFinished 延迟恢复

**文件**: `ReaderScreen.kt` `onPageFinished`

50ms → 150ms，给大章节 HTML 充足的 layout 时间。

## 验证

- `compileDebugKotlin`: BUILD SUCCESSFUL（零错误）
- `assembleDebug`: BUILD SUCCESSFUL
- APK: `app/build/outputs/apk/debug/app-debug.apk`

待真机验证：
1. EPUB 翻到章末 → 连续快速翻越章节边界 → 无白屏
2. 夜间/护眼主题下翻越 → 无白色闪烁
3. 短章节（3-4页）快速翻越 → 预渲染命中时即时切换
4. 回翻章节 → 缓存命中，切换即时
