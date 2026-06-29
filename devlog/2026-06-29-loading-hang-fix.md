# 2026-06-29 点击书本卡在加载界面修复

## 问题

点进书本后一直停留在加载界面（过渡动画），无法进入阅读页。

## 根因

[PageFlipAnimation.kt](../../app/src/main/java/com/ebook/reader/ui/animation/PageFlipAnimation.kt) `SlideCoverAnimation.buildAnimationJs()` 第 25 行：

```javascript
var topLayer = null, botLayer = null, edgeShadow = null, flipping = false;
// ...
botLayer._preIdx = undefined;  // ← 此时 botLayer 是 null！
```

这行代码在脚本**顶层**执行，此时 `botLayer` 还是 `null`（`initLayers()` 尚未被调用）。对 `null` 设置属性会抛出 JavaScript `TypeError`：

```
TypeError: Cannot set properties of null (setting '_preIdx')
```

这个异常导致整个 IIFE（立即执行函数）停止执行：
- `addEventListener('resize', init)` 未执行
- `init()` 从未被调用
- `AndroidBridge.onPageChanged()` 从未触发
- `AndroidBridge.onPaginationComplete()` 从未触发
- `ViewModel.isLoading` 永远为 `true`
- NavGraph 的 `BookTransitionOverlay` 永远不消失

**虽然 JS 函数声明会被提升（hoisting），`init()` 函数本身是存在的，但它从未被调用。**

## 修复

### 改动 1: PageFlipAnimation.kt — 修复 null 引用

将 `botLayer._preIdx = undefined;` 从脚本顶层移到 `initLayers()` 内部，在 `botLayer` 创建后执行。

```javascript
// 之前：脚本顶层（botLayer 为 null）
botLayer._preIdx = undefined;

// 之后：initLayers 内部（botLayer 已创建）
function initLayers(b) {
    // ...
    botLayer = document.createElement('div');
    // ...
    botLayer._preIdx = undefined;  // ← 移到这里
    // ...
}
```

同时给 `cleanupLayers()` 添加 null 安全检查：
```javascript
function cleanupLayers() {
    if (botLayer && botLayer._preIdx !== undefined) { ... }
    if (topLayer) { ... }
    if (botLayer) { ... }
    if (edgeShadow) { ... }
    if (outer) { ... }
}
```

### 改动 2: ReaderScreen.kt — 加载超时保护

添加 10 秒超时兜底机制，通过 `LaunchedEffect(uiState.chapterHtml)` 启动：
- 如果 HTML 已加载但 10 秒内 `isLoading` 仍为 `true`，强制调用 `viewModel.onPaginationDone()`
- 避免未来出现类似问题时用户被永久卡住

### 改动 3: ReaderScreen.kt — JS 诊断日志

- 分页 JS 注入 2 秒后读取 `window.__paginateDebug` 和 `window.__paginationReady`，输出诊断日志
- `handlePaginationComplete` 被调用时输出 `"onPaginationComplete CALLED"` 日志

## 验证

- `compileDebugKotlin`: BUILD SUCCESSFUL
- `assembleDebug`: BUILD SUCCESSFUL
- APK: `app/build/outputs/apk/debug/app-debug.apk`
