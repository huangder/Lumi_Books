# 章节翻页失效修复（第六轮：诊断 + 编码修复）

日期：2026-06-30

## 问题

经过 5 轮修复后，真机测试仍然不工作。用户报告：`Emergency preRender result: null (chapter 21)` — emergency 路径触发但章节切换动画不播放。

**关键发现**：`evaluateJavascript` 回调返回 `"null"` 是**歧义的**：
- JS 执行成功，最后表达式 `onPreRenderReady()` 返回 `undefined` → Android 转换为字符串 `"null"`
- JS 抛出未捕获异常 → 回调收到 `null` → 也显示为 `"null"`

无法区分这两种情况，`preRenderChapter` 或 `onPreRenderReady` 可能静默失败了。

## 根因分析

### 问题 1：诊断盲区

当前 `handleChapterFlipReady` 的日志只记录了最后的返回值，无法知道：
- `preRenderChapter` 是否真的创建了 `preRendered[adjIdx]`
- `__preRenderError` 中是否有错误
- `onPreRenderReady` 走到了哪个场景
- `__pendingFlip`、`flipping`、`__currentChapterIdx` 的实际值

### 问题 2：UTF-8 编码损坏

`preRenderChapter` 使用 `atob()` 解码 base64，但 `atob` 输出的是**二进制字符串**（每字符一个字节 0-255），不是 UTF-8 文本。对于中文 EPUB：
1. Kotlin: `html.toByteArray()` → UTF-8 字节（中文字符 = 3 字节）
2. Base64 编码
3. JS: `atob(b64)` → 二进制字符串（3 个独立字节 ≠ 1 个中文字符）
4. `innerHTML = binaryString` → 浏览器按 Latin-1 解释，中文变乱码

虽然乱码不阻止动画播放，但可能导致布局计算异常。

### 问题 3：可能的静默失败路径

在 `onPreRenderReady` 中，即使所有 guard 通过：
- `chapterFlipTo(dir)` 内 `flipping` guard — 如果竞态导致 `flipping=true`，直接 return
- `completeChapterSwap` 可能因 DOM 操作失败而 return false

## 修复方案

### 文件 1：ReaderScreen.kt

#### Fix 1 — Emergency 回调：增加全面诊断日志

将简单的 `result` 日志替换为后续诊断查询：

```kotlin
wv?.evaluateJavascript(
    "window.preRenderChapter && preRenderChapter($adjIdx, '$b64');window.onPreRenderReady && onPreRenderReady($adjIdx);"
) { result ->
    android.util.Log.e("PG", "Emergency preRender result: $result (chapter $adjIdx)")
    // 延迟 100ms 查询诊断状态
    wv?.postDelayed({
        wv?.evaluateJavascript(
            "JSON.stringify({err:window.__preRenderError||'none',pr:" +
            "(window.preRendered&&preRendered[$adjIdx]?1:0)," +
            "pf:window.__pendingFlip,fl:window.flipping," +
            "cci:window.__currentChapterIdx,bot:" +
            "(typeof botLayer!=='undefined'&&botLayer?1:0)," +
            "prk:Object.keys(window.preRendered||{}).join(',')})"
        ) { diag ->
            android.util.Log.e("PG", "Emergency DIAG: $diag")
        }
    }, 100)
}
```

#### Fix 2 — UTF-8 安全的 base64 decode（在 buildPaginationJs 的 preRenderChapter 中）

```javascript
window.preRenderChapter = function(chapterIndex, htmlB64) {
    if (preRendered[chapterIndex]) return;
    try {
        // UTF-8 安全解码：atob → Uint8Array → TextDecoder
        var bin = atob(htmlB64);
        var bytes = new Uint8Array(bin.length);
        for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        var html = new TextDecoder('utf-8').decode(bytes);
        // ... 后续逻辑不变（w.innerHTML = html）...
    } catch(e) { window.__preRenderError = 'ch' + chapterIndex + ':' + e.message; }
};
```

#### Fix 3 — 让 `onPreRenderReady` 返回诊断信息

修改返回值以标识走到了哪个分支：

```javascript
window.onPreRenderReady = function(chapterIndex) {
    // ... guards ...
    
    // 场景 1：用户仍在拖拽
    if (typeof dg !== 'undefined' && dg.on) {
        // ... 替换 loading 为真实内容 ...
        return 'scene1';
    }
    
    // 场景 2：有 pending 标记
    if (window.__pendingFlip === chapterIndex) {
        if (!flipping) {
            window.__pendingFlip = undefined;
            chapterFlipTo(dir);
            return 'scene2_flip';
        }
        return 'scene2_defer';  // flipping=true, defer to bounceBack
    }
    
    // 场景 3：无 pending
    window.__pendingFlip = undefined;
    return 'scene3_nop';
};
```

### 文件 2：PageFlipAnimation.kt

#### Fix 4 — EPUB onPreRenderReady 增加 console.log

在关键分支添加 `console.log` 输出（配合 Chrome DevTools remote debugging 或 `window.__diagLog` 数组）。

#### Fix 5 — PDF onPreRenderReady 同样修复

SimpleSlideAnimation 的 `onPreRenderReady` 使用共享的 `preRenderChapter`（UTF-8 修复已覆盖），但需要同步返回诊断字符串。

## 验证方法

```bash
./gradlew compileDebugKotlin
./gradlew assembleDebug
```

### 真机验证

```bash
adb logcat -s PG | grep -E "Emergency|DIAG|scene|preRender"
```

关键诊断解读：
- `DIAG: {err:"none", pr:1, pf:21, fl:false, cci:20, ...}` → 一切正常，问题在动画层
- `DIAG: {err:"ch21:...", pr:0, ...}` → `preRenderChapter` 失败，看 err 信息
- `DIAG: {pf:undefined, ...}` → `__pendingFlip` 丢失
- `DIAG: {fl:true, ...}` → 仍然在 flipping 状态（bounceBack 未完成）
- `Emergency preRender result: scene2_flip` → 触发了 chapterFlipTo ✓
- `Emergency preRender result: scene2_defer` → 等待 bounceBack 完成
- `Emergency preRender result: scene3_nop` → pending 不匹配，掉到场景3（问题！）

## 涉及文件

- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — Fix 1, Fix 2（buildPaginationJs 中的 preRenderChapter）, Fix 3
- `app/src/main/java/com/ebook/reader/ui/animation/PageFlipAnimation.kt` — Fix 4（EPUB onPreRenderReady 返回值）, Fix 5（PDF onPreRenderReady）

## 数据流回顾（5 轮修复后）

```
拖拽到章节边界
  ↓
aDrag → __pendingFlip = adjIdx → onChapterFlipReady(dir) → Kotlin async
  ↓
用户松手 → dEnd → bounceBack(350ms, flipping=true)
  ↓                                      ↓
                              Kotlin 完成 → preRenderChapter + onPreRenderReady
  ↓                                      ↓
                  onPreRenderReady: pending匹配但flipping=true → scene2_defer
  ↓                                      ↓
bounceBack 完成 → flipping=false → cleanupLayers
  ↓
检查 __pendingFlip → 找到pending + preRendered就绪
  ↓
chapterFlipTo(dir) → 动画播放 → completeChapterSwap → onChapterSwapped
```

点击末页右边缘：
```
touchend → tapFlip(1) → np >= total → no preRender → __pendingFlip = adjIdx → onChapterFlipReady(1)
  ↓
Kotlin async → preRenderChapter + onPreRenderReady
  ↓
onPreRenderReady: flipping=false, pending匹配 → scene2_flip → chapterFlipTo(1)
  ↓
动画播放 → completeChapterSwap → onChapterSwapped
```
