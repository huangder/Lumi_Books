# 文阅 - 设计规范

## 版本信息
- 版本：v1.1
- 日期：2026-06-18
- 状态：已更新

## 一、设计原则

### 1.1 设计理念
- **简洁**：界面清晰，无多余元素
- **舒适**：阅读体验优先
- **一致**：统一的视觉语言
- **沉浸**：全屏阅读体验

### 1.2 设计参考
- iOS设计风格：简洁、优雅
- MIUI设计风格：圆角、柔和
- Apple Books：阅读体验

## 二、颜色系统

### 2.1 浅色主题
```kotlin
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),        // 主色调 - iOS蓝
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    secondary = Color(0xFF5856D6),      // 次要色
    background = Color(0xFFF2F2F7),     // 背景色 - iOS灰
    surface = Color.White,
    onBackground = Color(0xFF1C1C1E),   // 主要文字
    onSurface = Color(0xFF3C3C43),      // 次要文字
    error = Color(0xFFFF3B30),          // 错误色
    onError = Color.White
)
```

### 2.2 深色主题
```kotlin
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),        // 主色调 - iOS蓝（深色）
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1C3A5F),
    secondary = Color(0xFF5E5CE6),      // 次要色
    background = Color(0xFF000000),     // 背景色 - 纯黑
    surface = Color(0xFF1C1C1E),        // 表面色
    onBackground = Color(0xFFFFFFFF),   // 主要文字
    onSurface = Color(0xFFEBEBF5),      // 次要文字
    error = Color(0xFFFF453A),          // 错误色
    onError = Color.Black
)
```

### 2.3 阅读主题
```kotlin
enum class ReaderTheme(
    val background: Color,
    val textColor: Color,
    val name: String
) {
    DAY(Color(0xFFFFFFFF), Color(0xFF1C1C1E), "日间"),
    NIGHT(Color(0xFF1C1C1E), Color(0xFFEBEBF5), "夜间"),
    SEPIA(Color(0xFFF5E6D3), Color(0xFF3E2723), "护眼"),
    GREEN(Color(0xFFE8F5E9), Color(0xFF1B5E20), "绿色")
}
```

## 三、字体系统

### 3.1 字体层级
```kotlin
val Typography = Typography(
    h1 = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp
    ),
    h2 = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp
    ),
    h3 = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp
    ),
    body1 = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp
    ),
    body2 = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    caption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    )
)
```

### 3.2 阅读字体
```kotlin
enum class ReaderFont(
    val fontFamily: FontFamily,
    val name: String
) {
    SYSTEM(FontFamily.Default, "系统字体"),
    SERIF(FontFamily.Serif, "宋体"),
    SANS_SERIF(FontFamily.SansSerif, "黑体"),
    MONOSPACE(FontFamily.Monospace, "等宽字体")
}
```

## 四、间距系统

### 4.1 基础间距
```kotlin
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}
```

### 4.2 圆角系统
```kotlin
object CornerRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val full = 999.dp
}
```

## 五、组件规范

### 5.1 按钮
**主要按钮**
```kotlin
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(CornerRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
```

**次要按钮**
```kotlin
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(CornerRadius.md),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
```

### 5.2 卡片
```kotlin
@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CornerRadius.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column {
            // 封面图片
            AsyncImage(
                model = book.coverPath,
                contentDescription = book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f),
                contentScale = ContentScale.Crop
            )
            
            // 书籍信息
            Column(
                modifier = Modifier.padding(Spacing.md)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.body1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
```

### 5.3 输入框
```kotlin
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}
```

## 六、页面布局

### 6.1 首页布局
```
┌─────────────────────────────────┐
│           应用标题               │
├─────────────────────────────────┤
│      今日阅读时长：00:00:00      │
├─────────────────────────────────┤
│  ┌─────┐ ┌─────┐ ┌─────┐      │
│  │封面1│ │封面2│ │封面3│      │
│  ├─────┤ ├─────┤ ├─────┤      │
│  │标题1│ │标题2│ │标题3│      │
│  │作者1│ │作者2│ │作者3│      │
│  └─────┘ └─────┘ └─────┘      │
├─────────────────────────────────┤
│  [首页]    [统计]               │
└─────────────────────────────────┘
```

### 6.2 统计页布局
```
┌─────────────────────────────────┐
│           统计                   │
├─────────────────────────────────┤
│  今日阅读：00:00:00             │
│  本月阅读：00:00:00             │
│  日目标：30分钟                 │
│  完成度：████████░░ 80%         │
├─────────────────────────────────┤
│  ┌─────────────────────────┐   │
│  │      柱状图              │   │
│  │  ▓                      │   │
│  │  ▓  ▓                   │   │
│  │  ▓  ▓  ▓                │   │
│  │  ▓  ▓  ▓  ▓             │   │
│  └─────────────────────────┘   │
├─────────────────────────────────┤
│  [首页]    [统计]               │
└─────────────────────────────────┘
```

### 6.3 阅读界面布局
```
┌─────────────────────────────────┐
│                                 │
│  ┌─────────────────────────┐   │
│  │                         │   │
│  │      阅读内容            │   │
│  │                         │   │
│  │                         │   │
│  └─────────────────────────┘   │
│                                 │
│  点击中间区域显示菜单：          │
│  ┌─────────────────────────┐   │
│  │  目录  字体  主题  亮度  │   │
│  ├─────────────────────────┤   │
│  │  书签  笔记  搜索  进度  │   │
│  └─────────────────────────┘   │
└─────────────────────────────────┘
```

## 七、动画规范

### 7.1 翻页动画（Apple Books 风格视差翻页）

**设计理念**
- 页面保持绝对平整，无任何 3D 弯曲或形变
- 双层视差结构：上层（当前页）100% 速度跟随手指，下层（下一页）25% 速度小范围渐显
- 近大远小：上层移动范围大（完整滑出屏幕），下层移动范围小（顺势渐显）
- 上层带模糊阴影 + 屏幕 R 角圆角，体现纸张层级感
- 松手后 cubic-bezier 缓动完成吸附或回弹

**手势参数**
| 参数 | 值 | 说明 |
|---|---|---|
| 方向锁定阈值 | 8px | 水平移动 > 8px 且 > 垂直移动才激活拖拽 |
| 翻页距离阈值 | 25% 屏宽 | 拖拽距离超过屏幕宽度 25% 即触发翻页 |
| 翻页速度阈值 | 400px/s | 快速轻扫即使距离不够也触发翻页 |
| 点击时间阈值 | 300ms | 无明显移动且 < 300ms 视为点击 |
| 点击区域划分 | 左 30% / 中 40% / 右 30% | 左侧上一页、右侧下一页、中间切换菜单 |

**视差参数**
| 层级 | 元素 | 移动速度 | 说明 |
|---|---|---|---|
| 上层（近） | overlay div（当前页快照） | 100%（跟随手指） | 完整滑出屏幕 |
| 下层（远） | body（CSS column 长条） | 25%（手指的 1/4） | 小范围渐显滑入 |

**上层阴影参数**
| 属性 | 值 | 说明 |
|---|---|---|
| box-shadow | `0 0 24px rgba(0,0,0,0.18), 2px 0 8px rgba(0,0,0,0.10)` | 模糊阴影区分层级 |
| border-radius | `0 Rpx Rpx 0`（R = 屏幕圆角） | 滑出时右上右下为圆角 |
| 边缘阴影条 | 80px 宽，渐变 0.20→0.06→transparent | 拖拽侧边缘的线性渐变 |

**缓动曲线**
| 场景 | 曲线 | 说明 |
|---|---|---|
| 翻页吸附 | `cubic-bezier(0.2, 0.8, 0.2, 1)` | 快速启动 + 长尾减速 |
| 回弹/弹性 | `cubic-bezier(0.25, 0.9, 0.25, 1)` | 柔和弹性 |

**动画时序**
```
章节内翻页（JS 驱动）：
  拖拽中 → overlay translateX(dragX) + body translateX(dragX*0.25)
  松手 → overlay 350ms cubic-bezier 滑出 + body 350ms 吸附到新页

跨章节翻页（Compose 驱动）：
  滑出 → 300ms FastOutSlowInEasing（当前章节移出视口）
  切换 → 加载新章节 HTML
  等待 → snapshotFlow 检测新章节就绪
  滑入 → 300ms FastOutSlowInEasing（新章节从对侧进入）
```

**橡皮筋效果**
- 第一页右滑 / 最后一页左滑时，位移衰减为 25%，提供边界反馈

### 7.2 菜单动画
```kotlin
val menuAnimation = tween<Float>(
    durationMillis = 200,
    easing = FastOutSlowInEasing
)
```

### 7.3 页面转场
```kotlin
val pageTransition = fadeIn() + slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth / 4 }
)
```

## 八、图标规范

### 8.1 图标库
- 使用Material Icons
- 统一使用Outlined风格
- 尺寸：24.dp

### 8.2 常用图标
```kotlin
object AppIcons {
    val Home = Icons.Outlined.Home
    val Statistics = Icons.Outlined.BarChart
    val Add = Icons.Outlined.Add
    val Search = Icons.Outlined.Search
    val Settings = Icons.Outlined.Settings
    val Bookmark = Icons.Outlined.Bookmark
    val Note = Icons.Outlined.Note
    val FontSize = Icons.Outlined.FormatSize
    val Theme = Icons.Outlined.Palette
    val Brightness = Icons.Outlined.Brightness6
}
```

## 九、响应式设计

### 9.1 屏幕适配
- 手机：单列布局
- 平板：双列布局
- 折叠屏：自适应布局

### 9.2 尺寸断点
```kotlin
object Breakpoints {
    val compact = 600.dp
    val medium = 840.dp
    val expanded = 1200.dp
}
```

## 十、无障碍设计

### 10.1 内容描述
- 所有图片提供contentDescription
- 按钮提供语义描述

### 10.2 触摸目标
- 最小触摸目标：48.dp
- 按钮间距：8.dp

### 10.3 对比度
- 文字与背景对比度 ≥ 4.5:1
- 大文字对比度 ≥ 3:1
