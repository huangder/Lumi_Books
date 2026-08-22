# 阅读器引擎 Bug 交接文档

> 2026-07-03

## 背景

在实现 UI 设计规范的过程中，阅读器引擎出现了两个 bug。尝试修复多次但未完全解决，需要其他 agent 接手。

## 问题描述

### Bug 1：字号变大后文字可以上下滑动

**现象**：调节字号变大后，一页内容可以上下滑动，而不是固定在一页内。

**根因分析**：
- `PageLayoutEngine` 使用 `StaticLayout` 分页，计算每页能容纳多少行文字
- `PageContentView` 的 `TextView` 设置了 `setTextIsSelectable(true)`
- `setTextIsSelectable(true)` 会让 `TextView` 变成可滚动的
- 当字号变大后，分页计算和实际显示可能出现偏差，导致文字溢出可滚动

**已尝试的修复**：
1. `textView.isVerticalScrollBarEnabled = false` — 无效，内部仍可滚动
2. 重写 `PageContentView.dispatchTouchEvent` 拦截垂直滚动 — 当前方案，可能有效但未验证

**当前代码状态**：
- `PageContentView.kt` 中有 `dispatchTouchEvent` 拦截垂直滚动的代码
- `PageContentView.configure` 中 `lineSpacingExtra` 参数已与 `PageLayoutEngine` 同步
- `ReadView.configureCurrentPageView` 传递 `lineSpacingExtra = 2.5f * density`

**可能的根因**：
- `StaticLayout` 和 `TextView` 的行高计算可能不完全一致
- `setTextIsSelectable(true)` 改变了 `TextView` 的内部布局行为
- `includeFontPadding = false` 在两边都设置了，但可能仍有差异

### Bug 2：点击翻页动画正常但文字跳回原位

**现象**：点击左右边缘翻页时，动画正常播放（页面滑动），但动画结束后文字内容跳回原来的页面。

**根因分析**：
- `SlidePageAnim.startFromTap` 没有设置 `isFlipAnim = true`
- `isFlipAnim` 是 `private` 的，`startFromTap` 无法直接设置
- `PageAnimationController.computeScroll()` 中检查 `isFlipAnim`，如果为 `false` 则不调用 `onAnimationComplete`
- `onAnimationComplete` 负责调用 `slotManager.shiftForward/Backward` 移动槽位
- 没有 `isFlipAnim = true` → 槽位不移动 → 动画结束后位置重置

**已修复**：
- `PageAnimationController.kt`：新增 `markAsFlip()` 公共方法
- `SlidePageAnim.kt`：`startFromTap` 中调用 `markAsFlip()`

**当前状态**：已修复，用户确认翻页正常。

## 相关文件

```
app/src/main/java/com/huangder/lumibooks/ui/reader/engine/
├── PageAnimationController.kt  — 翻页动画控制器（已修复：新增 markAsFlip()）
├── PageContentView.kt          — 单页内容 View（当前有 dispatchTouchEvent 拦截）
├── PageLayoutEngine.kt         — StaticLayout 分页引擎
├── PageSlotManager.kt          — 3 槽位页级管理器
├── ReadView.kt                 — 核心阅读视图（已修复：传递 lineSpacingExtra）
└── SlidePageAnim.kt            — 水平滑动翻页动画（已修复：startFromTap 调用 markAsFlip）
```

## 关键代码流程

### 分页流程
```
PageLayoutEngine.layout()
  → StaticLayout 排版（使用 visibleHeight = textHeight - marginTop - marginBottom）
  → 按行高累计切分为 PageLayout 列表
  → 每页包含 startCharOffset 和 endCharOffset
```

### 显示流程
```
PageSlotManager.loadSlot()
  → 调用 contentProvider 获取章节文本
  → 调用 layoutEngine.layout() 获取分页
  → 调用 contentView.setPageContent(text, startChar, endChar)
  → TextView 显示对应范围的文字
```

### 翻页流程
```
onTapLeft/Right → startTapAnimation → startFromTap
  → markAsFlip() + startAnim()
  → Scroller 驱动动画帧
  → computeScroll() 检测动画完成
  → if (isFlipAnim) onAnimationComplete()
  → slotManager.shiftForward/Backward()
```

## 修复建议

### Bug 1 修复方向

1. **方案 A**：在 `PageContentView.dispatchTouchEvent` 中彻底拦截所有触摸事件，只允许长按选择
   - 需要自己实现长按检测和文字选择逻辑
   - 复杂度高

2. **方案 B**：不用 `setTextIsSelectable(true)`，改用其他方式实现文字选择
   - 例如使用 `LinkMovementMethod` 或自定义 `MovementMethod`
   - 复杂度高

3. **方案 C**：确保 `PageLayoutEngine` 和 `TextView` 的行高计算完全一致
   - 检查 `StaticLayout` 的 `lineSpacingExtra` 和 `TextView` 的 `setLineSpacing` 是否真的相同
   - 检查 `includeFontPadding` 的影响
   - 可能需要在 `PageLayoutEngine` 中使用 `TextView` 的实际行高来分页

4. **方案 D**：在 `PageContentView` 中限制 `TextView` 的高度，使其刚好等于分页计算的高度
   - 设置 `textView.maxHeight` 或在 `onLayout` 中精确设置高度
   - 可能最简单

### Bug 2

已修复，无需额外处理。

## 测试方法

1. 编译：`./gradlew compileDebugKotlin`
2. 构建：`./gradlew assembleDebug`
3. 安装到真机测试：
   - 打开一本书
   - 调节字号到较大值（如 24sp）
   - 检查文字是否可以上下滑动
   - 点击左右边缘翻页，检查文字是否正确切换
   - 长按文字，检查是否能正常选择
