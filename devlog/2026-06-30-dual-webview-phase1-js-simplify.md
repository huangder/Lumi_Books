# 双 WebView 方案 Phase 1：JS 极简化

日期：2026-06-30

分支：`dual-webview`（基于 `chapter-fix-brunch`）

## 目标

删除所有跨章动画逻辑，章内翻页保留原始 SlideCoverAnimation 双层视差动画。跨章切换移到 Compose 双 WebView slide 动画（后续 Phase）。

## 变更

### ReaderScreen.kt — buildPaginationJs() 重写

| 指标 | 旧（Round 8） | Phase 1 v1（已废弃） | Phase 1 v2（当前） |
|------|-------------|-------------------|-------------------|
| 行数 | ~400 | ~195 | ~330 |
| 参数 | `(isPdf, bgColor, chapterIndex)` | `(bgColor, chapterIndex)` | `(bgColor, chapterIndex)` |
| 章内翻页动画 | SlideCoverAnimation 双层视差 | 临时 overlay clone（❌ 不符原逻辑） | SlideCoverAnimation 双层视差 ✅ |
| 跨章动画 | SlideCoverAnimation + SimpleSlideAnimation | 删除 | 删除 |

### v2 修复（2026-06-30）：恢复原始章内翻页动画

**问题**：v1 的临时 overlay clone 方案与原始 SlideCoverAnimation 章内翻页逻辑完全不同，真机测试章内翻页动画失效。

**根因**：原始章内翻页使用持久 `topLayer`/`botLayer` 双层视差：
- topLayer: 当前页 clone → 滑出屏幕
- botLayer: 目标页 clone → 从侧边滑入（带视差 `t*0.3+0.7`）
- wrap 在动画期间不移动，动画结束后 `cleanupLayers()` + wrap 跳转到目标页

v1 的临时 overlay 方案：
- 创建 overlay div → clone wrap → wrap 立即跳目标页 → overlay 滑出
- 无双层视差、无持久 layer、无 `outer.style.visibility='hidden'`

**修复**：恢复原始 SlideCoverAnimation 章内翻页完整逻辑，仅删除跨章相关代码。

**保留的原始代码（~150 行恢复）：**
- `topLayer` / `botLayer` / `edgeShadow` 变量声明
- `initLayers(b)` / `cleanupLayers()` / `resetLayer(el)` 
- `eo(t)` / `eb(t)` 缓动函数
- `flipTo(p, dur)` — 原始双层视差翻页动画
- `bounceBack(dur)` — 原始回弹动画（含 edgeShadow 渐隐）
- `aDrag(dx)` — 原始章内拖拽（topLayer/botLayer 视差跟随）
- `dEnd(dx, vel)` — 原始阈值判断 + flipTo/bounceBack
- `tapFlip(dir)` — 原始点击翻页
- `updateTheme()` — 恢复 topLayer/botLayer 背景色引用

**删除的跨章代码（保持不变）：**
- `preRendered` 字典 / `preRenderChapter` / `usePreRendered` / `checkPreRender`
- `chapterFlipTo` / `animateChapterSwap` / `completeChapterSwap`
- `onPreRenderReady`（3个场景分支）
- bounceBack 中的 `__pendingFlip` / `preRendered[pendingIdx]` 跨章检测
- aDrag 中的预渲染命中/loading 占位分支
- 所有诊断追踪变量（`__bbDone`, `__cftCalled`, `__ccsCalled` 等）

**章内行为（v2 修复后）：**
- ✅ 章内左右滑动拖拽 → 双层视差跟手（topLayer/botLayer），松手 flipTo 动画
- ✅ 点击左右侧翻页 → tapFlip → flipTo 双层视差动画
- ✅ 拖拽到章节边界 → 橡皮筋效果 (dx*0.25) + onChapterFlipReady 通知 + bounceBack
- ✅ 点击末页/首页 → onChapterFlipReady 通知（无动画）

### handleChapterFlipReady — 保持简化

```kotlin
handleChapterFlipReady = { direction ->
    val cci = viewModel.uiState.value.currentChapterIndex
    val adj = cci + direction
    android.util.Log.e("PG", "Chapter boundary: cci=$cci dir=$direction adj=$adj (cross-chapter not implemented yet)")
}
```

### 删除的 Kotlin 代码（保持不变）
- `LaunchedEffect(uiState.currentPageIndex, uiState.totalPages)` — JS 预渲染触发块
- `handlePaginationComplete` 中的 JS 预渲染循环
- `import SlideCoverAnimation` / `import SimpleSlideAnimation`
- `HtmlContent` 的 `isPdf` 参数

## 验证

```bash
./gradlew compileDebugKotlin  # ✅ BUILD SUCCESSFUL
./gradlew assembleDebug       # ✅ BUILD SUCCESSFUL
```

### 真机测试要点

```bash
adb logcat -s PG | grep -E "Chapter boundary|onPageChanged|flipTo"
```

**Phase 1 v2 预期行为：**
- ✅ 章内左右滑动翻页 → 双层视差拖拽 + flipTo 动画（与修复前 Round 8 一致）
- ✅ 点击左右侧翻页 → tapFlip → flipTo 正常
- ✅ 拖拽到章节末尾松手 → bounceBack 回弹 + log "Chapter boundary"（跨章尚未实现）
- ✅ 点击末页右侧 → log "Chapter boundary"（跨章尚未实现）
- ✅ 字体/主题变更正常（repaginate / updateTheme 含 topLayer/botLayer 引用）
- ❌ 跨章翻页不工作（预期，Phase 2-3 实现）

## 涉及文件

- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — 主要修改
