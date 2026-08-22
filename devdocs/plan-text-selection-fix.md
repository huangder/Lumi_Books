# 文字选择功能修复计划

> 基于 LumiBooks vs Legado 深度对比分析，2026-07-01

## 问题清单

| # | 问题 | 严重度 | 根因 |
|---|------|--------|------|
| 1 | 点击高亮后文字被色块挡住看不清 | 🔴 高 | 高亮颜色 80% 不透明度，叠加白色内层 |
| 2 | 选择菜单经常遮住被选文字 | 🟡 中 | 菜单基于触摸点定位，而非选区边界框 |
| 3 | 拖动手柄调整选区卡顿/不跟手 | 🟡 中 | 每次拖动触发整页 Bitmap 全量重绘 |
| 4 | 长按选择文字不够精准 | 🟢 低 | StaticLayout API 近似定位 + 简单扩词规则 |

## 修复方案（路线A：现有架构修补）

### Phase 1：高亮遮挡修复（改 2 个值，立即见效）

**文件**：`app/src/main/java/com/huangder/lumibooks/ui/reader/engine/PageRenderer.kt`

**改动**：
- 第 146 行：`highlightColor` 默认值从 `0xCCFFEB3B` 改为 `0x40FFEB3B`（80% → 25% 不透明度）
- 第 166 行：去掉或大幅降低 `innerPaint` 的 alpha（`0x66FFFFFF` → `0x10FFFFFF` 或直接删除内层矩形）

**验证**：选中文字 → 点高亮 → 文字应清晰可见，黄色色块作为背景衬底

### Phase 2：菜单定位修复（改定位逻辑）

**文件**：`app/src/main/java/com/huangder/lumibooks/ui/reader/ReaderScreen.kt`

**改动**：
- 第 1248 行 `SelectionState` 数据类：增加 `selStartX/Y`、`selEndX/Y` 字段（选区起止坐标）
- 第 1281-1293 行菜单定位逻辑：改为基于选区边界框定位
  - 选区在屏幕上半部分 → 菜单显示在选区**下方**
  - 选区在屏幕下半部分 → 菜单显示在选区**上方**
  - 水平位置：对齐选区起始 X，夹到屏幕边缘
- `ReadView` 需要通过 callback 传出选区的像素坐标（起始行顶部、结束行底部）

**参考实现**：Legado `TextActionMenu.show()` 传入 `startX, startTopY, startBottomY, endX, endBottomY`

### Phase 3：拖动流畅性优化（分层渲染）

**文件**：
- `app/src/main/java/com/huangder/lumibooks/ui/reader/engine/ReadView.kt`
- `app/src/main/java/com/huangder/lumibooks/ui/reader/engine/PageRenderer.kt`
- `app/src/main/java/com/huangder/lumibooks/ui/reader/engine/PageSurfaceView.kt`

**改动**：
1. 将渲染拆分为两层 Bitmap：
   - **文字层**：只绘制文字，不包含高亮（`renderPage()` 只画文字）
   - **标注层**：透明 Bitmap，只绘制高亮/选区色块
2. `PageSurfaceView` 改为绘制两个 Bitmap 叠加（或用两个 `SurfaceView`）
3. 拖动手柄时：只重绘标注层 Bitmap，文字层不动
4. 拖动结束/翻页时：才重绘文字层

**预期效果**：拖动时只重绘高亮矩形（轻量），不再重绘全部文字（重量）

### Phase 4（可选）：选词精准度提升

**文件**：`app/src/main/java/com/huangder/lumibooks/ui/reader/engine/ReadView.kt`

**改动**：
- 第 303-313 行扩词逻辑：引入 `java.text.BreakIterator.getWordInstance()` 替代简单的 CJK 左2右3 规则
- 英文：用 `BreakIterator` 做语言感知的单词边界检测
- 中文：保持按标点/空格分割，但扩展到段落边界

## 关键文件索引

| 文件 | 作用 | Phase |
|------|------|-------|
| `ui/reader/engine/PageRenderer.kt` | Bitmap 渲染（文字+高亮） | 1, 3 |
| `ui/reader/engine/ReadView.kt` | 核心 View，选区状态管理 | 2, 3, 4 |
| `ui/reader/ReaderScreen.kt` | Compose 层：手柄、菜单、交互 | 2 |
| `ui/reader/engine/PageSurfaceView.kt` | Bitmap 绘制到屏幕 | 3 |
| `ui/reader/engine/PageLayoutEngine.kt` | 文字排版/分页/命中测试 | 4 |
| `ui/reader/engine/PageAnimationController.kt` | 手势检测（长按/拖拽/翻页） | — |

## Legado 参考文件

| 文件 | 参考点 |
|------|--------|
| `legado/.../entities/column/TextColumn.kt:45-70` | 逐字符绘制 + 选中矩形叠加 |
| `legado/.../TextActionMenu.kt:104-167` | 菜单定位算法 |
| `legado/.../ReadView.kt:760-796` | 手柄拖动处理 |
| `legado/.../ReadBookActivity.kt:801-816` | 手柄位置更新 |
| `legado/.../ContentTextView.kt:44-49` | selectedPaint 定义（39% 灰色） |
