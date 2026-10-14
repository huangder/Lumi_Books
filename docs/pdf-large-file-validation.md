# 大 PDF 原画模式内存回归

验证日期：2026-10-13。使用用户提供的 1,535,411,587 字节 PDF（2085 页），原文件不写入仓库。

## 文件及风险

- 页面为 JPEG 扫描图，页尺寸及图片尺寸主要为 1758×2500、1761×2500，最大为 1782×2500。
- 手机为 24129PN74C，Android 17，屏幕宽度 1200px，应用普通堆上限 256MiB。
- 旧策略原画没有像素预算。1758×2500 页按 1280px 宽度桶和 4 倍超采样渲染，单张 ARGB 位图约 142MiB；缩放后达到 6 倍 PDF 倍率上限时约 604MiB。页面缓存、PDF 解码和锐化副本还会额外占用内存。
- 未在设备上主动触发旧策略 OOM；上述旧版分配量根据原策略及实际页尺寸计算。此前的低内存退出记录无法单独证明由此文件引起。

## 修复

- 所有 PDF 清晰度档位均限制整页像素。原画最高 16MP，实际预算进一步限制为应用最大堆字节数 / 32；此设备约 8.39MP，即 32MiB 位图。
- 最长边限制为 8192px。内存不足时宽高减半重试，最多三次；失败渲染的位图回收，缓存键使用实际输出尺寸。
- 阅读区锐化遇到 OOM 保留源位图。
- 本文件源图约 4.4MP，此设备原画输出仍超过源 JPEG 分辨率；触及预算后继续放大不会再增加整页位图分配。

## 真机验证

仅执行 `:app:installDebug`，未安装测试 APK，未卸载或清空应用数据。

debug 专用 `PdfMemoryProbeActivity` 复用生产代码中的 `BitmapPageSourceFactory`、`RasterRenderSession` 和 `PdfPageDecoder`，开启相邻页预取；ImageView 实际绘制位图并检查绘制帧。原画额外执行 0.5 强度锐化。

抽样页（1-based）：1、2、3、200、406、407、408、591、1000、1500、2085。每页依次运行正常、高清、原画、原画 5 倍目标宽度，共 **44 次渲染全部通过**。原画输出约 2426×3456，最大分配低于 32MiB，无解码失败或本次进程崩溃。采样时总 PSS 约 277–573MiB，其中包含原生、GPU 等内存，不能等同于 Java 堆使用。

复现入口（文件已放入应用外部 files 目录）：

```powershell
.\gradlew.bat :app:installDebug
adb shell am start -W -n com.huangder.lumibooks/.ui.reader.PdfMemoryProbeActivity
adb shell cat /sdcard/Android/data/com.huangder.lumibooks/files/pdf-memory-probe.txt
```

手机需解锁且屏幕亮起。可通过 `--es pages 1,406,2085` 指定抽样页，通过 `--ez sharpen false` 关闭锐化。

验证范围是实际文件的渲染、缓存预取、锐化及硬件绘制；未逐页渲染全部 2085 页，未验证完整阅读器的全部手势及菜单流程。

## 本地回归

`RasterPageDecodePolicyTest` 包含此文件尺寸和手机堆上限的回归用例，并覆盖超长页、OOM 降级及重试停止条件。`RasterRenderSessionTest` 验证降级结果不会以全分辨率缓存键误命中。另执行 `ReaderImageAdjustmentsTest` 和 `:app:compileDebugAndroidTestKotlin`；没有运行 connected Android 测试。
