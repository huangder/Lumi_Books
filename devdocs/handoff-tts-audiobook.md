# 交接文档：语音听书引擎（TTS Audiobook）

**日期**：2026-07-20
**状态**：待实现
**目标**：新增语音听书功能，调用 Android 系统 `TextToSpeech` API 朗读 EPUB/TXT 书籍，支持后台播放 + 通知栏控制。

---

## 1. 项目关键背景

- **包名**：`com.huangder.lumibooks`
- **技术栈**：Kotlin + Jetpack Compose + Hilt + Room + DataStore
- **阅读引擎**：EPUB/TXT 使用**自研 Canvas 引擎**（StaticLayout 分页 + 3 槽位 PageContentView 轮转），不是 WebView
- **核心架构**：`ReaderScreen` (Compose) → `ReaderViewModel` → `ReadView` (FrameLayout) → `PageSlotManager` (3 槽位) → `PageContentView` (TextView + JustifiedTextView 双层)
- **依赖注入**：Hilt `@Singleton`，模块在 `di/AppModule.kt`（object 类型）
- **偏好存储**：Jetpack DataStore，键在 `DataStoreManager.kt` companion object 中定义

### 文本内容获取链路（Canvas 引擎）

```
parser.getChapterContent(chapterIndex) → CharSequence (EPUB: Spanned, TXT: String)
  → ReaderViewModel.getChapterText(index)
    → PageLayoutEngine.layout(chapterIndex, text) → StaticLayout 分页 → PageLayout(startCharOffset, endCharOffset)
      → PageSlotManager.loadSlot() → 切片: fullText.subSequence(startCharOffset, endCharOffset)
        → PageContentView.textView.text (此页文本，Spannable)
```

**关键结论**：当前页文本有两种获取途径：
1. `ReadView.curPageView.textView.text` — 已渲染的 Spannable（需要处理 ImageSpan）
2. **推荐**：章节级文本 + `PageLayout` 偏移切片 — 对 EPUB/TXT 统一，无需处理 Span

---

## 2. 目标功能

| 功能 | 描述 |
|------|------|
| 🔈 朗读 | 从当前阅读位置开始朗读页面文字 |
| ⏯️ 播放/暂停 | 暂停后恢复，从断点继续 |
| ⏭️ 跳过 | 跳到下一句/上一句 |
| 📄 自动翻页 | 读完一页自动翻到下一页继续朗读 |
| 📖 自动翻章 | 读完一章自动进入下一章 |
| 🎚️ 语速调节 | 0.5× ~ 2.0×，偏好持久化 |
| 🔔 后台播放 | ForegroundService + MediaSession + 通知栏控制 |
| 🎛️ UI 面板 | 阅读器内浮动胶囊播放条 |

**不支持**：PDF 格式（仅 EPUB + TXT）

---

## 3. 文件变更清单

### 3.1 新建文件（8 个）

| # | 文件路径 | 说明 |
|---|---------|------|
| 1 | `tts/TtsPlaybackState.kt` | 播放状态枚举：IDLE / INITIALIZING / PLAYING / PAUSED |
| 2 | `tts/TtsEngine.kt` | 封装 Android `TextToSpeech`，`@Singleton`，暴露 `engineStatus: StateFlow` |
| 3 | `tts/TtsTextExtractor.kt` | 文本提取 + 中文分句，统一处理 EPUB Spanned 和 TXT String |
| 4 | `tts/TtsController.kt` | 播放状态机 + 自动翻页编排，`@Singleton` |
| 5 | `service/TtsForegroundService.kt` | 前台 Service + MediaSession |
| 6 | `service/TtsNotificationManager.kt` | 通知渠道 + MediaStyle 通知构建 |
| 7 | `ui/reader/TtsPlayerPanel.kt` | Compose 浮动迷你播放条 |
| 8 | `strings.xml` 新增条目 | TTS 相关字符串（按钮标签、通知文案） |

### 3.2 修改文件（6 个）

| # | 文件路径 | 改动内容 |
|---|---------|---------|
| 1 | `data/local/DataStoreManager.kt` | 新增 `TTS_SPEECH_RATE` / `TTS_PITCH` 偏好键 |
| 2 | `di/AppModule.kt` | 新增 `provideTtsEngine()` / `provideTtsTextExtractor()` / `provideTtsController()` |
| 3 | `ui/reader/engine/ReadView.kt` | 新增 `getNextPageLocation()` / `getPrevPageLocation()` 查询方法 |
| 4 | `ui/reader/ReaderViewModel.kt` | 注入 `TtsController`，新增 `ttsPlaybackState` 到 `ReaderUiState`，暴露控制方法 |
| 5 | `ui/reader/ReaderScreen.kt` | 集成 `TtsPlayerPanel` overlay + 听书启动按钮 + POST_NOTIFICATIONS 权限请求 |
| 6 | `app/src/main/AndroidManifest.xml` | 新增 3 个权限 + TtsForegroundService 声明 |

---

## 4. 详细设计

### 4.1 TtsPlaybackState（无依赖，最先创建）

```kotlin
// tts/TtsPlaybackState.kt
package com.huangder.lumibooks.tts

enum class TtsPlaybackState {
    IDLE,          // 未播放
    INITIALIZING,  // TTS 引擎初始化中（首次需下载语音数据，1-3 秒）
    PLAYING,       // 正在朗读
    PAUSED         // 已暂停
}
```

### 4.2 TtsEngine（封装 Android TextToSpeech）

```kotlin
// tts/TtsEngine.kt
package com.huangder.lumibooks.tts

class TtsEngine(private val context: Context) {
    // Android TextToSpeech 实例
    private var tts: TextToSpeech? = null

    // 引擎状态
    private val _engineStatus = MutableStateFlow(TtsEngineStatus.INITIALIZING)
    val engineStatus: StateFlow<TtsEngineStatus> = _engineStatus.asStateFlow()

    // 初始化（suspendCancellableCoroutine 包装回调）
    suspend fun initialize(locale: Locale = Locale.getDefault()): Result<Unit>

    // 将文本加入合成队列，utteranceId 格式 "ch{章节}_pg{页}_s{句序号}"
    suspend fun speak(text: String, utteranceId: String): Result<Unit>

    // 清空队列 + 停止
    suspend fun stop()

    // 语速 0.5~2.0，默认 1.0
    suspend fun setSpeechRate(rate: Float)

    // 音调 0.5~2.0，默认 1.0
    suspend fun setPitch(pitch: Float)

    // 设置句子完成监听
    fun setOnUtteranceListener(listener: UtteranceProgressListener)

    // 释放资源
    fun shutdown()
}
```

**关键注意事项**：
- Android TTS 没有真正的 `pause()` API，暂停只能 `stop()` + 记住断点，恢复时重新合成
- `speak()` 返回 `TextToSpeech.QUEUE_ADD` 模式（追加到队列末尾），不打断当前朗读
- `utteranceId` 是核心追踪机制 —— `UtteranceProgressListener.onDone(utteranceId)` 精确告知哪句话读完

### 4.3 TtsTextExtractor（文本提取 + 分句）

```kotlin
// tts/TtsTextExtractor.kt
package com.huangder.lumibooks.tts

class TtsTextExtractor {
    /**
     * 从章节文本中提取当前页的纯文本。
     * @param chapterText 章节完整文本（EPUB 调用 .toString() 得到纯文本）
     * @param startCharOffset 当前页在章节中的起始字符偏移
     * @param endCharOffset 当前页在章节中的结束字符偏移
     */
    fun extractPageText(
        chapterText: String,
        startCharOffset: Int,
        endCharOffset: Int
    ): String {
        val safeStart = startCharOffset.coerceIn(0, chapterText.length)
        val safeEnd = endCharOffset.coerceIn(0, chapterText.length)
        return chapterText.substring(safeStart, safeEnd).trim()
    }

    /**
     * 中文分句。
     * 分割优先级：中文标点(。！？；) > 英文标点(.!?) > 段落(\\n\\n) > 逗号(，,) > 硬截断(200字符)
     */
    fun splitIntoSentences(text: String): List<String>
}
```

**分句正则**：
```kotlin
// 核心分句逻辑
private val CJK_TERMINATOR = Regex("([。！？；])")
private val WESTERN_TERMINATOR = Regex("([.!?])\\s+")
```

**边界处理**：
- 空页/纯空白页 → 返回空列表，触发立即翻页
- 纯图片页 → 跳过
- 页末不完整句 → 拼接下一页第一句

### 4.4 TtsController（核心编排层）

```kotlin
// tts/TtsController.kt
package com.huangder.lumibooks.tts

@Singleton
class TtsController @Inject constructor(
    private val ttsEngine: TtsEngine,
    private val textExtractor: TtsTextExtractor,
    private val dataStoreManager: DataStoreManager
) {
    val playbackState: StateFlow<TtsPlaybackState>

    // 启动：传入文本提供者 lambda + 起始位置
    suspend fun start(
        textProvider: suspend (chapterIndex: Int) -> String?,
        startChapter: Int,
        startPage: Int,
        chapterCount: Int
    )

    // 暂停/恢复
    fun pause()
    fun resume()

    // 停止
    fun stop()

    // 跳过：forward=true 前进一句，false 后退一句
    fun skip(forward: Boolean = true)

    // 语速/音调
    suspend fun setSpeechRate(rate: Float)
    suspend fun setPitch(pitch: Float)

    // 每页朗读完成后调用（由外部页面切换触发）
    fun onPageReady(chapterIndex: Int, pageIndex: Int)
}
```

**状态机流程**：

```
IDLE ──[start()]──→ INITIALIZING ──→ PLAYING
                                         │
                     ← resume() ← PAUSED │
                                         │
    (每句完成) onDone(utteranceId) ──────┤
      └─ 页末句 → advanceToNextPage() ──┘
      └─ 全书完 → stop() → IDLE
```

**自动翻页流程**：
```
1. speakCurrentPage()
   - 通过 textProvider 获取章节文本
   - extractPageText() + splitIntoSentences()
   - 逐句 ttsEngine.speak(sentence, "ch{ch}_pg{pg}_s{i}")
   - 标记最后一句话的 utteranceId

2. UtteranceProgressListener.onDone(utteranceId)
   - 如果是页末句 → advanceToNextPage()
   - 否则什么都不做（队列自动播下一句）

3. advanceToNextPage()
   - 检查下一页/下一章是否存在
   - 通知 ReadView 翻页（通过回调）
   - 等待 onPageReady() → speakCurrentPage()

4. onPageReady(chapterIndex, pageIndex)
   - 翻页完成后被调用
   - 继续朗读新页面
```

**预加载优化**：当前页句子全部入队后，立即异步调用 `textProvider(nextChapter)` + 分句，翻页后跳过 IO 直接入队。

### 4.5 TtsForegroundService + TtsNotificationManager

```kotlin
// service/TtsForegroundService.kt
@AndroidEntryPoint
class TtsForegroundService : Service() {
    @Inject lateinit var ttsController: TtsController
    @Inject lateinit var notificationManager: TtsNotificationManager

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        // 创建 MediaSession
        mediaSession = MediaSessionCompat(this, "TtsPlayback").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = ttsController.resume()
                override fun onPause() = ttsController.pause()
                override fun onStop() { ttsController.stop(); stopSelf() }
                override fun onSkipToNext() = ttsController.skip(forward = true)
                override fun onSkipToPrevious() = ttsController.skip(forward = false)
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notificationManager.buildNotification(...))
        // TtsController 已在 ReaderViewModel.startTts() 中启动
        return START_STICKY
    }
}
```

```kotlin
// service/TtsNotificationManager.kt
class TtsNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 创建通知渠道（在 EBookReaderApp.onCreate() 中调用）
    fun createChannel()

    // 构建 MediaStyle 通知
    fun buildNotification(
        bookTitle: String,
        chapterTitle: String,
        isPlaying: Boolean,
        mediaSession: MediaSessionCompat
    ): Notification
}
```

### 4.6 TtsPlayerPanel（UI 浮动面板）

```kotlin
// ui/reader/TtsPlayerPanel.kt
@Composable
fun TtsPlayerPanel(
    playbackState: TtsPlaybackState,
    speechRate: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onRateChange: (Float) -> Unit,
    capsuleBgColor: Color,
    capsuleContentColor: Color,
    modifier: Modifier = Modifier
)
```

**视觉布局**（水平行，胶囊风格，参考 FloatingReaderMenu 的 ActionCapsule）：

```
┌──────────────────────────────────────────────┐
│ [◀◀] [▶/⏸] [▶▶]   [语速: 0.75×|1.0×|1.5×]  [✕] │
└──────────────────────────────────────────────┘
```

- `RoundedCornerShape(24.dp)`，跟随阅读器主题背景色
- `AnimatedVisibility` 从底部滑入/滑出
- `playbackState == INITIALIZING` 时显示旋转 CircularProgressIndicator
- 面板位置：`Alignment.BottomCenter`，`navigationBarsPadding()` 之上

### 4.7 ReaderViewModel 集成点

**ReaderUiState 新增字段**（在现有 data class 末尾追加）：
```kotlin
data class ReaderUiState(
    // ... 现有字段保持不变 ...
    val ttsPlaybackState: TtsPlaybackState = TtsPlaybackState.IDLE,
    val ttsSpeechRate: Float = 1.0f,
)
```

**ReaderViewModel 构造函数新增参数**：
```kotlin
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    private val dataStoreManager: DataStoreManager,
    private val ttsController: TtsController  // ← 新增
) : ViewModel()
```

**新增控制方法**：
```kotlin
fun startTts() {
    // 1. 初始化 TTS 引擎
    // 2. 启动 ForegroundService
    // 3. 调用 ttsController.start(textProvider = { getChapterText(it)?.toString() }, ...)
}

fun stopTts() { ttsController.stop(); /* stopService */ }
fun toggleTtsPlayPause() { if (playing) ttsController.pause() else ttsController.resume() }
fun ttsSkipForward() { ttsController.skip(true) }
fun ttsSkipBackward() { ttsController.skip(false) }
fun setTtsSpeechRate(rate: Float) { viewModelScope.launch { ttsController.setSpeechRate(rate) } }
```

**状态收集**（在 init 块中）：
```kotlin
viewModelScope.launch {
    ttsController.playbackState.collectLatest { state ->
        _uiState.value = _uiState.value.copy(ttsPlaybackState = state)
    }
}
```

### 4.8 ReaderScreen 集成点

**1. 启动入口**：在 `FloatingReaderMenu` 底部胶囊栏中新增"听书"按钮（`ActionCapsule`，图标：Headphones/MusicNote），点击调用 `viewModel.startTts()`

**2. TTS 面板**：在 `ReaderScreen` 的顶层 `Box` 内，`FloatingReaderMenu` 之上添加：
```kotlin
AnimatedVisibility(
    visible = uiState.ttsPlaybackState != TtsPlaybackState.IDLE,
    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        .padding(bottom = if (uiState.isMenuVisible) 80.dp else 48.dp)
) {
    TtsPlayerPanel(...)
}
```

**3. 权限请求**：启动 TTS 前检查 `POST_NOTIFICATIONS`（API 33+），使用 `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`。

### 4.9 ReadView 新增方法

```kotlin
// ReadView.kt 新增（不对现有行为做任何改动）
/**
 * 查询下一页位置（不执行翻页），供 TtsController 调用。
 * @return Pair<chapterIndex, pageInChapter>，如果是全书末尾返回 (-1, -1)
 */
fun getNextPageLocation(): Pair<Int, Int> {
    val cur = slotManager.getCurSlot()
    val layout = layoutEngine.getChapterLayout(cur.chapterIndex) ?: return Pair(-1, -1)
    return if (cur.pageIndex + 1 < layout.totalPages) {
        Pair(cur.chapterIndex, cur.pageIndex + 1)
    } else if (cur.chapterIndex + 1 < chapterCount) {
        Pair(cur.chapterIndex + 1, 0)
    } else {
        Pair(-1, -1)
    }
}

// 同理 getPrevPageLocation(): Pair<Int, Int>
```

### 4.10 DataStoreManager 新增

在 `companion object` 中添加（模仿现有键命名风格）：
```kotlin
private val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
private val TTS_PITCH = floatPreferencesKey("tts_pitch")
```

新增 Flow 属性和 setter（模仿现有 `fontSize` 等字段的模式）。

### 4.11 AppModule 新增

```kotlin
@Provides @Singleton
fun provideTtsEngine(@ApplicationContext context: Context): TtsEngine = TtsEngine(context)

@Provides @Singleton
fun provideTtsTextExtractor(): TtsTextExtractor = TtsTextExtractor()

@Provides @Singleton
fun provideTtsController(
    ttsEngine: TtsEngine,
    textExtractor: TtsTextExtractor,
    dataStoreManager: DataStoreManager
): TtsController = TtsController(ttsEngine, textExtractor, dataStoreManager)
```

### 4.12 AndroidManifest 新增

```xml
<!-- 新增权限（在 manifest 根标签下，与其他 uses-permission 并列） -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- 新增 Service（在 application 标签内） -->
<service
    android:name=".service.TtsForegroundService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback" />
```

### 4.13 依赖检查

**预计无需新增 Gradle 依赖**。`TextToSpeech` 是 `android.speech.tts` 内置包，`MediaSessionCompat` 来自已存在的 `androidx.core`。唯一可能需要在 `app/build.gradle.kts` 中添加的是 `androidx.media:media`（如果未间接依赖）。

---

## 5. 实现顺序

**按以下顺序实施，每步编译验证**：

| 步骤 | 文件 | 操作 |
|------|------|------|
| 1 | `DataStoreManager.kt` | 新增 TTS 偏好键 + Flow + setter |
| 2 | `TtsPlaybackState.kt` | 新建枚举 |
| 3 | `TtsEngine.kt` | 新建 TextToSpeech 封装 |
| 4 | `TtsTextExtractor.kt` | 新建文本提取+分句 |
| 5 | `AppModule.kt` | 新增 3 个 @Provides |
| 6 | `TtsController.kt` | 新建播放状态机 |
| 7 | `TtsNotificationManager.kt` | 新建通知管理 |
| 8 | `TtsForegroundService.kt` | 新建前台 Service |
| 9 | `AndroidManifest.xml` | 新增权限+Service 声明 |
| 10 | `ReadView.kt` | 新增 getNextPageLocation/getPrevPageLocation |
| 11 | `ReaderViewModel.kt` | 集成 TtsController + TTS 状态 + 控制方法 |
| 12 | `TtsPlayerPanel.kt` | 新建浮动播放面板 Composable |
| 13 | `ReaderScreen.kt` | 集成面板 + 启动按钮 + 权限请求 |
| 14 | `strings.xml` | 新增 TTS 相关字符串资源 |
| 15 | `app/build.gradle.kts` | 验证依赖（按需补充） |

**每一步完成后执行**：`./gradlew compileDebugKotlin` 验证编译通过。

---

## 6. 风险与边界

| 风险 | 缓解措施 |
|------|---------|
| TTS 引擎首次初始化需 1-3 秒 | `TtsEngine.engineStatus` 暴露状态，UI 显示初始化指示器 |
| 翻页延迟导致朗读断档 | 预取下一页文本并提前分句；PageSlotManager 已预加载 NEXT 槽位 |
| 屏幕关闭/切后台时 ViewModel 销毁 | `TtsController` 是 `@Singleton`，不依赖任何 ViewModel/View 生命周期 |
| 大章节全部入队导致 TTS 内存压力 | 每次只入队一页（约 8-15 句），页尾触发下一页 |
| EPUB 的 ImageSpan 在 `.toString()` 时残留乱码 | 使用章节级文本 + PageLayout 偏移切片的方案，完全绕过 ImageSpan |
| 部分国产 ROM 无内置 TTS 引擎 | `initialize()` 返回 `Result.failure`，UI 提示用户安装 Google 文字转语音 |
| 中文分句准确性 | 中文标点 `。！？；` 优先分割；超 200 字符无标点时按逗号或硬截断 |
| `POST_NOTIFICATIONS` 权限被拒绝 | 降级运行（Service 可启动，仅通知不显示） |

---

## 7. 验证清单

- [ ] `./gradlew compileDebugKotlin` 编译通过
- [ ] `./gradlew assembleDebug` 完整构建通过
- [ ] 打开 EPUB 书籍 → 点击听书按钮 → TTS 开始朗读
- [ ] 浮动面板出现，播放/暂停/跳过/停止按钮功能正常
- [ ] 当前页朗读完毕 → 自动翻到下一页继续
- [ ] 当前章节朗读完毕 → 自动进入下一章继续
- [ ] 按 Home 键 → TTS 继续朗读，通知栏显示控制条
- [ ] 通知栏按钮（播放/暂停/跳过/关闭）功能正常
- [ ] 语速 0.75×/1.0×/1.5× 调节正常且偏好持久化
- [ ] 重新打开 App → 语速偏好保持
- [ ] 关闭听书 → 浮动面板消失，通知消失，Service 停止
- [ ] TXT 格式书籍 → 朗读正常
- [ ] 最后一章最后一页 → 自动停止，提示"朗读完成"

---

## 8. 关键文件路径速查

```
# 新建
app/src/main/java/com/huangder/lumibooks/tts/TtsPlaybackState.kt
app/src/main/java/com/huangder/lumibooks/tts/TtsEngine.kt
app/src/main/java/com/huangder/lumibooks/tts/TtsTextExtractor.kt
app/src/main/java/com/huangder/lumibooks/tts/TtsController.kt
app/src/main/java/com/huangder/lumibooks/service/TtsForegroundService.kt
app/src/main/java/com/huangder/lumibooks/service/TtsNotificationManager.kt
app/src/main/java/com/huangder/lumibooks/ui/reader/TtsPlayerPanel.kt

# 修改
app/src/main/java/com/huangder/lumibooks/di/AppModule.kt
app/src/main/java/com/huangder/lumibooks/data/local/DataStoreManager.kt
app/src/main/java/com/huangder/lumibooks/ui/reader/ReaderViewModel.kt
app/src/main/java/com/huangder/lumibooks/ui/reader/ReaderScreen.kt
app/src/main/java/com/huangder/lumibooks/ui/reader/engine/ReadView.kt
app/src/main/AndroidManifest.xml
app/src/main/res/values/strings.xml
app/build.gradle.kts  # 验证依赖即可，大概率无需修改
```
