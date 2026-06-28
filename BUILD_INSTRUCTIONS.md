# 📱 电子书阅读器 - APK构建说明

## 🎯 快速构建（推荐）

### 使用Android Studio

1. **打开Android Studio**
2. **选择 "Open an existing Android Studio project"**
3. **选择项目目录**：`d:\vibe_coding\android_books`
4. **等待Gradle同步**（首次需要5-10分钟下载依赖）
5. **点击菜单**：`Build → Build Bundle(s) / APK(s) → Build APK(s)`
6. **等待构建完成**
7. **获取APK**：`app/build/outputs/apk/debug/app-debug.apk`

## 🔧 命令行构建

### 前提条件

1. **安装Android Studio**（包含Android SDK）
2. **安装Java JDK 17**
3. **配置环境变量**：
   ```cmd
   set ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk
   set JAVA_HOME=C:\Program Files\Java\jdk-17
   ```

### 构建步骤

#### Windows
```cmd
cd d:\vibe_coding\android_books
gradlew.bat assembleDebug
```

#### Linux/Mac
```bash
cd /path/to/android_books
chmod +x gradlew
./gradlew assembleDebug
```

### 使用构建脚本

#### Windows
```cmd
build.bat
```

#### Linux/Mac
```bash
chmod +x build.sh
./build.sh
```

## 📦 APK位置

构建完成后，APK文件位于：

```
app/build/outputs/apk/debug/app-debug.apk
```

## 🚀 安装到设备

### 方法一：使用ADB
```bash
# 连接设备
adb devices

# 安装APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 方法二：直接安装
1. 将APK文件传输到手机
2. 在手机上打开文件管理器
3. 找到APK文件并点击安装
4. 允许安装未知来源应用

### 方法三：使用模拟器
1. 在Android Studio中打开AVD Manager
2. 创建或选择一个模拟器
3. 运行模拟器
4. 拖拽APK到模拟器安装

## ⚠️ 常见问题

### 1. Gradle同步失败
**问题**：Gradle下载超时或失败
**解决**：
- 检查网络连接
- 使用VPN或代理
- 修改Gradle镜像源

### 2. SDK路径错误
**问题**：`SDK location not found`
**解决**：
- 检查 `local.properties` 中的 `sdk.dir` 路径
- 确保Android SDK已安装

### 3. Java版本不兼容
**问题**：`Unsupported class file major version`
**解决**：
- 安装JDK 17
- 设置正确的JAVA_HOME

### 4. 内存不足
**问题**：`OutOfMemoryError`
**解决**：
- 在 `gradle.properties` 中增加内存：
```properties
org.gradle.jvmargs=-Xmx4096m
```

## 📊 项目信息

- **项目名称**：电子书阅读器
- **版本**：1.0.0
- **开发日期**：2026-06-17
- **技术栈**：Kotlin + Jetpack Compose
- **最低Android版本**：8.0 (API 26)
- **目标Android版本**：14 (API 34)

## 📚 相关文档

- [README.md](README.md) - 项目说明
- [构建指南](docs/build-guide.md) - 详细构建说明
- [技术规范](docs/technical-spec.md) - 技术细节
- [项目状态](docs/project-status.md) - 项目完成状态

## 🆘 技术支持

如有问题，请查看：
1. 项目文档：`docs/` 目录
2. 开发日志：`devlog/` 目录
3. 构建指南：`docs/build-guide.md`

## ✅ 构建成功标志

构建成功后，您将看到：
```
BUILD SUCCESSFUL in Xs
```

APK文件将生成在：
```
app/build/outputs/apk/debug/app-debug.apk
```

## 🎉 下一步

构建完成后，您可以：
1. 安装APK到设备进行测试
2. 测试所有功能
3. 准备发布到应用商店
4. 收集用户反馈
5. 持续优化和更新

---

**注意**：首次构建可能需要较长时间下载依赖，请耐心等待。确保网络连接正常。
