# Bitmap + Canvas 翻页引擎（Phase 1-3 实现）

日期：2026-06-30

分支：`dual-webview`

## 背景

双 WebView + Compose graphicsLayer 跨章翻页方案经过 7 轮修复仍无法稳定工作。改为参考 HiReader/NovelReader 的成熟架构：**预渲染 Bitmap + Compose Canvas 翻页**。

## 核心思路

```
Chapter Text → StaticLayout 排版 → Canvas → Bitmap (预渲染)
                                            ↓
                                     PageCache (3-slot: prev/cur/next)
                                            ↓
                                     PageCanvas (Compose Canvas 绘制)
                                            ↓
                                     pointerInput 手势 (跟手拖拽/点击翻页)
```

**章节边界只是页索引边界**——翻过最后一页就是下一章第一页。动画层完全看不到章节边界。

## 新增文件

| 文件 | 作用 |
|------|------|
| `ui/reader/PageData.kt` | 一页数据：Bitmap + 章节/页码元数据 |
| `ui/reader/PageRenderer.kt` | 页面渲染器接口（per-format 实现） |
| `ui/reader/TxtPageRenderer.kt` | TXT 渲染器：StaticLayout 排版 → Canvas → Bitmap |
| `ui/reader/PageCache.kt` | 3-slot 缓存：prev/cur/next，shift 窗口 + 章节边界透明 |
| `ui/reader/PageCanvas.kt` | Compose Canvas + pointerInput 翻页组件 |
| `ui/reader/TxtReaderContent.kt` | TXT 阅读区：串联 renderer→cache→canvas |

## 修改文件

| 文件 | 改动 |
|------|------|
| `ReaderViewModel.kt` | + `getChapterText()` 方法（TXT 用），+ `updatePosition()` 方法（仅更新索引，不加载 HTML） |
| `ReaderScreen.kt` | 添加格式分支：TXT → TxtReaderContent，EPUB/PDF → 保持原 HtmlContent 双槽位 |

## 当前状态

- TXT 格式走新 Bitmap+Canvas 引擎（Phase 1-3 完成）
- EPUB/PDF 仍走原 WebView 双槽位（待后续迁移）

## 架构对比

| 维度 | 旧方案（WebView） | 新方案（Bitmap+Canvas） |
|------|------------------|------------------------|
| 页面渲染 | WebView + JS CSS columns | StaticLayout → Canvas → Bitmap |
| 翻页动画 | WebView DOM 操作 / Compose graphicsLayer | Canvas drawBitmap + offset |
| 手势 | JS touch 事件 → @JavascriptInterface | Compose pointerInput |
| 跨章翻页 | 双 WebView slot 交换 | PageCache shift（透明） |
| 预加载 | 隐藏 WebView loadDataWithBaseURL | 后台 preloadNextAsync() → renderPage |
| 调试 | Chrome DevTools（困难） | Logcat（简单） |
| JS 代码 | ~450 行 | 0 行（TXT 路径） |

## 验证

```bash
./gradlew compileDebugKotlin  # ✅ BUILD SUCCESSFUL
./gradlew assembleDebug       # ✅ BUILD SUCCESSFUL
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### 真机测试（TXT 格式）

预期行为：
- ✅ 章内拖拽跟手（手指滑动，当前页+下一页跟随）
- ✅ 跨章拖拽跟手（和章内一模一样）
- ✅ 点击左右侧翻页
- ✅ 点击中间区域呼出菜单
- ✅ 松手力度 > 25% → 翻页动画
- ✅ 松手力度 < 25% → 回弹动画
- ✅ 目录跳转 → 重新渲染目标章节
- ✅ 页码正确更新

### 已知局限

- EPUB/PDF 仍走旧方案（待后续迁移）
- 未处理快速连翻（连续多次快速拖拽）
- 未处理 Bitmap 内存回收（onTrimMemory）

## Round 8 修复（2026-06-30 晚）

真机测试反馈的 5 个问题：

### 1. 动画抽搐 + 松手回跳 ✅
**根因**：和双 WebView Round 7 相同的帧间跳变 — `isDragging = false` 同步切换导致 `derivedStateOf` 从 `dragOffset` 切换到 `slideAnim.value`（0），而 `snapTo` 在协程内异步执行。

**修复**：重写 PageCanvas 动画逻辑：
- `startAnimateTurn` 和 `handleDragEnd` 改为 `remember` lambda
- `isDragging = false` 移到 `slideAnim.snapTo(fromOffset)` 之后
- 消除 dragOffset → slideAnim 帧间跳变

### 2. ANSI/GBK 编码乱码 ✅
**根因**：`file.readText()` 默认 UTF-8，GBK 文件读出乱码。

**修复**：`TxtParser.readFileWithEncoding()` — BOM 检测 + UTF-8 → GBK → GB2312 降级尝试

### 3. 章节识别失败
部分原因是编码问题（修复 #2 应解决）。TxtParser 的 `splitChapters()` regex 支持常见中文格式（第X章/Chapter X 等），如果格式特殊则退化到固定大小分段。

### 4. 翻页动画完全不对 ✅
修复 #1 已解决抽搐，同时优化了动画起点逻辑：
- 拖拽松手：`slideAnim` 从手指位置开始动画（无缝）
- 点击翻页：`slideAnim` 从 0 开始动画
- 目标页 null → 回弹（不会卡住）

### 5. 屏幕边距太小 ✅
**根因**：`TxtPageRenderer` 文本 x=0,y=0 渲染，无内边距。

**修复**：添加 `_marginX` / `_marginY`（1.5x / 0.8x 字体大小），StaticLayout 宽度和 Canvas 绘制都减掉边距。
