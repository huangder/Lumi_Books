# 2026-06-27 修复计划：背景色硬编码 + 字号丢失 + 章节切换卡顿

## 问题清单

### 问题1：背景色硬编码为白色

**现象**：切换主题（夜间/护眼/绿色）后，阅读页背景依然是白色，只有切换章节的瞬间能看到正确背景色。

**根因**：多处 JS 代码硬编码 `background:#FFF`：
- `buildPaginationJs` 中 body（第150行）、wrap（第166行）、outer（第183行）的 `style.cssText`
- `PageFlipAnimation.kt` 中 topLayer（第34行）、botLayer（第37行）、flipTo函数（第58、72行）、aDrag函数（第121行）

CSS 的 `!important` 无法覆盖行内样式 `style.cssText` 中的背景色。

**修复方案**：
- `buildPaginationJs` 增加参数 `bgColor: String`，替换所有 `#FFF` 为变量
- `PageFlipAnimation.buildAnimationJs()` 增加参数 `bgColor: String`，替换所有 `#FFF` 为变量
- `onPageFinished` 中注入分页 JS 时传入当前主题的背景色
- `update` 中主题变化时，通过 JS 更新 body/wrap/outer/topLayer/botLayer 的背景色

**涉及文件**：
- `ReaderScreen.kt` — buildPaginationJs 函数、onPageFinished、update 块
- `PageFlipAnimation.kt` — buildAnimationJs 函数

---

### 问题2：切换章节/退出重进后字号变小

**现象**：用户设置的字号是20，切换章节或退出重进后，实际显示的字号变小了（回到默认16），但设置面板显示的还是20。

**根因**：`webViewClient` 在 `factory` lambda 中创建，只执行一次。`onPageFinished` 回调中捕获的 `fontSize` 是 WebView 创建时的值，不会随 Compose 重组更新。

流程：
1. 用户打开阅读器，`fontSize=16`（默认值，DataStore还没加载完）
2. `factory` 执行，创建 `webViewClient`，闭包捕获 `fontSize=16`
3. DataStore 加载完成，`uiState.fontSize` 更新为 `20`
4. 用户切换章节 → `onPageFinished` 触发 → 注入 `fontSize=16`（闭包中的旧值）

**修复方案**：
- 在 `HtmlContent` 中使用 `remember` 存储一个可变的 `currentFontSize` 变量
- 在 `update` 中更新 `currentFontSize` 的值
- `onPageFinished` 读取 `currentFontSize` 而不是闭包捕获的值

或者更简单：在 `onPageFinished` 中不注入字体 CSS，只运行分页 JS。字体 CSS 统一由 `update` 块注入（`update` 每次重组都会执行，能拿到最新值）。

**涉及文件**：
- `ReaderScreen.kt` — HtmlContent 函数的 factory 和 update 块

---

### 问题3：切换章节卡顿

**现象**：滑到边缘后，不是立刻切换章节，而是卡住一会儿才切换。

**根因**：`handleChapterFlipReady` 中，即使有预渲染的章节，也需要先查询 JS 状态（同步查询可能失败），然后走加载流程。原来的章节切换流程有延迟。

需要检查：
- `handleChapterFlipReady` 是否正确调用 `usePreRendered`
- `evaluateJavascriptSync` 是否可靠工作
- 预渲染是否在正确的时机触发

**修复方案**：
- 简化 `handleChapterFlipReady`：直接调用 `usePreRendered`，成功则立即切换
- 如果预渲染失败，走原来的加载流程，但减少不必要的延迟
- 确保预渲染在用户接近边缘时就开始，而不是等到切换时

**涉及文件**：
- `ReaderScreen.kt` — handleChapterFlipReady 函数
- `ReaderViewModel.kt` — 章节切换相关函数

---

### 问题4：字号显示不一致

**现象**：设置面板显示字号20，但实际显示的是16。

**根因**：同问题2。设置面板读取的是 `uiState.fontSize`（DataStore中的值），而 WebView 实际使用的是闭包捕获的旧值。

**修复方案**：修复问题2后，此问题自动解决。

---

## 执行顺序

1. **问题2+4**（字号丢失）— 最核心，影响阅读体验
2. **问题1**（背景色硬编码）— 影响主题切换
3. **问题3**（章节切换卡顿）— 影响流畅性

## 验证方法

1. 设置字号为20 → 退出重进 → 检查显示是否为20
2. 设置字号为20 → 切换章节 → 检查显示是否为20
3. 切换到夜间主题 → 检查背景是否为深色
4. 切换到护眼主题 → 检查背景是否为米色
5. 滑到章节边缘 → 检查是否立即切换（无卡顿）
