# LUMI — Dream
## Apple 风格产品宣传片项目

---

## 📋 项目概述

这是一个使用 Three.js + GSAP 构建的电影级产品动画，展示 LUMI 阅读器的核心理念。

**规格**
- 分辨率：1920 × 1080 (16:9)
- 时长：约 28 秒
- 帧率：60 fps (浏览器) / 30 fps (导出)
- 风格：Apple 产品发布会级别

---

## 🎬 镜头设计

### SHOT 01 — BLACK / LIGHT (3.5s)
从纯黑画面开始，柔和的光线扫过，LUMI 图标的玻璃轮廓逐渐显现。镜头缓慢推进。

### SHOT 02 — LUMI ICON (4.0s)
玻璃质感的图标悬浮在黑色空间中，带有极其细微的内部发光。图标缓慢旋转，展现立体感。

### SHOT 03 — BOOK TRANSFORMATION (4.5s)
图标以优雅的 spring 动画展开成为一本书。书页打开，每一页都有真实的物理惯性和厚度。

### SHOT 04 — READING INTERFACE (3.5s)
镜头进入书页，转换为 LUMI 阅读界面。文字行逐行出现，页面产生轻微纵向滚动。

### SHOT 05 — FILE FORMATS (4.0s)
EPUB、PDF、MOBI、TXT、MD、CBZ 六种格式如纸张般从阅读页面漂浮出来，经过镜头时产生轻微运动模糊，然后重新汇聚。

### SHOT 06 — LIQUID GLASS (4.5s)
进入极简的 Liquid Glass 场景。半透明玻璃面板缓慢展开，展示 UI 层次和深度。镜头继续推进。

### SHOT 07 — DREAM (4.0s)
所有元素消失，只剩黑色背景。镜头后退，画面中央出现 "LUMI" 和 "Dream" 文字，停留后缓慢淡出。

---

## 🚀 运行方法

### 方法 1：直接在浏览器打开

```bash
# Windows
start chrome lumi-dream-promo.html

# macOS
open -a "Google Chrome" lumi-dream-promo.html

# Linux
google-chrome lumi-dream-promo.html
```

### 方法 2：本地服务器（推荐）

```bash
# Python 3
python -m http.server 8000

# Node.js
npx http-server -p 8000

# 然后访问
# http://localhost:8000/lumi-dream-promo.html
```

### 键盘控制
- `R` - 重新播放动画

---

## 🎥 录制方法

### 方法 1：OBS Studio（推荐）

1. 下载安装 OBS Studio
2. 新建场景，添加「窗口捕获」源
3. 选择浏览器窗口
4. 设置输出：
   - 分辨率：1920x1080
   - 帧率：30 fps
   - 编码器：x264
   - 比特率：8000 Kbps
5. 全屏浏览器（F11）
6. 开始录制，按 `R` 播放动画

### 方法 2：Chrome 屏幕录制

1. 安装 Chrome 扩展：Screen Recorder
2. 设置录制区域为全屏
3. 选择 1920x1080 分辨率
4. 开始录制

### 方法 3：FFmpeg + Puppeteer（自动化）

需要 Node.js 环境，创建录制脚本：

```javascript
// record.js
const puppeteer = require('puppeteer');
const { exec } = require('child_process');

(async () => {
  const browser = await puppeteer.launch({
    headless: false,
    args: ['--window-size=1920,1080', '--enable-webgl']
  });
  
  const page = await browser.newPage();
  await page.setViewport({ width: 1920, height: 1080 });
  await page.goto('http://localhost:8000/lumi-dream-promo.html');
  
  // 等待资源加载
  await page.waitForTimeout(2000);
  
  // 开始录制
  console.log('开始录制...');
  
  // 使用 FFmpeg 捕获浏览器窗口
  exec('ffmpeg -f gdigrab -framerate 30 -i title="Chrome" -t 30 -c:v libx264 -preset slow -crf 18 output.mp4');
  
  // 触发动画
  await page.keyboard.press('r');
  
  // 等待动画完成
  await page.waitForTimeout(30000);
  
  console.log('录制完成');
  await browser.close();
})();
```

运行：
```bash
npm install puppeteer
node record.js
```

---

## 🎨 参数调节

所有关键参数都在代码开头的 `CONFIG` 对象中：

```javascript
const CONFIG = {
  // 时长控制
  totalDuration: 28,
  shot01Duration: 3.5,
  shot02Duration: 4.0,
  // ... 更多镜头时长

  // 相机
  cameraFov: 45,
  cameraSpeed: 0.3,

  // 视觉效果
  bloom: 0.3,
  glassOpacity: 0.08,
  glassBlur: 20,
  lightIntensity: 1.2,
  ambientIntensity: 0.4,

  // 动画
  springStiffness: 100,
  springDamping: 20,
  pageRotationSpeed: 0.5,

  // 后期效果
  grainAmount: 0.02,
  vignetteStrength: 0.3,
  depthOfField: 0.15
};
```

修改这些参数后刷新页面即可看到效果。

---

## 📦 导出视频（FFmpeg）

### 从录制的视频导出

如果已经用 OBS 或屏幕录制工具录制了视频：

```bash
# 转换为 H.264，优化质量
ffmpeg -i recorded.mp4 \
  -c:v libx264 \
  -preset slow \
  -crf 18 \
  -pix_fmt yuv420p \
  -r 30 \
  lumi-dream-final.mp4

# 添加背景音乐（如果有）
ffmpeg -i lumi-dream-final.mp4 -i bgm.mp3 \
  -c:v copy \
  -c:a aac \
  -b:a 192k \
  -shortest \
  lumi-dream-with-music.mp4
```

### 参数说明
- `-preset slow` - 使用慢速编码以获得最佳质量
- `-crf 18` - 恒定质量因子（0-51，18 是高质量）
- `-pix_fmt yuv420p` - 兼容性最好的像素格式
- `-r 30` - 帧率 30 fps

### 优化文件大小

```bash
# 压缩为适合网络分享的大小
ffmpeg -i lumi-dream-final.mp4 \
  -c:v libx264 \
  -preset medium \
  -crf 23 \
  -maxrate 5M \
  -bufsize 10M \
  lumi-dream-compressed.mp4
```

---

## 🎵 背景音乐建议

推荐的音乐风格（需自行添加）：

- **极简主义电子音乐**：Brian Eno 风格的环境音乐
- **节奏**：60-80 BPM（缓慢、沉浸）
- **乐器**：合成器垫音、钢琴、弦乐
- **氛围**：宁静、专注、未来感

推荐曲目参考：
- Max Richter - On The Nature of Daylight
- Ólafur Arnalds - Saman
- Nils Frahm - Says
- Jon Hopkins - Immunity

---

## 🔧 技术栈

- **Three.js** - 3D 渲染引擎
- **GSAP** - 专业级动画库
- **WebGL** - GPU 加速渲染
- **Pure JavaScript** - 无额外构建工具

---

## 📂 项目结构

```
lumi-dream-promo.html       # 主文件（包含所有代码）
README-LUMI-DREAM.md         # 本说明文档
output/                      # 导出视频存放目录（需自行创建）
  lumi-dream-final.mp4
  lumi-dream-with-music.mp4
```

---

## 🎯 设计原则

### Apple 风格特征
1. **极简主义** - 每个镜头只展示一个核心元素
2. **慢速运动** - 所有动画都使用缓慢、优雅的节奏
3. **高级材质** - 真实的玻璃、金属、纸张质感
4. **精确曲线** - 使用 cubic-bezier 和 spring 动画
5. **留白呼吸** - 大量黑色背景，给视觉以休息空间

### 动画曲线
- **淡入**：`power2.out`
- **淡出**：`power2.in`
- **转场**：`power1.inOut`
- **弹性**：`back.out(1.2)`
- **循环**：`sine.inOut`

### 避免的元素
- ❌ 廉价的线性动画
- ❌ 过度的粒子特效
- ❌ 夸张的镜头光晕
- ❌ 突兀的颜色变化
- ❌ PPT 式的转场
- ❌ AI 生成的随机运动

---

## 🐛 故障排查

### 动画不流畅
- 降低浏览器缩放比例（Ctrl + 0 重置）
- 关闭其他占用 GPU 的程序
- 使用 Chrome 或 Edge 浏览器
- 检查显卡驱动是否最新

### 页面一片黑
- 检查浏览器控制台是否有错误
- 确认 Three.js 和 GSAP CDN 加载成功
- 刷新页面（Ctrl + F5 强制刷新）

### 文字没有显示
- 等待 0.5 秒，动画会自动开始
- 按 `R` 键手动重新播放

---

## 🔄 后续优化方向

当前版本是第一版实现，后续可以优化的方向：

1. **添加 Bloom 后期效果** - 使用 EffectComposer
2. **添加景深效果** - BokehPass
3. **添加细腻噪点** - FilmPass
4. **优化玻璃材质** - 使用 PMREM 环境贴图
5. **添加粒子系统** - 极其细微的漂浮粒子
6. **镜头运动优化** - 更平滑的相机路径
7. **文字动画增强** - 字母逐个出现效果

---

## 📞 技术支持

如有问题，请检查：
1. 浏览器版本（建议 Chrome 90+）
2. WebGL 支持（访问 get.webgl.org 测试）
3. JavaScript 控制台错误信息

---

**制作时间**：约 28 秒  
**技术难度**：中高级  
**设计风格**：Apple 产品发布会级别  
**适用场景**：产品发布、品牌宣传、应用介绍

---

*LUMI — Dream*  
*为阅读而生*
