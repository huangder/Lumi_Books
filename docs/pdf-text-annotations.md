# PDF 文字标注

PDF 原版页面继续由 `PdfRenderer` 渲染，文字层通过现有 PDFBox 提取，不依赖 Android 新版 PDF 扩展接口；应用最低版本仍为 Android 8。

## 使用

- 普通阅读模式下长按文字，拖动两端手柄可在本页内跨行选择；菜单提供复制、高亮、划线、笔记、摘录、PDF 全书搜索、网页搜索和词典。长按成功时有触觉反馈，手指在文字附近也可命中。
- 缩放后手柄保持屏幕大小，单指平移保留选区，点击空白处清除；切页、双指操作或切换阅读模式清除。
- 高亮沿用阅读器色板透明度，圆角及留白随字高收小，相邻文字行之间保留间隔；下划线共用阅读器的细线与平滑波浪样式。
- 点击已有标注，可修改颜色、下划线样式、标签、笔记或删除记录。
- 阅读底部菜单的“更多 → 笔记”打开标注列表，点击记录回到对应页并强调原文。旧批注若没有有效文字定位，仍保留在列表中并可跳页。
- 手写批注模式继续使用原画笔、荧光笔和橡皮，文字标注仍然显示。连续滚动中的落笔及预览使用实际页面画布坐标，排除封面居中留白，保持松手保存前后的位置一致。
- 无文字层页面提示“此页暂无可选文字”；第一版不含 OCR、跨页选区、写回原 PDF。

## 数据与资源

- 使用独立、延迟打开的 PDFBox 会话，首次页面展示完成后才后台提取可见页；缓存最近三页，采用 32 MiB 流缓存与磁盘临时文件策略。该配置不是整个 PDFBox 文档对象的总内存上限。
- 页内文字使用 UTF-16 偏移；连字与代理对作为完整字形选择。几何位置在旋转后的完整 PDF 页内归一化，显示层再处理缩放、居中留白和左右裁边。
- 现有 `Note` 表保存普通 `highlight` / `underline` 与笔记身份；页号存入 `chapterIndex`。locator 使用 `kind=pdf_text, version=1`，包含文件指纹、页文本摘要、选中文字及各行四边形，不改变数据库结构。
- 文件指纹使用长度和文件首尾各 64 KiB，路径变化不影响备份恢复；绘制前还校验该页完整文本摘要、选区文本；显示时用当前完整字高重建几何位置，以兼容早期偏细的定位框。无法匹配的标注保留记录但不绘制。
- 删除了打开 PDF 时自动清理旧文字批注的链路。此前已经被旧版本删除的数据无法凭此变更恢复。

## 验证

本地测试涵盖 Android API 26、29、35 的实际 PDF 提取、四种页面旋转、非零 CropBox、两行文字、扫描图片页、LRU、中文与连字的范围映射、双栏顺序、裁边坐标、批注拆分、Room 重开与备份恢复。Compose 测试覆盖长按震动、手指轻微移动和文字附近命中、跨行拖动手柄、缩放后手柄尺寸、平移保留选区、空白点击清除、已有批注点击与阅读器手势竞争、笔记预填及菜单改色和线型操作。原生 Canvas 本地测试比较 PDF 四种划线与阅读器的像素一致性，并检查高亮覆盖混合字高和色板透明度。

另外提供 `PdfTextRenderingInstrumentedTest`，比较实际 `PdfRenderer` 黑色像素与文字四边形是否重合。Robolectric 无法完整运行原生 PDF 渲染，该测试按项目要求只做本地编译校验，未安装或执行测试 APK。

回归命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*PdfText*Test' --tests '*PdfAnnotationPersistenceTest' --tests '*RasterReaderActionsTest' --tests '*SelectionExcerptMenuTest' --tests '*PdfZoomGestureTest' --tests '*PdfPageModeTest' --tests '*RasterPageDecodePolicyTest' --tests '*AnnotationNoteEditPlannerTest' --tests '*BookNotesExportFormatterTest' --tests '*ReaderUnderlineRenderingTest' --tests '*ContinuousUnderlineAnnotationsTest' :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

真机手工验收应检查：全部翻页模式、双页、放大后选择、自动/手动裁边、标注改色与样式、笔记编辑/删除/标签、退出重开、列表定位、导出，以及大扫描 PDF 快速翻页的内存与流畅度。设备安装只能使用 `:app:installDebug`；不要安装测试 APK、运行 connected 测试、卸载或清空应用数据。
