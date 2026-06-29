# 2026-06-29 章节切换卡顿根因修复

## 问题

上一轮白屏修复后，章节切换仍有**零点几秒卡顿**（主线程冻结），且不像页内翻页那样跟手。

## 根因分析

### 致命问题：`evaluateJavascriptSync` 阻塞主线程

```kotlin
// 原代码 ReaderScreen.kt:106-115
private fun WebView.evaluateJavascriptSync(script: String): String {
    val latch = CountDownLatch(1)
    evaluateJavascript(script) { ... }
    latch.await(2, TimeUnit.SECONDS)  // ← 阻塞主线程！
}
```

每次章节切换（Hit/Miss 路径都调用 `usePreRendered`），`CountDownLatch.await()` 阻塞主线程等待 WebView JS 执行。在此期间 Compose 无法渲染帧、动画无法推进 → 用户感知为"卡住零点几秒"。

### 次要问题：Hit 路径无动画

预渲染命中时，DOM 瞬间 swap + ViewModel 状态更新，没有任何视觉过渡 → 感觉像"pop"而非平滑翻页。

## 修复方案（3 项改动）

### 改动 1: `evaluateJavascriptSync` → `evaluateJavascriptSuspend`

**文件**: `ReaderScreen.kt`

用 `suspendCancellableCoroutine` 替代 `CountDownLatch`。协程挂起时不阻塞主线程，Compose 可以正常渲染。

```kotlin
// 新：挂起协程，不阻塞主线程
private suspend fun WebView.evaluateJavascriptSuspend(script: String): String {
    return suspendCancellableCoroutine { cont ->
        evaluateJavascript(script) { result ->
            cont.resume(result?.removeSurrounding("\"") ?: "")
        }
    }
}
```

### 改动 2: Hit 路径 — 检查与执行分离 + 动画

**原**: `usePreRendered` 在一次调用中既检查又执行 DOM swap。Hit 时 DOM 瞬间替换，无动画。

**新**: 
1. 先用 `preRendered[$idx] !== undefined` 只读检查（~1ms，不阻塞）
2. **Hit 路径**：先滑出旧内容(180ms) → swap DOM（异步挂起）→ 瞬移+滑入新内容(180ms) = 总动画 360ms，流畅跟手
3. **Miss 路径**：流程与之前一致，但所有 JS 调用改为异步（不阻塞主线程）

```kotlin
// Hit 路径动画序列:
flipOffset.animateTo(-1f, 180ms)              // 滑出（旧内容可见）
wv.evaluateJavascriptSuspend("usePreRendered") // swap DOM（WebView 不可见）
viewModel.onChapterSwapped(direction)          // 更新状态
flipOffset.snapTo(1f)                          // 瞬移
flipOffset.animateTo(0f, 180ms)                // 滑入（新内容可见）
```

### 改动 3: Miss 路径动画时长微调

250ms → 200ms，减少等待感，保持视觉流畅。

## 对比效果

| 阶段 | 原方案 | 新方案 |
|------|--------|--------|
| 检查预渲染 | `evaluateJavascriptSync` 阻塞主线程 | `evaluateJavascriptSuspend` 挂起协程 |
| Hit 切换 | 无动画，瞬间 DOM swap（pop 感） | 滑出→swap→滑入 360ms 动画 |
| Miss 加载等待 | 阻塞主线程 + 250ms 动画 | 协程挂起 + 200ms 动画 |

## 验证

- `compileDebugKotlin`: BUILD SUCCESSFUL（零错误）
- `assembleDebug`: BUILD SUCCESSFUL
- APK: `app/build/outputs/apk/debug/app-debug.apk`

待真机验证：
1. 章节边界翻页无明显卡顿（主线程不被阻塞）
2. 预渲染命中时，滑入滑出动画流畅（类似页内翻页的感觉）
3. 快速连续翻越章节边界，动画不丢帧
