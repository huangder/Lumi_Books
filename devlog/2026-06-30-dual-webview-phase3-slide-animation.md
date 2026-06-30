# 双 WebView 方案 Phase 3：Compose slide 动画

日期：2026-06-30

分支：`dual-webview`

## 目标

实现 Compose Animatable + graphicsLayer.translationX 驱动的跨章 slide 动画，替代 JS DOM 操作。

## 补充修复

### 最后一页拖拽闪烁

**根因**：`aDrag` 在章节边界（最后页向前 / 第一页向后）时，`target = cur ± 1` 越界（如 `target = total`），botLayer 的 clone 被定位到 `translateX(-total * vw)` 超出内容区域，显示空白/闪烁。

**修复**：`atBoundary` 时仅显示 topLayer（当前页 clone + 橡皮筋），隐��� botLayer（`display:none`），不创建越界 clone。

## 变更

### ReaderScreen.kt

#### 状态变量

```kotlin
var activeSlot by remember { mutableStateOf(0) }   // 当前活跃槽位
var chapterInSlot0 by remember { mutableStateOf(...) }
var chapterInSlot1 by remember { mutableStateOf(-1) }
var slot0Ready / slot1Ready                         // WebView 加载完成标志
var pendingTransitionDir                            // 待执行的翻页方向
var hiddenSlotSide                                  // 隐藏槽位位于哪侧（1=右/-1=左）
val slideAnim = remember { Animatable(0f) }        // 驱动两个槽位的 translationX
val slot0Html / slot1Html                           // 每个槽位的 HTML
val slot0WebView / slot1WebView                     // 每个槽位的 WebView 引用
```

#### 双桥接

| Bridge | Slot | handlePaginationComplete | handleChapterFlipReady |
|--------|------|--------------------------|------------------------|
| bridge0 | 0 | 正常流程（跳页、loading） + 标记 ready | 预加载隐藏槽位 + 触发动画 |
| bridge1 | 1 | 标记 ready + 触发动画 | 同 bridge0（Slot 1 活跃时需此功能） |

#### Slide 动画流程

```
1. 用户拖拽/点击到章节边界
   → JS onChapterFlipReady(dir)
   → bridge handleChapterFlipReady
   → preloadHiddenSlot(adj, dir)
     → 加载相邻章节 HTML 到隐藏槽位
     → pendingTransitionDir = dir
     → hiddenSlotSide = dir>0 ? 1 : -1
     → checkAndAnimate()

2. 隐藏槽位 WebView 分页完成
   → bridge handlePaginationComplete
   → slot{0/1}Ready = true
   → checkAndAnimate()

3. 动画执行（300ms FastOutSlowInEasing）
   → slideAnim.animateTo(dir>0 ? -1f : +1f)
   → 活跃槽位: translationX = anim * screenW   (0→-W 或 0→+W)
   → 隐藏槽位: translationX = hiddenSide*W + anim*W  (W→0 或 -W→0)

4. 动画完成 → 交换槽位
   → activeSlot 切换
   → slideAnim.snapTo(0f)
   → viewModel.onChapterSwapped(dir)  更新 ViewModel 章节索引
   → preloadHiddenSlot(nextAdj, dir)  预加载下一个相邻章节
```

#### 布局

```kotlin
Box(Modifier.fillMaxSize().graphicsLayer {
    translationX = if (activeSlot == 0) slideAnim.value * screenW
                   else hiddenSlotSide * screenW + slideAnim.value * screenW
}) {
    HtmlContent(html = slot0Html, bridge = bridge0, ...)
}

Box(Modifier.fillMaxSize().graphicsLayer {
    translationX = if (activeSlot == 1) slideAnim.value * screenW
                   else hiddenSlotSide * screenW + slideAnim.value * screenW
}) {
    HtmlContent(html = slot1Html, bridge = bridge1, ...)
}
```

#### TOC/滑块跳转处理

当用户通过 TOC 跳转到完全不同的章节时：
- LaunchedEffect 监听 `uiState.currentChapterIndex` 变化
- 仅当 `activeSlot == 0` 时重置 Slot 0 HTML
- 清除 Slot 1 预加载状态

## 验证

```bash
./gradlew compileDebugKotlin  # ✅ BUILD SUCCESSFUL
./gradlew assembleDebug       # ✅ BUILD SUCCESSFUL
```

### 预期行为

- ✅ 章内翻页正常（Phase 1 修复版）
- ✅ 最后一页拖拽不闪烁
- ✅ 跨章 slide 动画（300ms ease）
- ✅ Slot 预加载 + 交换
- ✅ TOC 跳转重置双槽位

### 已知限制

- 快速连翻可能有问题（Phase 5 处理）
- 字体/主题同步到两个槽位（Phase 5 处理）
- 首章/末章边界（已在 handleChapterFlipReady 中校验范围）

## 涉及文件

- `app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt` — 主要修改
