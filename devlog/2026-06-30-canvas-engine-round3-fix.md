# Canvas 引擎第3轮修复 — 主题/空白页/动画层级

日期：2026-06-30

## 问题

1. **主题切换不实时**：遮罩层（Compose）正常变，内容区翻几页才变
2. **大量空白页**：有些章节页面全白
3. **字飞天/点屏幕消失**：文字位置错乱，点击后内容消失
4. **动画变平移**：没有原来 WebView 的层级感，变成纯平移

## 根因与修复

### 1. 主题切换延时

`PageSlotManager.initialize()` 调用 `loadSlot(SLOT_CUR, sameCh, samePg)`，但 `loadSlot` 检测到 `slot.isLoaded == true` 且 `chapterIndex == sameCh`，early-return 跳过重渲染。用户翻页后新槽位不同才触发新渲染，所以"翻几页才变"。

**修复**：`initialize()` 开头强制清空所有槽位（`isLoaded = false`，回收 Bitmap），`loadSlot` 不再 early-return。CUR 加载完成后自动预加载 PREV/NEXT。

### 2. 空白页 + 字飞天/消失

多条链路叠加：
- `resolvePrevPage()` 遇到未缓存章节时返回 `prevCh to 0`，但 page 0 可能不存在（章节文本为空或异常）
- shiftForward/Backward 不检查目标槽位是否真的加载了有效 Bitmap，直接把 null bitmap 转到 CUR 槽位 → 空白页
- 用户点击后 `dispatchDraw` 重绘 → `animationController.onDraw` → `getCurBitmap()` 返回 null → 空白

**修复**：
- `loadSlot` 加边界检查：`pageInChapter >= chapterLayout.totalPages` 时不标记 `isLoaded`
- `shiftForward/Backward` 加防护：目标槽位无有效 Bitmap 时阻止转移，保持当前页
- `PageAnimationController` 加 `onCanFlip` 回调：翻页前检查目标槽位已加载，否则回弹

### 3. 动画变平移 — 缺少层级感

`SlidePageAnim` 原来两页用同一个 `offsetX`，速度完全一致，无深度感。

**修复**：目标页用 quadratic ease-out 曲线 (`1-(1-p)²`)，当前页保持线性。效果：
- 目标页先快后慢，模拟"从底层滑入"
- 当前页全速滑动，模拟"顶层抬起"
- 产生明显的前后层级关系

## 修改文件

| 文件 | 改动 |
|------|------|
| `PageSlotManager.kt` | `initialize()` 强制清空槽位；`loadSlot()` 加页边界检查；`shiftForward/Backward` 加 null bitmap 防护 |
| `SlidePageAnim.kt` | `onDraw()` 用 quadratic ease-out 实现目标页视差层级动画 |
| `PageAnimationController.kt` | 新增 `onCanFlip` 回调，翻页前校验目标槽位 |
| `ReadView.kt` | 接线 `onCanFlip` 到 slotManager 的 isLoaded 检查 |

## 编译

```bash
./gradlew assembleDebug  # ✅ BUILD SUCCESSFUL
```
