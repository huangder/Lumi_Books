# GPT-5.5 快速上手文档

## 项目概览

Android 电子书阅读器（Kotlin + Jetpack Compose + Hilt），包名 `com.huangder.lumibooks`，支持 EPUB/PDF/TXT。

核心阅读引擎是**自研 Canvas 渲染**——不用 WebView，直接用 `StaticLayout` 在 `Bitmap` 上排版，3 个 `PageSurfaceView` 做槽位轮转，`SlidePageAnim` 驱动翻页动画。
APK在 `app/build/outputs/apk/debug/app-debug.apk`

## 关键文件地图

```
ui/reader/engine/
├── ReadView.kt              ← FrameLayout 总控：触摸、选择、配置
├── PageAnimationController.kt ← 基类：手势、Scroller动画、长按(GestureDetector)
├── SlidePageAnim.kt         ← 翻页动画：Canvas绘制上下层页面 + 阴影
├── PageLayoutEngine.kt      ← StaticLayout 分页 + 缓存 (LRU 5章)
├── PageRenderer.kt          ← Bitmap 渲染：文本 + 高亮 + 手柄
├── PageSlotManager.kt       ← 3槽位传送带 (PREV/CUR/NEXT)
├── PageSurfaceView.kt       ← 单页 Bitmap 容器 View
├── ChapterLayout.kt / PageLayout.kt / SlotState.kt  ← 数据类

ui/reader/
├── ReaderScreen.kt          ← Compose 主界面：AndroidView 桥接 ReadView + 所有 Sheet
├── ReaderViewModel.kt       ← 状态管理：书籍、章节、书签、笔记、搜索、排版设置
├── ThemeSettingsSheet.kt    ← 主题/高级排版设置 BottomSheet

util/parser/
├── BookParser.kt            ← Chapter/BookContent 数据类
├── EpubParser.kt / TxtParser.kt / PdfParser.kt  ← 格式解析
```

## 当前待修 Bug（按优先级）

### 当前已修复

- 长按文本选择：已补上 `GestureDetector.onDown = true`，长按后会进入文本选区。
- 选择菜单：已改成非模态 Popup，并自动避开触点附近区域，不再盖住文本，也不会阻断手柄拖拽。
- 高亮反馈：已把荧光笔颜色加深并提高不透明度，视觉上更明显。
- 高亮持久化：已把保存的笔记/高亮回绘到当前页，清除临时选区后仍会保留。
- 笔记列表：已按 `note.color` 显示色条和色点，方便区分不同高亮。

### Bug 1：长按文本选择不工作 【P0】

**现象**：手指长按文字后无任何反应（不选词、不高亮、不弹菜单）

**根因**：`PageAnimationController` 长按检测可能未正常触发。已从手动 `Runnable+postDelayed` 改为系统 `GestureDetector`，但待验证。

**排查路径**：
1. `ReadView.onTouchEvent()` (line ~397) — 检查触摸是否到达 `animationController.onTouchEvent()`
2. `PageAnimationController.onTouchEvent()` (line ~96) — `gestureDetector.onTouchEvent(event)` 是否被调用
3. `GestureDetector.onLongPress` 回调 (line ~59-66) — 是否触发 `onLongPress?.invoke()`
4. `ReadView` init 中 `animationController.onLongPress` 回调 (line ~119) — 是否执行选词+高亮
5. 如有条件加 `android.util.Log.e("LP", "xxx")` 在每步打印确认

**涉及文件**：`PageAnimationController.kt`, `ReadView.kt`

---

### Bug 2：选择手柄错位 + 拖不动 【P0】

**现象**：选中文字后手柄圆点不在正确位置；手指拖不动手柄

**根因分析**：
- **坐标不一致**：`PageRenderer.drawSelectionHandles()` 和 `ReadView.hitTestHandle()` 计算手柄圆心用了不同公式
- **已修部分**：`getHandleCenter()` 已改为直接用 `renderer.renderMarginLeft/Top`（同包 internal 字段），但 `drawHandle()` 中的 `renderMarginTop` 可能还没同步
- **拖不动根因**：`ReadView.onTouchEvent()` 中 `selEnd = (charOff + 1).coerceAtLeast(selStart + 1)` —— 如果 `selStart` 为 0，`charOff+1` 可能小于 1，导致 `selEnd` 被锁在 1。另外 `ACTION_DOWN` 命中手柄后 `draggingHandle` 被设置，但 `ACTION_MOVE` 可能因为 `curSlot` 的 `chapterIndex` 与 `selChapter` 不匹配而拒绝更新

**排查路径**：
1. 打 log 验证 `hitTestHandle(x,y)` 的返回值——手柄区域触摸是否能命中
2. 打 log 验证 `getHandleCenter()` 返回的 cx/cy——和实际绘制位置是否一致
3. 检查 `applyHighlightOnCurrentPage()` 调用后 invalidate 是否触发重绘
4. 检查 `PageRenderer.drawHandle()` 第 215-222 行——`renderMarginTop` 是否正确

**涉及文件**：`PageRenderer.kt` (drawSelectionHandles/drawHandle), `ReadView.kt` (getHandleCenter/hitTestHandle/onTouchEvent)

---

### Bug 3：点击"高亮"后文字无荧光笔效果 【P0】

**现象**：选择菜单点"高亮"，文字上看不到荧光笔覆盖

**当前状态**：已把默认高亮色改成更高不透明度的亮黄，并增加内层提亮；保存后的高亮还会在当前页和翻页后继续回绘。
笔记列表里也会显示对应颜色标记，避免只看到文本内容看不出高亮类型。

**根因**：原先颜色太淡（`0x70FFE082` 在某些背景下不明显），视觉反馈不足

**排查**：
1. 确认 `PageRenderer.drawSelectionHighlight` 的高亮颜色和形状在深色/浅色主题下都清晰可见
2. 确认 `addNote` 保存后没有触发 `clearSelection()` 导致 bitmap 被 `renderPage()` 清除
3. 看 `ReaderScreen.kt` 中 `onHighlight` 回调：`selectionState = null` 后是否间接调用了 `clearSelection`

**涉及文件**：`PageRenderer.kt`, `ReaderScreen.kt` (SelectionMenuOverlay 回调)

---

### Bug 4：笔记列表滑动不跟手 + 方向反了 【P1】

**现象**：左滑笔记条目时内容延迟、不跟手指；垃圾桶图标不是随滑动同步出现；**向左滑时垃圾桶应向右出（目前方向可能反了）**

**根因**：`NoteItem` composable 中拖拽期间用了 `Animatable.snapTo()`（协程有 1 帧延迟）

**滑动方向说明**：向左滑→内容左移→右侧露出垃圾桶。如果目前是相反的（左滑内容右移），需要在 `onHorizontalDrag` 中把 `dragAmount` 取反：`rawOffset = (rawOffset - dragAmount).coerceIn(...)`。

**修复方向**：已部分改为 `mutableFloatStateOf` (`rawOffset`) 实时跟手，但滑动和翻页动画手感仍不同。翻页动画是在 `onTouchEvent` 的 `ACTION_MOVE` 中直接赋值 `touchX`（零延迟），Compose 滑动应同样直接赋值而非通过协程。垃圾桶 alpha 也直接根据 `displayOffset` 同步计算。

**涉及文件**：`ReaderScreen.kt` (NoteItem)

---

## 构建 & 验证

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

编译错误先检查 import 是否齐全，特别是 `androidx.compose.animation.core.Animatable`、`androidx.compose.foundation.layout.offset`、`androidx.compose.ui.unit.IntOffset`、`android.view.GestureDetector` 等。

## 重要注意事项

1. **不要改 git**（`.gitignore`、commit、分支等）
2. **Canvas 渲染是硬件加速的**——`clipPath()` 非矩形路径会被 GPU 静默忽略
3. **Bitmap 复用池**在 `PageRenderer` 中（最多 6 个），重渲染时必须用同一个 bitmap 实例
4. **3 槽位轮转**：`SLOT_CUR` 永远是 `slots[1]`，`shiftForward/Backward` 会 swap bitmap 引用
5. **阅读器所有覆盖层统一为底部弹出 BottomSheet**，顶部圆角 24dp，动画用 `sheetAlpha + sheetOffset` 两个 Animatable
6. **用户反馈风格**：直接、简短，说"没用"意思是完全没效果，不是"部分不行"
