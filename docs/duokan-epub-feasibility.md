# 多看 EPUB 兼容性研究

研究日期：2026-11-07。范围：公开资料与当前工作区源码静态审查；没有进行多看 App 实测，也没有完成同书截图对比。

本文件为第一轮结论。后续源码调查、五个浏览器实测场景、引擎比较、兼容层设计与原创对照 EPUB 见 [深入研究](duokan-epub-deep-research.md)。

2026-11-08 更新：真实样书见[样本报告](duokan-real-books-research.md)，用户提供的多看 8.4.19 APK 静态逆向见[APK 报告](duokan-apk-reverse-research.md)。APK 中标准 `text-indent` 与 `duokan-text-indent` 是独立属性入口，因此本文早期提到的简单属性转换应先验证语义和级联，不能直接当作等价规则。

## 结论

可以在 Lumi Books 现有 EPUB 渲染器上实现多看格式兼容。此次没有找到带明确复用授权的多看官方排版引擎源码或公开集成 SDK，因此不能把“移植多看引擎”作为已经可执行的方案。搜索不到不能证明其不存在；商业授权是否可取得仍未知。

建议先补多看扩展解析和原书样式保真。换用另一个 EPUB SDK 不会自动获得多看专用格式支持，也不能仅凭功能列表认定其排版优于当前实现。

## 资料与证据边界

- [Eninix/sigil-template](https://github.com/Eninix/sigil-template)：社区对多看 EPUB 的分析，包含图片注释、专用 CSS、字体名、OPF 页面属性的具体示例。属于兼容格式线索，不能视为多看官方完整规范，也不能据此解释多看当前版本的全部能力。本次未取得该仓库代码或原多看样式表的复用授权；只记录格式事实与独立实现方向，不复制样式表。
- [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit)：可集成的开源 Android 阅读工具包，项目声明 BSD-3-Clause，列有 EPUB 2/3、可重排/固定版式、RTL、搜索、高亮与 TTS 支持。其文档不能证明支持本文所列多看扩展。
- [多看官网](https://www.duokan.com/)：此次可访问页面为需要 JavaScript 的应用入口，没有从该页面取得可集成排版引擎资料。

## 与当前实现的对照

| 目标 | 当前源码证据 | 判断 |
| --- | --- | --- |
| 保留原书 HTML/CSS | `ReaderScreen.kt` 的原书模式进入 `EpubWebViewReader`；`EpubDocumentTransformer` 包装 XHTML、保留原资源并注入分页脚本 | 已有保真路线，适合作为兼容层基础 |
| 多看链接式脚注 | `EpubDocumentTransformer.kt` 的 `hasFootnoteHint`、`isFootnoteReference` 及 `EpubParser.kt` 均识别 `duokan-footnote`/`duokan_footnote` | 已部分支持，不能把正则识别等同于支持全部多看脚注格式 |
| 图片自身携带注释 | 社区示例为无链接的 `img.duokan-footnote`，正文保存在 `alt`；当前脚注标记遍历 `a[href]`，点击气泡入口依赖链接和 fragment | 缺少该格式的独立脚注入口；需将普通图片预览与这种注释图标区分 |
| 专用页面属性 | `EpubPackageReader` 保留 itemref 的 properties；`EpubRenderSession` 解释标准 rendition、page-spread 属性。主源码未查到 `duokan-page-*` 的解释 | `duokan-page-fullscreen`、`duokan-page-fitwindow`、跨页插图属性尚未形成专用呈现语义；已有纯图页推断不能等价替代 |
| 专用 CSS | 主源码未查到 `duokan-text-indent`、`duokan-drop-caps-style` 的转换 | 有标准 CSS 回退的书可能已正常显示；仅写专用声明的书需要转换或单独实现 |
| 字体角色 | 社区样式使用 `DK-KAITI`、`DK-SONGTI`、`DK-HEITI` 等别名；主源码未查到这些别名的映射 | 未嵌入字体且系统没有对应字体时，不能保证角色字体正确；保留书内字体优先级，回退可使用已有授权字体或用户字体 |
| 图文环绕 | Transformer 为部分浮动媒体容器打标，并在重排分页模式下强制 `float:none; clear:both; width:100%` | 部分图文环绕会被主动改成整行插图。这是确定的保真取舍，不是简单缺少 CSS 文件 |
| 竖排、注音、复杂版式 | WebView 脚本已有 writing-mode/翻页方向处理；`NativeEpubCompiler` 有有限 CSS/标签支持，对复杂 ruby、混合 writing-mode、部分图片布局等返回 Unsupported | 不能说完全不支持竖排/注音；需按实际路由验证复杂章节回退，不能贸然扩张原生白名单 |

`EpubCssIndex` 明确只用于宽度、背景等局部推断，不是完整 CSS 级联引擎。不能用它替换浏览器的 CSS 计算，也不能仅通过给它加几个属性就认定复杂排版已解决。

## 建议实施顺序

1. **建立同书对照样本。** 用用户实际觉得多看效果好的 EPUB，记录相同章节、字体、字号、屏幕尺寸与阅读模式的截图；先区分原书模式和阅读器重排模式。当前没有样本，不给出“已达到多看几成”的数字。
2. **补明确、范围小的格式差距。** 图片 `alt` 脚注、专用缩进属性、字体角色回退。对普通图片、已有链接脚注、标准 text-indent 和嵌入字体保留明确优先级，避免按宽泛类名误识别。
3. **补页面呈现语义。** 让已有 OPF 属性存储进入页面呈现决策，分别处理全屏、窗口适配、跨页插图；验证单页、横屏双页、进度定位与漫画模式，不把所有全屏属性粗暴解释成 EPUB 固定版式。
4. **再处理环绕和复杂分页。** 用跨页浮动图、大图加题注、并列图、表格样本验证；查清原强制展开规则解决的裁切/漏页问题，按场景恢复环绕。不能直接删除该规则就宣称修复。
5. **最后评估引擎替换。** 只有同一组样本证明现有架构确有难以解决的问题，再比较 Readium 等候选；同时验证选区、高亮、TTS、脚注、跨章翻页、已有定位和笔记兼容。跨引擎迁移是独立工程。

这些能力属于通用阅读器，应进入公开仓库，official 与 OSS 共用。此次没有修改 Premium 实现。

## 后续验证要求

实现格式兼容时，使用独立编写的最小 EPUB 固定样本覆盖标准格式、多看格式及混用情形，并做真实书的人工截图对照。共享代码修改后分别验证 official/OSS 编译和相关本地测试；涉及仪器测试时只编译测试源码，不运行 connected 测试或安装 test APK。

本次仅新增研究文档，没有修改运行代码，没有构建、安装或宣称测试通过。调研基于当前工作区（包含既有未提交修改）：公开分支 `codex/integration-current`，HEAD `3e48dfea93740fdc19cc9faffe837f0fd121fd97`；Premium 分支 `codex/integration-current`，HEAD `7e2150cda89ee356d42aecd69b3fd166ef0203e2`。这些提交号不代替对未提交源码的描述。
