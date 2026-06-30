# Phase 3 Bugfix Round 3：页码 + 双翻 + 回翻定位 + 跟手

日期：2026-06-30

分支：`dual-webview`

## 真机测试发现的 4 个 bug

### Bug 1：页码不更新

**现象**：跨章切换到 Slot 1 活跃后，底部页码 `${currentPageIndex + 1} / ${totalPages}` 不再更新。

**根因**：`bridge1.handlePageChanged = { _, _ -> }` 直接丢弃了所有 page changed 回调。Slot 1 成为活跃槽位后，JS 的每次 `onPageChanged` 都被忽略，ViewModel 的 `currentPageIndex/totalPages` 永远停留在 Slot 0 最后一页的值。

**修复**：
- `bridge0.handlePageChanged`：加 `if (activeSlot == 0)` 守卫
- `bridge1.handlePageChanged`：从 `{ _, _ -> }` 改为条件更新 `if (activeSlot == 1) viewModel.onPageChanged(page, total)`
- `bridge1.handlePaginationComplete`：当 `activeSlot == 1` 时也处理 `onPageReady()`、`paginationDeferred`、`pendingPageFraction`（与 bridge0 对称）

### Bug 2：有概率一次性翻两章

**现象**：跨章动画执行期间，偶尔一次手势翻了两章。

**根因**：
1. Compose slide 动画 300ms 期间，`isChapterAnimating = true` 守卫了 `checkAndAnimate()`，但 `handleChapterFlipReady` 没有守卫 → 仍会设置 `pendingTransitionDir` 和调用 `preloadHiddenSlot`
2. 动画结束时 `pendingTransitionDir` 没有被重置 → 残留值 + 后续 `onPaginationComplete` 触发第二次动画

**修复**：
- `bridge0/1.handleChapterFlipReady` 开头加 `if (isChapterAnimating.value) return` 守卫
- 动画完成回调末尾加 `pendingTransitionDir = 0`（防御纵深）

### Bug 3：往上一章翻会翻到上一章节第一页

**现象**：向后翻章（dir=-1）总是显示上一章第 1 页，违背电子书惯例（向后翻应显示上一章**最后一页**）。

**根因**：`preloadHiddenSlot` 加载 HTML 后，JS `init()` 始终初始化 `cur = 0`（第 1 页）。

**修复**：在 `checkAndAnimate()` 中，`dir < 0` 时先用 `evaluateJavascript("window.flipToLastPage()")` 把隐藏槽位跳到最后一页，`delay(80ms)` 等 WebView 渲染，再启动 Compose slide 动画。

### Bug 4：动画不跟手

**现象**：用户拖拽到章节边界松手后 → JS 橡皮筋回弹 → 等待加载（200-500ms）→ Compose slide 动画。三段式体验不跟手。

**根因**：隐藏槽位没有主动预加载——只有 `onChapterFlipReady` 触发后才 `loadDataWithBaseURL`。加载+分页延迟导致松手后长时间等待。

**修复（阶段 A — 主动预加载）**：
- 在 `bridge0/1.handlePageChanged` 中检测用户是否靠近章节边界（前 2 页 / 后 2 页）
- 靠近边界时主动调用 `preloadHiddenSlot(adj, dir, setPending=false)` 后台预加载
- `handleChapterFlipReady` 触发时，隐藏槽位大概率已 ready → 立即进入动画

## 变更详情

### ReaderScreen.kt

1. **bridge0 handlePageChanged** (line 620) — 加 `activeSlot == 0` 守卫 + 主动预加载逻辑
2. **bridge0 handleChapterFlipReady** (line 638) — 加 `isChapterAnimating` 守卫
3. **bridge1 handlePageChanged** (line 693) — 从丢弃改为条件更新 + 主动预加载逻辑
4. **bridge1 handleChapterFlipReady** (line 703) — 加 `isChapterAnimating` 守卫
5. **bridge1 handlePaginationComplete** (line 716) — activeSlot==1 时对称处理 onPageReady/paginationDeferred/pendingPageFraction
6. **checkAndAnimate** (line 594) — dir<0 时 flipToLastPage + delay(80ms)
7. **checkAndAnimate** (line 621) — 动画完成后 `pendingTransitionDir = 0`

## 验证

```bash
./gradlew compileDebugKotlin  # ✅ BUILD SUCCESSFUL
./gradlew assembleDebug       # ✅ BUILD SUCCESSFUL
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### 真机测试

```bash
adb logcat -s PG | grep -E "preload|checkAndAnimate|Swap|BLOCKED|flipToLast|proactive"
```

预期：
- ✅ 翻章后页码正确更新
- ✅ 不会一次翻两章（BLOCKED 日志 = 守卫生效）
- ✅ 向后翻章显示上一章最后一页（flipToLastPage 日志）
- ✅ 章节末尾拖拽 → 动画立即启动（主动预加载命中）

## 涉及文件

- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — 7 处修改
