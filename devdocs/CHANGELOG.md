# 更新日志

## v1.0.01.124 (2026-07-06) — 首个正式版本

### 📖 阅读引擎

- **自研 Canvas 渲染引擎**：StaticLayout 直接绘制到 Bitmap，3 槽位 PageSurfaceView 轮换，替代早期 WebView/TextView 方案
- **EPUB 解析器**：自实现 ZIP 解压 + HTML 解析，图片转为 Base64 内嵌到 HTML
- **PDF 解析器**：Android PdfRenderer 按需渲染 + LRU 缓存（最多 3 页）+ JPEG 压缩
- **TXT 解析器**：纯文本智能分章，包装为 HTML 布局
- **视差翻页动画**：SlidePageAnim 双层视差（覆盖层 100% 速度 + 主体层 25% 速度），Cubic Bezier 缓动，边缘阴影渐变，3 槽轮换实现无缝跨章
- **章节切换异步化**：修复主线程阻塞导致的白屏和卡顿问题
- **分页精确修复**：StaticLayout 与 TextView breakStrategy 对齐（HIGH_QUALITY）、共享 TextPaint 消除字体度量差异、includeFontPadding 处理
- **圆角裁剪**：Canvas clipPath 软件渲染兼容（setLayerType LAYER_TYPE_SOFTWARE）

### ✨ 文字选择与标注

- **原生文字选择**：setTextIsSelectable(true) + ActionMode 拦截，兼容 MIUI/MagicOS
- **自定义 Compose 选择菜单**：6 色高亮调色板 + 添加笔记 + 全文搜索 + 复制 + 移除高亮
- **SpanWatcher 拖拽检测**：300ms 防抖，拖动选择手柄自动重新弹出菜单
- **选择手柄 Compose 覆盖层**：从 Bitmap 渲染迁移到独立 Compose 层，消除坐标反馈环抖动
- **分层渲染**：文本层 + 标注层分离，拖动选择时只重绘标注层，大幅提升流畅度
- **BreakIterator 选词**：智能词语边界检测，精准选中中文词语
- **高亮持久化**：保存后重新渲染不丢失，点击已有高亮可修改颜色或删除

### 📚 书架与书库

- **书籍导入**：系统文件选择器，EPUB/TXT/PDF 过滤，自动提取封面和元数据
- **书籍展示**：Coil 封面加载 + 缓存，网格/列表布局
- **长按上下文菜单**：玻璃拟态遮罩 + 封面缩放动画 + 交错菜单项弹出
- **自定义封面**：从相册选取图片替换默认封面
- **编辑书籍信息**：书名、作者编辑（EditInputDialog 卡片风格组件）
- **收藏/取消收藏**：首页三点菜单快速操作
- **删除动画**：缩小 + 淡出动画

### 📊 阅读统计

- **今日阅读**：今日阅读时长 + 本周趋势柱状图
- **阅读目标**：每日时长目标设定，完成百分比显示
- **周/月/年三 Tab 导航**：左右箭头切换时间段
- **月热力图**：日历网格布局，按阅读时长着色（浅→深）
- **年热力图**：GitHub 贡献图风格，53 列 × 7 行 Canvas 渲染，支持横向滚动
- **周柱状图**：Canvas 原生绘制，星期标签对齐
- **阅读连胜**：连续打卡天数追踪，周起始日统一为周日
- **数据驱动圆圈**：首页每周打卡圆圈（完成/未完成状态）

### ⚙️ 设置

- **完整设置体系**：6 大分类（个人信息、阅读设置、显示与外观、阅读目标、存储管理、关于）
- **独立 Activity 架构**：设置页及二级菜单使用独立 Activity + Android 原生过渡动画
- **阅读设置双向同步**：与阅读器通过 DataStore 实时同步字号/行距/字间距/字体/边距
- **深色模式**：跟随系统 / 浅色 / 深色，全局生效
- **数据备份与恢复**：ZIP 压缩导出（数据库 + DataStore + 头像 + 书籍），系统文件选择器导入恢复
- **自定义 PillSlider 胶囊滑动条**：Canvas 绘制 + 手势拖拽，替代系统 Slider，无拇指圆圈
- **字体系统**：霞鹜文楷（正文）、仿宋、楷体（标题装饰）三款内置字体，支持导入自定义字体
- **亮度调节**：滑块控制，支持 -1 跟随系统亮度
- **通用 EditInputDialog**：卡片风格编辑弹窗，imiPadding 键盘避让，G2 连续曲率圆角（Squircle 风格）
- **关于页面**：隐私条款 / 用户协议 / 开源许可，WebView 加载本地 HTML

### 🎨 UI 与体验

- **设计语言**：Airbnb 风格设计系统，Rausch 珊瑚红（#E85D5D）主色调，玻璃拟态效果
- **欢迎/引导页**：首次启动展示，隐私政策与用户协议底部弹窗（FormattedPolicyContent 正则解析渲染），同意后进入应用
- **首页三点菜单**：继续阅读卡片快速收藏/删除操作
- **书籍过渡动画**：全屏加载动画，深色模式适配
- **Tab 栏选中指示器**：胶囊形状动画
- **全面屏适配**：enableEdgeToEdge()，状态栏/导航栏沉浸
- **dynamicColor 关闭**：避免 MIUI 兼容问题

### 🏗️ 技术架构

- **语言**：Kotlin 2.0.21
- **UI**：Jetpack Compose (BOM 2024.12.01) + Material 3
- **架构**：MVVM + Repository 模式，单 Activity + Navigation Compose
- **DI**：Hilt 2.52
- **数据库**：Room 2.6.1 (Book / Bookmark / Note / ReadingRecord)
- **偏好存储**：DataStore Preferences 1.1.1
- **图片加载**：Coil 2.7.0
- **构建**：AGP 8.7.3 + Gradle 8.7.3
- **目标**：compileSdk 35, targetSdk 35, minSdk 26, JDK 17

### 📄 文档与合规

- 完整的中文隐私政策与用户协议（4 处同步：网站 / App WebView / 开屏弹窗 / 原始草稿）
- MIT 开源许可证
- 项目网站（huangder.top，GitHub Pages 部署，Airbnb 风格设计）
- 47 篇开发日志（devlog/），覆盖从项目启动到首版发布全流程
- 15 份项目文档（devdocs/），含需求、技术规范、设计规范、UI 设计实现、项目状态等

### 🐛 关键修复

- **白屏问题**：11 项修复（包括 WebView 加载时序、章节切换竞态、字体指标对齐等）
- **文字溢出与内容断裂**：breakStrategy 不匹配 + TextPaint 字体度量差异 → 统一 StaticLayout 参数与共享 TextPaint
- **字体大小与分页联动**：修改字号后重新分页失败 → 强制重建 Layout
- **MIUI/MagicOS 兼容性**：ActionMode 隐藏（mode.hide(Long.MAX_VALUE)）、Spannable 死对象异常
- **章节切换卡顿**：runBlocking 主线程阻塞 → 异步化
- **笔记跳转定位**：跳转到笔记所在章节后页码不准确 → 修正 pageIndex 计算
- **阅读时长累积**：upsert 逻辑错误导致重复计数 → 修复插入/更新判断
- **R 角渲染**：GPU 忽略非矩形 clipPath → 软件渲染兼容处理
- **EPUB 链接文字消失**：CSS 伪类渲染问题 → ImageSpan 解析修正

---

*首次正式版本发布。*
