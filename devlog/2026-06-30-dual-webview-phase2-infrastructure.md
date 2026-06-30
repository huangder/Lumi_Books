# 双 WebView 方案 Phase 2：双 WebView 基础设施

日期：2026-06-30

分支：`dual-webview`（基于 `chapter-fix-brunch`）

## 目标

渲染两个 WebView 实例，Slot 0 为主视图（可见），Slot 1 为预加载槽位（隐藏在屏幕右侧）。为 Phase 3 Compose slide 动画做准备。

## 变更

### ReaderScreen.kt

#### HtmlContent 重构

- 新增 `modifier: Modifier = Modifier` 参数，传递给 `AndroidView`
- 移除了 `AndroidView` 调用中多余的 `modifier = Modifier.fillMaxSize()`

#### 双 WebView 状态

```kotlin
val activeWebViewRef = remember { mutableStateOf<WebView?>(null) }  // 替代 webViewRef
val hiddenWebViewRef = remember { mutableStateOf<WebView?>(null) }  // 预加载槽位
val screenWidthPx = remember { context.resources.displayMetrics.widthPixels.toFloat() }
var chapterInSlot0 by remember { mutableStateOf(uiState.currentChapterIndex) }
var chapterInSlot1 by remember { mutableStateOf(-1) }  // -1 = 未加载
val slot1Html = remember { mutableStateOf("") }
```

#### 双 WebView 渲染

```
Box(Modifier.fillMaxSize()) {
    │
    ├─ Slot 0 (主视图): translationX = 0
    │   └─ HtmlContent(html = displayedHtml, chapterIndex = chapterInSlot0, ...)
    │       → onWebViewCreated = { activeWebViewRef.value = it }
    │
    └─ Slot 1 (预加载): translationX = screenWidthPx（隐藏在屏幕右侧）
        └─ HtmlContent(html = slot1Html.value, chapterIndex = chapterInSlot1, ...)
            → onWebViewCreated = { hiddenWebViewRef.value = it }
            → 仅当 slot1Html.isNotEmpty() 时渲染（避免空白 WebView）
}
```

#### handleChapterFlipReady → 预加载隐藏槽位

```kotlin
handleChapterFlipReady = { direction ->
    val adj = cci + direction
    if (adj in 0 until chapterCount) {
        val html = viewModel.getAdjacentChapterHtml(adj)  // 从 LRU 缓存获取
        if (html != null) {
            chapterInSlot1 = adj
            slot1Html.value = html  // 触发 Slot 1 的 HtmlContent 渲染
        }
    }
}
```

#### TOC/滑块跳转 → 清除预加载

```kotlin
LaunchedEffect(uiState.currentChapterIndex) {
    if (chapterInSlot0 != uiState.currentChapterIndex) {
        chapterInSlot0 = uiState.currentChapterIndex
        slot1Html.value = ""       // 清除 Slot 1（相邻章节上下文已变）
        chapterInSlot1 = -1
    }
}
```

#### webViewRef → activeWebViewRef

所有通过 WebView 引用执行 JS 的代码已更新为使用 `activeWebViewRef`：
- 加载超时诊断查询
- `handlePaginationComplete` 中的跳页操作

## 架构

```
Chapter N (Slot 0, 可见)          Chapter N+1 (Slot 1, 隐藏在右侧)
┌─────────────────────────┐      ┌─────────────────────────┐
│ WebView @ translationX=0 │      │ WebView @ translationX=W  │
│ 章内翻页: JS flipTo      │      │ 章内翻页: JS flipTo      │
│ 边界: onChapterFlipReady │      │ 边界: onChapterFlipReady │
│   → 预加载到 Slot 1      │      │   → 预加载到 Slot 0      │
└─────────────────────────┘      └─────────────────────────┘
```

Phase 3 将添加 Compose Animatable 驱动的 graphicsLayer.translationX 动画实现章节切换。

## 验证

```bash
./gradlew compileDebugKotlin  # ✅ BUILD SUCCESSFUL
./gradlew assembleDebug       # ✅ BUILD SUCCESSFUL
```

### 真机测试要点

```bash
adb logcat -s PG | grep -E "Slot1|preload|Chapter boundary"
```

**Phase 2 预期行为：**
- ✅ 章节正常加载显示（Slot 0）
- ✅ 章内翻页动画正常（Phase 1 修复版）
- ✅ 拖拽/点击到章节边界 → log "Slot1 preloaded: chapter=N"
- ✅ Slot 1 在后台加载相邻章节 HTML（不显示）
- ✅ TOC 跳转时 Slot 1 被清除
- ❌ 跨章翻页动画不工作（预期，Phase 3 实现）

## 涉及文件

- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — 主要修改
