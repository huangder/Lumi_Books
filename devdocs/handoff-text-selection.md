# 文字选择功能 — AI 交接文档

> 本文档供下一个 AI 模型接手使用，包含完整的问题分析、架构对比、代码路径和修复方案。
> 创建于 2026-07-01

---

## 一、当前问题（4个）

| # | 问题 | 严重度 | 一句话根因 |
|---|------|--------|-----------|
| 1 | 点击高亮后文字被色块挡住看不清 | 🔴 高 | 高亮颜色 80% 不透明度（`0xCCFFEB3B`），太浓 |
| 2 | 选择菜单经常遮住被选文字 | 🟡 中 | 菜单基于触摸点定位，而非选区边界框 |
| 3 | 拖动手柄调整选区卡顿/不跟手 | 🟡 中 | 每次拖动触发整页 Bitmap 全量重绘 |
| 4 | 长按选择文字不够精准 | 🟢 低 | StaticLayout API 近似定位 + 简单扩词规则 |

---

## 二、架构对比：本项目 vs Legado

### 2.1 文本渲染

**LumiBooks**（本项目）：
- `PageLayoutEngine` 用 `StaticLayout` 排版整段文字
- `PageRenderer.renderPage()` 调用 `StaticLayout.draw(canvas)` 将整页文字绘制到一个 `Bitmap`
- `PageSurfaceView` 持有并显示这个 Bitmap
- 文字和高亮**烧入同一个 Bitmap**，不可分离

**Legado**：
- `TextChapterLayout` 用 `StaticLayout`/`ZhLayout` 排版，但将每个字符映射为独立的 `TextColumn(start, end, charData)`
- `ContentTextView.onDraw()` → `TextPage.draw()` → `TextLine.draw()` → `TextColumn.draw()`
- 每个字符用 `Canvas.drawText()` **逐字绘制**
- 文字和高亮是**同一帧内的两个绘制步骤**，先画文字再叠半透明矩形

### 2.2 选择高亮

**LumiBooks**：
```kotlin
// PageRenderer.kt 第 187-188 行
canvas.drawRoundRect(RectF(...), cornerRadius, cornerRadius, paint)        // 80% 黄色
canvas.drawRoundRect(RectF(inset...), cornerRadius-1f, cornerRadius-1f, innerPaint) // 40% 白色内层
```
- 高亮颜色：`0xCCFFEB3B`（80% 不透明度黄色）
- 内层叠加：`0x66FFFFFF`（40% 白色）
- 效果：文字几乎完全被遮挡

**Legado**：
```kotlin
// TextColumn.kt 第 67-69 行
canvas.drawText(charData, start, y, textPaint)  // 先画文字
if (selected) {
    canvas.drawRect(start, 0f, end, textLine.height, view.selectedPaint)  // 再叠半透明矩形
}
```
- 高亮颜色：`#63858585`（39% 不透明度灰色）
- 无额外叠加层
- 效果：文字清晰可见，灰色作为背景衬底

### 2.3 手柄

**LumiBooks**：
- Compose `Canvas.drawCircle()` 自绘，24dp 圆形，深红色 + 白色描边
- 叠在 `AndroidView`（ReadView）上方
- 拖动时跨 Compose → AndroidView 边界通信

**Legado**：
- Activity 层 `ImageView`，水滴形矢量图（`ic_cursor_left.xml` / `ic_cursor_right.xml`）
- 直接 `setX()/setY()` 移动，无跨框架问题
- 支持手柄反转（左手柄拖到右手柄右侧时角色互换）

### 2.4 菜单

**LumiBooks**：
- Compose `Popup`，基于**触摸点坐标**定位
- 上半55% → 下方弹出，下半45% → 上方弹出
- 问题：触摸点在选区中间时菜单遮住选区

**Legado**：
- `PopupWindow`，基于**选区边界框**（6个坐标参数）定位
- 传入 `startX, startTopY, startBottomY, endX, endBottomY`
- 菜单始终出现在选区外围

### 2.5 坐标系统

**LumiBooks**：字符偏移量 `charOffset: Int`，通过 `StaticLayout.getLineForOffset()` / `getPrimaryHorizontal()` 转换为像素坐标

**Legado**：三级坐标 `TextPos(relativePagePos, lineIndex, columnIndex)`，每个 TextColumn 有精确的 `(start, end)` 像素 X 坐标

---

## 三、关键文件路径

### 本项目（LumiBooks）

| 文件 | 作用 | 关键行 |
|------|------|--------|
| `ui/reader/engine/ReadView.kt` | 核心 View，选区状态管理，手柄坐标转换 | 87-104（刷新流程）、147-216（手柄）、293-327（长按选词） |
| `ui/reader/engine/PageRenderer.kt` | Bitmap 渲染（文字+高亮） | 83（renderPage）、140-190（drawSelectionHighlight） |
| `ui/reader/engine/PageLayoutEngine.kt` | StaticLayout 排版/分页/命中测试 | 128（排版）、263-279（getCharOffsetAtPoint） |
| `ui/reader/engine/PageSurfaceView.kt` | Bitmap 绘制到屏幕 | 37（onDraw） |
| `ui/reader/engine/PageAnimationController.kt` | 手势检测（长按/拖拽/翻页） | 94-199（onTouchEvent） |
| `ui/reader/ReaderScreen.kt` | Compose 层：手柄、菜单、交互 | 323-356（手柄）、556-587（高亮操作）、1248-1337（菜单） |

### Legado（参考）

| 文件 | 参考点 |
|------|--------|
| `legado/.../entities/column/TextColumn.kt:45-70` | 逐字符绘制 + selected 矩形叠加 |
| `legado/.../ContentTextView.kt:44-49` | selectedPaint 定义（39% 灰色） |
| `legado/.../TextActionMenu.kt:104-167` | 菜单定位算法（基于选区边界框） |
| `legado/.../ReadView.kt:174-266` | 触摸事件入口（长按/拖动选择） |
| `legado/.../ReadView.kt:319-388` | onLongPress 选词（BreakIterator） |
| `legado/.../ReadView.kt:473-492` | selectText 拖动扩展选区 |
| `legado/.../ReadBookActivity.kt:760-796` | 手柄拖动处理（OnTouchListener） |
| `legado/.../ReadBookActivity.kt:801-816` | 手柄位置更新（upSelectedStart/End） |
| `legado/.../ReadBookActivity.kt:834-847` | showTextActionMenu 传参 |

---

## 四、修复方案

### Phase 1：高亮遮挡修复（改 2 个数值，立即见效）

**文件**：`app/src/main/java/com/huangder/lumibooks/ui/reader/engine/PageRenderer.kt`

**改动**：
- 第 146 行：`highlightColor` 默认值 `0xCCFFEB3B` → `0x40FFEB3B`（80% → 25%）
- 第 166 行：`innerPaint` 的 `0x66FFFFFF` → `0x10FFFFFF` 或直接删除内层矩形绘制

### Phase 2：菜单定位修复

**文件**：`app/src/main/java/com/huangder/lumibooks/ui/reader/ReaderScreen.kt`

**改动**：
- `SelectionState` 增加选区像素坐标字段（`selStartX/Y`、`selEndX/Y`）
- `ReadView` 通过 `ReadViewCallbacks` 传出选区起止的像素坐标
- 菜单定位逻辑改为基于选区边界框：上半部分 → 下方弹出，下半部分 → 上方弹出
- 参考 Legado `TextActionMenu.show()` 的 6 参数定位方式

### Phase 3：拖动流畅性优化（分层渲染）

**文件**：`ReadView.kt`、`PageRenderer.kt`、`PageSurfaceView.kt`

**改动**：
- 渲染拆分为文字层 Bitmap + 标注层 Bitmap
- `renderPage()` 只画文字到文字层
- 高亮/选区画到标注层（透明 Bitmap）
- 拖动时只重绘标注层，文字层不动
- 拖动结束/翻页时才重绘文字层

### Phase 4（可选）：选词精准度提升

**文件**：`app/src/main/java/com/huangder/lumibooks/ui/reader/engine/ReadView.kt`

**改动**：
- 第 303-313 行扩词逻辑引入 `java.text.BreakIterator.getWordInstance()`
- 替代简单的 CJK 左2右3 规则

---

## 五、开发规范提醒

- 每次改 Kotlin/构建文件后执行 `./gradlew compileDebugKotlin` 验证编译
- 重要变更更新 `devlog/YYYY-MM-DD.md`
- 阅读器修改需在真机上验证
- commit message 用中文，格式：`<type>: <描述>`
- 不许擅自修改 git 设置或执行 git 回退

---

## 六、详细修复计划

见 `docs/plan-text-selection-fix.md`
