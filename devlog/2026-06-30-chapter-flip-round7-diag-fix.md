# 章节翻页失效修复（第七轮：修复诊断假阴性）

日期：2026-06-30

## 问题

第六轮的 DIAG 数据：
```json
{"err":"none","pr":0,"pf":1,"cci":0,"prk":""}
```

用户解读为 `preRenderChapter` 没有被调用或失败。但仔细审查代码后发现 **DIAG 查询本身有 bug**。

## 根因：DIAG 假阴性

### Bug 1：`window.preRendered` 永远是 `undefined`

`preRendered` 在 `buildPaginationJs` 中用 `var preRendered = {}` 声明，是 **IIFE 闭包变量**，从未暴露到 `window`。

```javascript
(function() {
    var preRendered = {};           // ← 闭包变量
    window.preRenderChapter = ...   // ← 通过闭包访问 preRendered
})()
```

DIAG 查询使用了 `window.preRendered`：
```kotlin
"(window.preRendered&&preRendered[$adjIdx]?1:0)"  // ← window.preRendered 永远是 undefined!
"Object.keys(window.preRendered||{}).join(',')"   // ← 永远是空！
```

所以 `pr:0` 和 `prk:""` 是**假阴性**——无论 `preRenderChapter` 是否成功，这两个值永远不会变。

### Bug 2：`window.flipping` 永远是 `undefined`

同理，`flipping` 在动画 JS 中用 `var flipping = false` 声明，也是闭包变量。`window.flipping` 永远是 `undefined`。

DIAG 输出的 JSON 中 `fl` 键直接缺失（`JSON.stringify` 会省略 `undefined` 值），进一步证实了这个 bug。

## 修复方案

### 修复 1：暴露 `preRendered` 到 `window`

```javascript
var preRendered = {};
window.preRendered = preRendered;  // 🔥 暴露引用，对象突变对外可见
```

### 修复 2：添加闭包内诊断函数 `__getDiag`

```javascript
window.__getDiag = function(adjIdx) {
    return JSON.stringify({
        err: window.__preRenderError || 'none',
        pr: preRendered[adjIdx] ? 1 : 0,        // ← 直接访问闭包变量
        pf: window.__pendingFlip,
        fl: typeof flipping !== 'undefined' ? flipping : null,  // ← 直接访问闭包变量
        cci: window.__currentChapterIdx,
        prk: Object.keys(preRendered).join(','),  // ← 直接访问闭包变量
        inv: window.__preRenderInvoked || ''       // ← 新增：调用跟踪
    });
};
```

### 修复 3：添加 `preRenderChapter` 调用跟踪

```javascript
window.preRenderChapter = function(chapterIndex, htmlB64) {
    ...
    preRendered[chapterIndex] = { wrapper: w, total: t };
    window.__preRenderInvoked = (window.__preRenderInvoked || '') + chapterIndex + ':ok;';
    ...
    } catch(e) {
        window.__preRenderError = 'ch' + chapterIndex + ':' + e.message;
        window.__preRenderInvoked = (window.__preRenderInvoked || '') + chapterIndex + ':err;';
    }
};
```

### 修复 4：DIAG 查询改用 `__getDiag`

```kotlin
// 旧：内联 JSON.stringify，访问 window.preRendered（假阴性）
// 新：调用闭包内 __getDiag(adjIdx)（准确）
wv?.evaluateJavascript(
    "window.__getDiag && __getDiag($adjIdx) || JSON.stringify({err:'no_diag_fn'})"
)
```

## 新 DIAG 字段解读

| 字段 | 含义 | 可能值 |
|------|------|--------|
| `err` | `__preRenderError` | `"none"` / `"ch1:InvalidCharacterError..."` |
| `pr` | `preRendered[adjIdx]` 是否存在 | `1`（成功）/ `0`（失败） |
| `pf` | `__pendingFlip` 值 | `1` / `undefined` |
| `fl` | `flipping` 标志（闭包变量） | `true` / `false` / `null`（未定义） |
| `cci` | `__currentChapterIdx` | 当前章节索引 |
| `prk` | `Object.keys(preRendered)` | `"0,1"` / `""` |
| `inv` | 调用跟踪 | `"1:ok;2:err;"` — 格式 `章节:结果` |
| `err:no_diag_fn` | `__getDiag` 不存在 | JS 未注入，可能是 WebView 上下文已销毁 |

### 关键诊断矩阵

```
inv: "1:ok;"  → preRenderChapter(1) 确实被调用且成功执行
pr: 1         → preRendered[1] 存在，确认成功
pr: 0, inv含有":ok" → 成功设置后被其他代码清除了（usePreRendered/completeChapterSwap等）
inv: "1:err;" → preRenderChapter 抛异常，看 err 字段
inv: ""       → preRenderChapter 从未被调用！看 preRender result 日志
err: "no_diag_fn" → WebView JS 上下文已销毁，__getDiag 不存在
```

## 涉及文件

- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — 修复 1-4

## 验证

```bash
adb logcat -s PG | grep -E "Emergency|DIAG|scene|inv"
```

编译 ✅ 构建 ✅ APK: `app/build/outputs/apk/debug/app-debug.apk`
