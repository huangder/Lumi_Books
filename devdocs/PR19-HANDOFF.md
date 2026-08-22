# Lumi Books PR #19 合并与测试交接文档

> 最后更新：2026-08-16（原排版上下滚动及跨章过渡真机通过）
> 用途：本文件是**唯一的事实来源**。任何新对话接手本任务时，先读本文件，再决定下一步，不要重新评审、重新合并或重新编译已经完成的部分。

---

## 1. 任务背景

- 仓库：`huangder/Lumi_Books`（本地工作区 `D:\vibe_coding\android_books`，分支 `main`）
- PR #19：`fix: 修复25项阅读体验与稳定性问题`，作者 `spencer1012`（fork：`spencer1012/Lumi_Books`），单一 squash commit `164ad21e`，当前状态 **open**（未合入，未关闭）
- 用户诉求：评估 PR 代码质量 → 发现 PR 头有 46 个编译错误 → 用户决定**方案 A**：拉到临时目录，修到能编译，然后**逐项真机测试**（用户强调"一个一个测试"）
- PR 描述里的 `#1~#25` 是**作者内部编号**，与仓库 GitHub issue 无关，不要尝试去对应

## 2. 当前状态（已完成，勿重复）

| 事项 | 状态 |
|---|---|
| PR 完整评审（45 文件、+3863/-471） | ✅ 已完成，结论：编译不过、多处运行时风险 |
| 临时合并分支 | ✅ `C:\Users\Huangder\AppData\Local\Temp\lumi_pr19_20260812210214\repo`，分支 `merge-pr19`，commit `b89b6eb` |
| 合并基线 | ✅ main = `d052e7f`（用户已自行 push"书签并入目录容器+滚动条"，与 PR #15/#22 重复） |
| 冲突处理 | ✅ ReaderScreen.kt 10 处冲突：**保留 main 用户自己的实现**，丢弃 PR 重复版；PR 独有功能保留 |
| 编译修复 | ✅ 48 处错误全部修复，`:app:compileDebugKotlin` 0 错误 |
| 完整打包 | ✅ `:app:assembleDebug` BUILD SUCCESSFUL |
| APK | ✅ 已复制到 `artifacts\lumi_pr19_merge-debug-arm64.apk`（及 `-universal.apk`） |
| 安装 | ✅ 已 `adb install -r` 安装到用户手机（设备 `24129PN74C`，覆盖原 1.7.8 debug 版，数据保留） |
| 逐项测试 | 🔄 进行中：第 0 步（数据库升级）✅ 用户确认通过；#6/#7 测试文件已就位，待用户操作 |

## 3. 编译修复清单（48 处 → 0）

### ReaderScreen.kt（30 处）
- `toggleBookmark` 引用了声明在后面的 `readViewRef` → 整段 lambda 移到 `readViewRef` 声明之后
- 高级排版设置弹窗处缺 `isTxtBook` → 补 `val isTxtBook = uiState.book?.format?.name == "TXT"`
- `uiState.book?.format == "TXT"` / `!= "TXT"`（枚举与字符串比较）→ 改 `.name`
- `LazyColumn` 不存在 `beyondBoundsItemCount` 参数 → 删除
- `ComicChapterImages`：补 `import android.text.Spanned`；`Color(Color)` 双重包裹 → 直接用 `bg.red/green/blue`；每图新建 ImageLoader → hoist 为 composable 内单个共享 ImageLoader（顺带修复性能问题）；`stringResource` 写进 `remember{}` → 移到可组合上下文
- 冲突残留的重复 `val sortedBookmarks` → 删除 PR 版本，保留 main 版本
- `when (menuMode)` 缺 `SelectionMenuMode.Settings` 分支 → 补
- `SelectionMenuSettingsDialog` 的 `remember { listOf(... stringResource ...) }` → 去掉 remember
- 补字符串资源 `comic_mode_no_images`（默认 values/strings.xml，其他语言回退中文）

### SettingsDetailScreens.kt（11 处）
- 补 `import androidx.compose.foundation.shape.CircleShape`
- 补 `import com.huangder.lumibooks.ui.components.LiquidGlassDialog`
- 其余为上述缺失导致的连锁 @Composable 报错，随 import 修复消除

### BookTagBottomSheet.kt（2 处）
- `AnimatedVisibility` 在 RowScope 隐式接收者下不可调用 → 改为 `if (deleteVisible) { ... }` 条件包裹（原动画效果移除，功能等价）
- `Modifier.padding(start, vertical)` 非法组合 → `padding(start, top, bottom)`

### 其他文件（5 处）
- `PageContentView.kt`：`textView.setFakeBoldText(...)` 不存在 → `textView.paint.isFakeBoldText = ...`
- `FloatingTabBar.kt`：`Brush.solidColor` 不可用 → `Brush.verticalGradient(listOf(color, color))`
- `DataStoreManager.kt`：补 `import org.json.JSONArray`
- `TtsFloatingWindowService.kt`：补 `import androidx.compose.foundation.layout.offset`
- `ReaderViewModel.kt`：`book.format != "TXT"` → `book.format.name != "TXT"`

## 4. 25 项功能测试清单（⚠️ 为重点关注项，全部未测）

### 第一批：正确性与稳定性
| # | 内容 | 测试方法 |
|---|---|---|
| 6 | 大体积 Mobi 导入/打开闪退（LRU 缓存 32 条/128MB） | ✅ 用户确认 150MB 测试文件可打开（2026-08-12）。注意：该文件无大量图片，图片缓存 OOM 路径未覆盖，仍建议用真实大图 Mobi 复测 |
| 7 | 部分 Mobi 图片不显示（GIF 签名/0-based 索引/尺寸上限） | ✅ 基础解析通过（540KB 公版书可打开）。带图/GIF 的 Mobi 仍未验证，需真实文件 |
| 16 | 书签页码不对应（字符偏移定位） | ✅ 用户确认改字号+旋转后书签跳转准确（2026-08-12） |
| 21 | 上下滚动页 TTS 不可用（continuousTtsPageContent） | ✅ 通过（2026-08-13，含滚动跟随/高亮/悬浮窗多轮修复后验证） |

### 第二批：核心阅读体验
| # | 内容 | 测试方法 |
|---|---|---|
| 5 | 原排版上下滚动：跨章无缝+连续进度 | ✅ 通过（2026-08-16）：原排版已恢复“上下滚动”入口；章节内滚动及跨章切换正常；章末继续上拉时当前章跟手上移淡出，下一章在首帧绘制后从下方上移淡入；末章边界不会淡成空白 |
| 12 | 划线功能（下划线+颜色） | 选中→划线→删线→笔记列表 |
| 13 | 自定义高亮色卡（增删/排序/编辑） | 设置改色→阅读器同步 |
| 14 | **⚠️ 跨页选择文本** | ✅ 通过（2026-08-13，经"拖边缘自动翻页+合并修复+高亮刷新+笔记去分段"多轮修复后用户确认） |
| 20 | **⚠️ 自定义菜单 + TXT 原位替换** | ✅ 通过（2026-08-13）：替换后当前页实时刷新，退出重进内容一致；GBK 编码回归测试通过；替换弹窗复用现有液态玻璃宿主，G2 折射闪退已修复，圆角和标题字阶经真机调整后用户确认完成 |
| 3 | TTS 当前句淡高亮（深浅色自适应） | 深浅主题朗读高亮可见性 |
| 10 | **⚠️ 听书悬浮窗字幕** | ✅ 已授权场景通过（2026-08-13）：用户确认浅色透明悬浮窗、四按钮、大 G2 圆角及撤销缩放后的版本正常；字幕点击返回阅读器、TTS/悬浮窗保持行为已修复。未授权权限仍会静默失败，保留为已知问题 |
| 17 | 下滑添加/取消书签 | 顶部下滑手势是否误触翻页 |

### 第三批：界面与多端
| # | 内容 | 测试方法 |
|---|---|---|
| 1 | 漫画适配（满宽+无缝拼接） | 带图书开漫画模式 |
| 2 | 语音引擎自由选择 | 切换引擎 |
| 4 | 深色 tab 栏纯色 | 深色模式 |
| 24 | 字重+仅正文开关 | 加粗是否只影响正文 |
| 25 | 阅读页弹层描边/阴影/纯色 | 视觉检查 |
| 9 | **⚠️ 标签一级/二级体系** | **重点测：子标签无法单独删除（代码层已确认入口不可达）** |
| 15 | 书签移入目录容器 | 已是 main 用户自己的实现（d052e7f），测用户版 |
| 22 | 目录右侧滚动条 | 同上 |
| 8 | 书库多选+全选 | 长按进编辑、批量操作 |
| 23 | 导入弹窗排版切换图标 | 图标替代数字 |
| 18 | TXT 编码按钮移入高级设置 | 入口可用 |
| 19 | 阅读页深浅模式响应式 | 系统切主题 |
| 11 | 平板横屏适配（侧栏+双页） | **⚠️ 侧栏"首页/书库/统计"硬编码中文，未本地化** |

## 5. 已知运行时问题（编译后仍存在，代码审查确认，未修）

按优先级：
1. **跨页选区合并 bug**（`ui/reader/engine/ReadView.kt` 的 `getSelectionInfo`）：拿章节**绝对偏移**去 `coerceIn(0, fullText.length)` 截**页面局部文本** `getJustifiedText()`，会截错或合并失败 → 对应 #14
2. ~~**TXT 替换直接改写原书文件**~~：已修复。`ReaderViewModel.replaceTxtText` 改用 `TxtParser.rewriteWithOperations`，保留原编码并通过 GBK 回归测试；支持 `content://`，成功后清解析/分页缓存并实时刷新当前页 → 对应 #20
3. **悬浮窗权限未处理**（`TtsFloatingWindowService`）：无 `Settings.canDrawOverlays()` 检查、无 `ACTION_MANAGE_OVERLAY_PERMISSION` 引导；`tts_floating_permission_required`/`tts_floating_go_settings` 两个字符串写了但全项目未用 → 对应 #10
4. **二级标签删除入口不可达**（`BookTagBottomSheet.kt`）：子标签传 `deleteVisible=false` + `onShowDelete={}`，无法单独删除 → 对应 #9
5. **硬编码中文未本地化**：`BookshelfScreen.kt` "已选 X 本"（有现成资源 `selected_books_count` 未用）；`NavGraph.kt` 平板侧栏"首页/书库/统计"（有 `home_title`/`bookshelf_title`/`stats_title`）
6. **新增约 50 个字符串只有 3 个有翻译**（en/ja/ko 各 +3，其余回退中文）
7. 调试日志残留：`Log.e("ContinuousProgressDebug", ...)`、`ReaderSelectionDebug`、`ContentDebug`、`PageSlotManager` 等
8. **进度保存防抖被移除**：原来 350ms 防抖 `scheduleProgressSave()` 被删，改为每翻页直接 `saveProgress()`，可能有写入放大
9. 连续滚动进度计算每帧 O(章节数)（`chapterHeights.values.sum()`）

## 5.1 测试中发现并已修复的问题

- **#21 闪退（已修，2026-08-12）**：点 TTS 图标 → 拉起 `TtsFloatingWindowService` → `onCreate` 里调用 `savedStateRegistryController.performRestore(null)` 抛 `IllegalStateException: Restarter must be created only during owner's initialization stage`（Service 不该用 SavedStateRegistry）。修复：删除全部 SavedStateRegistry 相关代码（imports、成员、performRestore、setViewTreeSavedStateRegistryOwner），服务只保留 LifecycleOwner。
- **#21 闪退第 2 轮（已修，2026-08-12）**：删除 SavedStateRegistry 后，新版 Compose 的 `ComposeView` 强制要求 View 树传播 `ViewTreeSavedStateRegistryOwner`，抛 `IllegalStateException: Composed into the View which doesn't propagate ViewTreeSavedStateRegistryOwner`。正确修复：**恢复**全部 SavedStateRegistry 代码，并把 `onCreate` 顺序改为 **先 `performRestore(null)`（此时 lifecycle 仍为 INITIALIZED）再 `handleLifecycleEvent(ON_CREATE)`**。`performRestore` 要求 owner 处于初始化阶段，之前崩正是顺序反了。
- **悬浮窗 UI 改造（已做，2026-08-12）**：按用户要求改成"应用内播放胶囊"样式（圆角 28dp，匹配 `TtsPlayerPanel`）的两行粗胶囊：上行=当前句字幕（点击跳回阅读器）；下行=上一句/播放暂停/下一句/关闭。控制调用 `ttsController.skip(false)/pause()/resume()/skip(true)`。
- **悬浮窗行为调整（2026-08-12，用户需求）**：① 显示时机改为**退出应用到后台才显示**（MainActivity `ON_STOP` 且 TTS 非 IDLE → start；`ON_START` → stop；ReaderViewModel 只保留 IDLE 兜底 stop，删掉原"播放即显示"）；② 默认位置**居中偏上**（`gravity = TOP or CENTER_HORIZONTAL`，y = 屏高 20%）；③ 背景色更透明（alpha 0.92→0.75）；④ 内容外层加 12dp padding 修**拖动时右下角阴影被窗口边界裁切**的问题。
- **悬浮窗拖动重做（2026-08-12）**：原实现是"窗口不动、内容在窗口内 `Modifier.offset` 偏移"，内容超出窗口矩形即被裁切（用户反馈：四周有边界、拖不到顶部）。改为**整体移动窗口**：窗口固定宽度（屏宽-32dp）、`gravity = TOP|START`、初始 x 居中 y=屏高 20%；拖动回调走 Service 的 `moveFloatingWindow(dx,dy)` 更新 `LayoutParams.x/y` 并 `updateViewLayout`，钳制在屏幕内。另：**关闭按钮现在会同时 `ttsController.stop()`**（用户反馈：点叉之前只关窗不停 TTS）。
- **TTS 高亮三处改造（2026-08-12，用户反馈+需求）**：① **滚动跟随**：连续滚动模式下新增 `LaunchedEffect(ttsCurrentSentence)`，朗读时自动滚到当前句（先滚到目标章，等章节高度就绪后按字符比例滚动，句子停在视口上 1/3 处）；② **高亮样式**：新建 `ui/reader/engine/TtsLineBackgroundSpan.kt`（`LineBackgroundSpan` 整行圆角背景），替换原来矩形 `BackgroundColorSpan`；颜色改为很淡（亮度差 0.06，原 0.14），圆角 12dp、左右 padding 4dp（连续滚动与分页共用）；③ **分页模式高亮**：ReadView 新增 `ttsHighlightRange` 成员注入 `buildHighlights`，经 `PageContentView.TTS_HIGHLIGHT_RGB`（0x00FF9E80）特殊色值传入 `PageContentView.setPageContent` 转成圆角整行 span。
- **TTS 高亮第 2 轮改造（2026-08-13，用户反馈）**：① **多行共用一个圆角矩形**：不再用 `LineBackgroundSpan`（每行独立圆角），改为 `TtsSentenceHighlightSpan`（`CharacterStyle` 标记）+ 渲染端统一绘制——`RoundedHighlightTextView`（连续滚动）、`JustifiedTextView`（分页横排，新增 `setTtsHighlight/clearTtsHighlight`）、`VerticalTextView`（分页竖排，同样新增）各自在 `onDraw` 画**整个句子块一个**大圆角矩形（12dp 圆角、整行宽度）；② **滚动跟随修复**：原来滚动分两步（先 `scrollToItem` 停章顶、再 `scrollBy`），中间态触发 `onChapterVisible` 导致 TTS 跳回章节开头重读标题。改为：章节文本未加载先补加载 → `isRestoringPosition=true` 抑制回传 → 等章节高度 → `scrollToItem(chapterIndex, targetScrollOffset)` 一次到位；③ **分页模式不显示修复**：原因确认——`JustifiedTextView` 渲染只认 `ReaderSearchHighlightSpan/ReaderHighlightSpan/BackgroundColorSpan`，旧 TTS span 不画；现改由渲染 view 直接画。另：分页模式句子变化时 `LaunchedEffect(uiState.ttsCurrentSentence)` 触发 `forceRelayout()` 刷新当前页（若真机卡顿再优化为轻量刷新）。
- **TTS 高亮第 3 轮改造（2026-08-13，用户反馈）**：① **TTS 底色盖住笔记高亮/划线**：`TtsSentenceHighlightSpan.computeHighlightColor` 的 alpha 从 255 改为 `0x55`（33% 半透明），笔记高亮/划线可透出；② **跳回标题重读**：根因=`onPageVisible` 收到滚动位置回传后 `moveToPage` 重置。修复=滚动跟随把句子停在**视口中心**（原来 1/3 处导致视口中心页偏后）+ 滚动完成后 500ms 内抑制 `onChapterVisible` 回传（`lastTtsFollowScrollAt`）；③ **分页模式仍无高亮**：根因=`forceRelayout`→`loadSlot` 对已加载页直接 return（`clearContentCache` 不清 slot 状态），从不重绘。修复=新增 `PageSlotManager.refreshCurrent()`（强制置 `isLoaded=false` 后重新 `loadSlot`）+ `ReadView.refreshCurrentPage()`，分页刷新改调它。
- **TTS 高亮第 4 轮改造（2026-08-13，小米 10 实测反馈）**：① **滚动抽搐**：不再"先 `scrollToItem` 停章顶再跳句子"，改为 `animateScrollToItem(chapterIndex, offset)` 一次动画到位；目标章不可见时用已知章节平均高度估算 offset，item 测量后再 `scrollBy` 校正；句子停在视口中心。② **高亮跑出屏幕**：由滚动到位率不足导致，同上修复。③ **颜色加深**：`computeHighlightColor` alpha `0x55`→`0x66`（40%）。
- **上下滚动双标题 bug（2026-08-13，截图确认）**：网文书源正文每章第一行就是章节标题，而连续滚动模式章节顶部又渲染了一次标题（PR 把"仅未加载时显示标题"改成"始终显示"）→ 同一标题显示两次。修复：正文若以 `chapterTitles[chapterIndex]` 开头，渲染前去掉该标题行（`chapterBodyText`），保留 item 顶部标题。
- **上下滚动双标题第 2 轮（2026-08-13，用户澄清）**：用户确认两个标题分别是"顶部 Compose 标题（系统字体）"和"正文第一行标题（用户设置字体）"，要求**删系统字体那个（顶部标题）**。已撤销 `chapterBodyText` 方案（正文保留原样），删除 ContinuousScrollReader 章节 item 顶部的 `Text(chapterTitle)` 块。注意：正文不含标题的书在连续滚动模式将不再显示章节标题（用户已接受此取舍）。
- **章节间距（2026-08-13）**：删除顶部标题后章节间无留白（上一章末尾与下一章标题贴太近），给章节 item 加 `.padding(bottom = 28.dp)` 间距。
- **跨页选择：拖手柄到边缘自动翻页（2026-08-13，用户需求）**：PR 原方案（翻页保留选区合并）用户不认可，用户明确要"拖动手柄到页面底部/右缘 → 自动翻到下一页 → 继续选择"。实现：`PagedSelectableTextView.onSelectionChanged` 检测选择终点到达文本末尾且最后一行贴近页面底部 → 回调 `ReadView.handleSelectionReachEnd()` → `turnToNextPage()`（内部已 `saveCrossPageSelectionIfNeeded` 保存上一页选区）→ 420ms 后 `rebuildSelectionOnCurrentPage()`（新页开头设选区 + 模拟长按弹出手柄），用户继续拖即可跨页合并。
- **跨页选择第 2 轮（2026-08-13）**：① 自动翻页触发确认正常（selEnd 到文本末尾即触发，日志 `REACH_END sel=210 len=211`）；② 新页选区重建：模拟长按改真实时间差（先 DOWN、延迟 600ms 再 UP，原同步 down+up 会被系统当短按取消，手柄弹不出）；③ **合并 bug 修复**：`getSelectionInfo` 原来拿章节绝对偏移去截页面局部文本（越界导致合并失败、只显示第二页内容），改为直接拼接 `crossPage.startText + text` 并保持正确绝对偏移。
- **跨页选择第 3 轮（2026-08-13）**：① 高亮刷新：`setSavedNotes` 改调新增的 `PageSlotManager.refreshAllHighlights()`（刷新 PREV/CUR/NEXT 已加载 slot，跨页高亮第一页翻回即显示）；曾误用 `invalidateAllSlots`（把 slot 置未加载导致翻页被 `turnToNextPage` 拒绝 + 高亮不刷），已废弃并恢复文件。② **进度 0% 定位**：非 bug——该书 3187 章，读第 1 章第 13 页进度=(1+0.65)/3187≈0.05%，书库显示 `(progress*100).toInt()%` 取整为 0%。修复：新增 `formatProgressPercent()`（<10% 显示一位小数），应用于 BookshelfScreen/BookshelfSearchOverlay。③ **笔记容器跨页文本**：显示时 `selectedText.replace('\n',' ')` 去分段连续排（BookNotesScreen + ReaderScreen NotesListSheet）。
- ⚠️ 待办：**TXT 打开慢**（用户确认正式版仅首次初始化慢、后续快；合并包持续慢——疑似 PR 引入，需专项对比排查）。另：跨页"滑动扩展选区"（新页滑动跟手）仍未实现。
- **划线样式改造（2026-08-13）**：新增 `WaveUnderlineSpan`（带颜色、不改文字色），分页（PageContentView→JustifiedTextView）与连续滚动（RoundedHighlightTextView）都在 onDraw 画**波浪线**（振幅 1.6dp、波长 5.5dp、基线下方 3.5dp），替换原 UnderlineSpan 直线 + 前景色。
- ⚠️ 待办（用户需求，2026-08-13）：**选择菜单多浮层交互**——单一状态（已高亮/已划线）长按弹 2 个菜单（上=普通菜单隐藏已存在类型项、下=颜色+文字删除按钮）；叠加状态（既高亮又划线）弹 3 个菜单（高亮颜色、划线颜色、普通菜单隐藏高亮和划线）。当前仍是单菜单内两组，未按新方案重构。
- **划线功能修复 + 选择菜单三项改造（2026-08-13）**：① **划线渲染**：分页模式划线笔记原被当高亮画背景——新增 `PageContentView.UNDERLINE_FLAG`（alpha 位标记）+ `buildHighlights` 对 underline 笔记用 `(0xFE shl 24)|color`，`setPageContent` 检测后设 `ForegroundColorSpan + UnderlineSpan`（文字下划线、不画背景）；② **菜单文案**：垃圾桶 contentDescription 按类型显示"删除划线"/"删除高亮"（新增字符串 `menu_delete_underline`）；③ **菜单左边距**：菜单行 padding 4dp→10dp；④ **双菜单**：既有高亮又有划线时菜单内上下两组（上=划线、下=高亮，各带颜色点和删除按钮；暂未区分删除对象，都调 `onDeleteHighlight`——待细化）。
- **#12 划线渲染第 2 轮修复并真机通过（2026-08-13，小米 10）**：① 书籍原排版跨 DOM 选区不再用 `Range.surroundContents()`，改为逐文本节点读取 `ClientRect` 并生成 SVG 波浪线，纯空白节点不参与，解决“划线变整块高亮”和行间隙被高亮；② 修复合并矩形只有 `left/top/right/bottom`、却错误读取 `rect.width/height`，导致 SVG 尺寸为 `NaN`、路径为空、页面完全无划线；③ 高亮和划线拆为两层：普通高亮保持正文下方，划线层置于正文上方且不接收事件，波浪线收进字形框底部；④ 阅读器排版修正自动换行行尾宽度及按字形 descent 定位，用户反馈正常，本轮未再改；⑤ 新增回归测试，要求跨元素划线有非空路径、有效宽度、无背景高亮回退，且划线层级高于高亮层。最终 APK 已覆盖安装，原排版第 1、2 页真机截图确认多色跨行波浪线均可见、无行间隙高亮（`artifacts/original-underline-after-page1.png`、`artifacts/original-underline-after-page2.png`）。注意：早期自检只数 SVG/path 节点，未校验路径内容，曾产生假阳性，后续不可只看节点数量。
- **打开书一直停在加载画面（已修，2026-08-13）**：用户设备保存的全局翻页效果是 `continuous`，而《神秘复苏》保存的文字方向是 `vertical_rl`。界面层通过 `ReaderWritingMode.effectivePageTransition()` 把“竖排 + 连续滚动”降级为分页 `slide`，实际创建原生分页 `ReadView`；但 `ReaderViewModel.onNewEnginePageChanged()` 仍只看原始 `pageTransition == "continuous"`，把分页首屏回调当作旧引擎回调直接丢弃，`isLoading` 因此永远不清。修复：在 `ReaderWritingMode` 新增 `usesContinuousScroll(preferredTransition, eInkModeEnabled)`，ReaderScreen 与 ReaderViewModel 的连续滚动判断全部复用同一逻辑（包含页变更、双页回调、连续滚动位置与 TTS 起始页）。新增单测覆盖横排 continuous、竖排 continuous 降级、电子墨水模式和普通分页。`:app:compileDebugKotlin`、定向 `ReaderEdgeTapModeTest`、`:app:assembleDebug` 均通过；最终 APK 已覆盖安装且保留数据。设备当时处于系统锁屏，无法完成“加载页实际退出”的最后截图，解锁后需冷启动复验一次。
- **新建划线未先选颜色（已修，2026-08-13）**：根因是选择菜单的 `onUnderline` 分支直接以 `DefaultReaderHighlightColor` 创建 `underline`，只有 `onHighlight` 会进入六色面板。修复：新增 `AnnotationColorTarget` 和待选目标状态；点击高亮或划线都先打开同一颜色面板，点色后再按目标写入 `highlight`/`underline`，菜单关闭、选区改变或保存完成时清理目标状态。书籍原排版与阅读器排版共用该回调，因此同时覆盖。新增 `AnnotationColorTargetTest`，强制重跑定向单测通过；`:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug`、`git diff --check` 均通过。arm64 APK 已覆盖安装到小米 10（`60a76f64`，保留数据，`lastUpdateTime=2026-08-13 21:19:13`），SHA256=`36F1D2FCDC3961435B22AEDC82D109A5FFC31888DF4CF99BB22D9F086966942E`；待用户真机确认点击“划线”会显示六色圆点且选色后划线颜色正确。
- **诊断时额外发现（未纳入本轮修复）**：冷进程通过外部 `open_book_id` Intent 直接打开书时，`NavGraph` 可能在导航图挂载前执行 `navigate()`，抛 `IllegalArgumentException: Navigation graph has not been set`。用户从书架点书不走该冷启动时序；后续处理小组件/外部入口时应单独修复。
- ⚠️ **教训（2026-08-13）**：本轮菜单改造中多次误删/误加 ReaderScreen 括号（formatReaderPageLabel 结尾、ReaderScreen 函数结束、SelectionMenuOverlay 函数结束），曾出现 102~667 编译错误；最终用括号栈分析定位并修复。**后续对 ReaderScreen 做删除/替换操作必须先用 `git diff` 复核结构**。
- ⚠️ 待办：**新页滑动扩展选区**（用户期望在新页左右滑动时选区跟随手指，而不是必须拖手柄；当前系统 TextView 需拖手柄，且滑动会触发翻页手势）——尚未实现。
- ⚠️ **最后一行无法长按选词（待修，2026-08-13）**：用户确认同一页"带过"（倒数第二行）可选中、"前辈"（页面最后一行）选不中。疑似选择层（隐藏 TextView layout）与可见层（JustifiedTextView）最后一行行位置偏差，或最后一行行底超出选择层可视区。尚未定位/修复。
- ⚠️ 第 4 轮 APK 已安装到小米 10（`60a76f64`，09:12:10），待实测。
- ⚠️ 测试设备更新（2026-08-13）：新设备 **小米 10（`60a76f64`，USB 直连）**，第 3 轮 APK 已安装（09:01:26，全新安装，versionCode 7）。原设备（24129PN74C 无线调试）已断开。小米 10 需开启"USB 安装"（已开）；听书悬浮窗权限需在 MIUI 设置里手动授权。
- ⚠️ 手机无线调试再次断开（2026-08-13），**本轮 APK 已编译打包在 `artifacts\lumi_pr19_merge-debug-arm64.apk`，尚未安装**；恢复连接后 `adb install -r` 即可。
- ⚠️ 手机无线调试中途断开后已恢复（2026-08-12，现连接 `192.168.0.100:41013`），**高亮三处改造的 APK 已安装**（lastUpdateTime 22:56），待真机验证。
- 注：悬浮窗显示依赖"显示在其他应用上层"权限（MIUI 需手动授权），未授权时 addView 失败会静默停服，无提示（权限引导尚未做，属已知问题第 3 条）。
- **听书悬浮窗缩放功能已撤销（2026-08-13，用户调整需求）**：删除右下角长按拖拽手柄、等比例缩放手势、缩放比例持久化及按钮行预留空位，窗口恢复为固定宽度（屏幕左右各留 16dp）。保留普通拖动、四个按钮、浅色透明样式、G2 连续圆角和点击字幕返回阅读器的修复；同时移除临时导出的 `DebugTtsFloatingWindowReceiver`。`:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug`、`git diff --check` 均通过。新版 arm64 APK 已覆盖到 `artifacts/lumi_pr19_merge-debug-arm64.apk`，SHA256=`D86B2EB242216CF5D5A3965C8FB487FB61930D54C6CCC8590E64425F8B26A328`；已于 22:27:52 通过 `adb install -r` 保留数据覆盖安装到小米 10（`versionName=1.7.8`、`versionCode=7`）。待用户真机确认无缩放手柄/预留空位，并复验字幕点击后应用、TTS 与悬浮窗均保持正常。
- **#20 TXT 替换弹窗闪退修复（2026-08-13，真机通过）**：干净 logcat 定位到 `UnsupportedOperationException: Only RoundedRectangularShape or CornerBasedShape is supported in lens effects`。根因不是 TXT 替换业务，而是新弹窗把生成 `Outline.Generic` 的 `G2ContinuousCornerShape` 直接交给 kyant `lens()`，且弹窗另建全屏遮罩和局部 backdrop，绕开了应用现有的弹窗宿主。修复为：① `ReplaceTextDialogOverlay` 直接复用全局 `LiquidGlassDialog` / `LiquidGlassDialogHost`，由现有逻辑统一处理遮罩、返回键、进出场和 reader backdrop；② `LiquidGlassSurface` 对 G2 shape 使用同半径 `RoundedCornerShape` 作为 lens SDF 代理，真实 G2 path 仍用于最终 clip/border，兼顾折射库限制与 G2 外观；③ 删除弹窗内部额外的 `BackHandler`、遮罩、`rememberLayerBackdrop`、`layerBackdrop` 和 `ProvideLiquidGlassBackdrop`；④ 外框 G2 圆角最终由 64dp 收敛至 40dp，标题由页面级 `AppType.Title`（28sp）统一为容器级 `AppType.Section`（20sp）。`:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug`、`git diff --check` 均通过。最终版已于 23:34:42 保留数据覆盖安装到小米 10；用户确认功能到此结束。
- **笔记跳转后的高亮翻页消失（2026-08-19，真机通过）**：从笔记跳到高亮页时高亮存在，但翻到下一页再返回会消失；连续翻两页返回时还可能先出现再消失，点击页面后恢复。数据库范围和 `ReaderHighlightSpan` 均正常，日志最终确认 `RoundedHighlightTextView.onDraw()` 时 Span 仍在、但 `TextView.layout == null`。根因是翻页完成回调重复执行 `configureCurrentPageView()`，其中无条件调用的 `TextView.setLineSpacing()` 即使参数未变也会清空刚建立的内部 Layout。修复：`PageContentView.configure()` 对字号、字体、行距和字间距全部增加值变更守卫，避免无效配置破坏目标页 Layout；页面槽移动继续从章节缓存重建内容和高亮 Span，含自定义高亮/划线/搜索/TTS Span 的页面使用软件绘制层。小米 10 上已验证“下一页→返回”及“连续两页→返回并等待 3 秒”，高亮持续存在且不闪烁；用户确认正常。临时 `HighlightDraw`/`HighlightLifecycle` 日志已删除，最终 `:app:assembleDebug` 成功、`git diff --check` 通过。修复代码位于 `D:\vibe_coding\android_books\.pr19-work`，设备使用相同功能代码覆盖安装，全程未卸载、未清数据。

## 6. 下一步（测试流程）

建议顺序：
1. **第 0 步（最重要）数据库升级验证**：正式版留数据（书签/高亮/标签/进度）→ 装测试包 → 确认 v4→v6 迁移不丢数据、不闪退（本包 versionCode=7 覆盖安装，已保留数据）
2. 第一批：#6 → #7 → #16 → #21
3. 第二批高风险：#14 跨页选区、#10 悬浮窗、#20 TXT 替换（测试前备份 TXT）
4. 第二批其余 + 第三批

每测完一项：记录结果（通过/失败/现象），并**回写本文件第 4 节的状态**。

## 7. 临时目录与命令速查

- 临时仓库：`C:\Users\Huangder\AppData\Local\Temp\lumi_pr19_20260812210214\repo`
- 分支：`merge-pr19`；提交：`b89b6eb`
- 主仓库 main：`d052e7f`；PR 头：`spencer1012/Lumi_Books` @ `164ad21e`
- adb：`F:\SDK\platform-tools\adb.exe`；当前设备：小米 10（`60a76f64`，USB 直连）
- 编译：`cd <临时repo> && .\gradlew.bat :app:compileDebugKotlin --offline` 或 `:app:assembleDebug`
- 安装：`adb -s <device> install -r D:\vibe_coding\android_books\artifacts\lumi_pr19_merge-debug-arm64.apk`
- 测试文件：`/sdcard/lumi/pride_test.mobi`（540KB）、`/sdcard/lumi/pride_150mb_test.mobi`（150MB）；源头在 `%TEMP%\mobi_test\`（公版书《傲慢与偏见》Gutenberg 下载 + 脚本放大）
- SDK：`F:\SDK`（local.properties 里 sdk.dir）

## 8. 把分支拿回用户仓库的方式（待用户选择，未执行）

1. 推 `merge-pr19` 到 `origin`（分支名建议 `codex/merge-pr19`）；或
2. 生成 patch 由用户 `git apply`。

注意：用户本地工作区（`D:\vibe_coding\android_books`）目前干净（仅未跟踪 `.tmp_jsdom/`），**不要**动用户工作区，所有合并/修改都留在临时仓库。
