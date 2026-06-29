# 2026-06-29 章节边界拖拽动画修复

## 问题

1. **章节最后一页滑出滑到一半会突然消失**：在章节边界拖拽过阈值松手后，页面突然跳到新章节，没有任何过渡动画
2. **动画速率飞快，不能像章节内部跟手**：边界无预渲染时橡皮筋阻力太大(0.25x)，跟手反馈极弱

## 根因

### 根因1：`completeChapterSwap` 无动画过渡

`dEnd` 中当拖拽过阈值 + 预渲染命中时，直接调用 `completeChapterSwap()`：

```javascript
// dEnd 中（旧代码）：
if (preRendered[adjIdx]) {
    completeChapterSwap(dir, adjIdx);  // ❌ 瞬间清除图层，显示新章节
    return;
}
```

`completeChapterSwap` 立即执行 `topLayer.innerHTML=''`、`botLayer.innerHTML=''`，然后显示 outer。从拖拽中途到新章节内容瞬间跳变——用户看到页面"消失"。

对比：`chapterFlipTo`（点击边界）有 380ms easeOutCubic 缓动动画，但拖拽提交路径没有。

### 根因2：橡皮筋阻力固定 0.25x

边界无预渲染时 `c = dx * 0.25`，跟手反馈极弱。小幅度拖拽时应该接近 1:1 跟踪，只有大幅度才逐渐变硬。

### 根因3：`bounceBack` 缺少状态清理

`bounceBack` 动画完成后没有设置 `animId = null` 和 `flipping = false`，可能影响后续动画。

## 修复

### 改动1：新增 `animateChapterSwap` 函数 (SlideCoverAnimation)

从当前拖拽位置开始 280ms easeOut 动画（与 `chapterFlipTo` 380ms 同风格），动画完成后才调用 `completeChapterSwap`。

```javascript
function animateChapterSwap(dir, adjIdx) {
    if (flipping) return;
    flipping = true;
    var d = 280, t0 = performance.now();
    var startTop = gx(topLayer), startBot = gx(botLayer);
    var endTop = dir > 0 ? -vw : 0, endBot = 0;
    // 边缘阴影随动画渐显
    ...
    (function go(now) {
        ...
        topLayer.style.transform = 'translateX(' + Math.round(startTop + (endTop - startTop) * t) + 'px)';
        botLayer.style.transform = 'translateX(' + Math.round(startBot + (endBot - startBot) * t) + 'px)';
        ...
        animId = null; flipping = false;
        completeChapterSwap(dir, adjIdx);
    })(performance.now());
}
```

### 改动2：`dEnd` 使用 `animateChapterSwap` 替代直接 `completeChapterSwap`

```javascript
// 旧：completeChapterSwap(dir, adjIdx);  ← 瞬间跳变
// 新：animateChapterSwap(dir, adjIdx);   ← 280ms 缓动动画
```

### 改动3：渐进式橡皮筋阻力

```javascript
// 旧：c = dx * 0.25;
// 新：
var absDx = Math.abs(dx);
var maxSlide = vw * 0.3;
c = (dx > 0 ? 1 : -1) * maxSlide * (1 - Math.exp(-absDx / (vw * 0.1)));
```

- 小幅度拖拽：接近 1:1 跟手
- 大幅度拖拽：逐渐接近 maxSlide（30%视口宽度），不再增长
- 指数衰减公式，过渡平滑自然

### 改动4：`bounceBack` 状态修复

- SlideCoverAnimation: 添加 `flipping = true/false` 和 `animId = null`
- SimpleSlideAnimation: 同

### 改动5：SimpleSlideAnimation `dEnd` 动画化

PDF 路径的 `dEnd` 预渲染命中也改为 280ms 动画交换（旧 wrap 滑出 + 新 wrap 滑入）。

## 涉及文件

- [PageFlipAnimation.kt](app/src/main/java/com/ebook/reader/ui/animation/PageFlipAnimation.kt)

## 验证

- `compileDebugKotlin`: BUILD SUCCESSFUL
- `assembleDebug`: BUILD SUCCESSFUL
- APK: `app/build/outputs/apk/debug/app-debug.apk`

待真机验证：
1. **章节边界拖拽过阈值** → 280ms 缓动动画滑到新章节（不再"突然消失"）
2. **章节边界拖拽未过阈值** → bounceBack 回弹自然
3. **章节边界无预渲染拖拽** → 渐进式橡皮筋，小幅度跟手，大幅度逐渐变硬
4. **章节内拖拽** → 行为不变（1:1 跟手）
5. **点击章节边界** → chapterFlipTo 行为不变（380ms 动画）
