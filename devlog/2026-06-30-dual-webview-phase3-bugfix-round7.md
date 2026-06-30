# Phase 3 Bugfix Round 7：动画抽搐 + 拖拽不跟手（根本性修复）

日期：2026-06-30

分支：`dual-webview`

## 问题

真机测试反馈，两个独立 bug 叠加：

### Bug A：动画抽搐
松手瞬间动画会抽搐一下再完成翻页。原因是 `isDraggingAtBoundary` 同步置 false，但 `slideAnim.snapTo(dragFraction)` 在协程内异步执行。中间帧 graphicsLayer 从 `dragFraction` 切换到 `slideAnim.value`（值为 0），导致视觉跳变。

### Bug B：不跟手
跨章拖拽必须拖很远才能看到相邻章节内容。双重阻力：
- JS 橡皮筋 `c * 0.5`（50% 阻力）
- Compose `coerceIn(-0.7, 0.7)` 限制拖拽范围
- 隐藏槽位在最远拖拽处：`screenW + (-0.7)*screenW = 0.3*screenW`，仅 70% 可见

## 根因分析

### Bug A — 帧间跳变

```
handleChapterFlipReady 同步执行:
  isDraggingAtBoundary = false  ← 同步，立即生效
  scope.launch {                ← 排队，尚未执行
    slideAnim.snapTo(...)       ← 还没跑
  }

下一帧渲染:
  graphicsLayer: offset = slideAnim.value  ← 因为是 false，用 slideAnim
  slideAnim.value = 0 (旧值)
  offset 从 dragFraction 跳到 0 → 抽搐！

协程执行:
  slideAnim.snapTo(dragFraction) ← snap 到正确值
  offset 从 0 跳回 dragFraction → 二次抽搐！
```

### Bug B — 隐藏槽位远离屏幕

```
dragFraction = 0 → 隐藏槽位在 screenW（完全不可见）
dragFraction = -0.3 → 隐藏槽位在 0.7*screenW（30% 可见，只是一个边）
dragFraction = -0.7 → 隐藏槽位在 0.3*screenW（70% 可见）

用户需要拖 30% 屏幕宽度才开始看到内容 → 体验差
```

## 修复

### 变更 1：消除帧间跳变

`isDraggingAtBoundary = false` 从同步执行移到协程内，放在 `snapTo` **之后**：

```kotlin
// Before: 同步切换 → 帧间跳变
isDraggingAtBoundary = false
scope.launch {
    slideAnim.snapTo(dragFraction)
    checkAndAnimate()
}

// After: 协程内切换 → 无跳变
val captureDragFraction = dragFraction
scope.launch {
    if (continueFromDrag) {
        slideAnim.snapTo(captureDragFraction)
    }
    isDraggingAtBoundary = false  // ← snap 后再切换
    checkAndAnimate()
}
```

关键：`dragFraction` 在协程外先捕获到局部变量，避免协程执行时值已被其他线程修改。

同样修复应用于 `handleDragCancel`：`isDraggingAtBoundary = false` 移到 `snapTo` 之后。

### 变更 2：减轻阻力 + 扩大范围

| 参数 | Before | After |
|------|--------|-------|
| JS 橡皮筋系数 | `c * 0.5` | `c * 0.7` |
| maxDrag（已预加载） | `0.7` | `0.85` |
| maxDrag（未预加载） | `0.35` | `0.35`（不变） |

**隐藏槽位可见比例对比**（已预加载场景）：

| dragFraction | Before 隐藏槽位 | After 隐藏槽位 |
|-------------|-----------------|---------------|
| -0.1 | 0.9*screenW (10% 可见) | 0.9*screenW (10% 可见) |
| -0.3 | 0.7*screenW (30% 可见) | 0.7*screenW (30% 可见) |
| -0.5 | 0.5*screenW (50% 可见) | 0.5*screenW (50% 可见) |
| -0.7 | 0.3*screenW (70% 可见) | **0.15*screenW (85% 可见)** |

注意：maxDrag 0.7→0.85 不影响轻度拖拽的可见性。但 JS 橡皮筋 0.5→0.7 使手指阻力更轻，用户更容易达到较大的 dragFraction。

## 数据流（修复后）

```
touchmove → aDrag(dx):
  c = dx * 0.7             ← 轻阻力橡皮筋
  onDragAtBoundary(dx)     ← 通知 Compose
  dragFraction = (dx/W) coerceIn ±0.85  ← 更大拖拽范围
  graphicsLayer 用 dragFraction 实时跟随

touchend → dEnd → onChapterFlipReady:
  handleChapterFlipReady:
    captureDragFraction = dragFraction
    scope.launch:
      slideAnim.snapTo(captureDragFraction)  ← 同步到 Animatable
      isDraggingAtBoundary = false           ← 此时切换，无跳变
      checkAndAnimate():
        slideAnim.animateTo(±1, 300ms)      ← 无缝动画

graphicsLayer 始终读取:
  offset = isDragging ? dragFraction : slideAnim.value
  snapTo 后 slideAnim.value == dragFraction → 切换无跳变 ✓
```

## 变更清单

| 位置 | 改动 |
|------|------|
| JS `aDrag` | `c * 0.5` → `c * 0.7` |
| bridge0 `handleChapterFlipReady` | `isDraggingAtBoundary` 移入协程，snapTo 后执行 |
| bridge0 `handleDragCancel` | `isDraggingAtBoundary` 移入协程，snapTo 后执行 |
| bridge0 `handleDragAtBoundary` | maxDrag `0.7f` → `0.85f` |
| bridge1 三个 handler | 同上 |

## 验证

```bash
./gradlew compileDebugKotlin  # ✅ BUILD SUCCESSFUL
./gradlew assembleDebug       # ✅ BUILD SUCCESSFUL
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### 真机测试

```bash
adb logcat -s PG | grep -E "dragFrac|checkAndAnimate|Swap|continueFromDrag|capture"
```

预期行为：
- ✅ 边界拖拽 → 松手 → 动画平滑，无抽搐
- ✅ 边界拖拽 → 取消回弹 → 动画平滑，无抽搐
- ✅ 点击边界翻章 → 动画从零开始，无抽搐
- ✅ 拖拽阻力明显减轻（0.7x vs 之前 0.25x）
- ✅ 预加载命中时拖拽范围更大（±0.85），隐藏槽位最多 85% 可见
- ✅ 向前/向后反复翻章，动画始终正常

## 涉及文件

- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — 6 处修改
