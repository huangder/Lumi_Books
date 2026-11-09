# 多看阅读 8.4.19 APK 静态逆向研究

日期：2026-11-08。目标是为 Lumi 的 EPUB 格式与交互兼容提供可核验的行为线索。

分页调度、位图缓存、快速翻页和跨章路径的后续分析见 [排版与翻页性能研究](duokan-pagination-performance-research.md)。

## 结论

已经定位到多看原生 EPUB 引擎、页面对象接口、页面属性解析分支及部分 Java 消费路径。多看兼容性包含自有排版内核和独立交互对象，不能用“补一份 CSS”概括。现有证据支持在 Lumi 中独立实现有针对性的兼容行为；尚不能把原生引擎当作已验证、可发布的集成 SDK。

本次使用 JADX 和 ARM64 静态反汇编实际分析了 APK，未执行目标应用。没有恢复完整 C++ 源码，没有完成原书渲染或触摸验收，没有改动 Lumi 运行源码或 Premium 实现，也没有构建或安装 APK。

## 样本与方法

| 项目 | 记录 |
| --- | --- |
| 包名 / 版本 | `com.duokan.reader` / `8.4.19`，versionCode `804190000` |
| APK 大小 | 77,901,217 bytes |
| APK SHA-256 | `daa8c4044dc8533c120d95b18b0db4fbdce9384b0abe40ee2c248a9706e97b36` |
| 包结构 | 8,553 个 ZIP 条目，17 个 DEX；有 arm64-v8a 与 armeabi-v7a 原生库 |
| 已反编译范围 | `classes3.dex`、`classes11.dex`、`classes12.dex`；JADX 报告处理完成，共 1,094 个进度项。不是全 APK 反编译覆盖率 |
| Java 工具 | JADX 1.5.6，下载自官方 GitHub release；ZIP SHA-256 与该 release 的 digest 一致 |
| 原生工具 | pyelftools 0.33、Capstone 5.0.9；读取 ELF sections、动态符号、重定位及选定 ARM64 代码区域 |
| 原生主样本 | `lib/arm64-v8a/libdkkernel.so`，7,801,920 bytes，SHA-256 `517e9d0d1fb0a1caf734f8c51ea7fd4461881de55c8d07bd9a89a7099829fc27` |

工具与反编译结果均保留在本机忽略目录 `.local/duokan-research/apk/`，不作为应用源码或资源。下面的原生地址均为这个 ARM64 ELF 的虚拟地址，不能套用于其他版本或 ABI。

证据强度分为：**指令分支**（已核对实际比较、返回/写入值）、**Java 消费路径**（已读反编译方法及调用）、**声明/字符串**（仅证明接口或标记存在）。声明与字符串不能独立证明特定书籍的运行效果。

## 引擎边界

`DkNative` 的加载器明确加载 `dkkernel`，EPUB 的 `DkeBook`、`DkePage`、`DkeGallery`、`DkeFlexPage` 经 JNI 调用该库。章节分页、页面绘制、文字坐标、命中检测及主要交互对象由原生接口提供。这个路径具备自有排版内核，不能等同于 Android WebView。

包内另有 `libddlayoutkit.so` 与 `com.dangdang.reader.dread` 组件；Java 的 `rx0.n` 方法中可以看到按文档类型选择 `DdBook` 或 `DkeBookEx` 的分支。它说明包内存在不止一种文档处理路径，不说明三本用户样书都会使用第二套引擎。

原生主库的依赖包括 Android 的 `libjnigraphics.so`、`liblog.so` 以及 libc、libm、libdl、libz 等。把 `.so` 复制进 Lumi 不会自动获得资源、字体、生命周期、布局配置、笔记定位与界面交互接缝；本轮也没有验证独立宿主调用成功或取得可再分发授权。

REA 是这些分析工具的编排入口。本次没有安装或注册 REA MCP，实际调用的是独立 JADX 和 Python 静态分析工具。因 REA 的 Windows Ghidra 接口对 Android ELF 的支持存在边界，本次没有把其 Windows 原生适配器视为已可用条件。

## 页面属性：已核对实际比较分支

原生 `0x0f2b04` 附近读取 XML 的 `properties` 属性，`0x0f2b14` 调用 `0x1afdc8`，随后保存返回值。后者通过实际 `strcmp` 比较这些值：

| 属性值 | 原生返回值 | Java 常量交叉核对 |
| --- | ---: | --- |
| `duokan-page-fullscreen` | 2 | `DKE_CHAPTER_FULLSCREEN` |
| `duokan-page-fitwindow` | 3 | `DKE_CHAPTER_FITWINDOW` |
| `duokan-page-fullscreen-interactive` | 4 | `DKE_CHAPTER_FULLSCREEN_INTERACTIVE` |
| `duokan-page-fullscreen-spread-left` | 5 | `DKE_CHAPTER_FULLSCREEN_SPREAD_LEFT` |
| `duokan-page-fullscreen-spread-right` | 6 | `DKE_CHAPTER_FULLSCREEN_SPREAD_RIGHT` |
| `duokan-page-fullscreen-vertflow` | 7 | 此次所读 `DkeConstants` 未列出对应命名常量 |

Java 的页面呈现类 `com.yuewen.ny0` 在 `k2` 方法对类型 5/6 建立专用跨页图对象，`p2` 方法对这两种类型跳过普通页面绘制；其内部类 `r` 再区分左右方向。这比属性名字本身提供了更强的跨页呈现证据。该类在类型 2 的绘制配置中还关闭了普通夜间图片优化，说明全屏插图和正文页面的处理确实有差异。

这仍没有证明单页/双页模式的最终裁切、填充、页边距和转屏行为。尤其不能直接将这些属性转换成整本 EPUB fixed-layout，或把 `spread-left` 简化为标准空白页插入规则。新兼容层应保留标准 EPUB token 解析与明确的单项 presentation hint，避免照搬旧解析器的整串比较限制。

对三本样书的直接价值：前两本首项含 fullscreen；第三本首项含 fullscreen-spread-left。它们是应进入实际呈现决策的格式提示，而不是可以靠封面猜测长期替代的标记。

## 交互对象与脚注

`DkeConstants` 将普通图、静态图、脚注、交互图、跨页图、图库、表格分别定义为不同页面对象类型。`DkePage` 暴露 `getFootnotes`、`getInteractiveImages`、`getGalleries`、`getInteractiveTables`、`hitTestObject` 与 `hitTestLink`；这部分属于接口声明证据。

进一步的 Java 消费路径表明：

- 脚注对象携带边界、替代文字与可独立排版的 `DkeFlexPage`。`ny0.i` 同时接收 `mAltText` 和 `mAltPage`，不是只能显示纯字符串的接口。
- `ny0.f2` 收集整页脚注，并遍历图库各个 cell 收集其中的脚注，之后恢复原来的 active cell。图库内容和内部脚注不是同一个普通图片预览操作。
- `DkeGallery` 有 cell 数量、当前 cell、切换 cell、图像/题注边界、内部脚注与链接接口。它说明图库有独立状态，但本轮没有验证具体滑动手势。
- 原生 `0x211058` 开始的注册区将 `img` + `.` + `duokan-footnote` 和 `a` + `.` + `duokan-footnote` 分别构造成自定义标记；随后也注册 `ol.duokan-footnote-content` 等结构。因此“图片式入口”和“链接式入口”均有原生识别证据。尚未完整追踪所有原始属性到 `mAltText/mAltPage` 的转换。

原生 `0x1b2d84` 附近的配置解析使用 `footnote-options`、`option`、`name`、`appearance`；其中实际比较 `bubble` 后写入 1，比较 `paper-tape` 后写入 2。Java 常量及 `EpubDocument.B` 将两者映射为 BUBBLE 和 PAPERTAPE。这是外观配置分支证据，不是已经复原整个扩展 XML schema。

三个用户样书均没有 `META-INF/duokan-extension.xml`，不能把这条外观配置路径当作其脚注的必要条件。第三本的 20 组链接脚注仍应优先验证已有 Lumi 路径；前两本无这类脚注。图片式兼容必须限定明确的脚注语义，不能把第二本的普通头像 alt 也变成注释。

## CSS 与字体：修正此前的简单映射假设

原生 CSS 名称注册区域 `0x1cfe54` 将标准 `text-indent` 注册为属性编号 8，`0x1cfe80` 将 `duokan-text-indent` 注册为编号 9；诊断字符串也分别存在。**它们是独立属性入口，现有证据不足以证明可以无条件相互改名。**独立编号也不单独证明所有情况下视觉不同；完整取值语义、覆盖优先级与布局使用还需跟踪或运行对照。

因此前期研究提出的简单 alias 只能降为待验证方案：先覆盖标准声明与多看声明同 selector 共存、顺序相反、重要性不同、值冲突、继承和分页/滚动模式，再决定兼容转换。三本已有标准缩进回退的部分不需要为了消除未知属性而贸然覆盖。

字体方面，Java 的 `pr0`、`DkConstants` 存在 DK-SONGTI、DK-KAITI、DK-HEITI 等角色；`DkeBook.registerFonts` 读取字体映射、检查本地文件，并分别设置正文/默认/备用字体。角色别名是运行时字体注册的一部分，不能假定 Android 系统自带对应字体。Lumi 应优先保留嵌入字体，并用用户字体或具有适当授权的字体作角色回退。本次没有提取或分发多看字体。

其他如 `duokan-bleed`、首字下沉、多看图片/图库、交互表格、公式、音视频、练习题标记，在原生字符串或接口中存在。除本文明确追踪的分支外，它们仍是后续调查线索，不列为已复原规范或 Lumi 已支持能力。

## Lumi 的落地顺序

1. **先验证高频版式差异。** 三本原书中的浮动插图、聊天头像/气泡以及第三本脚注，与当前 Lumi 原书/重排模式逐页对照；保留分页边界与定位回归。这些样书已知风险见[真实样书研究](duokan-real-books-research.md)。
2. **明确交互分类。** 将明确标记的脚注、普通图片、交互图、图库分别匹配到操作；脚注内容独立呈现，避免向正文流注入隐藏注释文本而改变索引。图片查看、气泡关闭、图库翻动与正文翻页需要实际触摸验收。
3. **接入页面提示。** 对单个 spine item 解释 full-screen/fit-window/spread 等提示，覆盖窄屏单页、横屏双页、旋转、返回和进度恢复，不改变整书的 rendition layout。
4. **逐项验证 CSS 与字体。** 先补最小原创结构用例，再依据实际差异实施规则；暂不将所有 `duokan-*` 删除前缀后当标准 CSS。
5. **保留现有引擎作为实现起点。** 此次证据可以指导兼容层设计；若后续真实样本证明 WebView 分页无法达到要求，再独立评估更换引擎。直接封装专有 `.so` 尚不属于可交付方案。

这三本是目标样本；APK 中存在更多交互不意味着三本已覆盖它们，也不能据此宣称 90% EPUB 兼容率。覆盖率口径仍按真实样书报告建立。

## 本机证据索引

以下文件均位于忽略目录 `.local/duokan-research/apk/`，未加入公开历史：

| 文件 | 用途 |
| --- | --- |
| `analysis-manifest.json` | APK、选定 DEX/原生库摘要和工具版本，JADX release digest 验证结果 |
| `engine-classes.json` | 基于 DEX 类型/类定义表的候选类清单；关键词匹配含无关项，不是实际调用图 |
| `native-format-strings.json` | 格式标记与原生文件偏移；只作为线索 |
| `native-format-xrefs.json` | 经 ELF 重定位追到 GOT 的候选代码引用；线性匹配，不是完整数据流分析 |
| `native-regions.json` | 页面属性、脚注选项、CSS/自定义标记注册与 JNI 局部反汇编；人工核对分支用 |
| `chapter-type-callers.json` | 页面属性转换 helper 的直接调用点 |
| `decompiled/`、`jadx.log` | 选定三份 DEX 的本地反编译结果与处理日志 |

前期公开资料研究见[可行性研究](duokan-epub-feasibility.md)。本报告补充了 APK 证据，并收紧其中尚未验证的 CSS 等价转换假设。

## 后续适配记录

基于本报告的阅读器实现与验证见[多看 EPUB 内容适配](duokan-epub-adaptation.md)。
