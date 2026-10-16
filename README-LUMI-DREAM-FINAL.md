# LUMI — Dream 
## 电影级产品宣传片完整项目

---

## 📦 项目文件清单

```
lumi-dream-promo.html        # 基础版本
lumi-dream-enhanced.html     # 增强版本（推荐）★
README-LUMI-DREAM.md         # 本文档
record-video.js              # 自动录制脚本（需创建）
package.json                 # Node.js 依赖配置（需创建）
output/                      # 输出目录（自动创建）
  └── lumi-dream-final.mp4
```

---

## 🎯 两个版本对比

### 基础版 (lumi-dream-promo.html)
- 纯 Three.js + GSAP
- 适合快速预览
- 性能要求低

### 增强版 (lumi-dream-enhanced.html) ★ 推荐
- 添加 Bloom 后期效果
- 添加 Vignette 暗角
- 添加细腻噪点纹理
- 优化材质质感
- 更精细的动画曲线
- 添加进度条显示
- 更多交互控制

**建议使用增强版进行最终录制。**

---

## 🚀 快速开始

### 1. 在浏览器中预览

```bash
# 直接打开增强版
start chrome lumi-dream-enhanced.html

# 或使用本地服务器
python -m http.server 8000
# 访问 http://localhost:8000/lumi-dream-enhanced.html
```

### 2. 键盘控制

- `R` - 重新播放
- `Space` - 暂停/继续
- `F11` - 全屏（录制前建议全屏）

---

## 🎥 录制方法（三种方案）

### 方案 1：OBS Studio（最推荐）

**优点**：最高质量，最稳定，专业级

**步骤**：

1. **下载安装 OBS Studio**  
   https://obsproject.com/

2. **配置 OBS**
   - 新建场景
   - 添加来源 → 窗口捕获
   - 选择 Chrome 浏览器窗口

3. **设置输出**
   - 设置 → 输出
   - 输出模式：高级
   - 编码器：x264
   - 比特率：10000 Kbps
   - 预设：Quality
   - 配置文件：high

4. **设置视频**
   - 设置 → 视频
   - 基础画布分辨率：1920x1080
   - 输出分辨率：1920x1080
   - 帧率：30 fps

5. **开始录制**
   - 在浏览器中按 F11 全屏
   - 在 OBS 中点击「开始录制」
   - 在浏览器中按 `R` 开始动画
   - 等待 30 秒动画完成
   - 停止录制

6. **文件位置**
   - 默认：`C:\Users\[用户名]\Videos\`
   - 文件名：`2026-XX-XX XX-XX-XX.mp4`

---

### 方案 2：Chrome 内置录制

**优点**：简单，无需额外软件

**步骤**：

1. 打开 Chrome
2. 按 `F12` 打开开发者工具
3. `Ctrl + Shift + P` 打开命令面板
4. 输入 "Capture" → 选择 "Capture full size screenshot"
5. 或使用扩展：Screencastify / Loom

---

### 方案 3：FFmpeg 自动化（高级用户）

**优点**：完全自动化，可重复，最高质量

创建 `record.bat`（Windows）：

```batch
@echo off
echo 正在启动 Chrome...
start chrome --new-window --start-fullscreen http://localhost:8000/lumi-dream-enhanced.html

echo 等待 3 秒让页面加载...
timeout /t 3

echo 开始录制 30 秒...
ffmpeg -f gdigrab -framerate 30 -i desktop -t 30 -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p output\lumi-dream-raw.mp4

echo 录制完成！
pause
```

运行：
```bash
record.bat
```

---

## 🎨 后期处理（FFmpeg）

录制完成后，使用 FFmpeg 进行后期优化：

### 1. 基础优化

```bash
# 创建输出目录
mkdir output

# 优化质量和压缩
ffmpeg -i lumi-dream-raw.mp4 ^
  -c:v libx264 ^
  -preset slow ^
  -crf 18 ^
  -pix_fmt yuv420p ^
  -r 30 ^
  output\lumi-dream-final.mp4
```

### 2. 添加背景音乐

```bash
# 将音乐和视频合并
ffmpeg -i output\lumi-dream-final.mp4 -i bgm.mp3 ^
  -c:v copy ^
  -c:a aac ^
  -b:a 192k ^
  -shortest ^
  output\lumi-dream-with-music.mp4
```

### 3. 调整色彩（可选）

```bash
# 提升对比度和饱和度
ffmpeg -i output\lumi-dream-final.mp4 ^
  -vf "eq=contrast=1.1:saturation=1.05" ^
  -c:a copy ^
  output\lumi-dream-color-graded.mp4
```

### 4. 生成 GIF 预览

```bash
# 生成低分辨率 GIF 用于预览
ffmpeg -i output\lumi-dream-final.mp4 ^
  -vf "fps=15,scale=640:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" ^
  output\lumi-dream-preview.gif
```

### 5. 压缩用于网络分享

```bash
# 压缩到适合上传的大小（<10MB）
ffmpeg -i output\lumi-dream-final.mp4 ^
  -c:v libx264 ^
  -preset medium ^
  -crf 28 ^
  -maxrate 2M ^
  -bufsize 4M ^
  -vf "scale=1920:1080" ^
  output\lumi-dream-compressed.mp4
```

---

## ⚙️ 参数调节指南

打开 `lumi-dream-enhanced.html`，在代码开头找到 `CONFIG` 对象：

```javascript
const CONFIG = {
  // 时长调节
  shot01Duration: 4.0,    // 增加可让动画更慢
  shot02Duration: 4.5,
  // ...

  // 相机
  cameraFov: 50,          // 减小 = 更远，增大 = 更近
  cameraInitialZ: 18,     // 初始相机距离

  // 视觉效果
  bloom: {
    strength: 0.4,        // 光晕强度 (0-3)
    radius: 0.8,          // 光晕范围 (0-1)
    threshold: 0.1        // 触发阈值 (0-1)
  },
  glassOpacity: 0.12,     // 玻璃透明度 (0-1)
  glassTransmission: 0.95,// 玻璃透光率 (0-1)
  lightIntensity: 0.8,    // 主光强度
  ambientIntensity: 0.3,  // 环境光强度

  // 后期效果
  vignette: 0.4,          // 暗角强度 (0-1)
  grain: 0.03             // 噪点强度 (0-0.1)
};
```

### 调节建议

**更强的电影感**：
```javascript
bloom.strength: 0.6
vignette: 0.5
grain: 0.05
```

**更清爽的科技感**：
```javascript
bloom.strength: 0.2
vignette: 0.2
grain: 0.01
```

**更慢的节奏（30秒 → 40秒）**：
```javascript
shot01Duration: 5.5
shot02Duration: 6.0
shot03Duration: 6.5
shot04Duration: 5.5
shot05Duration: 6.0
shot06Duration: 6.5
shot07Duration: 5.0
```

修改后刷新页面（Ctrl + F5）即可看到效果。

---

## 🎵 背景音乐建议

### 推荐风格
- 极简主义电子音乐
- 环境音乐 (Ambient)
- 节奏：60-90 BPM
- 情绪：宁静、专注、未来感

### 推荐曲目

**免费可商用**：
- Epidemic Sound: "Dreams" 系列
- Artlist: "Minimal Tech" 类别
- YouTube Audio Library: "Ambient" 分类

**参考艺术家**（需购买版权）：
- Max Richter
- Ólafur Arnalds
- Nils Frahm
- Jon Hopkins
- Brian Eno

### 音乐时长要求
- 应至少 30 秒
- 建议选择 1-2 分钟的完整曲目，然后裁剪

---

## 📊 技术规格总结

| 项目 | 参数 |
|------|------|
| 分辨率 | 1920 × 1080 (16:9) |
| 帧率 | 30 fps (推荐) / 60 fps (浏览器) |
| 时长 | ~31 秒 |
| 编码 | H.264 |
| 比特率 | 8-10 Mbps |
| 文件大小 | 30-50 MB (无压缩) / 5-10 MB (压缩) |
| 像素格式 | yuv420p |

---

## 🎬 镜头详细设计

### SHOT 01 — BLACK / LIGHT (4.0s)
- 从纯黑开始
- 主光源从右侧扫到左侧
- 玻璃图标轮廓逐渐显现
- 相机从 z=18 推进到 z=14
- 50 个细小粒子淡入

### SHOT 02 — LUMI ICON (4.5s)
- 图标完全显现，带有内部粉色光晕
- 缓慢旋转 y 轴 108° 和 z 轴 27°
- 细微呼吸动画（scale 1.0 → 1.03）
- 粒子绕 z 轴旋转 90°

### SHOT 03 — BOOK TRANSFORMATION (5.0s)
- 图标缩小消失
- 书籍以弹性动画出现
- 8 页书页依次翻开（间隔 0.25s）
- 相机推进到 z=9，y 轴微调到 0.5

### SHOT 04 — READING INTERFACE (4.0s)
- 书籍缩小消失
- 阅读界面以 0.6 倍大小放大到 1.0
- 12 行文字逐行出现（间隔 0.06s）
- 页面缓慢向上滚动

### SHOT 05 — FILE FORMATS (4.5s)
- 6 种文件格式卡片从上方飘落
- 每张卡片旋转 360° 经过镜头
- 卡片产生浮动动画（不同频率）
- 整组旋转 y 轴 27°

### SHOT 06 — LIQUID GLASS (5.0s)
- 3 层玻璃面板从下方升起
- 每层透明度递减（0.12 → 0.10 → 0.08）
- 面板产生细微呼吸（scale 1.0 → 1.02）
- 相机推进到 z=5
- 整组旋转 y 轴 18°

### SHOT 07 — DREAM (4.0s)
- 所有 3D 元素淡出
- Bloom 强度提升到 0.8
- "LUMI" 文字 2.5 秒淡入
- "Dream" 文字延迟 0.7 秒后淡入
- 相机后退到 z=25
- 停留 1.5 秒后淡出

---

## 🔍 质量检查清单

录制完成后，检查以下项目：

- [ ] 分辨率确实是 1920×1080
- [ ] 没有录入浏览器 UI 元素（地址栏等）
- [ ] 动画完整播放，没有卡顿
- [ ] 没有鼠标指针出现在画面中
- [ ] 黑色背景纯净，没有压缩噪点
- [ ] 文字清晰可读
- [ ] 玻璃效果正常渲染
- [ ] 总时长约 30 秒
- [ ] 如添加音乐，音画同步

---

## 🐛 常见问题

### Q: 动画卡顿不流畅
**A**: 
- 关闭其他占用 GPU 的程序
- 降低浏览器缩放（Ctrl + 0）
- 使用 Chrome 或 Edge（性能最好）
- 录制时降低 OBS 预览质量

### Q: 玻璃效果看不到
**A**: 
- 确认显卡支持 WebGL 2.0
- 更新显卡驱动
- 尝试基础版 `lumi-dream-promo.html`

### Q: 页面一片黑
**A**: 
- 等待 0.8 秒，动画会自动开始
- 按 `R` 手动重播
- 检查浏览器控制台错误（F12）

### Q: 文字模糊
**A**: 
- 确保浏览器缩放为 100%
- 录制时使用更高的比特率（12000+）
- 导出时使用 `-crf 18` 或更低

### Q: 录制的视频文件太大
**A**: 
```bash
# 使用此命令压缩
ffmpeg -i input.mp4 -c:v libx264 -crf 23 -preset medium output.mp4
```

---

## 📈 性能优化

如果在低配电脑上运行卡顿，可以降低质量：

```javascript
// 在 CONFIG 中调整
renderer.setPixelRatio(1);  // 降低像素比
bloom.radius: 0.5,          // 降低 Bloom 范围
```

或使用基础版 `lumi-dream-promo.html`，它不包含后期效果。

---

## 🎓 技术解析

### 使用的技术

1. **Three.js** - 3D 场景渲染
   - PerspectiveCamera - 透视相机
   - DirectionalLight - 平行光
   - MeshPhysicalMaterial - 物理材质（玻璃效果）
   - MeshStandardMaterial - 标准材质（书页）

2. **后期处理**
   - EffectComposer - 后期合成器
   - UnrealBloomPass - 虚幻 Bloom 效果
   - ShaderPass - 自定义着色器（Vignette + Grain）

3. **GSAP** - 动画库
   - Timeline - 时间轴
   - power2/power3 - 缓动曲线
   - elastic - 弹性效果

### 为什么选择这些技术

- **Three.js**：最成熟的 WebGL 库，性能好，文档完善
- **GSAP**：业界标准动画库，曲线精准，性能优秀
- **后期处理**：让渲染看起来更电影化

---

## 📂 完整项目文件结构

```
lumi-dream/
│
├── lumi-dream-promo.html          # 基础版
├── lumi-dream-enhanced.html       # 增强版 ★
├── README-LUMI-DREAM.md           # 本文档
│
├── output/                        # 输出目录
│   ├── lumi-dream-raw.mp4
│   ├── lumi-dream-final.mp4
│   ├── lumi-dream-with-music.mp4
│   └── lumi-dream-preview.gif
│
└── assets/ (可选)
    ├── bgm.mp3                    # 背景音乐
    └── lumi-logo.svg              # Logo 素材
```

---

## 🎯 最终检查

完成所有步骤后，你应该得到：

1. ✅ 一个 1920×1080 的高质量 MP4 视频
2. ✅ 时长约 30 秒
3. ✅ 文件大小 30-50 MB（或压缩后 5-10 MB）
4. ✅ 流畅的 30 fps 动画
5. ✅ Apple 风格的克制美学
6. ✅ 配有合适的背景音乐（可选）

---

## 📞 下一步

1. **测试增强版**：在浏览器打开 `lumi-dream-enhanced.html`
2. **调节参数**：根据个人喜好调整 CONFIG
3. **录制视频**：使用 OBS Studio 录制
4. **后期处理**：使用 FFmpeg 优化
5. **添加音乐**：合并背景音乐
6. **分享发布**：上传到 TikTok / YouTube / Bilibili

---

**制作时间**：约 30 秒  
**技术难度**：中高级  
**最终效果**：Apple 产品发布会级别

*LUMI — Dream*  
*为阅读而生*
