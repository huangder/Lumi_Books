# Markdown 原生阅读

## 阅读路径

Markdown 保留 `MARKDOWN` 格式，支持 `.md` / `.markdown` 导入、分享和授权目录。阅读路径为 CommonMark 0.24 → 紧凑文档索引 → Markwon 4.6.2 → 原生视图；不生成阅读用 HTML/EPUB，不创建 WebView。原有 `MarkdownRenderer` 继续服务 EPUB 导出和 MinerU。

`MarkdownDocument` 保存单个逻辑章节、H1–H6 大纲、唯一标题锚点、块的源范围和 UTF-16 可见文本范围。首次语义解析在 IO 线程进行，正文和全局引用解析完毕后释放 AST，按 SHA-256 与解析版本保存私有二进制缓存。重复标题采用递增计数，巨大段落切片按有序样式范围推进，避免反复从头扫描。

渲染块默认上限 4096 字符，公式保持完整（最长 16384 字符）；分块不会切断代理对。段落、列表编号、嵌套层级、代码注释/字符串上下文和引用目标随块保留。Markwon 仅处理块内重建的节点，不修改缓存 AST。

## 原生布局与主题

- `MarkdownBlockSource` 按需生成 Spannable；`MarkdownPageProvider` 只建立块级 StaticLayout。首屏不等待全书分页，后台只保留页边界。
- 分页逐块填充，连续滚动使用 RecyclerView。正文位置通过文本行恢复；页面目录、搜索和定位使用同一 UTF-16 坐标。
- 滑动/卷页使用已有 `PageAnimationSurface` 和动画控制器，过渡只持有两张视口快照。减少动态效果和电子墨水设置关闭动画。
- 字体、字号、行距、段距、缩进、对齐、颜色使用阅读器设置；Markdown 使用原生排版设置分组。纯颜色变化复用已完成的分页边界。
- 保存的批注复用 EPUB 阅读器的圆角高亮和划线绘制，支持直线、双线、波浪线、虚线；改色及切换色板直接刷新正文，不重新分页。
- 批注刷新覆盖 RecyclerView 中已挂载的文本块，不依赖 Compose 宿主的全局可见区域。每块底部为划线预留空间；满列分割线的高亮圆角限制在文本视图内，避免被裁切。长按由原生 surface 独立消费，移动超过阈值后交还滚动。
- 表格按行布局、内部横向滚动，过高的行按单元格文本行续排并重复短表头。代码默认换行，Prism4j 按需高亮常见语言，未知语言保留普通代码。
- Coil 2.7 按视口尺寸加载快照、data 和 HTTP/HTTPS 图片，支持 SVG；缺失或加载失败的图片显示占位。JLatexMath 在串行后台任务中生成公式，失败时保留源码；块公式可横向滚动，复制返回 LaTeX 源文本。
- 渲染缓存和布局缓存各按估算字节限制为 24 MiB；表格布局最多缓存 16 块。退出时取消渲染/分页任务、清空缓存及视图。

默认 Lumi、Material3、Liquid Glass 共用 `ReaderScreen` 的菜单、选区操作、链接确认、更新提示和错误弹窗。玻璃使用现有能力检测和背景采样；选区强调色使用 `AppColors.Accent`，电子墨水使用正文色。

## 存储、迁移与同步

快照继续使用 `books/markdown/<book-id>/<manifest-hash>/document.md`，同目录保留 `manifest.json` 和 `assets/`。导入仅抽取 Markdown 元数据和资源，不生成完整 HTML。网络图片和越界路径不进入快照；普通单文件授权无法保证读取相邻资源。

打开时检查源文件，更新前让用户确认；拒绝更新或来源不可用时继续旧快照。WebDAV/备份继续使用包含原文与本地资源的 `.mdpack`，书架身份保持 Markdown。

原生 locator 使用 version 3、format `markdown-native`、href `document.md`，包含文本位置、摘录和前后文。首次打开及阅读中恢复的旧批注会在 Room 事务中迁移；每次迁移共享一份规范化索引。匹配失败保留摘录、笔记和书签并标记 `isStale`。缺少可验证摘录的旧书签保守标记 stale，阅读进度则允许按比例恢复。迁移可重试且不会重复改写已迁移记录。

第三方许可证、JLatexMath 链接例外与字体通知见 `app/src/main/assets/licenses/markdown-native/`。

## 验证方法与尚待验收

本地执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:assembleDebug
```

覆盖语法与坐标、标题、引用、公式/图片、重复渲染、表格续页、巨大段落与代码、1/5/20 MiB 文档、缓存重开、迁移幂等/stale/同步旧记录、资源包和主题设置。原生界面冒烟测试确认未创建 WebView，并检查分页转连续滚动的正文位置。

桌面 JVM 的一轮索引测试（不是手机首屏成绩）：

| 源大小 | 块数 | 冷解析+写缓存 | 重读缓存 |
|---|---:|---:|---:|
| 1 MiB | 2016 | 480 ms | 76 ms |
| 5 MiB | 10061 | 1082 ms | 119 ms |
| 20 MiB | 40241 | 2120 ms | 367 ms |

真机无线调试在本次验证中断开，尚未安装此次重建的 debug 包。还需真机验收现有 5.49 MiB 文件的冷/热首屏、帧耗时、峰值/遍历后内存，以及图片、横向表格、公式、跨页选择、TTS、字体/旋转恢复、两种主题弹窗和两设备 WebDAV。

剩余边界：连续滚动中的表格恢复目前定位到行块；跨页选区手柄和卷页尚无真机手势验证；图片预览与脚注气泡尚未复用，脚注当前采用文内跳转。不能把本地语法/布局测试等同于完整性能及交互验收。

只允许安装 debug APK，不安装 test APK，不运行 connected 测试、不卸载应用或清空数据。

## 本轮结果（2026-10-04）

- 最终 Markdown 与原生界面/主题分组 30 项测试通过；`:app:compileDebugAndroidTestKotlin` 和 `:app:assembleDebug` 通过。
- 全量 1642 项中 7 项失败、1 项跳过。失败为现有 `ContinuousScrollReaderTest.comicImagePixelsUpdateWithoutMovingOrResizingThePage`、`ReaderImageAdjustmentsTest.preferencesSurviveNewReaderAndStayScopedToBook`、`ReaderPunctuationLayoutTest` 的 `continuousAndPagedInkAreIdenticalWithOneLayout` / `indentedParagraphsKeepBothModeInkInsideReaderMargins`（各两个 SDK）、`TxtTitlePageRenderingTest.chapterTitleStaysLeftAlignedInEveryAlignmentMode`。全量尚未通过。
- 最后一次 1/5/20 MiB 样本冷解析+缓存分别为 329/509/1667 ms，重开为 52/112/369 ms。
- 极端单段落（大量内联加粗/链接）和巨大代码的组合约 2.49 MB，生成 418 个有界块，但冷解析仍达 11.65 秒；这说明“任何大 Markdown 都能快速首屏”的目标尚未达到，仍需对 CommonMark 语义解析阶段进一步做性能剖析。
- 本次 debug 产物：`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`。设备断连，未安装。
- 日志：`app/build/full-validation.log`、`app/build/markdown-validation.log`。
