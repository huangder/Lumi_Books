# 多看 EPUB 内容适配

2026-11-08。基于[APK 静态研究](duokan-apk-reverse-research.md)与用户提供的三本 EPUB，在 Lumi 现有 EPUB 章节加载、CSS 和交互桥接中实现格式兼容。没有引入多看原生库、反编译实现、字体或书籍资源。

## 已实现

- `img.duokan-footnote` 且具有非空 `alt`、不在链接内时，以纯文本注释气泡显示。气泡在 `body` 外，不新增正文节点；短按不触发翻页，长按不触发图片预览。普通头像/插图的 `alt` 保持原用途。
- 链接图片脚注继续使用链接目标的富文本内容；补充识别老式 `type="noteref"` / `type="footnote"`。沿用现有关闭、定位与资源读取机制。
- 多看解谜等整幅图片也可以是注释入口。发布方明确声明为插图且占较大相对宽度的图片，保留块级尺寸和分页保护；只将真正的行内图标缩为脚注标记。WebView 与原生重排共用这一判定，避免把 `width="100%" class="duokan-image-static"` 的大图缩成约 19 像素。
- 保留正文行内图片、聊天头像、公式的发布方高度及行内布局，避免分页 CSS 的统一 `display:block` / `height:auto!important` 覆盖。
- 浮动插图按当前视口、真实 CSS 计算尺寸。宽度含外边距不超过正文列的 55%、且高度适合一页时保留环绕；过大或尺寸不适合时使用原有整块回退。浮图内部的 `width:100%` 不再误触发整页贴边。
- 读取六种 `duokan-page-*` 章节属性。fullscreen、fitwindow、interactive、spread-left/right 使用章节级全窗口展示；保持原来的 reflowable/FXL 类型。spread-left/right 不等同于 EPUB 左右页定位，也不推断图片需要裁半。vertflow 只保留元数据，未推断其未证实的分页语义。
- 实验性原生 CSS 路径遇到多看页面属性或脚注时，按章回到支持这些交互的 WebView。
- 对缺失的光栅图片引用，仅当同目录同文件主名在 manifest 中有唯一存在的光栅图片时补偿扩展名错误；准确路径优先，歧义、跨目录、未声明资源不补偿。返回实际 MIME，图片预览共用同一解析。

这些改动属于公开阅读器。Premium 实现未因本任务修改；构建包含汇总工作树原有的两侧未提交改动。

## 验证方式

本地 JVM 测试覆盖章节属性、双页前后翻、行内标记、正文/图片 UTF-16 坐标、原生回退、图片 MIME 与预览、唯一资源修复及歧义拒绝。

`node tools/test-epub-duokan-compat.cjs` 提取生产 CSS/JS，在桌面 Chromium 检查分页/滚动、两种窗口尺寸及全窗口页：头像高度、浮图回退、图片/链接脚注、触摸后合成 click、气泡关闭、普通图像预览和注释前后索引。结构标记另由 JVM 测试覆盖。

私有实书验证可设置以下环境变量后运行 `:app:testDebugUnitTest --tests '*EpubDuokanLocalCorpusTest'`：

- `LUMI_DUOKAN_TEST_BOOKS`：用系统路径分隔符连接的 EPUB 绝对路径。
- `LUMI_DUOKAN_EXPORT_DIR`：可选，本机忽略目录；输出已处理章节与原书资源，供浏览器验证，不应纳入 Git。

测试扫描全部逻辑章节，抽取首章、首个头像章、首个浮图章、首个可修复图片章及所有脚注章，通过真实 `EpubRenderSession` / asset loader 读取处理结果，核对搜索及图片坐标。`node tools/test-epub-duokan-corpus.cjs <export-dir>` 对这些章节在分页/滚动中检查实际布局；对全部脚注执行定位、浏览器命中测试、气泡内容与事件隔离检查。日志只输出数量。

同时运行现有 `tools/test-epub-annotation-taps.cjs` 与 `tools/test-epub-underlines.cjs`，检查高亮交互回归。共享源码分别执行 official 与 OSS 的相关本地测试及 `:app:assembleDebug`。

## 范围

实书定向验证覆盖《诡秘之主》的浮图、《修真聊天群》的头像和错误 logo 扩展名、《查理九世11》的全部 20 组链接脚注，以及三本首章的多看页面属性。完整扫描不等于逐页人工验收；本次浏览器行为检查不等于 Android 真机触摸验收。

图库翻组、视频/表单/3D、多看专用缩进、首字下沉、专用字体别名与 vertflow 精确语义仍不在本次兼容范围。三本样书不能用于宣称全市场 90% 兼容率。

构建基线：公开 `codex/integration-current` / `3e48dfea93740fdc19cc9faffe837f0fd121fd97`；Premium 同名分支 / `7e2150cda89ee356d42aecd69b3fd166ef0203e2`。两侧均含已有未提交改动。构建记录、工作树审查、测试 XML 与浏览器结果位于本机 `.local/duokan-implementation/`。

## 本次结果

official debug 与 OSS debug 构建均成功；相关本地测试分别 69 项、68 项通过，未运行全量测试。official 的额外一项是启用私有实书的语料测试：扫描 4,695 个逻辑章节，检查 26 个选定章节的真实加载结果，确认 29 处图片引用修复及全部 20 组脚注。

实书浏览器复验共 52 个章节/模式组合通过，20 组脚注在两种模式中共打开 40 次，所测章节无缺图。独立 fixture 的 204 项多看兼容断言、现有 136 项高亮点按断言与 80 项下划线断言通过。浮图与头像页面完成本机截图检查。未安装手机应用，未执行仪器测试。

构建工作树为 `C:/Users/Huangder/.codex/worktrees/cloudflare-license/android_books`，Premium 为该目录的 `app/src/premium/`。两边分支和 HEAD 如上，均有未提交改动；本任务的功能实现全部在公开侧。当前源码已核对公共/私有小组件、笔记导出接缝与实现、5 天试用；具体审查记录留在本机。

官方 ARM64 APK：`app/build/official/outputs/apk/debug/app-arm64-v8a-debug.apk`。OSS 构建输出路径为 `app/build/oss/outputs/apk/debug/app-arm64-v8a-debug.apk`，验证副本保存在本机忽略目录 `.local/duokan-implementation/oss-validation-arm64-debug.apk`，仅用于开发核验。本次观察到切换 edition 后旧 edition 的输出被清理，因此交付前最后重新运行 official 构建。均使用当前工作树；本机 `verification-record.json` 记录实际绝对路径、大小、SHA-256、源码哈希与验证数量。

### 注释大图尺寸修正

真机截图暴露了此前交互测试未检查尺寸的问题：《查理九世11》的解谜图本身是 `noteref`，图片声明 `width="100%" class="duokan-image-static"`。将链接内所有图片视为脚注小图标，会把整幅解谜图压到约 19 CSS 像素。现通过共享的 `EpubFootnoteImagePolicy` 区分有明确插图声明与较大相对宽度的大图，保留块级图片尺寸和注释点击；普通图标继续走行内标记。WebView 与原生重排路径都已修正。

该修正 official/OSS 构建均通过，相关本地测试分别 57/56 项通过；独立浏览器 fixture 258 项断言、现有高亮点按 136 项与下划线 80 项断言通过。实书 52 个章节/模式组合复验通过，20 张解谜图在两种模式下均检查大图尺寸、命中及气泡打开，共 40 次。滚动模式的点击测量使用图片在视口内的可见部分，避免把大图完整矩形的屏幕外中心误判为无法点击。

修正后的官方 debug 已用 `:app:installDebug` 安装到手机，真机截图确认整幅解谜图及含答案图片的气泡均可见。本机记录为 `.local/duokan-implementation/note-size-verification.json`，截图为 `note-size-after-initial.png`；公开/Premium 的分支、HEAD 与未提交状态沿用上述汇总工作树。本次未运行全量测试。


### 滚动模式的背景与正文边距

上下滚动不再通过缩小整个 WebView 留出上下边距。WebView 保持阅读区域全尺寸，出版方的页面背景图独立铺满视口，正文及其高亮层单独裁剪；正文首尾仍有对应留白。固定版式的页面背景也使用独立层，设计内容按可用区域缩放。

真机快速滚动暴露了动态正文 clip-path 会落后于 WebView 合成器的问题。最终实现使用固定在视口边缘的纸张遮挡层：按完整视口尺寸复用书籍/阅读器背景，仅显示上下留白对应的部分。正文不再使用移动裁剪，不依赖滚动事件或 ScrollTimeline 更新；拖动、惯性滚动和主线程短暂停顿也不会把正文露进边距。底色由当前宿主传入，透明原书纸张也与原界面一致。遮挡层没有文本，追加在原书内容之后，不包装或移动原书节点，不改变正文文本索引；固定版式和章节拖动时使用根节点中的同一遮挡层。分页切换和实时边距调整会更新或移除遮挡。阅读位置、漫画位置和手工高亮命中测试使用正文可见区域。

`tools/test-epub-scroll-background.cjs` 使用真实时间的 Chromium CDP，检查 html/body 背景、重排/固定版式、正文命中、阅读位置、边距变更、章节拖动、分页切换，以及快速跳动并阻塞主线程时边缘遮挡的稳定性；截图像素检查上下边距仍是原书背景。实书脚本注入当前生产脚本，避免复验旧导出脚本；52 个章节/模式组合和 40 次注释打开通过，所测章节无缺图。现有夜间脚本同时更新为读取当前五段生产脚本，并按已有的原排版夜间浅色正文行为验证。


### 注释点击的蓝色蒙版

仅对已识别的注释引用及其子元素、独立图标设置 `-webkit-tap-highlight-color: transparent !important`。文字脚注、小图标及整幅解谜图仍保持原有点击、弹窗与关闭行为；普通链接继续使用原有样式。浏览器 fixture 增加每类入口的实际计算样式检查。


### 贴顶章节装饰图

《诡秘之主》的章节头图通过 `duokan-bleed: lefttopright` 明确要求贴顶。滚动模式将已识别的 `horizontal-top` 图片及纯图片容器画在边距纸张之上，正文仍在其下，避免把人物头部当成正文截掉。头图正常随章节滚走；离开后正文边距仍有效。切换回分页时恢复出版方的原有 position/z-index。露在边距中的头图仍支持图片预览，普通正文和隐藏注释不能借此穿透边距。


上述滚动边距、贴顶头图及注释去蓝色蒙版修正，最终 official debug 构建/安装通过，OSS 编译通过；两边相关本地测试各 57 项通过，未运行全量测试。真实时间浏览器通过 260 项视口断言、背景边缘截图像素检查及贴顶头图/图片预览场景；原有夜间 1,108 项、注释兼容 288 项、高亮点按 136 项与下划线 80 项通过。三本实书的 52 个章节/模式组合、40 次注释打开通过，所测章节无缺图。

真机在正文区域快速滑动并抽取 12 帧，确认正文实际移动，而上下边距采样区域没有正文泄漏。截图确认《诡秘之主》人物头图贴顶完整显示；验证后恢复进入验证前的阅读位置。最终构建、公开/Premium 分支及 HEAD、两侧未提交状态、APK 的物理输出路径与 SHA-256，记录于本机 `.local/duokan-implementation/scroll-background-header-verification.json`。本次实现只修改公开阅读器；Premium 原有工作区改动随官方构建保留。
