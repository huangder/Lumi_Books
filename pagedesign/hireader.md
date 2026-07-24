基于文档分析，hireader 解决章节切换空白页的核心机制：

## hireader 的章节切换机制

### 1. **预加载机制**
```java
// PageLoader 中有预加载逻辑
// 当用户翻到当前章最后几页时，提前加载下一章
private void updateStatus(int chapter) {
    // 预加载下一章
    if (chapter + 1 < chapterList.size()) {
        bookChapterBean = chapterList.get(chapter + 1);
        // 异步加载章节内容并分页
        loadChapter(bookChapterBean, true);
    }
}
```

### 2. **Bitmap 缓存**
```java
// PageView 使用 Bitmap 缓存
private Bitmap bitmap;
private Bitmap bitmapNext;  // 下一页的 Bitmap

// 翻页时直接切换 Bitmap，不需要重新绘制
private void drawPage(Canvas canvas) {
    canvas.drawBitmap(bitmap, 0, 0, null);
}
```

### 3. **章节切换流程**
```
用户翻到章节最后一页
    ↓
JS 调用 onChapterFlipReady(1)
    ↓
Kotlin 开始翻页动画（flipOffset 从 0 → -1）
    ↓
动画完成一半时，调用 nextChapter()
    ↓
PageLoader 已经预加载了下一章的页面数据
    ↓
直接从缓存中获取下一页的 Bitmap
    ↓
翻页动画继续（flipOffset 从 1 → 0）
    ↓
无缝切换完成
```

### 4. **关键区别**

| 特性 | hireader | 你的 App |
|------|----------|----------|
| 渲染方式 | Canvas + Bitmap | WebView + HTML |
| 分页计算 | 预加载，提前计算 | 实时计算 |
| 章节切换 | 直接切换 Bitmap | 重新加载 HTML |
| 空白页 | 无（预加载） | 有（等待分页） |

### 5. **你的 App 可以借鉴的思路**

1. **预加载下一章**：在用户翻到当前章最后几页时，提前加载下一章的 HTML 并分页
2. **双 WebView 方案**：使用两个 WebView，一个显示当前页，一个预加载下一章
3. **分页缓存**：缓存已分页的结果，避免重复计算

**最简单的修复方案**：在 `handleChapterFlipReady` 中，先预加载下一章的 HTML 并分页，等分页完成后再开始翻页动画。