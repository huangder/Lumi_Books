# Phase 3 Bugfix Round 4：双翻 + 回翻定位 + 跟手（根本性修复）

日期：2026-06-30

分支：`dual-webview`

## 问题

Round 3 修复了 4 个 bug 的症状，但真机测试发现 Bug 2/3/4 并未根本解决：

- **Bug 2（一次性翻两章）**：轻滑成功，慢滑到上上章第一页
- **Bug 3（回翻到第一页）**：轻滑才成功，慢滑仍显示第一页
- **Bug 4（动画不跟手）**：滑动后下一页马上滑入，不像章内翻页跟手

## 根因分析

**核心缺陷**：`onChapterFlipReady` 在 `aDrag`（touchmove 期间）调用，而不是在 `dEnd`（touchend）调用。

这导致以下时序问题（以第一页慢速右滑为例）：

```
1. touchmove → aDrag(dx) → atBoundary=true → onChapterFlipReady(-1)
2. Kotlin 加载上一章 HTML 到隐藏槽位
3. 分页完成 → checkAndAnimate() → Compose slide 动画启动！
4. 动画 380ms 播放（用户手指还在屏幕上！）
5. 动画完成 → Slot 交换 → 用户手指现在在新 WebView 上
6. 新 WebView（上一章最后一页）→ 继续 touchmove → aDrag → 又一个 onChapterFlipReady！
7. → 再翻一章（双重翻页）
```

快速轻滑偶尔成功是因为：`dEnd` 在分页完成前触发，`checkAndAnimate()` 返回 early（隐藏槽位未 ready）；分页完成后 `touchend` 已过，动画才启动。慢滑时动画在手指抬起前就完成了。

**Bug 4 根因**：Compose `slideAnim` 是固定 300ms tween，完全与手指位置脱节。JS `aDrag` 只在 WebView 内部做橡皮筋效果，Compose 层不知道手指在哪儿。

## 修复方案

**核心原则**：跨章 transition 在 `touchend`（dEnd）提交，不在 `touchmove`（aDrag）提交。同时 Compose 层实时追踪手指位置。

### JS 变更（buildPaginationJs）

1. **`aDrag` atBoundary**：移除 `onChapterFlipReady` 调用。改为 `onDragAtBoundary(dx)` — 传递原始手指偏移给 Compose 做实时跟随。
2. **`dEnd` atBoundary + 力度足够**：调用 `onChapterFlipReady(dir)` — 在手指抬起时提交跨章 transition。
3. **`dEnd` atBoundary + 力度不足**：调用 `onDragCancel()` — Compose 层回弹到 0。

### Kotlin 变更（ReaderScreen.kt）

1. **ReaderJsBridge**：新增 `handleDragAtBoundary(offsetPx: Float)` 和 `handleDragCancel()` 回调 + `@JavascriptInterface` 方法。

2. **新状态变量**：
   - `isDraggingAtBoundary`：是否正在边界拖拽
   - `dragFraction`：实时拖拽偏移（归一化，-0.4~0.4）

3. **graphicsLayer 位置计算**：
   ```kotlin
   val offset = if (isDraggingAtBoundary) dragFraction else slideAnim.value
   translationX = if (activeSlot == 0) offset * screenW
                  else hiddenSlotSide * screenW + offset * screenW
   ```
   拖拽期间用 `dragFraction`（实时跟手），动画期间用 `slideAnim`（tween 驱动）。

4. **`handleDragAtBoundary`**：每帧更新 `dragFraction`，设置 `isDraggingAtBoundary = true`。

5. **`handleDragCancel`**：取消拖拽 → snap `slideAnim` 到 `dragFraction` → `animateTo(0f, 200ms)` 回弹。

6. **`handleChapterFlipReady`**（现在只在 `dEnd`/`tapFlip` 触发）：
   - 设置 `isDraggingAtBoundary = false`
   - `scope.launch { slideAnim.snapTo(dragFraction); checkAndAnimate() }`
   - 动画从手指位置无缝继续到 ±1.0

## 数据流对比

### 修复前（有 bug）
```
touchmove → aDrag → onChapterFlipReady → preload + checkAndAnimate
                                            ↓ (分页完成)
                                      动画启动（手指还在屏幕上！）
                                            ↓ (380ms 后)
                                      Slot 交换（手指在新 WebView 上）
                                            ↓
                                      又一次 onChapterFlipReady → 双重翻页
```

### 修复后（正确）
```
touchmove → aDrag → onDragAtBoundary(dx) → Compose 实时跟手移动 WebView
                                            ↓
touchend  → dEnd → onChapterFlipReady(dir) → snap slideAnim → animateTo(±1)
                                            ↓
                                      动画从拖拽位置无缝完成（300ms）
                                            ↓
                                      Slot 交换（手指已抬起，无残留触摸）
```

## 变更清单

| 位置 | 改动 |
|------|------|
| `buildPaginationJs()` — `aDrag` | 移除 `onChapterFlipReady`，改为 `onDragAtBoundary(dx)` |
| `buildPaginationJs()` — `dEnd` | 边界+力度足够→`onChapterFlipReady`；边界+力度不足→`onDragCancel` |
| `ReaderJsBridge` | 新增 `handleDragAtBoundary`、`handleDragCancel` + 2 个 `@JavascriptInterface` |
| bridge0 | 新增 `handleDragAtBoundary`/`handleDragCancel`；`handleChapterFlipReady` 加 snap + coroutine |
| bridge1 | 同上 |
| State | 新增 `isDraggingAtBoundary`、`dragFraction` |
| Slot 0 graphicsLayer | 用 `effectiveOffset = isDragging ? dragFraction : slideAnim.value` |
| Slot 1 graphicsLayer | 同上 |

## 验证

```bash
./gradlew compileDebugKotlin  # ✅ BUILD SUCCESSFUL
./gradlew assembleDebug       # ✅ BUILD SUCCESSFUL
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### 真机测试

```bash
adb logcat -s PG | grep -E "dragFrac|checkAndAnimate|Swap|BLOCKED|DragCancel|ChapterFlipReady"
```

预期行为：
- ✅ 慢速边界拖拽 → 松手后只翻一章（不再双翻）
- ✅ 向后翻章 → 显示上一章最后一页（`flipToLastPage` 在动画前有充足时间执行）
- ✅ 边界拖拽时 WebView 跟随手指滑动（`onDragAtBoundary` 实时更新 `dragFraction`）
- ✅ 力度不足松手 → Compose 回弹（`onDragCancel` → `animateTo(0f)`）
- ✅ 快速轻滑（tapFlip）→ 行为不变，仍然正确

### 已知局限

- 如果隐藏槽位未预加载完成（cache miss），松开手指后页面会短暂停留在拖拽位置（`slideAnim = dragFraction`），等待分页完成后动画才启动。主动预加载（Round 3）应在大多数情况下覆盖此场景。
- PDF 阅读器不受影响。

## 涉及文件

- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — 10 处修改
