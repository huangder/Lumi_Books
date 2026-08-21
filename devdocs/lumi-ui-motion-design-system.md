# Lumi UI 与交互动效设计规范

> 版本：2.0
> 更新日期：2026-08-22
> 状态：当前实现基线，供开发者与 Agent 直接执行

## 0. 文档优先级

本文件记录 2026 年 8 月 UI 重构完成后的现行规则。遇到设计冲突时，按以下顺序判断：

1. 用户在当前任务中的明确要求。
2. 本文件。
3. 当前组件代码与既有页面行为。
4. `design-spec.md`、`ui-design-spec.md` 等早期文档。

早期文档包含历史页面方案和旧视觉参数，不能据此恢复已经移除的布局、连续背景采样或旧版玻璃控件。

## 1. 核心原则

### 1.1 阅读优先

- Lumi 是阅读工具，不是展示型网站。信息密度应适中，装饰不能抢过书籍、进度和正文。
- 高频操作要立即响应，界面动画原则上不超过 `300ms`。
- 进入与退出尽量沿同一路径；锚定内容从触发源附近出现。
- 页面结构稳定，切换主题时只改变颜色、材质和必要的光学表现，不改变功能位置。

### 1.2 三主题同构

应用保留三套外观：

| 主题 | 标识 | 视觉职责 |
|---|---|---|
| Lumi | `lumi` | 默认品牌色、柔和实色表面 |
| Material 3 | `material3` | 系统动态色和 Material 表面 |
| Liquid Glass | `liquid_glass` | 真实背景捕获、模糊、透镜和按压高光 |

三套主题必须共享：

- 页面布局、控件尺寸和点击区域。
- Enabled、disabled、pressed、selected、dragging、loading 和 error 状态。
- 导航语义、TalkBack 文案、RTL 行为和业务回调。
- 内容层级和交互节奏。

不得为了玻璃主题复制一套业务页面。材质差异应收敛在主题令牌和公共组件中。

### 1.3 玻璃不是透明

- 已有玻璃 Dialog、菜单和 Sheet 必须保留玻璃效果。
- 玻璃表面应有足够的底色、边缘和内容对比度，不能变成完全透明层。
- 透明度是用户偏好，范围 `0..1`，默认 `0.55`，设置步长 `0.05`。不得以兼容性为由移除此设置。
- 静止控件保持克制；透镜、色散、速度形变主要服务按压和拖动反馈。

## 2. 能力检测与回退

统一使用 `LiquidGlassCapability`，不要在页面内自行判断 Android 版本。

### 2.1 Liquid Glass 支持条件

必须同时满足：

- Android API 31 或更高。
- 当前窗口启用硬件加速。
- 非电子墨水模式。
- `RenderEffect` 模糊初始化成功。

行为规则：

- 不支持时从主题列表中完全移除 Liquid Glass，不显示灰色禁用项。
- 同时隐藏透明度、HDR 高光和相关说明。
- 已保存的 `liquid_glass` 值不得删除；运行时临时以 Lumi 作为有效主题。
- 能力恢复后，Liquid Glass 自动重新出现，原设置继续生效。
- 缺失 Backdrop 时只能使用可读的材质回退，不允许空白、全透明或半初始化表面。

### 2.2 HDR 高光

HDR 是 Liquid Glass 的独立附加能力，不是主题支持条件。当前要求：

- Android 15/API 35 或更高。
- 显示器同时报告 HDR 和广色域支持。
- Liquid Glass 本身已通过能力检测。

HDR 只增强按下玻璃按钮时的局部高光。不得把正文、整张卡片或整页背景提升为 HDR 内容。

- 不支持 HDR：继续提供 Liquid Glass，只隐藏 HDR 开关。
- 支持 HDR：申请有限的 `1.15x` headroom，避免 OEM 对整窗 SDR 内容做明显压暗或产生条带。
- 禁止在 API 34 及以下强制 `COLOR_MODE_HDR`。
- 真机若出现整页变暗、色阶断层或异常亮线，首先关闭 HDR 路径，不能通过提高全局亮度掩盖。

能力实现见：

- `ui/theme/LiquidGlassCapability.kt`
- `ui/theme/Theme.kt`
- `ui/settings/SettingsDetailScreens.kt`

## 3. 基础设计令牌

令牌定义以 `ui/theme/AppTokens.kt` 为准。页面不应散落新的同义常量。

### 3.1 字阶

| 令牌 | 大小 | 用途 |
|---|---:|---|
| `AppType.Huge` | 36sp | 极少数核心数字或欢迎标题 |
| `AppType.Display` | 32sp | 页面大标题 |
| `AppType.Title` | 28sp | 页面标题 |
| `AppType.Section` | 20sp | 分组标题 |
| `AppType.Body` | 16sp | 正文和主要设置项 |
| `AppType.BodySmall` | 14sp | 次要操作和说明 |
| `AppType.Caption` | 12sp | 辅助信息 |

UI 默认可跟随系统字体；用户选择的阅读正文字体独立处理，不能被全局 UI 字体覆盖。

### 3.2 间距

| 令牌 | 数值 |
|---|---:|
| `AppSpace.xs` | 4dp |
| `AppSpace.sm` | 8dp |
| `AppSpace.md` | 16dp |
| `AppSpace.lg` | 24dp |
| `AppSpace.xl` | 32dp |

优先组合这些间距。为视觉居中允许使用少量局部值，但必须说明原因，不能形成另一套间距系统。

### 3.3 圆角

| 令牌 | 数值 | 用途 |
|---|---:|---|
| `AppRadius.sm` | 8dp | 小型内嵌元素 |
| `AppRadius.md` | 14dp | 普通卡片、封面 |
| `AppRadius.lg` | 18dp | 较大卡片 |
| `AppRadius.xl` | 22dp | Sheet、Dialog、大容器 |
| `AppRadius.capsule` | 28dp | 胶囊控件 |
| `AppRadius.full` | 999dp | 圆形或完全胶囊 |

整体风格要圆润但不过度。普通卡片不要全部做成胶囊；相邻层级应通过 `14/18/22dp` 形成差异。

### 3.4 颜色

- Lumi 强调色：浅色 `#E85D5D`，深色 `#FF8A80`。
- Material 3 使用 `MaterialTheme.colorScheme.primary`，不要写死蓝色。
- Liquid Glass 的开关、拖动条激活区和选中状态使用当前 `AppColors.Accent`。
- 主要、次要文字必须使用 `AppColors.TextPrimary/TextSecondary`，不要用低对比度的任意灰。
- 电子墨水模式只使用高对比度黑、白和必要的灰阶。

## 4. 页面结构规则

### 4.1 首页

- 保留现有“继续阅读”排版，不重做为大型英雄卡片。
- “最近阅读”紧跟最近读过的书卡片，不放到页面末尾。
- 最近阅读可点击打开书籍，不限制只显示三本。
- 封面圆角与同层卡片保持一致。
- 无书时显示空状态和明确的导入操作，不常驻展示导入教学。

### 4.2 书架

保留既有总体顺序：顶部左右胶囊、书架标题、搜索框以及后续内容。允许优化对齐、间距、图标和状态，但不得擅自改成另一套顶栏布局。

- 布局切换使用列表、紧凑网格、标准网格图标。
- 编辑模式在原位替换顶栏，不新增一层导航。
- 长按菜单锚定书籍触点出现并保留玻璃材质。
- 关闭长按菜单必须只触发一次状态清理，不能重复 dismiss 或访问已释放锚点。

### 4.3 统计

- 周、月、年使用分段控件。
- 核心数字直接展示，图表位于低层级分组表面。
- 数据变化用短插值；图表增长动画只在首次加载播放。

### 4.4 设置

- 页面只保留一个可见“设置”标题，避免系统栏标题与页面大标题重复。
- 使用大标题、分组列表、内部隔线；不要为每一项单独套悬浮卡片。
- 阅读设置、高亮颜色色卡和色卡管理保持既有信息架构。高亮色卡入口先进入色卡列表，管理操作留在下一层。
- 高亮颜色色卡页面的既有布局是固定资产，修复功能时不得顺手重做。

### 4.5 阅读器

- 正文可延伸到半透明工具栏下方，但必须正确处理安全区和点击区域。
- 主题 Sheet 首层展示高频设置，高级排版进入第二层。
- “高级设置”胶囊文字始终单行，容器宽度至少能容纳完整标签。
- 主题、目录、搜索、TTS、笔记和选择菜单继续使用既有玻璃容器。
- 打开书籍保留原有加载页和主页进入效果；当前规范不要求封面共享元素转场。
- 返回阅读主页不得叠加系统横移与自定义共享元素动画。

### 4.6 主导航与导入

- Liquid Glass 主 Tag 栏高度 `72dp`；非玻璃分支高度 `56dp`。
- Liquid Glass 导入按钮为独立的 `72dp` 圆形按钮，与 Tag 栏同高、水平对齐。
- `+` 不参与三个 Tab 的索引、选中或折射采样。
- 不支持 Liquid Glass 时使用普通导入入口，不保留透明占位按钮。

## 5. Liquid Glass 渲染规范

### 5.1 最多三层可见玻璃

单个 Dialog 或 Sheet 最多允许三层玻璃：

1. Dialog/Sheet 主表面。
2. 内部功能分组卡片。
3. 选中状态、预览或主要操作卡片。

第三层内部只能使用透明或实色控件。`LiquidGlassSurface` 会通过 `LocalLiquidGlassLayer` 自动限制深度；不要绕过该机制直接增加 `drawBackdrop`。

### 5.2 Backdrop 所有权

- 页面根层只创建一个页面 Backdrop。
- 控件需要折射自身轨道或 Tag 内容时，可增加一个不可见捕获层，并使用一次 `rememberCombinedBackdrop`。
- 同一视觉结果不得叠加两次 RenderEffect、两份隐藏内容或两次手工逆变换。
- 捕获层必须与可见内容使用同一尺寸、中心和颜色，避免折射内容偏移或变灰。
- 阅读页仅在菜单、Sheet、选择菜单、TTS 等玻璃覆盖物可见时启用页面捕获；静止阅读时不得持续采样。

### 5.3 表面与内容

- 玻璃表面形变放入 `drawBackdrop.layerBlock`。
- 内容可部分跟随形变，但不能脱离表面形成“拖着文字/图标走”的感觉。
- 装饰阴影必须放在不会裁切的位置；外层点击区域不能裁掉拉伸后的右上角或边缘。
- 动态高度容器的内容必须参与父级测量。禁止给唯一内容层使用 `matchParentSize()`；该修饰符只能用于已确定尺寸的背景或遮罩层。
- 自定义 G2 Shape 仅在 Backdrop 支持其轮廓时启用 lens；不兼容时回退为无 lens 的静态玻璃，不能崩溃。

### 5.4 Dialog、Sheet 与菜单

- 遮罩和主容器同时存在，主容器必须按内容测量并可见。
- 主容器保持玻璃，但不要把容器本身配置成会吞掉内部控件手势的可拖拽按钮。
- 内容必须按内缩后的玻璃圆角裁剪，尤其检查 Sheet 左上、右上圆角。
- Dialog/Sheet 关闭要播放退出动画后再释放状态，不能直接从组合树移除。
- 锚定菜单从触点方向出现；屏幕边缘应自动修正位置，不改变锚定关系。

## 6. 公共控件规范

### 6.1 玻璃按钮

使用 `LiquidGlassButton`、`LiquidGlassTextButton`、`LiquidGlassIconButton` 或 `LiquidGlassSurface`，不要在业务页面复制手势算法。

- 最小触控高度 `44dp`。
- 文本按钮最小宽度 `72dp`，文字单行，空间不足时省略。
- 默认胶囊形状，卡片型操作可传入 `AppRadius` 对应 Shape。
- 按下后允许有限放大和拖拽形变；超过阈值后形变必须钳制，不能无限拉成橡皮筋。
- 图标和文字应以较弱幅度跟随玻璃形变，保持视觉整体，但不能完全锁死或完全跟随手指。
- 只有按压期间出现局部高光；HDR 可用时高光可超过 SDR 白点。

### 6.2 Liquid Glass Switch

液态玻璃分支采用上游化结构：

- 轨道 `58x30dp`。
- 滑块 `32x24dp`，水平行程 `20dp`。
- 轨道是一次 Canvas 绘制和一个捕获层；透镜只作用于滑块。
- 点击、拖动、取消和外部状态同步共享 `LiquidGlassDampedMotionState`。
- 拖动超过 Touch Slop 后 1:1 跟手，松开以 `0.5` 为阈值吸附。
- 每次操作最多调用一次 `onCheckedChange`。
- 支持 RTL、disabled、Switch 语义和电子墨水回退。

非 Liquid Glass 主题继续使用 Material `Switch` 外观，不套用玻璃尺寸和光学效果。

### 6.3 PillSlider

Liquid Glass 分支遵循 AndroidLiquidGlass 的细轨道结构：

- 轨道高度 `6dp`。
- 玻璃滑块 `40x24dp`。
- 激活区使用 `AppColors.Accent`，禁止写死蓝色。
- 底层轨道和激活区只绘制一次；滑块组合页面和轨道 Backdrop。
- 拖动时 `onDragValueChange` 连续预览，松手后按 `step` 吸附并调用最终 `onValueChange`。
- 纵向滚动超过判断阈值时取消本次滑动并回到外部值。
- 外部值尚未确认时保留短稳定窗口，避免松手瞬间闪到旧进度再恢复。
- 点击轨道表现为滑块升起、移动、吸附、落下。

非 Liquid Glass 分支保留原来的粗胶囊轨道和视觉样式。

### 6.4 底部 Tag 栏棱镜

- 可见 Tag 栏只绘制一次基础玻璃和遮罩。
- 使用一个不可见 `tabsBackdrop` 捕获同一份 Tag 内容；棱镜通过一次 combined backdrop 折射。
- 棱镜未按下时不显示动态折射，只保留极淡底色以提示当前位置。
- 按住后中心内容清晰，边缘出现折射；内部 Tag 相对棱镜呈缩小效果。
- 折射内容中心必须与 Tag 栏垂直、水平中心一致，不允许 Y 轴补偿。
- 原图标保持原位，棱镜不能额外直接绘制一份图标。
- 每个 Tab 有独立点击区域。点击其他 Tab 固定表现为同帧升起和移动、到位后下落。
- 拖动 1:1 跟手，释放后结合速度投影吸附；动画中可反向接管。
- 左右边界使用对称阻尼，最大约 `4dp`；右端可朝 `+` 方向自然突出，但不采样 `+`。
- 深浅色只跟随应用主题，以约 `180ms` 淡变。禁止恢复连续屏幕亮度采样。

## 7. 动效规范

### 7.1 时间词汇

| 场景 | 时长/规格 |
|---|---|
| 按压反馈 | `120ms` |
| 菜单进入 | `180ms` |
| 菜单退出 | `140ms` |
| Sheet 进入 | `260ms` 或无过冲弹簧 |
| Sheet 退出 | `200-220ms` |
| 页面首次进入 | 不超过 `240ms` |
| 深浅色淡变 | `180ms` |
| 主题到高级设置重叠延迟 | `90ms` |

统一曲线和弹簧见 `ui/animation/AppEasing.kt`：

- 程序触发：`ProgrammaticSpring`，无过冲。
- 手势释放：`GestureSpring`，允许轻微回弹。
- 入场：`AppEasing.Smooth/Decelerate`。
- 退出：`AppEasing.Accelerate`。

### 7.2 状态机

玻璃控件统一使用 `LiquidGlassDampedMotionState`：

- `beginInteraction()`：中断旧动画并升起。
- `dragTo()`：同步跟手。
- `animateToValue()`：点击或程序触发的升起、移动、下落事务。
- `settleTo()`：拖动释放后的吸附。
- `cancelInteraction()`：取消后回到可信外部状态。

通过 `MutatorMutex` 保证新手势能中断旧动画。不得在一个控件内再维护第二套相互竞争的 Animatable。

### 7.3 容器切换

主题 Sheet 到高级设置采用交叠转场：

1. 点击后立即触发主题 Sheet 退出。
2. 延迟 `90ms` 创建高级设置 Sheet。
3. 两个容器短暂同时动画，高级设置绘制在上层。
4. 主题退出完成只清理主题状态，不负责启动下一容器。

这可避免“完整退出后再完整进入”造成的停顿。其他同层容器切换可复用这一节奏，但要确保遮罩不会叠加到明显变暗。

### 7.4 减弱动态

`MotionPreference.REDUCED` 和电子墨水模式关闭：

- 位移、缩放、色散、速度形变、交错和弹跳。

仍保留：

- 玻璃材质。
- `80-120ms` 短淡变。
- 必要的位置吸附和状态反馈。

不能用“减弱动态”作为移除玻璃或让状态瞬间不可辨识的理由。

## 8. 性能规范

### 8.1 禁止项

- 禁止逐帧 PixelCopy、屏幕亮度采样或 draw observation 来决定 Tag 栏深浅色。
- 禁止在阅读页静止时保持无消费者的页面 Backdrop 捕获。
- 禁止为一个棱镜、滑块或开关建立重复的隐藏内容和多个 RenderEffect。
- 禁止用高频 Compose State 驱动只需在绘制层完成的形变。
- 禁止在外层 `graphicsLayer` 放大后再手工逆变换 Backdrop。
- 禁止每帧分配 Brush、Paint、列表或大型路径；能 `remember` 的对象应复用。
- 禁止用全局 blur 包裹整页来实现局部控件模糊。

### 8.2 运行目标

- Tag 点击和拖动 jank 目标低于 `5%`。
- 不出现超过 `700ms` 的冻结帧。
- 阅读页静止时不持续触发玻璃采样或大范围重组。
- 页面滚动和翻页优先保证帧率；复杂光学效果只能在局部按压期间启用。
- 若需降低光学采样成本，应先通过 profile 确认瓶颈，再在 Backdrop 内降低分辨率；不能引入一帧以上可感知延迟。

## 9. 无障碍与系统适配

- 所有可点击控件触控区域至少 `44x44dp`。
- 图标按钮提供 `contentDescription`；熟悉图标可用 Tooltip 补充，不用页面文字解释操作方式。
- Switch 使用 `Role.Switch` 和 `ToggleableState`；Slider 提供范围和步进语义；Tab 提供 selected 状态。
- 字体放大后，紧凑胶囊文字优先保持单行并扩大容器；仍不足时省略，不能换行挤坏布局。
- RTL 下开关、滑块、Tab 拖动方向和索引映射必须镜像。
- 深色模式不能只降低透明度，需同步检查文字、边缘和遮罩对比度。
- 电子墨水模式禁用 Liquid Glass、HDR 和复杂动画，提供高对比度实色路径。
- 系统减弱动态偏好与应用设置均应映射到 `LocalMotionEnabled`。

## 10. 代码落点

| 职责 | 文件 |
|---|---|
| 主题令牌、字体、圆角、CompositionLocal | `ui/theme/AppTokens.kt` |
| 主题注入、HDR window 配置 | `ui/theme/Theme.kt` |
| Liquid Glass 能力检测 | `ui/theme/LiquidGlassCapability.kt` |
| 动效曲线与时间 | `ui/animation/AppEasing.kt` |
| 共享玻璃表面和三层限制 | `ui/components/LiquidGlassSurface.kt` |
| 可中断运动状态 | `ui/components/LiquidGlassDampedMotionState.kt` |
| 按钮 | `ui/components/LiquidGlassButton.kt`、`LiquidGlassIconButton.kt` |
| 开关 | `ui/components/LiquidGlassSwitch.kt` |
| 拖动条 | `ui/components/PillSlider.kt` |
| 底部导航与导入按钮 | `ui/components/FloatingTabBar.kt` |
| Dialog 宿主 | `ui/components/LiquidGlassDialog.kt` |
| 锚定菜单 | `ui/components/AnchoredLiquidGlassMenu.kt` |
| Sheet 运动 | `ui/components/BottomSheetMotion.kt` |
| 阅读主题和高级设置 | `ui/reader/ThemeSettingsSheet.kt`、`ReaderScreen.kt` |

来自 AndroidLiquidGlass 的运动与控件结构保留 Apache-2.0 来源说明。修改上游化组件时不得删除文件头署名和链接。

## 11. Agent 修改流程

进行 UI 改动前：

1. 明确改动属于布局、材质、状态还是动效，避免同时重做四个维度。
2. 查找现有公共组件和令牌，不在页面内复制玻璃实现。
3. 确认 Lumi、Material 3、Liquid Glass、电子墨水和减弱动态五条路径。
4. 检查 Dialog/Sheet 的玻璃层数。
5. 保留用户明确要求不变的页面结构。

改动完成后：

1. 运行 `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest`。
2. 只覆盖安装主 Debug APK；除非用户明确要求，不安装 Android 测试 APK。
3. 在 API 26-30、API 31+、无 HDR、HDR、深浅色、电子墨水和减弱动态模式中检查能力分流。
4. 对手势控件检查点击、长按、短拖、快速拖、取消、松手、动画中反向和 RTL。
5. 对 Dialog/Sheet 检查入场、退出、返回键、遮罩点击、动态高度和圆角裁剪。
6. 使用真机截图确认对齐，再使用 frame timeline/jank 数据判断性能，不能只凭模拟器观感。

## 12. 发布验收清单

- [ ] 不支持 Liquid Glass 的设备完全看不到主题、透明度和 HDR 选项。
- [ ] 支持 Liquid Glass 但不支持 HDR 的设备只隐藏 HDR。
- [ ] 已保存的 Liquid Glass 设置在不支持设备上安全回退且不被覆盖。
- [ ] 三套主题布局、功能和状态一致。
- [ ] 所有既有玻璃 Dialog、菜单和 Sheet 仍为玻璃。
- [ ] 任一 Dialog/Sheet 不超过三层可见玻璃。
- [ ] 动态高度 Dialog 不会只显示遮罩。
- [ ] Sheet 退出有过渡，连续容器切换没有明显空档。
- [ ] 底部棱镜不复制图标，折射内容居中，左右阻尼对称。
- [ ] `+` 不参与 Tab 索引或折射。
- [ ] Liquid Glass Switch 和 Slider 支持拖动、取消、吸附与单次提交。
- [ ] Slider 激活色跟随主题强调色。
- [ ] 阅读页静止、滚动和翻页无持续背景采样造成的卡顿。
- [ ] TalkBack、字体放大、RTL、深色、减弱动态和电子墨水模式可用。
