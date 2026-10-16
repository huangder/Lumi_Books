# Markdown 大文档滚动诊断（2026-10-05）

## 真机证据

设备：24129PN74C，Android 17；debug 包；用户已打开的约 5.49 MB 测试文档。

ART 1ms 方法采样中，主线程记录约 2004ms CPU 时间。其中 `updateMarkdownTocPosition` 约 1553ms，`MarkdownParser.getHeadingOffsets` 约 1545ms（包含子调用）。这不是首屏解析耗时，也不是完整会话的墙钟占比；方法采样本身有开销。

`headingOffsets` 原来是计算属性，每次读取都会执行 `outline.associate`，重建整篇文档的 HashMap。当前标题匹配又在目录遍历的条件中读取它：一次进度更新可能多次重建全文索引。采样中的 Pair、Integer、HashMap/LinkedHashMap 节点分配与此相符，设备日志也记录了密集 GC。

原进度发布条件是“偏移变化超过阈值 **或** 距离上次发布超过 80ms”，因此快速滑动时几乎每帧都会进入这一调用链，并立即保存进度。

无方法采样的六次滑动对照：修复前记录 142 帧，中位 57ms，p90/p95 300ms，p99 350ms，Slow UI thread 33。只代表该文档当前位置的短时 debug 测试，不代表所有文档或发布版性能。

安装修复 debug 包后，自动恢复同一文档；缓存读取日志为 `warm=true bytes=5754665 blocks=17045 ms=222`。使用相同六次滑动及结束后 3s 等待：记录 550 帧，中位 16ms，p90 28ms，p95 32ms，p99 53ms，Slow UI thread 0。新帧截止统计 0/550 超时；legacy 统计仍为 110/550（20%），因此不能称为稳定满帧。前后帧数差异来自阻塞程度、惯性滚动及调度；没有保证逐帧场景或恢复位置完全一致，结果应视为短时诊断对照。

修复后的进程 PSS 约 377 MiB、RSS 约 547 MiB，WebViews 仍为 0；重启后的缓存与进程状态不同，不可把前后 PSS 当作内存优化收益。本次改善来自滚动调用链，尚无证据称整个进程内存已下降。

再次采样六次滑动后，全文标题映射 getter 已不出现在滚动调用栈中，`headingAnchorAt` 约 1.05ms、`updateMarkdownTocPosition` 约 5.22ms，后者原采样为约 1553ms。两次采样时长和事件数不同，这些累计时间只用于确认热点消失，不能算成严格性能倍率。剩余主线程耗时分散在 Compose 重组、输入处理和绘制：Compose 重组约 448ms（整个主线程采样累计约 1491ms）。若继续追求高刷新率，应针对进度状态驱动的重组范围进行优化；没有发现新的全文解析/目录索引重建热点。

## 本次修复

- 在后台解析/读取缓存后，一次性建立标题锚点映射和用于阅读目录的有序标题列表。
- 当前目录项使用文本偏移二分查找，滚动时不重建全文索引，也不遍历整个目录。
- 复用一个进度 Runnable，通过帧回调合并，再延迟至 80ms 时间间隔到期；松手提交最终位置，没有位置变化则不重复发布。
- Markdown 的进度写库复用已有防抖保存机制，离开阅读器/退后台仍使用即时保存。
- 修正表格多行合为一个条目后，Adapter 条目索引与文档块索引混用的问题。
- RecyclerView 首次定位至远处位置时，允许绑定当前条目，避免尚无可见位置时被预取条件挡住而留下空白占位。
- 重排、移除和退出阅读取消未执行的进度回调。

## 开源实现对照

以下结论来自源码结构，不是对这些项目做过同一 5 MB 样本的性能测量。

| 项目 | 源码中实际采用的策略 | 对 LUMI 的意义 |
| --- | --- | --- |
| Markwon | `MarkwonAdapterImpl` 将解析节点拆成 RecyclerView 条目，使用稳定 ID，按节点类型创建/绑定 Holder；Recycler 文档说明 SimpleEntry 缓存节点渲染结果 | 长文应使用分块条目及复用，不能把全文塞入一个 TextView；使用 Markwon core 本身并不会自动获得 Recycler 虚拟化 |
| mikepenz renderer | `LazyMarkdownSuccess` 在 LazyColumn 中显示顶层 AST 节点；remember 节点列表，startOffset 为 key，节点类型为 contentType | 只组合可见节点，并按类型复用；巨大单段/单表仍需独立解决有界排版问题 |
| compose-markdown | MarkdownText 使用 AndroidView/CustomTextView 和 Markwon；remember 内容 key，但 AndroidView update 仍会 resetTextState 和 setMarkdown | 它是 Compose 封装，单组件渲染整篇并无长文虚拟化保证；不适合作为本次卡顿的直接替代方案 |
| Pilcrow | Preview 使用 markwon-recycler，每个顶层块一个条目；代码/表格整体横向滚动，竖向由 RecyclerView 持有；保存条目及局部偏移，配置/正文改变时重建 | 架构参考有价值；滚动位置更新与重新解析/重建内容分开。只阅读架构，未复制 GPL 源码 |

来源：

- [MarkwonAdapterImpl](https://github.com/noties/Markwon/blob/HEAD/markwon-recycler/src/main/java/io/noties/markwon/recycler/MarkwonAdapterImpl.java)
- [Markwon Recycler 文档](https://github.com/noties/Markwon/blob/HEAD/docs/docs/v4/recycler/README.md)
- [LazyMarkdown](https://github.com/mikepenz/multiplatform-markdown-renderer/blob/HEAD/multiplatform-markdown-renderer/src/commonMain/kotlin/com/mikepenz/markdown/compose/LazyMarkdown.kt)
- [compose-markdown MarkdownText](https://github.com/jeziellago/compose-markdown/blob/HEAD/markdowntext/src/main/java/dev/jeziellago/compose/markdowntext/MarkdownText.kt)
- [Pilcrow Preview](https://github.com/pilcrowmd/pilcrow/blob/HEAD/app/src/main/java/com/pilcrowmd/ui/components/Preview.kt)

## 验证

回归测试覆盖索引复用、冷/热打开后的标题边界、脚注不成为当前目录标题、换文档时清空索引；真实 RecyclerView 连续滚动的进度合并/最终位置、退出后停止回调，以及表格之后的远处定位与进度坐标。

本地执行 `:app:testDebugUnitTest`、`:app:compileDebugAndroidTestKotlin`；设备只安装 debug 包，不安装测试 APK、不运行 connected 测试、不卸载应用。

结果：Markdown/放大镜相关 47 项测试通过，AndroidTest Kotlin 编译及 assembleDebug 通过，installDebug 安装成功。全量运行 1659 项，7 项失败、1 项跳过；失败包括 ContinuousScrollReader 漫画图片高度、ReaderPunctuationLayout 的两模式墨迹/边距（两个 SDK 配置）、TxtTitlePageRendering 标题对齐，以及 ReaderImageAdjustments 设置读取超时。这些路径未在本次改动中修改，尚未验证它们在改动前的基线，不将全量结果标为通过。
