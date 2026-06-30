# Phase 3 Bugfix Round 5：跨章拖拽跟手 + 内容实时可见

日期：2026-06-30

分支：`dual-webview`

## 问题

Round 4 修复了双翻（Bug 2）和回翻定位（Bug 3），但用户反馈跨章拖拽体验仍然很差：

1. **阻力太大**：跨章边界拖拽阻力明显强于章内
2. **松手才能看到内容**：拖拽过程中看不到相邻章节的内容，只有松手后动画才显示
3. **不能跟手**：不像章内翻页那样能实时看到"下一页"内容跟随手指

## 根因分析

两个因素叠加导致：

### 1. JS 橡皮筋阻力过大

章内拖拽：`c = dx`（1:1 跟手）
跨章边界：`c = dx * 0.25`（4x 阻力）

### 2. 隐藏槽位无法进入可见区域（核心问题）

`dragFraction` 被 `coerceIn(-0.4f, 0.4f)` 限制。

隐藏槽位位置公式：`hiddenSlotSide * screenW + dragFraction * screenW`

以向前翻章（dir=+1, hiddenSlotSide=1, dragFraction=-0.4）为例：
- 活跃槽位 (Slot 0)：`-0.4 * screenW` → 左移 40%
- 隐藏槽位 (Slot 1)：`1 * screenW + (-0.4 * screenW) = 0.6 * screenW` → **仍在屏幕右侧 60% 处，完全不可见！**

即使隐藏槽位已预加载完成（内容已就绪），用户拖拽时也看不到它。只有松手后 `checkAndAnimate()` 启动 ±1.0 范围的动画，隐藏槽位才进入屏幕。

## 修复

### 变更 1：JS 橡皮筋阻力减半

```javascript
// Before: c = c * 0.25;  (4x 阻力)
// After:  c = c * 0.5;   (2x 阻力，更接近章内体验)
```

### 变更 2：动态 dragFraction 范围

当隐藏槽位**已预加载**时，允许 `dragFraction` 到 ±0.7：
- 隐藏槽位位置：`1 * screenW + (-0.7 * screenW) = 0.3 * screenW`
- **隐藏槽位 70% 进入屏幕，内容完全可见！**

当隐藏槽位**未预加载**时，限制在 ±0.35：
- 隐藏槽位仅 35% 可见，用户感到自然阻力

```kotlin
val maxDrag = if (hiddenReady) 0.7f else 0.35f
dragFraction = (offsetPx / screenWidthPx).coerceIn(-maxDrag, maxDrag)
```

### 变更 3：延迟预加载（lazy preload）

首次边界拖拽时，如果隐藏槽位未预加载，立即触发：
```kotlin
if (!hiddenReady) {
    // 根据拖拽方向确定相邻章节并加载
    val dir = if (offsetPx < 0) 1 else -1
    val adj = cci + dir
    preloadHiddenSlot(adj, dir, setPending = false)
}
```

兜底主动预加载没有触发的情况（如 TOC 跳转后立刻边界拖拽）。

### 变更 4：扩大主动预加载触发区

| 条件 | Before | After |
|------|--------|-------|
| 靠近章节开头 | `page <= 1` | `page <= 2` |
| 靠近章节结尾 | `page >= total - 2` | `page >= total - 3` |
| 最短章节页数 | `total > 3` | `total > 4` |

提前 1-2 页开始预加载，给 WebView 更多时间完成分页。

## 数据流（修复后）

```
touchmove → aDrag(dx) → atBoundary:
  ├─ JS:  c = dx * 0.5   (moderate rubber-band)
  ├─ JS:  onDragAtBoundary(dx)  (raw dx, uncompressed)
  └─ Kotlin:
       ├─ hiddenReady? → maxDrag = 0.7  (slot slides 70% into view!)
       │                 用户实时看到相邻章节内容
       ├─ !hiddenReady? → maxDrag = 0.35  (natural resistance)
       │                  + lazy preload triggered
       └─ dragFraction updated → graphicsLayer moves both slots

touchend → dEnd:
  ├─ 力度足够 → onChapterFlipReady → snap slideAnim → animateTo(±1)
  └─ 力度不足 → onDragCancel → animateTo(0) bounceBack
```

## 关键数字对比

| 指标 | Round 4 | Round 5 |
|------|---------|---------|
| JS 橡皮筋系数 | 0.25 | **0.5** |
| dragFraction 范围 (已预加载) | ±0.4 | **±0.7** |
| dragFraction 范围 (未预加载) | ±0.4 | **±0.35** |
| 隐藏槽位可见比例 (已预加载) | ~0% | **~70%** |
| 主动预加载提前量 | 1-2 页 | **2-3 页** |
| 懒加载兜底 | ❌ | ✅ |

## 验证

```bash
./gradlew compileDebugKotlin  # ✅ BUILD SUCCESSFUL
./gradlew assembleDebug       # ✅ BUILD SUCCESSFUL
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### 真机测试

```bash
adb logcat -s PG | grep -E "lazy preload|dragFrac|checkAndAnimate|Swap|ChapterFlipReady"
```

预期行为：
- ✅ 跨章边界拖拽阻力明显减小（0.5x vs 之前 0.25x）
- ✅ 预加载命中时，拖拽过程中能看到相邻章节内容实时滑入屏幕（70% 可见）
- ✅ 预加载未命中时，自然阻力感（35% 可见）+ 自动触发加载
- ✅ 松手后动画从手指位置无缝继续到 ±1.0（Round 4 已修复）
- ✅ 力度不足松手回弹（Round 4 已修复）

## 涉及文件

- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — 5 处修改
