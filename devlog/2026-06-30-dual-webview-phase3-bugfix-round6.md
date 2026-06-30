# Phase 3 Bugfix Round 6：回翻动画异常（dragFraction 残留污染）

日期：2026-06-30

分支：`dual-webview`

## 问题

真机测试反馈：

- ✅ 正常阅读顺序读到最后一页向前翻章 → 动画正常
- ❌ 翻到下一章后**返回上一章** → 动画异常
- ❌ 此时再**向前翻章** → 动画依旧异常

问题一旦触发就持续存在，必须杀掉应用才能恢复。

## 根因分析

**`dragFraction` 从不重置，残留值永久污染后续动画。**

### 完整时序追踪

```
Step 1: 首次向前翻章 (3→4) ✅
  ├─ 用户拖拽：dragFraction = -0.3
  ├─ handleChapterFlipReady: slideAnim.snapTo(-0.3) ✓ (从手指位置开始)
  ├─ checkAndAnimate: dir=+1, target=-1f
  │   slideAnim -0.3 → -1.0 (0.7 范围，正确)
  ├─ 动画完成：slideAnim.snapTo(0f)   ← slideAnim 重置了
  └─ dragFraction 仍为 -0.3           ← 🔴 没重置！

Step 2: 返回上一章 (4→3) ❌
  ├─ 用户点击/tap（非拖拽）：dragFraction 仍是 -0.3
  ├─ handleChapterFlipReady: slideAnim.snapTo(-0.3) ← 跳到前进方向！
  │   但这次 dir=-1 (后退)，slideAnim 应该在 0 而不是 -0.3
  ├─ 活跃槽位跳到 -0.3*screenW (略微左移)，隐藏槽位到 -1.3*screenW (完全不可见)
  ├─ 等待隐藏槽位加载章节 3...
  ├─ checkAndAnimate: dir=-1, target=+1f
  │   slideAnim -0.3 → +1.0 (1.3 范围动画！应该是 0 → +1.0)
  ├─ 动画完成：slideAnim.snapTo(0f)
  └─ dragFraction 仍为 -0.3           ← 还是没重置

Step 3: 再次向前翻章 (3→4) ❌
  ├─ dragFraction 仍是 -0.3
  ├─ slideAnim.snapTo(-0.3) ← 又是错误起点
  ├─ checkAndAnimate: dir=+1, target=-1f
  │   slideAnim -0.3 → -1.0 (0.7 范围，应该是 0 → -1.0)
  └─ 动画缩短 30%，视觉不连贯
```

**核心缺陷**：`dragFraction` 变量在三个关键路径上都没有被清空：

1. `checkAndAnimate()` 动画完成后：`slideAnim.snapTo(0f)` 只重置了 Animatable，没重置 dragFraction
2. `handleDragCancel()` 回弹完成后：dragFraction 保持最后拖拽值
3. `handleChapterFlipReady()` 无论何种触发方式（拖拽/点击），都用 `slideAnim.snapTo(dragFraction)` — 点击时 dragFraction 可能是残留值

## 修复

### 变更 1：`checkAndAnimate()` — 动画完成后重置

```kotlin
slideAnim.snapTo(0f)
dragFraction = 0f  // 重置拖拽偏移，避免残留值污染后续动画
```

### 变更 2：`handleChapterFlipReady` — 区分拖拽/点击

```kotlin
val continueFromDrag = isDraggingAtBoundary  // 先捕获状态
isDraggingAtBoundary = false
// ...
scope.launch {
    if (continueFromDrag) {
        slideAnim.snapTo(dragFraction)  // 从手指位置继续
    }
    // 点击场景：slideAnim 保持 0，从零开始动画
    checkAndAnimate()
}
```

关键：点击（tap）触发的翻章 `isDraggingAtBoundary` 为 false，跳过 snapTo，slideAnim 保持 0。

### 变更 3：`handleDragCancel` — 回弹完成后重置

```kotlin
scope.launch {
    slideAnim.snapTo(dragFraction)
    slideAnim.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
    dragFraction = 0f  // 回弹完成后重置
}
```

## 数据流对比

### 修复前
```
dragFraction 生命周期：
  拖拽设置 → 动画使用 → [永不重置] → 污染后续所有动画
                                    ↓
  点击触发的后退动画从前进方向的偏移量开始 → 方向错误/范围异常
```

### 修复后
```
dragFraction 生命周期：
  ┌─ handleDragAtBoundary: dragFraction = 手指位置
  │
  ├─ 拖拽 + 松手提交:
  │    handleChapterFlipReady → snapTo(dragFraction) → animateTo(±1)
  │    → 动画完成 → dragFraction = 0f  ← 重置
  │
  ├─ 拖拽 + 松手取消:
  │    handleDragCancel → animateTo(0) → dragFraction = 0f  ← 重置
  │
  └─ 点击/tap:
       handleChapterFlipReady → continueFromDrag=false → 不用 dragFraction
       → animateTo(±1) from 0 → dragFraction = 0f  ← 重置
```

## 变更清单

| 位置 | 改动 |
|------|------|
| `checkAndAnimate()` — 动画完成 | + `dragFraction = 0f` |
| bridge0 `handleChapterFlipReady` | 捕获 `continueFromDrag`，条件 snapTo |
| bridge1 `handleChapterFlipReady` | 同上 |
| bridge0 `handleDragCancel` | + `dragFraction = 0f` |
| bridge1 `handleDragCancel` | + `dragFraction = 0f` |

## 验证

```bash
./gradlew compileDebugKotlin  # ✅ BUILD SUCCESSFUL
./gradlew assembleDebug       # ✅ BUILD SUCCESSFUL
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### 真机测试

预期行为：
- ✅ 正常向前翻章 → 动画正常（不受影响）
- ✅ 翻到下一章后返回上一章 → 动画正常（不再有方向/范围异常）
- ✅ 返回后再向前翻章 → 动画正常
- ✅ 反复前后翻章 → 每次动画都从正确位置（0 或手指位置）开始
- ✅ 点击边界翻章 → slideAnim 从 0 开始，无残留偏差
- ✅ 拖拽边界翻章 → slideAnim 从手指位置无缝继续

## 涉及文件

- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — 5 处修改
