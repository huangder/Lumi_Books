# UI 设计实现文档 - Lumi 设计模板


---

## 颜色系统

### 主色
```
强调色（粉红/珊瑚）: #E85D5D
强调色浅（用于背景）: #E85D5D1A (10% opacity)
页面背景: #FBFBFC
卡片背景: #FFFFFF
灰色背景: #F2F2F7
深色文字: #000000
次要文字: #6E6E73
分割线: #E5E5EA
```

### 阅读主题色
```
日间: 背景 #FFFFFF, 文字 #000000
夜间: 背景 #1C1C1E, 文字 #EBEBF5
护眼: 背景 #F5E6D3, 文字 #3E2723
护眼绿: 誓景 #E8F5E9, 文字 #1B5E20
```

### 高亮颜色
```
黄色: #FFEB3B
粉色: #FF8A80
绿色: #69F0AE
蓝色: #82B1FF
棕色: #BCAAA4
灰色: #BDBDBD
```

---

## 圆角系统
```
胶囊按钮: 28dp (RoundedCornerShape(28.dp))
卡片: 16dp (RoundedCornerShape(16.dp))
关闭按钮: CircleShape (圆形)
小卡片: 12dp (RoundedCornerShape(12.dp))
输入框: 26dp (RoundedCornerShape(26.dp))
```

## 阴影系统
```
TabBar: elevation = 40dp, shape = CircleShape, 羽化阴影
卡片: elevation = 12dp, shape = RoundedCornerShape(16.dp)
弹窗: shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
关闭按钮: elevation = 8dp, shape = CircleShape
```

---

## Page1 - 开屏欢迎页

### 布局结构
```
Column (全屏, 白色背景, 垂直居中)
├── Spacer (weight 1f, 推到中间)
├── App Icon (粉色圆角矩形 + 书本图案)
├── Spacer (24dp)
├── 「欢迎使用」(黑色, 32sp, Bold, DingliSong)
├── 「Lumi」(粉色 #E85D5D, 36sp, Bold)
├── Spacer (weight 1f, 推到底部)
├── 隐私说明区域
│   ├── 人物 Icon (24dp, 粉色)
│   ├── 隐私文字 (12sp, 灰色, 多行)
│   │   "Lumi 是一款纯粹的本地图书阅读器。我们绝不会在未经许可的情况下收集任何个人信息，且承诺永久不设网络账号服务。所有的阅读数据均储存在您的本地设备中，请务必定期做好数据备份以防丢失。Lumi 坚持最小权限原则，不会向您索取任何无关的敏感权限。点击"继续"按钮，即表示您已阅读并同意《隐私政策》和《用户协议》。"
│   │   其中「《隐私政策》」和「《用户协议》」用 #E85D5D 高亮
├── Spacer (24dp)
├── Row (两个按钮, 水平排列, 间距 16dp)
│   ├── 「退出」按钮
│   │   背景: #F2F2F7
│   │   文字: #000000, 16sp, Medium
│   │   圆角: 28dp (胶囊)
│   │   高度: 52dp
│   │   宽度: weight 1f
│   │   点击: finish() 退出 App
│   └── 「继续」按钮
│       背景: #E85D5D
│       文字: #FFFFFF, 16sp, Medium
│       圆角: 28dp (胶囊)
│       高度: 52dp
│       宽度: weight 1f
│       点击: 保存标记 → 跳转主页
└── Spacer (底部安全区)
```

### App Icon 绘制
```
使用 Canvas 或 Box + 背景:
- 外框: 80dp x 80dp, 圆角 20dp, 背景 #E85D5D
- 内部: 白色书本图案 (可用 Icon 或 Canvas 绘制)
- 书本有可爱的表情 (可简化为圆点眼睛 + 弧线嘴巴)
```

### 数据存储
```kotlin
// DataStoreManager.kt 添加:
private val HAS_SEEN_WELCOME = booleanPreferencesKey("has_seen_welcome")

val hasSeenWelcome: Flow<Boolean> = context.dataStore.data.map { preferences ->
    preferences[HAS_SEEN_WELCOME] ?: false
}

suspend fun saveHasSeenWelcome(seen: Boolean) {
    context.dataStore.edit { preferences ->
        preferences[HAS_SEEN_WELCOME] = seen
    }
}
```

### 导航逻辑
```kotlin
// NavGraph.kt 修改:
val hasSeenWelcome by viewModel.hasSeenWelcome.collectAsState(initial = null)

NavHost(
    navController = navController,
    startDestination = when (hasSeenWelcome) {
        null -> Screen.Home.route // 加载中，先显示 Home
        true -> Screen.Home.route
        false -> Screen.Welcome.route
    }
) {
    composable(Screen.Welcome.route) {
        WelcomeScreen(
            onContinue = {
                viewModel.saveHasSeenWelcome(true)
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Welcome.route) { inclusive = true }
                }
            },
            onExit = { finish() }
        )
    }
    // ... 其他路由
}
```

---

## Page3 - 今日阅读 Sheet

### 布局结构
```
Box (全屏)
├── 遮罩 (黑色 10% 透明度, 点击关闭)
└── Sheet (底部弹出, 白色背景, 顶部圆角 24dp)
    └── Column (padding 24dp)
        ├── Row (标题栏)
        │   ├── Spacer (weight 1f)
        │   └── 关闭按钮 (圆形 36dp, 背景 #F2F2F7, X 图标 18dp)
        ├── Spacer (16dp)
        ├── 「今日阅读」(20sp, Bold, DingliSong)
        ├── Spacer (32dp)
        ├── Row (居中, 垂直底部对齐)
        │   ├── 「0」(48sp, Bold, 黑色)
        │   └── 「分钟」(16sp, 灰色, 底部对齐)
        ├── Spacer (8dp)
        ├── 「目标 5 分钟」(14sp, 灰色, 居中)
        ├── Spacer (24dp)
        ├── 进度条 (高度 8dp, 圆角 4dp, 背景 #E5E5EA, 填充 #E85D5D)
        ├── Spacer (8dp)
        ├── 「还剩 4 分钟达标」(12sp, 灰色, 居中)
        ├── Spacer (24dp)
        ├── 「正在阅读」卡片
        │   背景: #F2F2F7, 圆角 12dp, padding 16dp
        │   Row:
        │   ├── Column (weight 1f)
        │   │   ├── 「正在阅读」(12sp, 灰色)
        │   │   └── 书名 (16sp, SemiBold, 黑色)
        │   └── 「1分钟」(16sp, 灰色)
        ├── Spacer (16dp)
        ├── 连续阅读打卡区域
        │   Row (水平排列, 垂直居中)
        │   ├── 7 个圆圈 (每个 28dp)
        │   │   - 达标: 蓝色实心 (#4FC3F7 或类似)
        │   │   - 未达标: 灰色描边 (#E5E5EA)
        │   ├── Spacer (weight 1f)
        │   └── 「连胜 3 天」(14sp, 蓝色 #4FC3F7, SemiBold)
        ├── Spacer (16dp)
        ├── Row (两个按钮)
        │   ├── 「修改每日目标」(weight 1f, 背景 #F2F2F7, 圆角 28dp, 高度 48dp)
        │   └── Spacer (12dp)
        │   └── 分享按钮 (48dp 圆形, 背景 #E85D5D, 分享 Icon 白色 20dp)
    └── Spacer (底部安全区)
```

### 关闭按钮样式
```
圆形 36dp
背景: #F2F2F7
图标: X (Icons.Outlined.Close), 18dp, 灰色 #6E6E73
点击: 关闭 Sheet
```

### 进度条
```
LinearProgressIndicator:
- 高度: 8dp
- 圆角: 4dp (clip(RoundedCornerShape(4.dp)))
- 颜色: #E85D5D
- 轨道: #E5E5EA
```

### 连续阅读打卡
```
7 个圆圈，代表周日到周六:
- 今天及之前达标: 蓝色实心圆
- 今天及之前未达标: 灰色描边圆
- 今天之后: 不显示或灰色描边

连胜天数计算:
- 从今天往前数，连续达标天数
- 中间断了一天则清零
- 需要在 Room 数据库中记录每日达标状态
```

### 分享按钮
```
圆形 48dp
背景: #E85D5D (带 8dp 阴影)
图标: Icons.Outlined.Share, 20dp, 白色
点击: 打开 ShareSheet
```

---

## Page4 - 分享 Sheet

### 布局结构
```
Box (全屏)
├── 遮罩 (黑色 10% 透明度)
└── Sheet (底部弹出, 白色背景, 顶部圆角 24dp)
    └── Column (padding 24dp)
        ├── Row (标题栏)
        │   ├── 「分享」(20sp, Bold, DingliSong)
        │   ├── Spacer (weight 1f)
        │   └── 关闭按钮 (圆形 36dp, 背景 #F2F2F7, 向下箭头 v 图标)
        ├── Spacer (24dp)
        ├── 分享海报 (居中, 宽度 80% 屏宽)
        │   - 使用 分享海报底版.png 作为背景
        │   - 在海报上叠加:
        │     - 「我在」(白色, 18sp)
        │     - 「Lumi 阅读」(白色, 24sp, Bold)
        │     - 「连续阅读」(白色, 14sp)
        │     - 天数数字 (白色, 72sp, Bold)
        │     - 「天」(白色, 18sp)
        ├── Spacer (24dp)
        ├── 下载按钮 (居中, 48dp 圆形, 背景 #E85D5D, 下载 Icon 白色)
        │   点击: 生成海报图片 → 保存到相册
    └── Spacer (底部安全区)
```

### 海报生成
```kotlin
// 方案 1: 使用 Compose Canvas 绘制
@Composable
fun SharePoster(streakDays: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // 绘制背景 (粉色渐变)
        // 绘制文字
        // 绘制书本图案
    }
}

// 方案 2: 使用 ImageView + 叠加文字
// 加载 分享海报底版.png，然后用 Canvas 叠加文字

// 保存到相册
fun savePosterToAlbum(context: Context, bitmap: Bitmap) {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "lumi_share_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Lumi")
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        resolver.openOutputStream(it)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        }
    }
}
```

---

## Page5 - 高亮与笔记 Sheet

### 布局结构
```
Box (全屏)
├── 遮罩 (黑色 10% 透明度)
└── Sheet (底部弹出, 白色背景, 顶部圆角 24dp, 高度 60% 屏幕)
    └── Column (padding 24dp)
        ├── Row (标题栏)
        │   ├── 「高亮与笔记」(20sp, Bold, DingliSong)
        │   ├── Spacer (weight 1f)
        │   └── 关闭按钮 (圆形 36dp, 背景 #F2F2F7, 向下箭头 v)
        ├── Spacer (16dp)
        ├── Tab 切换器
        │   Row (背景 #F2F2F7, 圆角 20dp, padding 2dp)
        │   ├── 「高亮 (N)」(选中: 白色背景, 圆角 18dp, 阴影; 未选中: 透明)
        │   └── 「笔记 (N)」(同上)
        ├── Spacer (16dp)
        ├── LazyColumn (weight 1f)
        │   └── 列表项 (每个)
        │       背景: #FFFBF0 (浅米色)
        │       圆角: 12dp
        │       padding: 16dp
        │       margin: 8dp 垂直
        │       Row:
        │       ├── 黄色竖条 (4dp 宽, 高度 100%, 背景 #FFEB3B, 圆角 2dp)
        │       ├── Spacer (12dp)
        │       └── Column (weight 1f)
        │           ├── 「被高亮的文字XXXXX」(14sp, 黑色, 最多2行)
        │           ├── Spacer (4dp)
        │           └── Row
        │               ├── 「第X章」(12sp, 粉色 #E85D5D)
        │               ├── Spacer (weight 1f)
        │               └── 「日期7/12」(12sp, 粉色 #E85D5D)
```

### Tab 切换器样式
```
外层容器:
- 背景: #F2F2F7
- 圆角: 20dp
- padding: 2dp
- 高度: 40dp

选中 Tab:
- 背景: White
- 圆角: 18dp
- 阴影: 2dp elevation
- 文字: 14sp, SemiBold, 黑色

未选中 Tab:
- 背景: Transparent
- 文字: 14sp, 灰色 #6E6E73
```

### 高亮列表项
```
容器:
- 背景: #FFFBF0 (浅米色)
- 圆角: 12dp
- padding: 16dp

左侧竖条:
- 宽度: 4dp
- 高度: 填满父容器高度
- 背景: #FFEB3B (黄色)
- 圆角: 2dp

文字:
- 被高亮文字: 14sp, 黑色, 最多2行
- 章节: 12sp, #E85D5D (粉色)
- 日期: 12sp, #E85D5D (粉色)
```

---

## Page6 - 主题与设置 Sheet

### 布局结构
```
Box (全屏)
├── 遮罩 (黑色 10% 透明度)
└── Sheet (底部弹出, 白色背景, 顶部圆角 24dp)
    └── Column (padding 24dp)
        ├── Row (标题栏)
        │   ├── 「主题与设置」(20sp, Bold, DingliSong)
        │   ├── Spacer (weight 1f)
        │   └── 关闭按钮 (圆形 36dp, 背景 #F2F2F7, 向下箭头 v)
        ├── Spacer (24dp)
        ├── 「字号」区域
        │   Row:
        │   ├── 「字号」(14sp, 灰色)
        │   ├── Spacer (weight 1f)
        │   └── 「20sp」(14sp, 灰色)
        │   Slider:
        │   - 值范围: 12f..28f
        │   - 颜色: 黑色滑块, 黑色轨道 (选中部分), #E5E5EA 轨道 (未选中)
        ├── Spacer (16dp)
        ├── 「亮度」区域
        │   Row:
        │   ├── 「亮度」(14sp, 灰色)
        │   ├── Spacer (weight 1f)
        │   └── 「80%」(14sp, 灰色)
        │   Slider:
        │   - 值范围: 0f..100f
        │   - 颜色: 黑色滑块, 黑色轨道, #E5E5EA 轨道
        ├── Spacer (16dp)
        ├── 「翻页」和「显示」区域 (跳过，不实现)
        ├── Spacer (16dp)
        ├── 「阅读背景」区域
        │   Text: 「阅读背景」(14sp, 灰色)
        │   Spacer (12dp)
        │   Column (垂直排列, 间距 12dp)
        │   ├── Row (水平排列, 间距 12dp)
        │   │   ├── 「日间」按钮 (weight 1f, 高度 48dp, 圆角 12dp)
        │   │   │   背景: #FFFFFF, 边框: 1dp #E5E5EA (选中时 #000000)
        │   │   │   文字: 14sp, 黑色
        │   │   └── 「夜间」按钮 (weight 1f, 高度 48dp, 圆角 12dp)
        │   │       背景: #1C1C1E, 无边框
        │   │       文字: 14sp, 白色
        │   └── Row (水平排列, 间距 12dp)
        │       ├── 「护眼」按钮 (weight 1f, 高度 48dp, 圆角 12dp)
        │       │   背景: #F5E6D3, 无边框
        │       │   文字: 14sp, #3E2723
        │       └── 「护眼绿」按钮 (weight 1f, 高度 48dp, 圆角 12dp)
        │           背景: #E8F5E9, 无边框
        │           文字: 14sp, #1B5E20
        ├── Spacer (24dp)
        ├── 「高级设置」按钮
        │   背景: #F2F2F7
        │   圆角: 28dp
        │   高度: 48dp
        │   文字: 14sp, SemiBold, 黑色, 居中
        │   点击: 打开高级设置 Sheet
    └── Spacer (底部安全区)
```

### Slider 样式
```
SliderDefaults.colors(
    thumbColor = Color.Black,
    activeTrackColor = Color.Black,
    inactiveTrackColor = Color(0xFFE5E5EA)
)
```

### 阅读背景按钮
```
每个按钮:
- 高度: 48dp
- 圆角: 12dp
- 宽度: weight 1f (两列等宽)

选中状态:
- 日间: 黑色边框 1dp
- 夜间: 白色边框 1dp (或无边框)
- 护眼: 黑色边框 1dp
- 护眼绿: 黑色边框 1dp

未选中状态:
- 无边框
```

---

## Page7 - 高级设置 Sheet

### 布局结构
```
Box (全屏)
├── 遮罩 (黑色 10% 透明度)
└── Sheet (底部弹出, 白色背景, 顶部圆角 24dp, 高度 85% 屏幕)
    └── Column (padding 24dp)
        ├── Row (标题栏)
        │   └── 关闭按钮 (圆形 36dp, 背景 #F2F2F7, 向下箭头 v)
        │   ├── Spacer (weight 1f)
        │   └── 确认按钮 (圆形 36dp, 背景 #000000, 白色勾选 ✓)
        ├── Spacer (16dp)
        ├── 「预览」区域
        │   Text: 「预览」(14sp, 灰色)
        │   Spacer (8dp)
        │   Box (预览框)
        │   - 背景: 当前阅读主题背景色
        │   - 圆角: 12dp
        │   - padding: 根据边距设置
        │   - 内部文字: 示例文字, 随设置实时变化
        ├── Spacer (16dp)
        ├── 「行间距」区域
        │   Row:
        │   ├── 「行间距」(14sp, 灰色)
        │   ├── Spacer (weight 1f)
        │   └── 「1.5x」(14sp, 灰色)
        │   Slider: 值范围 1.0f..2.5f, 步长 0.1f
        ├── Spacer (12dp)
        ├── 「字间距」区域
        │   Row:
        │   ├── 「字间距」(14sp, 灰色)
        │   ├── Spacer (weight 1f)
        │   └── 「4.0sp」(14sp, 灰色)
        │   Slider: 值范围 0f..10f, 步长 0.5f
        ├── Spacer (12dp)
        ├── 「左右边距」区域
        │   Row:
        │   ├── 「左右边距」(14sp, 灰色)
        │   ├── Spacer (weight 1f)
        │   └── 「40dp」(14sp, 灰色)
        │   Slider: 值范围 20f..80f, 步长 2f
        ├── Spacer (12dp)
        ├── 「上下边距」区域
        │   Row:
        │   ├── 「上下边距」(14sp, 灰色)
        │   ├── Spacer (weight 1f)
        │   └── 「40dp」(14sp, 灰色)
        │   Slider: 值范围 32f..120f, 步长 2f
        ├── Spacer (16dp)
        ├── 「字体」区域
        │   Text: 「字体」(14sp, 灰色)
        │   Spacer (12dp)
        │   Column (垂直排列, 间距 12dp)
        │   ├── Row (水平排列, 间距 12dp)
        │   │   ├── 「Aa系统」按钮 (weight 1f, 高度 48dp, 圆角 12dp)
        │   │   │   背景: #F2F2F7, 边框: 1dp #E5E5EA (选中时 #000000)
        │   │   │   文字: 14sp, 黑色
        │   │   └── 「Aa宋体」按钮 (weight 1f, 高度 48dp, 圆角 12dp)
        │   │       背景: #F2F2F7, 无边框
        │   │       文字: 14sp, 黑色
        │   ├── Row (水平排列, 间距 12dp)
        │   │   ├── 「AaMiSans」按钮 (同上)
        │   │   └── 「Aa楷体」按钮 (同上)
        │   └── Row (水平排列, 间距 12dp)
        │       ├── 「Aa仿宋」按钮 (同上)
        │       └── 「导入字体」按钮 (同上, 虚线边框)
        └── Spacer (底部安全区)
```

### 确认按钮
```
圆形 36dp
背景: #000000
图标: ✓ (白色, 18sp, Bold)
点击: 保存设置并关闭 Sheet
```

### 字体按钮
```
每个按钮:
- 高度: 48dp
- 圆角: 12dp
- 宽度: weight 1f (两列等宽)
- 背景: #F2F2F7

选中状态:
- 边框: 1dp #000000
- 文字: 黑色, SemiBold

未选中状态:
- 无边框
- 文字: 黑色

「导入字体」按钮:
- 虚线边框 (用 Canvas 绘制)
- 背景: White
```

---

## Page8 - 目录 Sheet

### 布局结构
```
Box (全屏)
├── 遮罩 (黑色 10% 透明度)
└── Sheet (底部弹出, 白色背景, 顶部圆角 24dp, 高度 70% 屏幕)
    └── Column (padding 24dp)
        ├── Row (标题栏)
        │   ├── 「目录」(20sp, Bold, DingliSong)
        │   ├── Spacer (weight 1f)
        │   └── 关闭按钮 (圆形 36dp, 背景 #F2F2F7, 向下箭头 v)
        ├── Spacer (16dp)
        ├── LazyColumn (weight 1f)
        │   └── 列表项 (每个章节)
        │       背景: #F2F2F7
        │       圆角: 12dp
        │       padding: 16dp 水平, 14dp 垂直
        │       margin: 8dp 垂直
        │       文字: 16sp, 黑色
        │       当前章节: 高亮 (Accent 色或加粗)
        └── Spacer (底部安全区)
```

### 章节列表项
```
容器:
- 背景: #F2F2F7
- 圆角: 12dp
- padding: 16dp (水平), 14dp (垂直)

文字:
- 字体: 16sp
- 颜色: 黑色
- 当前章节: 可用 #E85D5D 或加粗显示
```

---

## 通用关闭按钮样式

所有 Sheet 的关闭按钮统一使用:
```
圆形 36dp
背景: #F2F2F7
图标选项:
- X 图标: Icons.Outlined.Close, 18dp, #6E6E73
- 向下箭头: Icons.Outlined.KeyboardArrowDown, 20dp, #6E6E73
- 勾选: Text("✓"), 16sp, #E85D5D 或白色
点击: 关闭当前 Sheet
```

---

## 动画规范

### Sheet 打开动画
```
1. 遮罩渐显: alpha 0 → 0.1, 300ms
2. 内容从底部滑入: translationY size.height → 0, 300ms, Smooth easing
3. 内容缩放: scale 0.95 → 1.0, 300ms (可选)
4. 内容渐显: alpha 0 → 1, 200ms
```

### Sheet 关闭动画
```
1. 内容渐隐: alpha 1 → 0, 150ms
2. 内容滑出: translationY 0 → size.height, 200ms, Accelerate easing
3. 遮罩渐隐: alpha 0.1 → 0, 200ms
4. 内容缩放: scale 1.0 → 0.95, 200ms (可选)
```

---

## 需要新建的文件

1. `ui/welcome/WelcomeScreen.kt` - 开屏欢迎页
2. `ui/home/ShareSheet.kt` - 分享 Sheet

## 需要修改的文件

1. `data/local/DataStoreManager.kt` - 添加 has_seen_welcome 字段
2. `ui/navigation/Screen.kt` - 添加 Welcome 路由
3. `ui/navigation/NavGraph.kt` - 添加 Welcome 路由 + 首次启动判断
4. `MainActivity.kt` - 可能需要传递 Context 给 WelcomeScreen
5. `ui/home/ReadingGoalSheet.kt` - 重做 UI
6. `ui/reader/ThemeSettingsSheet.kt` - 重做主题设置 + 高级设置
7. `ui/reader/ReaderScreen.kt` - 重做目录 Sheet + 高亮笔记 Sheet
8. `ui/components/FloatingTabBar.kt` - 微调阴影效果

---

## 验证步骤

每个页面完成后:
1. 运行 `gradlew.bat compileDebugKotlin` 检查编译
2. 运行 `gradlew.bat assembleDebug` 构建 APK
3. 安装到真机/模拟器验证 UI 效果
4. 对比设计稿检查颜色、间距、字体是否一致
