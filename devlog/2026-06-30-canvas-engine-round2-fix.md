# Canvas 引擎初始化 + 渲染修复（第2轮）

日期：2026-06-30

## 问题

1. **排版错乱**：打开书籍显示的页面内容不对
2. **动画不对**：翻页动画被静态页面穿透
3. **修改主题不能实时**：切换主题后页面颜色不更新

## 根因分析

### 1. 排版错乱 + 动画穿透 — dispatchDraw 绘制了3个堆叠 View

`ReadView.dispatchDraw()` 调用 `super.dispatchDraw(canvas)` 绘制了3个 PageSurfaceView 子 View。它们全是 MATCH_PARENT 大小，Bitmap 都在 (0,0) 绘制。z-order 靠后的 NEXT 在最上面，所以用户看到的是**下一页**的内容而非当前页。

动画时 `super.dispatchDraw` + `animationController.onDraw()` 同时绘制，静态 NEXT 页穿透到动画层下面。

**修复**：`dispatchDraw` 改为只调用 `animationController.onDraw(canvas)`。PageSurfaceView 降级为纯粹的 Bitmap 存储容器，不参与 View 树的绘制。

### 2. 章末最后一行被裁剪

`PageRenderer.renderPage()` 中 clip 高度计算用了 `getLineTop(endLine)`。endLine 是 exclusive，章末时 `endLine >= lineCount`，`coerceAtMost` 兜底后取到最后一行的 top 而非 bottom，导致最后一行被裁掉。

**修复**：改用 `getLineBottom(endLine - 1)` 计算 clip 底部，正确包含最后一行的完整高度。

### 3. 主题切换不实时

主题变更链路正确（`saveReaderTheme` → `_uiState` → recomposition → `update` → `configure` → `themeChanged=true` → `invalidateAll()` → `initialize()`），但修复 #1 之前，即使新 Bitmap 已渲染，用户也看不到（dispatchDraw 画了其它 View）。修复 #1 后主题切换应正常工作。

## 修改文件

| 文件 | 改动 |
|------|------|
| `ReadView.kt` | `dispatchDraw` 移除 `super.dispatchDraw`，统一由 `animationController.onDraw` 绘制 |
| `PageRenderer.kt` | clip 高度用 `getLineBottom(lastLine)` 替代 `getLineTop(endLine)` |
| `PageSlotManager.kt` | （上轮）`loadSlot` CUR 加载完成后触发 `notifyPageChanged()` + 预加载 PREV/NEXT |

## 编译

```bash
./gradlew assembleDebug  # ✅ BUILD SUCCESSFUL
```
