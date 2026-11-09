# 连续短章节交接修复（2026-11-08）

封面、目录、说明等单页章节会很快耗尽已准备页面。本次修复保留正在显示的文档，等待目标完成排版和实际绘制，再交接页面。

## 修改

- 平移与纵向分页：相邻槽尚未分配时也保留输入并主动请求目标，不回退到活动 WebView 直接翻页。
- 波浪分页：未准备目标、动画期间的点击与滑动沿用等待队列。
- 仿真分页：当前截图尚未完成时也保留输入；移除 260ms 无进展即直接翻页的短超时。超过导航超时仍无法准备的跨章操作通过离屏导航恢复。
- 淡入淡出、无动画与连续滚动：同来源、同目标的重复跨章请求合并，避免 stopLoading 后重新准备同一章。普通跨章加载提示改为底部紧凑提示，保留原页面。
- 连续滚动章间移动保持不透明，静止淡入淡出在完整旧页上显示完整新页，避免露出底色。章末拖动脚本也保持 opacity=1，避免等待时停在低透明度。
- 调试入口通过 EpubNavigationRequest 驱动跨章，匹配正式阅读界面的离屏导航；不保存书库阅读进度。

原有排版、头图与背景边距豁免、脚注图片和点击交互继续保留。本次只修改公开代码，未修改 Premium 实现。

## 验证

- 最终 official、OSS 均 assembleDebug 成功，各 84 项相关 JVM 测试通过，0 失败、0 跳过。包含 7 项冷章交接测试、实际页面合成像素检查及三本本地 EPUB 解析。未运行全量测试或设备仪器测试。
- 浏览器检查通过：章末拖动与等待保持不透明；正文边距遮罩、原排版背景和头图显示正常。
- 真机第一阶段：三本书 × 七种模式，共 21 组有效运行；100ms API 输入或 200ms 滑动。活动章节重载为 0，原生动画未回退 JS，导航均成功。
- 修正原生动画透明度后：三本书 × 淡入淡出/连续滚动，共 6 组有效运行。录屏确认深色页面的交接亮度改善。
- 最后补充的脚本拖动 opacity 修正已通过 JVM/浏览器验证并安装；最终真机滚动复测因手机被用于其他操作而无效，已停止，不能计为通过。
- 录屏与日志属于抽样检查，不能证明所有书籍和每一帧均无闪屏；首次打开阅读器的初始加载不在这次章间交接保证范围内。

## 最终安装来源

- Edition：official debug；使用 :app:installDebug 覆盖安装，未卸载、清除数据或安装 test APK。
- 公开工作树：C:/Users/Huangder/.codex/worktrees/cloudflare-license/android_books
- 公开分支：codex/integration-current；HEAD：3e48dfea93740fdc19cc9faffe837f0fd121fd97。
- Premium 工作树：上述目录下 app/src/premium；分支：codex/integration-current；HEAD：7e2150cda89ee356d42aecd69b3fd166ef0203e2。
- 两边均有既有未提交改动；构建使用当前完整工作区，保留暂存区。本任务没有提交或推送。
- 已核对多工作树状态与相关实现；当前汇总源码保留小组件、笔记导出、五天试用及已有 EPUB 修复。
- APK：C:/Users/Huangder/.codex/worktrees/cloudflare-license/android_books/app/build/official/outputs/apk/debug/app-arm64-v8a-debug.apk。
- SHA-256：bddac4de25001159ec068598155ae5315d1fa0acbbc44fcceff45f807f843ad5。
- 构建、测试、任务前快照及设备检查记录：.local/duokan-implementation/short-chapters/（本地忽略目录）。
