# 章节翻页失效修复（第八轮：诊断增强 + ViewModel/WebView 同步修复）

日期：2026-06-30

## 背景

第七轮 DIAG 修复后，真机测试数据：
```json
{"err":"none","pr":1,"pf":1,"fl":true,"cci":0,"prk":"17,18,20,21","inv":"18:ok;17:ok;20:ok;21:ok;"}
```

### 分析

| 字段 | 值 | 含义 |
|------|---|------|
| `pr:1` | ✅ | preRendered[adjIdx] 存在，预渲染成功 |
| `inv` | ✅ | 所有 preRenderChapter 调用成功 |
| `pf:1` | ✅ | __pendingFlip 已设置 |
| `fl:true` | ⏳ | bounceBack 动画仍在进行（100ms < 350ms），正常 |
| `cci:0` | ⚠️ | WebView 当前章节为0 |
| `prk:"17,18,20,21"` | ⚠️ | 预渲染的章节远大于 cci=0，异常 |

关键问题：**100ms DIAG 无法判断 bounceBack 完成后是否成功触发 chapterFlipTo**。

## 根因分析

### 问题 1：诊断盲区（500ms后状态未知）

100ms DIAG 在 bounceBack(350ms) 完成前捕获，始终显示 `fl:true`，无法知道最终结果。

### 问题 2：ViewModel/WebView 状态可能不同步

`prk` 显示预渲染了 17-21 章，但 `cci=0`。两种可能：
- (A) 用户在章节0，预加载了1-5章（正常），但 DIAG 是针对 adjIdx=21 的（不同场景的 DIAG）
- (B) ViewModel.currentChapterIndex 与 WebView.__currentChapterIdx **不同步**

场景 (B) 的危害：Kotlin 侧用 `viewModel.currentChapterIndex + direction` 计算 adjIdx，与 JS 侧 `__currentChapterIdx + direction` 不一致 → **Kotlin 预渲染了错误的章节** → `onPreRenderReady` 因 `guard_adj` 拒绝 → 章节翻页静默失败。

## 修复方案

### 修复 1：bounceBack→chapterFlipTo→completeChapterSwap 链路追踪

SlideCoverAnimation 和 SimpleSlideAnimation 的 bounceBack 完成处理中添加追踪标志：

```javascript
// bounceBack 完成时
window.__bbDone = 1;
if (window.__pendingFlip !== undefined) {
    window.__bbPendingIdx = pendingIdx;
    if (preRendered[pendingIdx]) {
        window.__bbTriggeredFlip = pendingIdx;  // bounceBack 成功触发
    } else {
        window.__bbNoPr = pendingIdx;  // pending 存在但 preRendered 不存在 ← BUG!
    }
}
```

chapterFlipTo 入口追踪：
```javascript
function chapterFlipTo(dir) {
    if (flipping) { window.__cftBlocked = 'flipping'; return; }
    // ...
    if (!pr) { window.__cftNoPr = adjIdx; ... return; }
    window.__cftCalled = adjIdx;  // chapterFlipTo 开始执行
}
```

completeChapterSwap 入口追踪：
```javascript
function completeChapterSwap(dir, adjIdx) {
    window.__ccsCalled = adjIdx;  // completeChapterSwap 被调用 = 成功!
}
```

### 修复 2：__getDiag 扩展新字段

```javascript
window.__getDiag = function(adjIdx) {
    return JSON.stringify({
        // ... 原有字段 ...
        bb: window.__bbDone || 0,          // bounceBack 完成?
        bbt: window.__bbTriggeredFlip,     // bounceBack 触发了chapterFlipTo?
        bbn: window.__bbNoPr,              // bounceBack 发现pending但无preRender?
        bbp: window.__bbPendingIdx,        // bounceBack 看到的pendingIdx
        cft: window.__cftCalled,           // chapterFlipTo 被调用?
        cfb: window.__cftBlocked,          // chapterFlipTo 被 flipping 阻塞?
        cfn: window.__cftNoPr,             // chapterFlipTo 发现无preRender?
        ccs: window.__ccsCalled            // completeChapterSwap 被调用? = 成功!
    });
};
```

### 修复 3：500ms 二次 DIAG

Emergency 回调中增加 500ms 延迟查询，确认 bounceBack 完成后的最终状态：
- `bb:1, ccs:adjIdx` → ✅ 成功
- `bb:1, bbn:adjIdx` → ❌ bounceBack 发现 pending 但 preRendered 不存在（ViewModel/WebView 不同步）
- `bb:0` → ❌ bounceBack 未完成（可能卡住）
- `cfb:'flipping'` → ❌ chapterFlipTo 被竞态阻塞

### 修复 4：从 JS 侧读取权威 adjIdx（避免 ViewModel/WebView 不同步）

**根本修复**：JS 在设置 `__pendingFlip` 时已计算出正确的 adjIdx（基于 `__currentChapterIdx`），但 Kotlin 侧用 `viewModel.currentChapterIndex + direction` 重新计算。当两者不同步时，Kotlin 预渲染错误的章节。

```kotlin
// 旧（可能错误）：
val adjIdx = viewModel.uiState.value.currentChapterIndex + direction

// 新（从 JS 读权威值，回退到 ViewModel 计算）：
wv.evaluateJavascript("window.__pendingFlip") { pending ->
    val jsAdjIdx = pending?.removeSurrounding("\"")?.toIntOrNull()
    val adjIdx = jsAdjIdx ?: (viewModel.uiState.value.currentChapterIndex + direction)
    // ... 用 adjIdx 预渲染
}
```

同时在 `chapterFlipTo` 的 fallback 路径也设置 `__pendingFlip`：
```javascript
if (!pr) { window.__cftNoPr = adjIdx; window.__pendingFlip = adjIdx; ... }
```

## 涉及文件

- `app/src/main/java/com/ebook/reader/ui/animation/PageFlipAnimation.kt` — 修复 1（SlideCoverAnimation + SimpleSlideAnimation 追踪）、修复 4 的 JS 侧
- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — 修复 2（__getDiag 扩展）、修复 3（500ms DIAG）、修复 4（Kotlin 侧 adjIdx 读取）

## 验证

```bash
adb logcat -s PG | grep -E "Emergency|DIAG|jsAdjIdx"
```

### 新增诊断字段解读

| 场景 | 100ms DIAG | 500ms DIAG | 结论 |
|------|-----------|-----------|------|
| ✅ 成功 | fl:true, pf:1, pr:1 | bb:1, ccs:adjIdx, cci:新值 | bounceBack→chapterFlipTo→completeChapterSwap 链完整 |
| ❌ 不同步 | fl:true, pf:1, pr:1 | bb:1, bbn:1, cci:不变 | Kotlin预渲染了错误章节，bounceBack找preRendered[1]不存在 |
| ❌ 卡住 | fl:true, pf:1, pr:1 | bb:0, fl:true | bounceBack 未完成或 flipping 未清除 |
| ❌ 竞态 | fl:true, pf:1, pr:1 | bb:1, cfb:'flipping' | chapterFlipTo 被 flipping guard 阻塞 |

编译 ✅ 构建 ✅ APK: `app/build/outputs/apk/debug/app-debug.apk`
