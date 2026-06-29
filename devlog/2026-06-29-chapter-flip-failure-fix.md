# 章节翻页失效修复（第三轮）

日期：2026-06-29

## 问题

前两轮修复后：
- 动画跟手（拖拽时内容可见）
- **但拖拽松手后无法完成章节切换**（始终滑不到下/上一章）
- **点击左右边缘也无法翻到相邻章节**

## 根因分析

3 个相互关联的 bug：

### Bug 1 — 点击跨越章节边界：绕过 tapFlip

`buildPaginationJs` 的 `touchend` handler 在章节边界（首页/末页）直接调用 `onChapterFlipReady`，跳过了 `tapFlip` 的预渲染检查：

```javascript
// 修复前：
if (cur > 0) { tapFlip(-1); }
else try { AndroidBridge.onChapterFlipReady(-1); } catch(e) {}
```

`tapFlip` 内部有完整的边界逻辑（检查 `preRendered[adjIdx]` → `chapterFlipTo` 或 `onChapterFlipReady`），但 touchend 根本没用它。即使 eager 预渲染已就绪，点击也不生效。

**修复**：touchend 始终调用 `tapFlip(dir)`，让 `tapFlip` 统一处理章节内和跨章节两种情况。

### Bug 2 — 拖拽 Miss Path：内容到达后被 `_loadingIdx` 检查拦截

流程：
1. `aDrag` 在章节边界 → Miss Path → loading spinner + `botLayer._loadingIdx` 设置 + `onChapterFlipReady` → Kotlin async 加载
2. 用户松手 → `dEnd` → 无 preRender → `bounceBack` → `cleanupLayers` 清除 `botLayer.innerHTML` + `_loadingIdx`
3. Kotlin 加载完成 → `preRenderChapter` 创建 `preRendered[adjIdx]` → `onPreRenderReady(adjIdx)`
4. `onPreRenderReady` 检查 `botLayer._loadingIdx !== chapterIndex` → **`undefined !== N` → TRUE → return**

内容被丢弃！`preRendered[adjIdx]` 已缓存，但章节切换未完成。用户必须再拖一次才能走 Hit Path——但用户不知道，以为功能坏了。

### Bug 3 — `preRenderChapter` 静默吞错

`try { atob(htmlB64); ... } catch(e) {}` — 空 catch，atob 失败、innerHTML 解析失败、scrollWidth 计算失败都不可见。

## 修复方案

新增 **`window.__pendingFlip` 机制**：追踪"用户已触发章节切换，等待内容到达"的状态。

### 修改文件

#### [ReaderScreen.kt](app/src/main/java/com/ebook/reader/ui/reader/ReaderScreen.kt)（2 处）

1. **touchend handler**：章节边界不再绕过 `tapFlip`，始终调用 `tapFlip(dir)`
2. **`preRenderChapter`**：错误信息写入 `window.__preRenderError`，便于调试

#### [PageFlipAnimation.kt](app/src/main/java/com/ebook/reader/ui/animation/PageFlipAnimation.kt)（7 处）

1. **`cleanupLayers`**：增加 `window.__pendingFlip = undefined` 清理
2. **`completeChapterSwap`**：成功切换后清除 `window.__pendingFlip`
3. **`aDrag` Miss Path**：创建 loading spinner 后设置 `window.__pendingFlip = adjIdx2`
4. **`tapFlip` 边界**（SlideCoverAnimation）：无预渲染时设置 `window.__pendingFlip = adjIdx`
5. **`onPreRenderReady`（SlideCoverAnimation）**：核心改造——三种场景分流：
   - 场景 1：用户仍在拖拽（`dg.on`）→ 无缝替换 loading 为真实内容 + 清除 pending
   - 场景 2：用户已松手 + `__pendingFlip` 匹配 → **自动调用 `chapterFlipTo(dir)`** 完成章节切换动画
   - 场景 3：无 pending → 仅清除 loading 标记，preRendered 缓存留给下次

6. **SimpleSlideAnimation（PDF）**：同 SlideCoverAnimation，增加 `__pendingFlip` 跟踪 + `onPreRenderReady` 自动完成

### 设计要点

- `__pendingFlip` 在用户触发跨章节操作时设置（`aDrag` Miss Path、`tapFlip` 边界），在以下时清除：
  - `cleanupLayers`（bounceBack 后）
  - `completeChapterSwap`（章节切换成功后）
  - `onPreRenderReady`（内容到达并处理完后）
- `onPreRenderReady` 不再依赖 `botLayer._loadingIdx` 判断是否该完成切换（`_loadingIdx` 在 bounceBack 后已被清除）。改用独立的 `__pendingFlip` 标记
- PDF 的 `SimpleSlideAnimation` 新增 `onPreRenderReady`（之前没有），支持自动完成

## 编译

```bash
./gradlew compileDebugKotlin   # BUILD SUCCESSFUL
./gradlew assembleDebug         # BUILD SUCCESSFUL
```
