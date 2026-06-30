# Canvas 引擎第4轮修复 — 渲染/动画层级/R角/阴影

日期：2026-06-30

## 问题

1. **有的页有字有的页无字，字超出屏幕** — 每章仅第0页有文字
2. **动画层级关系不对** — 缺少"上层/下层"的深度感和正确的层级归属
3. **缺少R角裁剪和柔和阴影**

## 根因与修复

### Bug A（致命）：PageRenderer clipRect 坐标空间错误

`PageRenderer.renderPage()` 中 `clipRect` 在第二次 `translate(0, -pageStartY)` **之后**执行。对于 page 1+（pageStartY ≠ 0），clip 被偏移到 bitmap 可视区域之外，与 bitmap 交集仅几像素 → **页面全白**。

这就是"有的页有字有的页无字"的直接原因——每章只有 page 0 (pageStartY=0) 正常。

**修复**：`clipRect` 移到第二次 translate **之前**，使裁剪在边距坐标系中稳定在原位。同时移除复杂的 `clipHeight = clipBottom - pageStartY` 计算，改用固定的 `visibleHeight`。

### Bug B（动画层）：层级归属和移动路径重设计

用户期望的层级模型：

| 方向 | 上层（全速移动） | 下层（视差微移） | 绘制顺序 |
|------|-----------------|-----------------|---------|
| NEXT | 当前页 → 左滑出屏幕 | 下一页 → 微移被揭示 | nextPage → curPage |
| PREV | 上一页 → 从左全速滑入 | 当前页 → 微移被覆盖 | curPage → prevPage |

关键变化：PREV 方向不再是 curPage 在上层，而是 **prevPage 在上层覆盖 curPage**。

**修复**：`SlidePageAnim.onDraw()` 完全重写：
- NEXT: 先画 nextBitmap（下层，x=offsetX×0.12），再画 curBitmap（上层，x=offsetX）
- PREV: 先画 curBitmap（下层，x=offsetX×0.12），再画 prevBitmap（上层，x=-vw+offsetX）
- 视差因子 0.12：上层移动 100px 时下层仅移动 12px

### Phase 3+4：阴影 + R角

- **阴影**：改用三段渐变 `[0x22, 0x08, 0x00]`，宽度 = 90px×density，更淡更柔和
- **R角**：上层页面 `drawUpperPage()` 用 `Path.addRoundRect()` 对右上+右下角裁剪，半径 = 88px×density

## 修改文件

| 文件 | 改动 |
|------|------|
| `PageRenderer.kt` | `clipRect` 移到 T2 之前；新增 `visibleHeight` 参数 |
| `SlidePageAnim.kt` | 完全重写：分层绘制、视差移动、三段渐变阴影、R角裁剪 |
| `ReadView.kt` | `renderer.configure()` 传递 `visibleHeightPx` |

## 编译

```bash
./gradlew assembleDebug  # ✅ BUILD SUCCESSFUL
```
