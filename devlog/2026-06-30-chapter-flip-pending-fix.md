# 章节翻页失效修复（第四轮）

日期：2026-06-30

## 问题

第三轮 `__pendingFlip` 机制引入后，真机测试发现仍然不工作：
- 拖拽到章节末尾松手弹回原来那页
- 末页点击右边缘无反应
- 总是看到 loading 转圈

## 根因

通过对代码逐行审查，发现 5 个 bug：

### Bug 1（致命）：`cleanupLayers()` 过早清除 `__pendingFlip`

**位置**：`PageFlipAnimation.kt` SlideCoverAnimation 第 38 行

`bounceBack()` → `cleanupLayers()` → `window.__pendingFlip = undefined` 在异步内容到达之前就清除了 pending 标记。内容加载完成后 `onPreRenderReady` 找不到匹配的 `__pendingFlip`，落入场景3（仅缓存），章节切换永不完结。

### Bug 2（致命）：`bounceBack()` 过早清除 `__pendingFlip`

**位置**：`PageFlipAnimation.kt` SimpleSlideAnimation 第 521 行

PDF 动画的 `bounceBack()` 同样问题。

### Bug 3：章节内拖拽不清除旧 pending

用户在章节边界触发 `__pendingFlip`，但往回拖进章节内（不松手），旧 pending 继续存在，内容到达后可能触发意料之外的章节切换。

### Bug 4：`__currentChapterIdx` 未初始化

`onPreRenderReady` 中 `chapterIndex > window.__currentChapterIdx` 在 `__currentChapterIdx` 为 `undefined` 时得到 NaN 比较结果，导致 `chapterIndex !== NaN` 永远为 true，函数提前 return。

`ReaderScreen.kt` 第 1015 行的 `evaluateJavascript("__currentChapterIdx = $chapterIndex")` 和分页 JS 注入之间无顺序保证。

### Bug 5：`__currentChapterIdx` 为 undefined 导致 `__pendingFlip` 被设为 NaN

`adjIdx = undefined + 1 → NaN`，`__pendingFlip = NaN`，后续 `NaN === chapterIndex` 永远 false。

## 修复

### PageFlipAnimation.kt（6 处）

1. **SlideCoverAnimation `cleanupLayers()`**：删除 `window.__pendingFlip = undefined;`，改为注释说明不在 bounceBack 时清除 pending
2. **SimpleSlideAnimation `bounceBack()`**：删除 `window.__pendingFlip = undefined;`
3. **SlideCoverAnimation `aDrag` else 分支**：添加 `window.__pendingFlip = undefined;`（章节内拖拽时取消旧 pending）
4. **SimpleSlideAnimation `aDrag`**：重构 if/else，else 分支清除 `__pendingFlip`
5. **SlideCoverAnimation `onPreRenderReady`**：添加 `__currentChapterIdx !== undefined` 守卫
6. **SimpleSlideAnimation `onPreRenderReady`**：同上

### ReaderScreen.kt（2 处）

7. **`buildPaginationJs()`**：新增 `chapterIndex` 参数，在 `init()` 函数开头初始化 `window.__currentChapterIdx = $chapterIndex`，从根源消除竞态
8. **`evaluateJavascript` 回调**：所有空 `{}` 回调替换为结果日志回调，便于排查注入失败

## 设计原则

`__pendingFlip` 生命周期：

| 操作 | 清除？ | 原因 |
|------|--------|------|
| `completeChapterSwap` | ✅ | 章节切换成功 |
| `onPreRenderReady` 处理完成 | ✅ | 内容已到位 |
| `aDrag` 非边界分支 | ✅ | 用户取消跨章节操作 |
| `cleanupLayers()` | ❌ | bounceBack 是合法的等待状态 |
| `bounceBack()` | ❌ | 同上 |

## 编译

```bash
./gradlew compileDebugKotlin   # BUILD SUCCESSFUL
./gradlew assembleDebug         # BUILD SUCCESSFUL
```

## 验证要点

- 拖拽到章节末松手 → loading 闪现 → 弹回 → 1-2秒内自动滑入下一章
- 末页点击右边缘 → 自动触发章节切换动画
- logcat 检查 `"PG"` tag：无 NaN、undefined、静默失败
- `evaluateJavascript` 回调日志确认内容注入成功
