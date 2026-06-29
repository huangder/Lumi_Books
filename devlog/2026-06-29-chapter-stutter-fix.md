# 2026-06-29 章节切换卡顿根因修复（最终版）

## 问题演变

1. **上一轮**：修复白屏后，章节切换仍有零点几秒卡顿（`evaluateJavascriptSync` 阻塞主线程）
2. **第二轮**：将该函数改为 suspend 版本，加了 Compose 动画 → 用户反馈"页面滑到一半会突然消失，速率飞快，不能像章节内部跟手"

## 第二轮失败的根因

**Compose 动画平移整个 WebView，但 WebView 内部的章节边界处没有"下一章内容"可展示。**

```
章节内翻页：JS aDrag 平移 wrap → 相邻 column 始终存在 → 跟手
章节边界（原）：JS aDrag 只显示当前页克隆体（0.25阻力）→ 松手后 Compose 动画平移 WebView
              → WebView 滑出时，右侧没有下一章内容 → 白/空白 → "消失"
```

Compose 动画的 `flipOffset.animateTo(-1f)` 平移 WebView，但内部只有当前章节的最后一列。右侧没有预渲染的下一章内容，用户看到的是 outer 的 clip 边界 → 空白。

## 最终方案：JS 层原生处理章节边界

**将章节边界拖拽完全交给 JS 层处理**，用预渲染的相邻章节 wrapper 参与 `aDrag`/`dEnd`/`tapFlip`，与章节内翻页使用相同的动画机制。

### 改动的文件

| 文件 | 改动 |
|------|------|
| [PageFlipAnimation.kt](app/src/main/java/com/ebook/reader/ui/animation/PageFlipAnimation.kt) | `SlideCoverAnimation` + `SimpleSlideAnimation` 重写 `aDrag`/`dEnd`/`tapFlip`/`bounceBack`，新增 `completeChapterSwap`/`chapterFlipTo` |
| [ReaderScreen.kt](app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt) | `ReaderJsBridge` 新增 `onChapterSwapped`；`HtmlContent` 新增 `chapterIndex` 参数，加载后设置 `window.__currentChapterIdx`；`handleChapterFlipReady` 简化为纯 miss 路径 |

### JS 层关键逻辑

**aDrag 边界命中预渲染**：
- topLayer = 当前页克隆（滑动覆盖效果）
- botLayer = 预渲染相邻章节 wrapper（**真实内容，非克隆**）
- 拖拽参数与章节内完全一致（无 0.25 阻力！下一章内容真实存在）

**dEnd 边界命中预渲染**：
- 过阈值 → `completeChapterSwap()`（移除旧 wrap，预渲染 wrapper 接管 outer，通知 Kotlin）
- 未过阈值 → `bounceBack()`（预渲染 wrapper 移回 body 隐藏）

**tapFlip 边界命中预渲染**：
- `chapterFlipTo()` — 380ms 滑动覆盖动画（与 `flipTo` 相同风格）

**所有函数边界无预渲染时**：
- 回退到 `AndroidBridge.onChapterFlipReady()` → Kotlin miss 路径（loadDataWithBaseURL + Compose 动画）

### 数据流

```
JS 拖拽:
  touchmove → aDrag(dx) → 检测边界 + 检查 preRendered
    Hit: topLayer(当前页克隆) + botLayer(预渲染真实内容) 同步平移 → 跟手
    Miss: topLayer(当前页克隆) + 0.25 阻力 → 橡皮筋效果

  touchend → dEnd(dx, vel) → 过阈值?
    Hit: completeChapterSwap() → wrap 替换为预渲染 wrapper → onChapterSwapped → ViewModel
    Miss: onChapterFlipReady → Kotlin miss 路径
```

### Kotlin 侧简化

`handleChapterFlipReady` 现在是**纯 miss 路径**（JS 已确认无预渲染才调用）：
1. 隐藏 body + 设置背景
2. Compose 滑出(200ms) → loadDataWithBaseURL → 等分页(500ms) → 瞬移+滑入(200ms)
3. 显示 body

不再做预渲染检查 — 那由 JS 层负责。

## 验证

- `compileDebugKotlin`: BUILD SUCCESSFUL（零错误）
- `assembleDebug`: BUILD SUCCESSFUL
- APK: `app/build/outputs/apk/debug/app-debug.apk`

待真机验证：
1. **拖拽过章节边界** → 下一章内容跟手出现（不再"滑到一半消失"）
2. **点击章节边界** → 滑动覆盖动画切换到下一章（与章节内翻页风格一致）
3. **预渲染 miss** → Compose 动画回退，加载流畅
4. **回退到上一章** → 预渲染命中，展示上一章最后一页
