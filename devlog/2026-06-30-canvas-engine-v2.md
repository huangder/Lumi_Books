# Canvas 翻页引擎 v2 — 底层重构

日期：2026-06-30

## 背景

WebView 3槽位方案经过 7+ 轮修复，跨章翻页仍无法无缝跟手。根因是架构层面：章内翻页用 JS WebView，跨章翻页用 Compose Animatable，两者通过 JS Bridge 通信，存在固有延迟。

**这次从底层改起**：用 Android 原生 View + Canvas + StaticLayout + Scroller 完全替代 WebView。

## 方案

参考 Legado 的成熟架构（仅参考阅读引擎，不参考 JS 书源/网络代码）：

| 维度 | 旧方案 | 新方案 |
|------|--------|--------|
| 渲染 | WebView + JS CSS columns | StaticLayout + Canvas → Bitmap |
| 槽位粒度 | **章节级**（3个WebView=3章） | **页级**（3个PageSurfaceView=3页） |
| 跨章翻页 | JS→Bridge→Compose Animatable→graphicsLayer | 页索引越过章节边界，动画层透明 |
| 动画 | Compose Animatable | Android Scroller（不经过Compose重组） |
| 集成 | 纯 Compose | AndroidView 包装原生 View |

### 核心创新：全局页码索引

```
Chapter 0: pages [0, 1, 2, 3, 4]     (5 pages)
Chapter 1: pages [5, 6, 7, 8, 9, 10] (6 pages)

翻页只是 globalPageIndex ± 1
动画层不知道章节边界在哪
globalToLocal(5) → (chapterIndex=1, pageInChapter=0)
```

### 动画效果

保留与当前一致的视觉：
- 跟手拖拽：手指滑动时两页 Bitmap 跟随
- 松手翻页：300ms Scroller 动画（PathInterpolator 模拟 FastOutSlowInEasing）
- 松手回弹：200ms 回弹
- 阴影：LinearGradient 渐变阴影在页面边缘

## 新增文件

`app/src/main/java/com/ebook/reader/ui/reader/engine/`：

| 文件 | 作用 |
|------|------|
| `PageLayout.kt` | 页元数据：章节索引、页内索引、行范围、字符偏移 |
| `ChapterLayout.kt` | 章布局：StaticLayout + PageLayout 列表 + 总页数 |
| `PageLayoutEngine.kt` | StaticLayout 分页引擎，全局↔局部页码转换，LRU 缓存(5章) |
| `PageRenderer.kt` | PageLayout → Bitmap 渲染，Bitmap 复用池(最多6个) |
| `PageSurfaceView.kt` | 自定义 View，onDraw 绘制单页 Bitmap |
| `SlotState.kt` | 槽位状态数据类 |
| `ReadViewCallbacks.kt` | 回调接口：onPageChanged、onMenuToggle、onLoadingChanged |
| `ReadView.kt` | 核心 FrameLayout：3个 PageSurfaceView，触摸分发，生命周期 |
| `PageSlotManager.kt` | 3槽页级 conveyor belt：加载/卸载/移位，预加载策略 |
| `PageAnimationController.kt` | Scroller 动画基类：触摸处理、computeScroll |
| `SlidePageAnim.kt` | 具体实现：水平视差滑动动画 |

## 修改文件

| 文件 | 改动 |
|------|------|
| `ReaderScreen.kt` | **大量简化**：删除 1400+ 行 WebView/JS/Bridge 代码；添加 AndroidView(ReadView)；保留所有 Compose 覆盖层 UI；PDF 保留简化 WebView 路径 |
| `ReaderViewModel.kt` | 添加 `onNewEnginePageChanged()`、`globalPageIndex`、`useNewEngine` 字段；TXT/EPUB 自动走新引擎 |

## 删除文件

`PageCanvas.kt`、`TxtReaderContent.kt`、`TxtPageRenderer.kt`、`PageRenderer.kt`(接口)、`widget/TxtPage.kt`、`widget/PageView.kt`、`widget/PageAnimation.kt` — 全部被新引擎替代

## 编译

```bash
./gradlew compileDebugKotlin  # ✅ BUILD SUCCESSFUL
./gradlew assembleDebug       # ✅ BUILD SUCCESSFUL
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## 真机测试要点

1. TXT 章内拖拽跟手（手指滑动，页面实时跟随，无 Bridge 延迟）
2. TXT 跨章拖拽跟手（和章内体验一样——动画层无章节概念）
3. EPUB 跨章拖拽跟手
4. 点击左右侧翻页（左1/3上一页，右1/3下一页）
5. 点击中间区域呼出菜单（覆盖层不变）
6. 松手力度 >25% → 翻页动画；<25% → 回弹动画
7. 目录跳转（TOC）正确加载目标章节
8. 页码同步更新（底部页码指示器）
9. 字体大小切换 → 重新分页

## 已知局限

- EPUB 图片暂不支持（getChapterContent 返回纯文本，图片信息丢失）
- PDF 保留旧 WebView 路径（PdfRenderer 渲染图片，不适合 StaticLayout）
- 字体/主题切换后可能跳到章节开头（需后续优化位置恢复）
