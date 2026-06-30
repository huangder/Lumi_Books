# Canvas 引擎第6轮综合修复 — 10项问题

日期：2026-06-30

## 修复清单

### 1. 刚进书空白（点屏幕才有字）
**根因**: dispatchDraw 完全绕过子 View 绘制，PageSurfaceView.invalidate() 不一定触发 ReadView 重绘
**修复**:
- `ReadView.init` 设 `isClickable=true`, `isFocusable=true`
- `onPageChangedCallback` 末尾加 `invalidate()` 确保 Bitmap 加载后重绘

### 2. 阴影外扩 + 羽化200%
**根因**: 第5轮画的是内阴影（从右边缘向左）
**修复**: 改成外阴影（从右边缘向右延伸 200px*density），5-stop 渐变 `[20%,7%,1.5%,0.4%,0%]`

### 3. 轻滑: 动画→反弹→内容切页
**根因**: bounceBack 后 `direction` 保持 NEXT/PREV，`onAnimationComplete` 错误调用 shift
**修复**: `startBounceBack` 开始时清除 direction；`computeScroll` 仅当 `isFlipAnim=true` 时才 shift

### 4. 下一页偏左后回正
**根因**: 下层 parallax 导致动画结束时 `lowerX ≠ 0` → snap
**修复**: 下层完全静止 (`lowerX=0`)，移除 PARALLAX

### 5. 点击左右无翻页
**根因**: ReadView 未设 clickable；相邻槽位未加载时静默无反馈
**修复**: `isClickable=true`（与#1共修）

### 6. 滑一半反悔→错页
**根因**: direction 在首次 MOVE 锁定后永不更新
**修复**: 每次 MOVE 根据累计 `event.x - startX` 实时更新 direction

### 7. 主题背景不实时
**根因**: ThemeSettingsSheet 40% 黑色遮罩盖住全屏
**修复**: 遮罩透明度 0.4 → 0.1

### 8. 退出后不回原进度
**根因**: `pendingPageFraction` 死代码，从未用于设置 `currentPageIndex`
**修复**: `onNewEnginePageChanged` 首次回调时，若 `pendingPageFraction > 0` 则跳过 `saveProgress()`

### 9. 边距增大 (上下 ≈1.78x 左右)
**根因**: marginHoriz=24dp, marginVert=18dp 不够
**修复**: marginHoriz=28dp, marginVert=50dp

### 10. 章节标题加大加粗+空行
**根因**: 无标题格式化，plain text 直入 StaticLayout
**修复**: `getChapterText()` 用 SpannableString：首行 22sp 粗体 + 后空行；pipeline 改 String→CharSequence

## 修改文件

| 文件 | 改动 |
|------|------|
| `PageAnimationController.kt` | direction MOVE实时更新(累计dx)；bounceBack清direction+isFlipAnim；computeScroll仅flip时shift |
| `SlidePageAnim.kt` | 下层静止(0)；外阴影(200px*density, 5-stop)；移除PARALLAX |
| `ReadView.kt` | isClickable/isFocusable=true；marginHoriz 28dp, marginVert 50dp；onPageChanged加invalidate() |
| `PageSlotManager.kt` | contentProvider 类型 String→CharSequence |
| `ReaderViewModel.kt` | getChapterText标题SpannableString(22sp粗体+空行)；onNewEnginePageChanged首次不saveProgress |
| `ThemeSettingsSheet.kt` | 遮罩 alpha 0.4→0.1 |
| `PageLayoutEngine.kt` | layout() 参数 String→CharSequence |

## 编译

```bash
./gradlew assembleDebug  # ✅ BUILD SUCCESSFUL
```
