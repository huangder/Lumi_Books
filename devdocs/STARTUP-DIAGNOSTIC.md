# 启动闪屏专项诊断

如果只有 release 闪屏，使用 **release + 启动日志开关**。`diagnostic` 的 debuggable、混淆、资源压缩及额外诊断与 release 不同，不能代替 release 复现。

当前工程已在 `gradle.properties` 临时开启 `lumiStartupTrace=true`，同步后按原方式打 release 即可，无需额外参数。排查结束后删除该行或改为 false，恢复普通发布配置。

命令行打包时，在原 release 命令后加 `-PlumiStartupTrace=true`，例如 `./gradlew.bat :app:assembleRelease -PlumiStartupTrace=true`（签名沿用原有配置）。
通过 Android Studio 打签名包时，在项目 `gradle.properties` 临时加入 `lumiStartupTrace=true`，同步后仍选择 **release** 打包；发完专项包后删除此行或改为 false。

开关仅启用 `STARTUP_TRACE_ENABLED`：release 仍关闭 debuggable、开启 R8 混淆和资源压缩，不改包名、版本后缀及签名，不启用 TTS/阅读等其他 diagnostic 专属采集，也不扩容原 release 日志缓冲。
不开开关的 release、普通 debug 和 benchmark 不采集专项事件。现有 `diagnostic` 始终开启专项日志，版本带 `-diagnostic` 后缀。
保持原 applicationId，并用用户已安装版本相同的证书签名；版本号应满足覆盖安装要求。无需清数据或卸载。
本地验证可运行 `:app:testReleaseUnitTest --tests "com.huangder.lumibooks.util.diagnostics.StartupTraceTest" :app:compileReleaseKotlin -PlumiStartupTrace=true`，不生成 APK、不安装设备包。JVM 测试并不等价于真机验证 R8 后的行为。

给用户的操作说明：

1. 覆盖安装。首次打开如果出现欢迎流程，请先正常完成。
2. 确认开屏动画关闭，结束应用，再从桌面图标打开并复现闪屏。
3. 复现后尽快进入反馈中的诊断页面，点击“导出启动闪屏日志”，把 ZIP 分享给开发者；无需提前点击开始抓取。可附一段录屏。

安装后应能看到“启动闪屏日志”和“导出启动闪屏日志”；如果没有，先核对是否安装了新包。专项 ZIP 应包含 `startup-summary.md`，且 `manifest.json` 中 `startupTraceEnabled=true`、`startupOnly=true`。ZIP 名称里的 diagnostic 只是通用导出文件名，不表示专项追踪已启用。

日志仅本地保存，沿用各构建的有界轮转：release 内存最多 4000 条/2 MiB、当前及上一份磁盘日志各约 2 MiB；diagnostic 为 8000 条/8 MiB。启动导出筛选最近一小时仍保留的事件（release 最多 2000 条，diagnostic 最多 8000 条），独立于手动抓取会话；长期高频日志可能挤出早期记录，建议复现后立即导出。

ZIP 的 `manifest.json` 包含 `buildType`、`diagnosticBuild`、`startupTraceEnabled`、`debuggable`，每个进程的 `process_started` 也带构建标记，便于区分覆盖安装前后的日志。开启日志仍可能影响启动时序；如果 release 加日志后也不闪，记录这一对比，不据此宣称问题已修复。

先看 ZIP 的 `startup-summary.md`，再看 `events.ndjson.gz`。每个进程有独立 processId，sequence 确定进程内顺序，elapsedMs 是系统单调时钟。Activity instance/taskId 区分同进程内重入与恢复。
重点比较 launcher_entry 的 component、launcher_components 切换前后状态、welcome_route_decision、persisted_welcome_observed 的 mirrorMismatch、main_splash_decision 和 first_draw_callback。
持久化状态对照在后台现有观察流中进行，不为诊断增加同步读取。时间不同的缓存差异只作为线索，不能直接证明路由时读到了错误值。

`composition_enter` 只证明组件进入组合；`first_draw_callback` 证明应用执行了绘制回调，不能保证系统最终呈现该帧。没有页面绘制记录时，系统启动窗口、桌面缓存或任务快照是待调查方向，不能仅凭日志下结论，需结合录屏。启动追踪失败不改变路由，缺失记录不能证明从未显示。
Intent 仅记录固定的 action 白名单、应用内组件、flags、桌面分类与开屏布尔字段，不输出 data、ClipData、文件路径或任意 extras。
