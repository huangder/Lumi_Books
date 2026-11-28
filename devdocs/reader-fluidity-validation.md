# 流畅度验收

## 真机对照

使用当前整合工作树构建的官方 debug 包。记录公开和 Premium 的 HEAD、未提交改动、设备型号、系统、分辨率和固定刷新率。前后沿用同一套书单、主题、开书动画和阅读设置，每个场景做两轮，每轮十次。关闭性能叠层和录屏后捕获 Perfetto 的应用追踪、线程调度、FrameTimeline 与图形渲染事件。另做一轮目视检查。

| 场景 | 操作 | 指标 |
| --- | --- | --- |
| 启动 | 结束进程后从桌面打开。暖启动保留进程并重建 Activity。分别测开屏开启和关闭 | `Displayed` 和 `Fully drawn` 的中位数、P90 |
| 开书 | EPUB 使用手机书架上的《晚唐浮生》，分别测阅读器排版和原书排版。另测大 TXT。每种排版分别测冷缓存、缓存重开和从同一中间位置续读 | `book_open_to_first_content`、`book_open_to_interactive` 的中位数、P90 和两者差值 |
| 书架快滑 | 同一个有封面的大书架，固定起点，快速上滑四次、下滑两次。对比玻璃采样原画与性能档 | 超时量 P95、超时帧比例。检查封面解码、RenderThread 和 GPU 合成 |
| 正文快滑 | 《晚唐浮生》从同一章内位置开始，连续滚动模式前进后反向，覆盖跨章 | 超时量 P95、超时帧比例、跨章空白、最终定位 |
| EPUB 快速连翻 | 《晚唐浮生》用原书排版和卷页，从固定起始页连续前进十二次、反向五次，核对手势数、最终页码和跨章结果 | 超时量 P95、超时帧比例、队列完成时间和最终页 |

启动耗时使用 Android 启动区间。开书阶段查看 `reader_metadata_parse`、`reader_first_chapter_decode`、`reader_pagination` 和 `reader_transition_exit`。系统启动首帧已经结束但内容仍未就绪时，检查 `Lumi.start.*`、Room 查询及 `Lumi.library.*`。快滑时把 FrameTimeline 的实际完成时间与该帧的截止时间相减，用正值统计超时帧比例。对照 `Lumi.scroll.chapter_measure`、`Lumi.scroll.chapter_prepare` 和 `Lumi.scroll.chapter_draw` 定位排版、准备或绘制耗时。

合并性能改动前复验首次欢迎、普通桌面入口、禁用开屏、从外部打开文件、续读锚点和跨章反向，记录每轮观测值。首次无基准值时先记录基线。Baseline Profile 和 Startup Profile 在发布配置上单独做 A/B。

## 2026-11-18 首轮实测

设备型号为 `24129PN74C`，采集时显示模式为 120 Hz。候选为官方 arm64 debug，公开与 Premium 均在 `codex/integration-current`。公开 HEAD 为 `0c77f612`，含流畅度和并行卷页的未提交改动。Premium HEAD 为 `d22eb227`，工作区干净。候选包位于 `app/build/official/lumi-fluidity/outputs/apk/debug/app-arm64-v8a-debug.apk`，SHA-256 前缀为 `3ddb1b0a`。

| 场景 | 样本 | 中位数 | P90 |
| --- | ---: | ---: | ---: |
| 旧包冷启动首帧 | 10 | 1,140.5 ms | 1,161 ms |
| 候选冷启动首帧 | 5 | 1,308 ms | 1,327 ms |
| 候选冷启动可交互 | 5 | 3,912 ms | 3,928 ms |
| 候选暖启动首帧 | 10 | 60 ms | 63 ms |
| 候选《晚唐浮生》续读到正文 | 3 | 1,831 ms | 待复测 |
| 候选《晚唐浮生》续读到可交互 | 3 | 2,040 ms | 待复测 |

冷启动首帧的候选样本偏慢，尚缺同条件双轮复测。旧包缺少可对照的 `Fully drawn` 数据。独立追踪中，首帧前的 `bindApplication` 区间在旧包平均 863 ms（6 次），候选平均 979 ms（5 次）。首屏绘制阶段还存在较长的 Compose 布局、重组和语义树处理。候选的启动路由、字典初始化与 WebDAV 调度的已标记区间合计每次约 2 ms。

《晚唐浮生》从首页继续阅读卡片进入，约在全书 2.2% 处。三次续读的 `reader_pagination` 区间平均 681 ms，覆盖元数据完成后的定位、分页和首帧等待。`reader_metadata_parse` 平均 389 ms，目标章解码平均 37 ms。正文到可交互的间隔中位数为 209 ms。下一轮用同一锚点复测两种排版，记录缓存命中、定位与实际排版各自的耗时。

此次追踪保存在本机临时目录，分别为 `lumi-cold-start-20261118-191950.perfetto-trace`、`lumi-cold-candidate-20261118-193229.perfetto-trace` 与 `lumi-resume-20261118-193832.perfetto-trace`。手机在 19:39:56 换装了并行任务的验收包。开书矩阵、快滑帧统计、回归和发布配置 A/B 待继续。

## 本地编译

定向单测用 `:app:testDebugUnitTest --tests` 选择 `BrandedSplashTimingTest`、`WelcomeLaunchStateResolverTest`、`StartupTraceTest`、`MainTabsPagerTest`、`ReaderFirstContentTest`、`ReaderOpenPerformanceTest` 与 `ContinuousReaderRecompositionTest`。共享代码分别使用默认官方配置与 `'-Plumi.oss=true'`。`:app:compileBenchmarkKotlin :benchmark:compileBenchmarkBenchmarkKotlin` 验证测量代码编译。真机验收使用官方 debug 和手工操作。

官方与 OSS debug APK 均构建成功，两版各通过 36 项定向单测，两项 benchmark 编译任务成功。OSS arm64 验证包位于 `app/build/oss/lumi-fluidity/outputs/apk/debug/app-arm64-v8a-debug.apk`。全量单测在两版均有失败，并卡在 `MarkdownImageAndRuleTest.httpRasterAndSvgImagesRenderAndFailuresKeepAPlaceholder`，已结束本地测试进程。
