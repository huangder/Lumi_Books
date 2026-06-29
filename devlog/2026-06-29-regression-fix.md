# 白屏+无响应回归修复

日期：2026-06-29

## 问题

上一轮章节切换优化（[2026-06-29-chapter-animation-fix.md](2026-06-29-chapter-animation-fix.md)）引入两个严重回归：
1. 点击书本加载时间明显变长（~10秒）
2. 进入后页面空白，点击/滑动均无响应

## 根因

[PageFlipAnimation.kt](app/src/main/java/com/ebook/reader/ui/animation/PageFlipAnimation.kt) 中 `SlideCoverAnimation.aDrag()` 函数存在 **JavaScript 语法错误**。

在 Phase 2 修改时，Miss Path 的 loading 占位代码被错误地放在了第一个 `if (atBoundary)` 闭合之后、第二个重复的 `if (atBoundary)` 块中，导致 `} else {` 前多了一个孤立的 `}`：

```javascript
// 错误结构：
if (atBoundary) {
    if (pr) { ... return; }
}                              // ← 过早闭合了 if(atBoundary)

if (atBoundary) {              // ← 重复的 if(atBoundary)，JS 语法仍然合法但逻辑错误
    // miss path code
}                              // ← 这个闭合被当成了 if(atBoundary) 的闭合
} else {                       // ← 语法错误！孤立的 }
```

浏览器解析这段 JS 时，`} else {` 导致语法解析失败，**整个 `<script>` 块被丢弃**。
后果：
- `init()` 从未执行 → `__paginationReady` 永远为 `false`
- CSS column 分页从未创建 → WebView 内容不布局 → 白屏
- `aDrag`/`dEnd` 等触摸处理函数未定义 → 滑动无响应
- `onPaginationComplete()` 从未调用 → Compose 层 `isLoading` 一直为 `true`，直到 10 秒超时强制关闭

## 修复

将 Miss Path 代码合并回第一个 `if (atBoundary)` 块内，作为 `if (pr)` 的落空分支：

```javascript
// 正确结构：
if (atBoundary) {
    if (pr) {
        // hit path: 预渲染内容
        ...
        return;
    }
    // miss path: loading 占位（直接在 if(atBoundary) 内，作为 pr 检查的 fallthrough）
    var adjIdx2 = ...
    ...
    try { AndroidBridge.onChapterFlipReady(isF ? 1 : -1); } catch(e) {}
} else {
    // 正常章节内拖拽
    ...
}
```

修改文件：[PageFlipAnimation.kt](app/src/main/java/com/ebook/reader/ui/animation/PageFlipAnimation.kt) — `aDrag` 函数，删除重复的 `if (atBoundary) {` 和孤立的 `}`

---

## 第二轮修复：章节边界 loading 死循环

### 问题

JS 语法修复后，章节边界拖拽跟手了，但**始终显示"加载中..."，永远滑不到另一个章节**。

### 根因

三个相互关联的 JS 层 bug：

**Bug 1 — `cleanupLayers` 未清除 `botLayer._loadingIdx`**

`bounceBack` → `cleanupLayers` 清空了 `botLayer.innerHTML = ''`，但未清除 `botLayer._loadingIdx`。
第二次拖拽时，`aDrag` Miss Path 检查 `botLayer._loadingIdx !== adjIdx2` → false（匹配），跳过重建 loading spinner，导致 `botLayer` 被设为可见但**内部为空**——用户看到空白层一闪然后 bounceBack。

**Bug 2 — `onPreRenderReady` 要求 `dg.on === true` 才执行**

```javascript
if (typeof dg === 'undefined' || !dg.on) return;
```

Kotlin 异步加载 HTML → Base64 编码 → 注入 JS 的整个过程需要 100-500ms+。
用户拖拽到边界触发 `onChapterFlipReady` 后，通常在内容到达前就已经松手了。
松手 → `dEnd` → `bounceBack` → `dg.on = false`。
内容到达时 `onPreRenderReady` 因 `!dg.on` 直接返回，loading spinner 永不被替换。

**Bug 3 — `onPreRenderReady` 用 `dg.cx - dg.x0` 判断方向**

用户松手后 `dg.cx`/`dg.x0` 可能是过时值，方向判断可能出错，导致 `chapterIndex !== adjIdx` 检查失败。

### 修复（3 处）

1. **`cleanupLayers`**：增加 `botLayer._loadingIdx = undefined; delete botLayer._loadingIdx;`，确保清理后下次拖拽正确重建 loading spinner
2. **`onPreRenderReady`**：
   - 保留 `dg.on` 检查，但分为两个分支：
     - 拖拽中 (`dg.on`)：无缝替换 loading spinner 为真实内容
     - 已松手：清除 `_loadingIdx` 标记，preRendered 缓存已就绪，**下次拖拽走 Hit Path**
   - 方向判断改为 `chapterIndex > window.__currentChapterIdx`（不依赖拖拽状态）

### 关键设计原则

- **preRendered 缓存是持久状态**：内容加载完成后不删除 `preRendered[idx]`，下次拖拽直接命中
- **`_loadingIdx` 是临时标记**：仅用于标识"botLayer 当前正在显示哪个章节的 loading spinner"，清理后即失效
- **`onPreRenderReady` 不再阻止已松手场景**：内容到达后清除 loading 标记，为下一次拖拽铺路

## 编译

```bash
./gradlew compileDebugKotlin   # BUILD SUCCESSFUL
./gradlew assembleDebug         # BUILD SUCCESSFUL
```
