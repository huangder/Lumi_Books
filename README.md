# 电子书阅读器 (EBook Reader)

一款简洁优雅的Android电子书阅读器，类似Apple Books的安卓版本。

## 功能特性

### 📚 书籍管理
- 支持EPUB、PDF、TXT格式
- 本地文件导入
- 书籍封面展示
- 搜索和排序功能

### 📖 阅读体验
- 全屏沉浸式阅读
- 流畅的翻页动画
- 圆角矩形页面
- 多种阅读主题

### ⏱️ 阅读统计
- 今日阅读时长
- 本月阅读统计
- 阅读目标设置
- 柱状图可视化

### 🎨 个性化设置
- 字体大小调节
- 行距字间距设置
- 多种阅读主题
- 亮度调节

### 📌 书签笔记
- 添加书签
- 阅读笔记
- 高亮标记
- 快速跳转

## 技术栈

- **开发语言**：Kotlin
- **UI框架**：Jetpack Compose
- **架构模式**：MVVM
- **依赖注入**：Hilt
- **本地数据库**：Room
- **数据存储**：DataStore
- **图片加载**：Coil
- **电子书解析**：epublib、PdfRenderer

## 项目结构

```
android_books/
├── CLAUDE.md              # 项目指引
├── README.md              # 项目说明
├── devlog/                # 开发日志
├── docs/                  # 项目文档
│   ├── requirements.md    # 需求文档
│   ├── technical-spec.md  # 技术规范
│   ├── design-spec.md     # 设计规范
│   ├── development-plan.md # 开发计划
│   ├── app-description.md # 应用描述
│   └── project-summary.md # 项目总结
└── app/                   # Android应用代码
    ├── src/main/java/com/ebook/reader/
    │   ├── data/          # 数据层
    │   ├── domain/        # 领域层
    │   ├── ui/            # 表现层
    │   ├── di/            # 依赖注入
    │   ├── util/          # 工具类
    │   └── service/       # 服务
    └── src/main/res/      # 资源文件
```

## 开发环境

- Android Studio Hedgehog | 2023.1.1
- Kotlin 1.9.20
- Jetpack Compose 1.5.5
- Gradle 8.5
- Android SDK 34

## 系统要求

- Android 8.0 (API 26) 及以上
- 支持手机和平板

## 快速开始

### 使用Android Studio（推荐）

1. 克隆项目
```bash
git clone https://github.com/yourusername/ebook-reader.git
```

2. 使用Android Studio打开项目
   - 启动Android Studio
   - 选择 "Open an existing Android Studio project"
   - 选择项目目录

3. 等待Gradle同步完成（首次可能需要几分钟）

4. 运行项目
   - 点击工具栏的 "Run" 按钮
   - 选择模拟器或真机设备

### 使用命令行

1. 进入项目目录
```bash
cd android_books
```

2. 构建Debug APK
```bash
# Windows
gradlew.bat assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

3. 安装到设备
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 构建发布

1. 配置签名密钥
2. 生成签名APK
```bash
gradlew assembleRelease
```
3. 发布到应用商店

详细构建说明请参考 [构建指南](docs/build-guide.md)

## 版本历史

### v1.0.0 (2026-06-17)
- 初始版本发布
- 支持EPUB、PDF、TXT格式
- 实现核心阅读功能
- 实现阅读统计功能

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建Pull Request

## 许可证

本项目采用MIT许可证 - 详见 [LICENSE](LICENSE) 文件

## 联系方式

- 开发者：EBook Reader Team
- 邮箱：support@ebookreader.com

## 致谢

- Material Design设计规范
- Jetpack Compose官方文档
- epublib开源库
