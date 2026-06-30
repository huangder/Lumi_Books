# Phase 3 Bugfix：双动画无限回退 + 章节状态混乱

日期：2026-06-30

分支：`dual-webview`

## 问题

真机测试日志暴露了3个严重bug：

### Bug 1：跨章动画触发后立刻又触发第二次动画，无限回退

日志表现：
```
preloadHiddenSlot: chapter=6 dir=-1  (第一次预加载)
onPaginationComplete Slot1 → checkAndAnimate → animation
Animation complete: swap activeSlot=0->1 newChapter=6
preloadHiddenSlot: chapter=5 dir=-1  (swap后预加载)
onPaginationComplete Slot0 → checkAndAnimate → animation (第二次！)
Animation complete: swap activeSlot=1->0 newChapter=5
preloadHiddenSlot: chapter=4 dir=-1  (又预加载...)
```

**根因**：`checkAndAnimate()` 中 L577 `pendingTransitionDir = 0` 之后，L582 `preloadHiddenSlot(nextAdj, oldDir)` 又把 `pendingTransitionDir` 设回 `oldDir`。下一次 pagination complete 触发时，`checkAndAnimate` 发现 `pendingTransitionDir != 0`，再次启动动画倒回上一章 → 循环无限回退。

### Bug 2：两个 slot 的 onPaginationComplete 几乎同时到达，double animation race

```
onPaginationComplete Slot0 CALLED
checkAndAnimate: ready! dir=-1 activeSlot=1
onPaginationComplete Slot1 CALLED (preload)  ← 几乎同时
checkAndAnimate: ready! dir=-1 activeSlot=1   ← 第二次！
```

**根因**：`pendingTransitionDir = 0` 在 `scope.launch { }` 内部执行（异步），两个 `checkAndAnimate` 调用都在 `pendingTransitionDir` 清零之前进入。

### Bug 3：chapterInSlot1 值异常（=7，预期=6）

可能原因：双动画导致状态频繁切换，或 WebView `loadDataWithBaseURL` 双发 `onPageFinished`（已知 Android WebView quirk）。

## 修复

### 1. `preloadHiddenSlot` 加 `setPending` 参数

```kotlin
fun preloadHiddenSlot(adjChapter: Int, direction: Int, setPending: Boolean = true) {
    // ...
    if (setPending) {
        hiddenSlotSide = if (direction > 0) 1 else -1
        pendingTransitionDir = direction
    }
}
```

swap 后预加载调用 `preloadHiddenSlot(nextAdj, oldDir, setPending = false)`，不设 transition 状态。

### 2. `checkAndAnimate` 同步消费 `pendingTransitionDir`

```kotlin
fun checkAndAnimate() {
    if (pendingTransitionDir == 0) return
    if (isChapterAnimating.value) return  // 新增守卫
    val hiddenReady = ...
    if (!hiddenReady) return
    val dir = pendingTransitionDir
    pendingTransitionDir = 0  // ← 移到 scope.launch 之前，同步执行
    scope.launch { ... }
}
```

关键：`pendingTransitionDir = 0` 现在在协程外、主线程同步执行，第二个 `checkAndAnimate` 会因 `== 0` 直接返回。

### 3. `suppressNextBoundary` 冷却机制

```kotlin
var suppressNextBoundary by remember { mutableStateOf(false) }

// swap 后
suppressNextBoundary = true

// bridge callback 中
if (suppressNextBoundary) {
    suppressNextBoundary = false
    return  // 忽略交换后的第一次边界触发
}
```

防止交换后新章边界页（page=0 total=1）的 JS 误触发。

### 4. Bridge 回调加详细日志

- `handleChapterFlipReady` 加 cci/dir/adj 日志
- `handlePaginationComplete` 加 activeSlot 日志
- suppress 日志标注

## 验证

```bash
./gradlew compileDebugKotlin  # ✅ BUILD SUCCESSFUL
./gradlew assembleDebug       # ✅ BUILD SUCCESSFUL
```

## 真机测试

```bash
adb logcat -s PG | grep -E "preload|checkAndAnimate|Animation|Swap|SUPPRESSED"
```

预期：跨章动画只触发一次，不再无限回退。

## 涉及文件

- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — 4处修改

---

## 补充修复 2（同日）：删 suppressNextBoundary + 修 TOC + 修重复预加载

### 问题

真机测试发现：

1. **翻不到下/上一章**：`suppressNextBoundary` 在第一次 swap 后永久设为 `true`，吃掉所有后续用户手势的 `onChapterFlipReady`
2. **目录跳转失效**：`LaunchedEffect(currentChapterIndex)` 只处理 `activeSlot==0`，交换后 `activeSlot==1` 时 TOC 不做任何事
3. **连续翻章卡住**：swap 后 `preloadHiddenSlot(setPending=false)` 后台预加载了章节 5 到 Slot 0。用户再翻到章 5 时，`preloadHiddenSlot` 又设 `slot0Ready=false` 但 HTML hash 相同 → WebView 不重载 → 永远等不到 ready

### 根因分析

**suppressNextBoundary 的误判**：原以为 JS 在 swap 后会"自动"检测边界并调 `onChapterFlipReady`。但实际 JS 的 `onChapterFlipReady` 只在用户手势（tap/drag）时触发，不存在自动误触发。`suppressNextBoundary` 完全是个错误方案。

### 修复

1. **删除 `suppressNextBoundary`** — 全部移除，包括变量声明、swap 后赋值、bridge 回调中的检查
2. **TOC 跳转支持双槽位** — `LaunchedEffect(currentChapterIndex)` 现在同时处理 `activeSlot==0` 和 `activeSlot==1`
3. **`preloadHiddenSlot` 检测已加载** — 如果隐藏槽位已经持有目标章节的 HTML，直接标记 `ready=true` 而不是 `ready=false`

```kotlin
// preloadHiddenSlot 中新增的检测
if (chapterInSlot1 == adjChapter && slot1Html.value.isNotEmpty()) {
    slot1Ready = true  // 已加载 → 立即可用
} else {
    // 加载新 HTML
}
```
