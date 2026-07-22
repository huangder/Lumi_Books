# Lumi — 为阅读而生

> 简洁优雅的 Android 本地电子书阅读器，支持 EPUB、PDF、TXT 格式。纯本地离线，零网络权限，零第三方追踪。

[![Version](https://img.shields.io/badge/version-1.0.06-coral)](https://github.com/huangder/Lumi_Books/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-blue)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple)](https://kotlinlang.org)
[![Sponsor](https://img.shields.io/badge/%E2%9D%A4%EF%B8%8F-%E8%B5%9E%E5%8A%A9-ff385c)](https://huangder.top/sponsor.html)

---

## 功能特性

### 📖 阅读体验
- 支持 EPUB、PDF、TXT 三种主流电子书格式
- Canvas + StaticLayout 渲染引擎，流畅的多种翻页动画
- PDF 智能解析为可重排电子书，享受一致的排版阅读体验
- 多套阅读主题（当然还有自定义~），一键切换
- 精细排版调节（字号、行距、段间距、页边距、首行缩进）
- 自定义文字颜色与阅读背景色
- 应用内亮度调节，支持跟随系统
- 智能手势系统（左右点击翻页、中间唤出菜单、拖拽跟手翻页）
- TTS 听书（调用系统文字转语音引擎）
- 音量键翻页（在阅读页主题与设置中开启）
- 翻页动画切换（平滑切换 / 淡入渐变 / 模拟翻页）
- 阅读页角落信息区自定义显示

### ✨ 文字选择与标注
- Android 原生文字选择 + 自定义 Compose 浮动菜单
- 6 色高亮标注，支持点击查看和修改颜色
- 添加笔记（关联高亮文本）
- 全文搜索与复制
- 书签添加/删除/快速跳转

### 📚 书架管理
- 本地文件导入（系统文件选择器，EPUB/TXT/PDF 过滤）
- 书籍封面自动提取与展示（Coil 图片加载）
- 长按书籍唤出菜单
- 自定义封面（从相册选取）
- 自定义书籍信息（书名、作者）
- 收藏 / 删除管理

### 📊 阅读统计
- 今日阅读时长 + 本周趋势
- 每日阅读目标（完全自定义时长 + 开关）
- 周 / 月 / 年三 Tab 导航
- 月热力图（日历网格，按阅读时长着色）
- 年热力图（GitHub 贡献图风格，53 列 × 7 行 Canvas 渲染）
- 周柱状图（Canvas 绘制）
- 连续阅读天数（连胜记录）

### ⚙️ 设置与工具
- 完整设置体系（个人信息、阅读设置、显示外观、阅读目标、存储管理、关于）
- Material 3 设计语言适配，动态配色与全新组件样式
- 可选的启动开屏页（设置中开启/关闭）
- 预见式返回手势支持
- 界面元素动效（导航栏、卡片、按钮过渡动画）
- 数据备份与恢复（ZIP 导出/导入：数据库 + DataStore + 封面 + 书籍）
- 字体系统（霞鹜文楷、仿宋、楷体 + 自定义字体导入）
- 深色模式（跟随系统 / 浅色 / 深色）
- 设置项恢复默认
- 存储空间可视化
- 应用内检查更新
- 开发者主页入口 / GitHub Issue 反馈入口

### 🔒 隐私保护
- **本地优先** — 仅在检查更新时连接 GitHub 服务器，不携带任何个人信息
- **零第三方 SDK** — 无分析、广告、推送、云同步、崩溃报告
- **Android 沙盒存储** — 所有数据仅存于应用私有目录
- **开源可审计** — 完整源代码公开，欢迎审查
- 首次启动展示隐私政策与用户协议，透明告知

---

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.21 |
| UI 框架 | Jetpack Compose (BOM) | 2024.12.01 |
| 架构 | MVVM + Repository | — |
| 依赖注入 | Hilt | 2.52 |
| 数据库 | Room | 2.6.1 |
| 偏好存储 | DataStore Preferences | 1.1.1 |
| 导航 | Navigation Compose | 2.8.5 |
| 图片加载 | Coil | 2.7.0 |
| 构建工具 | AGP / Gradle | 8.7.3 |
| 最低 SDK | Android 8.0 (API 26) | — |
| 目标 SDK | Android 15 (API 35) | — |

---

## 项目结构

```
android_books/
├── LICENSE                 # MIT 许可证与第三方组件声明
├── README.md               # 项目说明
├── CLAUDE.md               # Agent 开发指引
├── build.gradle.kts        # 顶层构建配置
├── settings.gradle.kts     # 项目设置
├── gradle.properties       # Gradle 属性
│
├── app/                    # Android 应用代码
│   ├── build.gradle.kts    # 应用构建配置
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/html/    # 隐私政策/用户协议/开源许可 (HTML)
│       ├── java/com/huangder/lumibooks/
│       │   ├── data/       # 数据层 (Room DAO/Entity, DataStore, Repository)
│       │   ├── domain/     # 领域模型 (Book, Bookmark, Note, ReadingRecord)
│       │   ├── ui/         # 表现层 (Compose 页面 + ViewModel)
│       │   │   ├── home/       # 首页、书架
│       │   │   ├── reader/     # 阅读器（核心）
│       │   │   ├── statistics/ # 统计页
│       │   │   ├── settings/   # 设置页
│       │   │   ├── welcome/    # 欢迎/引导页
│       │   │   └── navigation/ # 导航
│       │   ├── di/         # Hilt 依赖注入
│       │   └── util/       # 工具类
│       │       └── parser/ # 电子书解析器 (EPUB/PDF/TXT)
│       └── res/            # 资源文件
│
├── devlog/                 # 开发日志（47 篇，按日期）
├── devdocs/                # 项目文档（16 份）
│   ├── CHANGELOG.md        # 更新日志
│   ├── requirements.md     # 需求文档
│   ├── technical-spec.md   # 技术规范
│   ├── design-spec.md      # 设计规范
│   ├── ui-design-spec.md   # UI 设计实现文档
│   ├── project-status.md   # 项目状态
│   ├── development-plan.md # 开发计划
│   └── ...                 # 更多文档
│
├── docs/                   # 项目网站 (GitHub Pages → huangder.top)
│   ├── index.html          # 首页
│   ├── features.html       # 功能特性
│   ├── tech.html           # 技术规格
│   ├── privacy.html        # 隐私政策（完整版）
│   ├── privacy-section.html # 隐私亮点
│   ├── terms.html          # 用户协议（完整版）
│   └── fonts/              # 网站字体 (Product Sans + MiSans)
│
├── pagedesign/             # 设计稿与原始文档
└── icon/                   # 应用图标素材
```

---


## 更新日志

详见 [更新日志页](https://huangder.top/changelog.html)

---

## 许可证

Lumi 原创代码采用 [MIT License](LICENSE) 开源。第三方依赖及改编代码继续遵循各自许可证；其中液态玻璃使用并改编自 Apache 2.0 许可的 [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)。© 2026 Huangder

---

## 链接

- 🌐 官网：[huangder.top](https://huangder.top)
- 📦 GitHub：[github.com/huangder/Lumi_Books](https://github.com/huangder/Lumi_Books)
- 📧 联系邮箱：huangder0104@126.com

---

<p align="center">
  <sub>一本好书的归宿，是一个好的阅读器。</sub>
</p>
