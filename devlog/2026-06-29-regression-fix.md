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

## 编译

```bash
./gradlew compileDebugKotlin   # BUILD SUCCESSFUL
./gradlew assembleDebug         # BUILD SUCCESSFUL
```
