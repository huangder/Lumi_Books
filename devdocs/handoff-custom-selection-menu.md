# 交接文档：屏蔽系统文字选择菜单，启用自定义 Compose 菜单

**日期**：2026-07-04
**状态**：待实现
**目标**：阅读页文字选择后，屏蔽系统默认 ActionMode 浮动工具栏（Copy/Select All/Web Search 等），改用 Compose 层自定义菜单。

---

## 1. 当前状态

### 1.1 文字选择机制

阅读器新引擎（TXT/EPUB）使用 Android 原生 `TextView.setTextIsSelectable(true)` 实现选词：

```
ReadView.kt (FrameLayout)
  ├── prevPageView: PageContentView
  │     └── TextView (setTextIsSelectable=true)
  ├── curPageView: PageContentView
  │     └── TextView (setTextIsSelectable=true)  ← 用户交互的当前页
  └── nextPageView: PageContentView
        └── TextView (setTextIsSelectable=true)
```

### 1.2 选词触发流程

1. 用户长按文字 → `PageAnimationController` 检测到长按（>500ms + <32px slop）
2. → `ReadView.onLongPress` 回调 → `curPageView.selectWordAt(x, y)` 程序化选词
3. → Android 系统弹出：**原生选择手柄**（泪滴） + **浮动工具栏**（Copy/Select All/Web Search 等）

### 1.3 当前自定义菜单

在 [ReadView.kt:510-573](app/src/main/java/com/huangder/lumibooks/ui/reader/engine/ReadView.kt#L510-L573)，通过 `customSelectionActionModeCallback` 在系统菜单中追加了三个自定义项：

| 菜单ID | 标签 | 行为 |
|--------|------|------|
| MENU_HIGHLIGHT=1 | 高亮 | `viewModel.addNote(noteText="")` |
| MENU_NOTE=2 | 笔记 | 保存 `PendingSelection`，弹出 `NoteInputSheet` |
| MENU_SEARCH=3 | 搜索 | 打开 `SearchSheet` 并搜索 |

这些菜单项**追加在系统菜单后面**，不是替换。系统菜单（Copy/Select All 等）依然显示。

### 1.4 已写好但未使用的 Compose 组件

[ReaderScreen.kt](app/src/main/java/com/huangder/lumibooks/ui/reader/ReaderScreen.kt) 中已有两个完整的 Composable，是之前 Bitmap 渲染时代的产物，迁移到 TextView 后闲置：

- **`SelectionMenuOverlay`**（第 1217-1283 行）：Popup 浮动菜单，包含高亮/笔记/搜索/复制/移除高亮/查看笔记按钮，根据选区位置自动定位（上方/下方）
- **`SelectionHandle`**（第 1714-1762 行）：圆形拖拽手柄 Composable（暗红棕色 + 白边框），支持拖拽调整选区

当前都用的是 Android 原生手柄和 ActionMode 菜单，这两个 Compose 组件没有被调用。

---

## 2. 目标

**屏蔽系统菜单，启用 Compose 自定义菜单。**

效果：长按选词后 → 原生手柄保留 → 系统浮动工具栏不出现 → Compose `SelectionMenuOverlay` 弹出，显示高亮/笔记/搜索/复制。

---

## 3. 技术方案：Activity 层拦截 ActionMode

### 3.1 为什么选这个方案

| 方案 | 评价 |
|------|------|
| `menu.clear()` 清除系统项 | 不稳定，API 23+ 浮动工具栏是独立机制可能绕过 |
| Reflection 改 TextView 内部 Editor | 兼容性差、维护成本高 |
| 关闭 `setTextIsSelectable` 全部自实现 | 工作量大、手柄体验难达原生 |
| **Activity 拦截 ActionMode** ✅ | 干净、跨版本、`mode.finish()` 不清除选区是 Android 保证行为 |

### 3.2 核心原理

```kotlin
override fun onActionModeStarted(mode: ActionMode) {
    if (mode.type == ActionMode.TYPE_PRIMARY) {
        mode.finish()  // 关闭系统浮动工具栏，但不清除 Spannable 上的 Selection
        // → 选区高亮和手柄保留
        // → 此时触发 Compose 自定义菜单
        return  // 不调 super，彻底阻止系统 UI
    }
    super.onActionModeStarted(mode)
}
```

`ActionMode.finish()` 只关闭 ActionMode 的 UI 展示层（浮动工具栏），**不会**调用 `Selection.removeSelection()`，所以：
- ✅ TextView 上的选区高亮保留
- ✅ 选择手柄保留
- ✅ `Selection.getSelectionStart/End()` 继续可读
- ❌ 系统 Copy/Select All 等菜单消失

---

## 4. 实现步骤

### Step 1：MainActivity 添加 ActionMode 拦截

**文件**：[MainActivity.kt](app/src/main/java/com/huangder/lumibooks/MainActivity.kt)

```kotlin
// 新增字段：Compose 层注册的回调
var onSelectionActionModeStarted: (() -> Unit)? = null

override fun onActionModeStarted(mode: ActionMode) {
    if (mode.type == ActionMode.TYPE_PRIMARY) {
        mode.finish()
        onSelectionActionModeStarted?.invoke()
        return
    }
    super.onActionModeStarted(mode)
}
```

### Step 2：ReadView 暴露选区信息获取方法

**文件**：[ReadView.kt](app/src/main/java/com/huangder/lumibooks/ui/reader/engine/ReadView.kt)

新增公开方法，供 Compose 层查询当前选区详情：

```kotlin
/**
 * 获取当前页选区的完整信息（供 Compose 自定义菜单使用）。
 * @return null 表示当前无选区
 */
fun getSelectionInfo(): SelectionInfo? {
    val tv = curPageView.textView
    val layout = tv.layout ?: return null
    val spannable = tv.text as? android.text.Spannable ?: return null
    val selStart = android.text.Selection.getSelectionStart(spannable)
    val selEnd = android.text.Selection.getSelectionEnd(spannable)
    if (selStart < 0 || selEnd <= selStart) return null

    val text = spannable.toString().substring(selStart, selEnd)
    val curSlot = slotManager.getCurSlot()
    val chapterIdx = curSlot.chapterIndex
    val chapterStartOffset = curPageView.chapterStartOffset

    // 选区屏幕坐标
    val startLine = layout.getLineForOffset(selStart)
    val endLine = layout.getLineForOffset(selEnd.coerceAtMost(spannable.length - 1))
    val topY = (tv.top + tv.paddingTop + layout.getLineTop(startLine)).toFloat()
    val bottomY = (tv.top + tv.paddingTop + layout.getLineBottom(endLine)).toFloat()
    val startX = tv.left + tv.paddingLeft + layout.getPrimaryHorizontal(selStart)
    val endX = tv.left + tv.paddingLeft + layout.getPrimaryHorizontal(selEnd)

    return SelectionInfo(
        selectedText = text,
        chapterIndex = chapterIdx,
        chapterStartOffset = chapterStartOffset,  // 章节级偏移转换用
        pageStart = selStart,
        pageEnd = selEnd,
        selTopY = topY,
        selBottomY = bottomY,
        selStartX = startX,
        selEndX = endX
    )
}

// 数据类（放在 ReadView.kt 底部或单独文件）
data class SelectionInfo(
    val selectedText: String,
    val chapterIndex: Int,
    val chapterStartOffset: Int,
    val pageStart: Int,
    val pageEnd: Int,
    val selTopY: Float,
    val selBottomY: Float,
    val selStartX: Float,
    val selEndX: Float
)
```

`getSelectionBounds()` 方法（第 480-497 行）已有的逻辑可以重用，合并进去即可。

### Step 3：ReaderScreen 连接回调 + 渲染菜单

**文件**：[ReaderScreen.kt](app/src/main/java/com/huangder/lumibooks/ui/reader/ReaderScreen.kt)

#### 3a. 新增 selectionState 状态管理（在现有状态变量附近，约第 130 行）

```kotlin
// 自定义选择菜单状态
var selectionState by remember { mutableStateOf<SelectionState?>(null) }
```

#### 3b. 注册 MainActivity 回调（在 `DisposableEffect(Unit)` 附近，约第 118 行）

```kotlin
val activity = context as? MainActivity

DisposableEffect(activity) {
    activity?.onSelectionActionModeStarted = {
        // ActionMode 被拦截触发，查询 ReadView 选区信息
        val info = readViewRef.value?.getSelectionInfo()
        if (info != null) {
            selectionState = SelectionState(
                chapterIndex = info.chapterIndex,
                pageInChapter = 0, // 可从 slotManager 获取
                charStart = info.chapterStartOffset + info.pageStart,
                charEnd = info.chapterStartOffset + info.pageEnd,
                selectedText = info.selectedText,
                touchX = info.selStartX,
                touchY = info.selTopY,
                selTopY = info.selTopY,
                selBottomY = info.selBottomY,
                selStartX = info.selStartX,
                selEndX = info.selEndX
            )
        }
    }
    onDispose {
        activity?.onSelectionActionModeStarted = null
    }
}
```

#### 3c. 在内容层渲染 SelectionMenuOverlay

在 `Box(Modifier.fillMaxSize().background(composeBgColor))` 内部，`AndroidView` 之后，所有 Sheet 之前，加入：

```kotlin
// 自定义选择菜单
SelectionMenuOverlay(
    state = selectionState,
    readerTheme = uiState.readerTheme,
    onDismiss = {
        selectionState = null
        readViewRef.value?.curPageView?.clearSelection()
    },
    onHighlight = {
        val s = selectionState ?: return@SelectionMenuOverlay
        viewModel.addNote(s.selectedText, "", s.chapterIndex, s.charStart, s.charEnd, "#40FFEB3B")
        selectionState = null
        readViewRef.value?.curPageView?.clearSelection()
    },
    onNote = {
        val s = selectionState ?: return@SelectionMenuOverlay
        pendingSelection = PendingSelection(s.selectedText, s.chapterIndex, s.charStart, s.charEnd)
        showNoteInput = true
        selectionState = null
    },
    onSearch = {
        val s = selectionState ?: return@SelectionMenuOverlay
        showSearch = true
        searchQuery = s.selectedText
        isSearching = true
        hasSearched = true
        scope.launch {
            searchResults = viewModel.searchAllChapters(s.selectedText)
            isSearching = false
        }
        selectionState = null
    },
    onCopy = {
        val s = selectionState ?: return@SelectionMenuOverlay
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("selected", s.selectedText))
        selectionState = null
        readViewRef.value?.curPageView?.clearSelection()
    },
    onRemoveHighlight = { /* TODO: 查询并删除该选区对应的 Note */ },
    onViewNote = { /* TODO: 打开笔记详情 */ }
)
```

#### 3d. 选区消失时自动隐藏菜单

当用户点击其他区域时，`onDestroyActionMode` 会被调用（当前走 `"dismiss"` action）。需要在该回调中清除 `selectionState`：

在 `onSelectionAction` 回调中 `"dismiss"` 分支（第 290-292 行）加上：

```kotlin
"dismiss" -> {
    selectionState = null
}
```

### Step 4：简化 customSelectionActionModeCallback

**文件**：[ReadView.kt](app/src/main/java/com/huangder/lumibooks/ui/reader/engine/ReadView.kt)，第 510-573 行

既然 Compose 层处理菜单，ActionMode 回调不再需要添加菜单项。改为最小实现：

```kotlin
private fun setupNativeSelectionActionMode(pageView: PageContentView) {
    pageView.textView.customSelectionActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            // 不添加任何菜单项，但返回 true 表示我们接管了 ActionMode
            // 实际上 MainActivity 会拦截 TYPE_PRIMARY 并 finish，
            // 所以这里的菜单永远不会显示
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false

        override fun onDestroyActionMode(mode: ActionMode?) {
            // 选区清除时通知 Compose 层隐藏菜单
            callbacks?.onSelectionAction("dismiss", "", -1, 0, 0)
        }
    }
}
```

删除三个 `MENU_*` 常量（第 26-28 行）。

### Step 5：处理 PDF/WebView 路径

**文件**：[ReaderScreen.kt](app/src/main/java/com/huangder/lumibooks/ui/reader/ReaderScreen.kt)

`LegacyWebViewContent`（PDF 路径）使用 WebView，不受 `onActionModeStarted` 影响（WebView 有自己独立的 ActionMode 机制）。该路径本来也没有自定义菜单需求，保持原样即可。

MainActivity 的拦截要加条件判断，确保只在阅读页（ReaderScreen 展示时）才拦截。最简单方式：用一个 `boolean` 标记：

```kotlin
// MainActivity 中
var isInReaderScreen = false
var onSelectionActionModeStarted: (() -> Unit)? = null

override fun onActionModeStarted(mode: ActionMode) {
    if (isInReaderScreen && mode.type == ActionMode.TYPE_PRIMARY) {
        mode.finish()
        onSelectionActionModeStarted?.invoke()
        return
    }
    super.onActionModeStarted(mode)
}
```

在 `ReaderScreen` 的 `DisposableEffect` 中设置/清除：

```kotlin
DisposableEffect(Unit) {
    activity?.isInReaderScreen = true
    onDispose {
        activity?.isInReaderScreen = false
        activity?.onSelectionActionModeStarted = null
    }
}
```

---

## 5. 涉及文件清单

| 文件 | 改动内容 | 改动量 |
|------|----------|--------|
| **MainActivity.kt** | 新增 `onActionModeStarted` 重写 + 回调字段 | ~15 行 |
| **ReadView.kt** | 新增 `getSelectionInfo()` 公开方法 | ~30 行 |
| **ReadView.kt** | 简化 `setupNativeSelectionActionMode`，删除 `MENU_*` 常量 | 删除 ~20 行，改 ~30 行 |
| **ReaderScreen.kt** | 新增 `selectionState` + MainActivity 回调注册 + 渲染 `SelectionMenuOverlay` | ~70 行 |
| **ReaderScreen.kt** | 移除 `ReadViewCallbacks.onSelectionAction` 中 highlight/note/search 的处理逻辑（可选，保留也没关系） | 可选删除 ~20 行 |

---

## 6. 边界情况

| 场景 | 预期行为 |
|------|----------|
| 长按选词 | 原生手柄出现 + Compose 菜单弹出，系统菜单不出现 |
| 点击其他区域 | `onDestroyActionMode` → `"dismiss"` → `selectionState = null` → 菜单消失 + 选区清除 |
| 翻页 | `ReadView` 已有的 `clearCurrentSelection()` 在翻页前清理 |
| 页面内点击（非选区区域） | `clearCurrentSelection()` 在 Tap 回调中调用 → 选区清除 → 菜单消失 |
| 切换到 PDF 路径 | `isInReaderScreen = true` 时 PDF WebView 不受影响 |
| 退出阅读页 | `DisposableEffect.onDispose` 清除回调，其他页面不受影响 |
| 已有高亮的选区再次选择 | `SelectionState.hasHighlight/hasNote` 字段需要从 `savedNotes` 中查询匹配 |
| 点击菜单"复制"后 | 复制到剪贴板 + 清除选区 + 隐藏菜单 |

---

## 7. 验证步骤

1. `./gradlew compileDebugKotlin` — 编译验证
2. `./gradlew assembleDebug` — 构建 APK
3. 真机安装测试：
   - 打开 EPUB/TXT 书籍 → 长按文字
   - **验证**：系统 Copy/Select All 浮动工具栏不出现 ✅
   - **验证**：Compose 自定义菜单出现（高亮/笔记/搜索/复制） ✅
   - **验证**：点击高亮→保存成功，菜单消失 ✅
   - **验证**：点击笔记→弹出 NoteInputSheet ✅
   - **验证**：点击搜索→打开 SearchSheet 并搜索 ✅
   - **验证**：点击复制→剪贴板内容正确 ✅
   - **验证**：点击其他区域→菜单消失+选区清除 ✅
   - **验证**：翻页→选区清除 ✅
   - **验证**：退出阅读页回首页→App 正常（不崩溃） ✅

---

## 8. 参考

- `devdocs/handoff-text-selection.md` — 之前的文字选择修复交接文档（4个Phase已完成）
- `devlog/2026-07-01-text-selection-fix.md` — 文字选择修复日志
- Android 源码：`ActionMode.finish()` 不会清除 TextView Selection（`Editor.java` 中 `stopTextActionMode()` 和 `Selection.removeSelection()` 是独立调用的）
