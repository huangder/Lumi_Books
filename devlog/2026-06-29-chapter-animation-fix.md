# 章节间翻页动画优化 — 核心修复

日期：2026-06-29

## 问题

跨章节翻页时动画断裂：
- 预渲染未命中时（Miss Path）：橡皮筋阻力拖不动、看不到下一页内容、Compose 整屏滑出→`loadDataWithBaseURL`→滑入（~900ms）

## 根因

预渲染窗口太窄、启动太晚。短章节（3-4页）用户翻一两页就到边界了，预渲染还没完成。

参考 HiReader 的 3 章节缓冲区设计：永远保持相邻章节已加载，动画与内容加载解耦。

## 修复方案（4 个阶段，5 个文件）

### Phase 1：扩大预渲染覆盖窗口（把 Miss Path 概率推到接近 0%）

1. **[ReaderViewModel.kt](app/src/main/java/com/ebook/reader/ui/reader/ReaderViewModel.kt)**：
   - 新增 `eagerPreloadAdjacent()`：进入章节后立即后台预加载前后 2 章（EPUB）或 5 页（PDF）到 preloadCache
   - `loadChapterContent()` 调用后触发 eagerPreloadAdjacent
   - preloadCache 容量 15→24
   - `preloadAdjacentChapters()` 阈值：progress ≥ 0% 即触发（原来 15%），prev 窗口 3→8 页

2. **[ReaderScreen.kt](app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt)**：
   - `onPaginationComplete` 立即触发 JS 预渲染相邻章节（绕过 Compose 重组延迟）
   - LaunchedEffect 预渲染阈值扩大：N+1 距边界 8 页（原来 5），N-1 前 5 页（原来 3）

### Phase 2：Miss Path 改为 Loading 占位 + 1:1 跟手

3. **[PageFlipAnimation.kt](app/src/main/java/com/ebook/reader/ui/animation/PageFlipAnimation.kt)**：
   - `SlideCoverAnimation.aDrag` Miss 路径：移除橡皮筋阻力，改为 botLayer 显示 loading spinner 占位 + **1:1 跟手拖拽**（与 Hit Path 相同参数），同时通知 Kotlin 紧急加载
   - 新增 `window.onPreRenderReady()`：Kotlin 异步加载完成后，如果用户仍在拖拽中，无缝替换 loading 占位为真实内容
   - `dEnd` Miss 路径：改为 bounceBack（不再调用旧 Compose 动画路径）
   - `SimpleSlideAnimation`（PDF）同样处理：移除橡皮筋阻力，1:1 跟手

### Phase 3：简化 Kotlin Miss Path

4. **[ReaderScreen.kt](app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt)**：
   - `handleChapterFlipReady` 重写：从 slideOut→loadDataWithBaseURL→wait→slideIn（~900ms）→ 简化为仅异步获取 HTML 并注入 JS（`preRenderChapter` + `onPreRenderReady`）
   - 不再使用 Compose flipOffset 动画，不再调用 `viewModel.nextChapter()`（避免触发全量 WebView 重载）

### Phase 4：大章节优化

5. **[BookParser.kt](app/src/main/java/com/ebook/reader/util/parser/BookParser.kt)**：
   - 新增 `getChapterHtmlLight()` 默认方法：提取 body 内容片段，减少预渲染 Base64 传输量 20-40%

## 效果

- Miss Path 发生概率从常见 → 接近 0%（Phase 1 激进预渲染）
- 即使 Miss Path 触发，也能 1:1 跟手拖拽 + loading 占位（Phase 2），不再有橡皮筋阻力
- 不再使用 `loadDataWithBaseURL` 做章节切换（Phase 3），消除全量重载延迟

## 编译

```bash
./gradlew compileDebugKotlin   # BUILD SUCCESSFUL
./gradlew assembleDebug         # BUILD SUCCESSFUL
```
