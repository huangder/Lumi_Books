# Canvas 引擎初始化修复 — 加载页面卡死

日期：2026-06-30

## 问题

点进书一直停留在过渡加载页面，永远不消失。

## 根因

`PageSlotManager.initialize()` 异步加载 CUR 槽位后，从未触发 `notifyPageChanged()` 回调。ViewModel 的 `isLoading` 标志只有 `onNewEnginePageChanged()` 才会设为 `false`，而这个方法只在 `notifyPageChanged()` → `onPageChangedCallback` 链中被调用。

`notifyPageChanged()` 原来只在 `shiftForward()`、`shiftBackward()`、`jumpTo()` 中调用，初始化路径漏掉了：

```
initialize() → loadSlot(SLOT_CUR) [async] → slot.isLoaded=true
                                            ↘ 无回调！→ isLoading 永远=true
```

## 修复

`PageSlotManager.loadSlot()` 中，当 SLOT_CUR 加载完成时调用 `notifyPageChanged()`：

1. **成功路径**：bitmap 设置完成后，若 slotIdx == SLOT_CUR → `notifyPageChanged()` → ViewModel `isLoading = false`
2. **空文本兜底**：contentProvider 返回 null/空时，也调用 `notifyPageChanged()`（页面为空但不卡死）
3. **异常兜底**：layout/render 抛异常时，也调用 `notifyPageChanged()`（保证 UI 不卡死）
4. **预加载补齐**：CUR 加载完成后自动预加载 PREV/NEXT（init 时因 CUR 未 loaded 同步跳过了）
5. **移除 cancel**：去掉了 `loadingJob?.cancel()`，避免 PREV/NEXT 的 `loadSlot()` 误取消 CUR 的加载协程

## 修改文件

`app/src/main/java/com/ebook/reader/ui/reader/engine/PageSlotManager.kt` — `loadSlot()` 方法

## 编译

```bash
./gradlew assembleDebug  # ✅ BUILD SUCCESSFUL
```
