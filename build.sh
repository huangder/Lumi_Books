#!/bin/bash

echo "=== 电子书阅读器 APK 构建脚本 ==="
echo ""

# 检查Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "❌ 错误：未设置ANDROID_HOME环境变量"
    echo "请设置Android SDK路径，例如："
    echo "export ANDROID_HOME=/path/to/android/sdk"
    exit 1
fi

# 检查Java
if ! command -v java &> /dev/null; then
    echo "❌ 错误：未找到Java"
    echo "请安装Java JDK并添加到PATH"
    exit 1
fi

echo "✅ 环境检查通过"
echo ""

# 清理项目
echo "🧹 清理项目..."
./gradlew clean

if [ $? -ne 0 ]; then
    echo "❌ 清理失败"
    exit 1
fi

echo "✅ 清理完成"
echo ""

# 构建Debug APK
echo "🔨 构建Debug APK..."
./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo "✅ 构建完成"
echo ""

# 显示APK位置
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo "📱 APK已生成："
    echo "   $APK_PATH"
    echo ""
    echo "📊 APK信息："
    ls -lh "$APK_PATH"
    echo ""
    echo "🚀 安装到设备："
    echo "   adb install $APK_PATH"
else
    echo "❌ 未找到APK文件"
    exit 1
fi

echo ""
echo "=== 构建完成 ==="
