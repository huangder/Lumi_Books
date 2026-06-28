# 电子书阅读器 - 技术规范

## 版本信息
- 版本：v1.1
- 日期：2026-06-18
- 状态：已更新

## 一、技术栈

### 1.1 开发语言
- **主语言**：Kotlin
- **版本**：2.0.21
- **特性**：协程、Flow、密封类

### 1.2 UI框架
- **框架**：Jetpack Compose
- **BOM版本**：2024.12.01
- **优势**：声明式UI、状态管理、动画支持

### 1.3 架构模式
- **架构**：MVVM (Model-View-ViewModel)
- **状态管理**：Compose State + ViewModel
- **依赖注入**：Hilt 2.52

### 1.4 核心库
| 库 | 版本 | 用途 |
|---|---|---|
| Jetpack Compose BOM | 2024.12.01 | UI框架 |
| Navigation Compose | 2.8.5 | 页面导航 |
| Hilt | 2.52 | 依赖注入 |
| Hilt Navigation Compose | 1.2.0 | 导航集成 |
| Room | 2.6.1 | 本地数据库 |
| DataStore | 1.1.1 | 键值存储 |
| Coil | 2.7.0 | 图片加载 |
| Coroutines | 1.9.0 | 异步处理 |

### 1.5 构建配置
| 配置项 | 版本 |
|---|---|
| compileSdk | 35 (Android 15) |
| targetSdk | 35 (Android 15) |
| minSdk | 26 (Android 8.0) |
| AGP | 8.7.3 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Java | 17+ |
| Room | 2.6.x | 本地数据库 |
| DataStore | 1.0.x | 键值存储 |
| Coroutines | 1.7.x | 异步处理 |
| Coil | 2.5.x | 图片加载 |

### 1.6 电子书解析库
| 格式 | 实现方式 | 说明 |
|---|---|---|
| EPUB | ZipFile + XML解析 | 自定义实现，支持图片Base64内嵌 |
| PDF | Android PdfRenderer | 系统API，每页渲染为图片 |
| TXT | 自定义解析 | 纯文本处理，按章节分段 |

### 1.7 图片加载方案
- **EPUB**: 解析时将 `<img>` 标签的相对路径转为 Base64 内嵌到 HTML
- **PDF**: 每页渲染为 Bitmap 后转 Base64 内嵌到 HTML
- **渲染方式**: 使用 WebView 加载 HTML，原生支持图片显示
- **优势**: 无需额外图片加载库，兼容性好

## 二、项目结构

### 2.1 目录结构
```
app/src/main/java/com/ebook/reader/
├── di/                    # 依赖注入
│   └── AppModule.kt
├── data/                  # 数据层
│   ├── local/            # 本地数据源
│   │   ├── dao/          # Room DAO
│   │   ├── entity/       # 数据库实体
│   │   └── database/     # 数据库配置
│   └── repository/       # 仓库实现
├── domain/                # 领域层
│   ├── model/            # 领域模型
│   └── repository/       # 仓库接口
├── ui/                    # 表现层
│   ├── home/             # 首页
│   ├── statistics/       # 统计页
│   ├── reader/           # 阅读器
│   ├── components/       # 公共组件
│   ├── theme/            # 主题
│   └── navigation/       # 导航
├── util/                  # 工具类
│   ├── file/             # 文件处理
│   ├── parser/           # 电子书解析
│   └── time/             # 时间处理
└── service/               # 服务
    └── ReadingTimerService.kt
```

### 2.2 模块划分
- **app**：主应用模块
- **core**：核心功能模块（可选）
- **feature**：功能模块（可选）

## 三、数据模型

### 3.1 书籍实体
```kotlin
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val coverPath: String?,
    val format: BookFormat,
    val lastReadTime: Long,
    val readingProgress: Float,
    val createdAt: Long
)

enum class BookFormat {
    EPUB, PDF, TXT
}
```

### 3.2 阅读记录
```kotlin
@Entity(tableName = "reading_records")
data class ReadingRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val date: String, // YYYY-MM-DD
    val duration: Long, // 阅读时长（毫秒）
    val startTime: Long,
    val endTime: Long
)
```

### 3.3 书签
```kotlin
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val position: Float,
    val title: String,
    val createdAt: Long
)
```

### 3.4 笔记
```kotlin
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val startPosition: Int,
    val endPosition: Int,
    val selectedText: String,
    val note: String,
    val color: String,
    val createdAt: Long
)
```

## 四、核心功能实现

### 4.1 电子书解析
**EPUB解析**
```kotlin
interface EpubParser {
    fun parse(filePath: String): BookContent
    fun getChapters(): List<Chapter>
    fun getChapterContent(chapterIndex: Int): String
}
```

**PDF解析**
```kotlin
interface PdfParser {
    fun parse(filePath: String): BookContent
    fun getPageCount(): Int
    fun renderPage(pageIndex: Int): Bitmap
}
```

**TXT解析**
```kotlin
interface TxtParser {
    fun parse(filePath: String): BookContent
    fun splitChapters(): List<Chapter>
}
```

### 4.2 阅读时长统计
```kotlin
class ReadingTimerService : Service() {
    private var startTime: Long = 0
    private var isRunning = false
    
    fun startTimer() {
        startTime = System.currentTimeMillis()
        isRunning = true
    }
    
    fun stopTimer(): Long {
        val duration = System.currentTimeMillis() - startTime
        isRunning = false
        return duration
    }
}
```

### 4.3 翻页动画（Apple Books 风格视差翻页）

**架构**：JS 驱动章节内翻页（双层视差）+ Compose 驱动跨章节动画

**双层视差结构**
- **底层（body）**：CSS column 分页长条，移动速度 = 手指的 25%，小范围渐显
- **顶层（overlay div）**：拖拽开始时用 `innerHTML` 快照当前页，移动速度 = 100% 跟随手指
- overlay 带 `box-shadow` 模糊阴影 + `border-radius` 屏幕 R 角圆角

**章节内翻页（JS 实现）**
- CSS multi-column 分页：`column-width = viewport-width`，每列 = 一页
- 拖拽跟随：overlay `translateX(dragX)` + body `translateX(dragX * 0.25)`
- 边缘阴影条：80px 宽渐变 div，贴在 overlay 拖拽侧边缘
- 松手吸附：`cubic-bezier(0.2, 0.8, 0.2, 1)` 350ms
- 松手回弹：`cubic-bezier(0.25, 0.9, 0.25, 1)` 350ms
- 翻页阈值：距离 > 25% 屏宽 或速度 > 400px/s
- 边界橡皮筋：第一页右滑 / 最后一页左滑时位移衰减 25%

**跨章节翻页（Compose 实现）**
```
1. JS 检测拖拽超出章节边界 → AndroidBridge.onChapterFlipReady(direction)
2. Compose 锁定 JS 手势：evaluateJavascript("window.__animating = true")
3. flipOffset.animateTo(±1.0, 300ms) — 当前章节滑出视口
4. viewModel.nextChapter() / previousChapter() — 触发 HTML 重载
5. snapshotFlow 等待新章节就绪（currentChapterIndex 变化 + currentPageIndex = 0）
6. flipOffset.snapTo(∓1.0) — 从对侧就位
7. flipOffset.animateTo(0f, 300ms) — 新章节滑入
8. 解锁 JS 手势：evaluateJavascript("window.__animating = false")
```

**JS ↔ Kotlin 通信（ReaderJsBridge）**
| 方法 | 方向 | 说明 |
|---|---|---|
| `onPageFlip(direction)` | JS → K | 章节内翻页完成（-1=上一页, 1=下一页） |
| `onPageChanged(page, total)` | JS → K | 页码变化，更新 ViewModel 状态 |
| `onChapterFlipReady(direction)` | JS → K | 拖拽到达章节边界，通知 Compose 执行跨章节动画 |
| `onCenterTap()` | JS → K | 点击屏幕中央 40% 区域，切换菜单 |
| `window.__animating` | K → JS | 布尔标志，跨章节动画期间锁定 JS 手势 |

## 五、状态管理

### 5.1 ViewModel
```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {
    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()
    
    private val _todayReadingTime = MutableStateFlow(0L)
    val todayReadingTime: StateFlow<Long> = _todayReadingTime.asStateFlow()
}
```

### 5.2 状态类
```kotlin
data class ReaderState(
    val book: Book? = null,
    val currentChapter: Int = 0,
    val currentPage: Int = 0,
    val isMenuVisible: Boolean = false,
    val fontSize: Float = 16f,
    val theme: ReaderTheme = ReaderTheme.DAY
)
```

## 六、数据库设计

### 6.1 数据库版本
- 版本：1
- 迁移策略：破坏性迁移（开发阶段）

### 6.2 索引
```kotlin
@Entity(
    tableName = "reading_records",
    indices = [
        Index(value = ["bookId", "date"], unique = true)
    ]
)
```

## 七、性能优化

### 7.1 图片加载
- 使用Coil异步加载封面
- 实现图片缓存
- 支持图片压缩

### 7.2 列表优化
- 使用LazyColumn实现懒加载
- 实现分页加载
- 优化item重组

### 7.3 内存管理
- 及时释放大对象
- 使用WeakReference
- 监控内存使用

## 八、错误处理

### 8.1 异常类型
```kotlin
sealed class AppException : Exception() {
    data class FileNotFound(val path: String) : AppException()
    data class ParseError(val format: BookFormat) : AppException()
    data class DatabaseError(val operation: String) : AppException()
}
```

### 8.2 错误处理策略
- 文件操作：捕获IO异常，提示用户
- 解析错误：捕获解析异常，提示格式问题
- 数据库错误：捕获SQL异常，记录日志

## 九、测试策略

### 9.1 单元测试
- ViewModel测试
- Repository测试
- 工具类测试

### 9.2 UI测试
- Compose UI测试
- 用户交互测试

### 9.3 集成测试
- 数据库测试
- 文件操作测试

## 十、构建配置

### 10.1 build.gradle
```kotlin
android {
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
}
```

### 10.2 依赖管理
- 使用Version Catalog管理依赖版本
- 统一版本号管理
