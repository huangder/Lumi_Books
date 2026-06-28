@echo off
chcp 65001 >nul

echo === 电子书阅读器 APK 构建脚本 ===
echo.

REM 检查Android SDK
if "%ANDROID_HOME%"=="" (
    echo ❌ 错误：未设置ANDROID_HOME环境变量
    echo 请设置Android SDK路径
    pause
    exit /b 1
)

REM 检查Java
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误：未找到Java
    echo 请安装Java JDK并添加到PATH
    pause
    exit /b 1
)

echo ✅ 环境检查通过
echo.

REM 清理项目
echo 🧹 清理项目...
call gradlew.bat clean

if errorlevel 1 (
    echo ❌ 清理失败
    pause
    exit /b 1
)

echo ✅ 清理完成
echo.

REM 构建Debug APK
echo 🔨 构建Debug APK...
call gradlew.bat assembleDebug

if errorlevel 1 (
    echo ❌ 构建失败
    pause
    exit /b 1
)

echo ✅ 构建完成
echo.

REM 显示APK位置
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk
if exist "%APK_PATH%" (
    echo 📱 APK已生成：
    echo    %APK_PATH%
    echo.
    echo 📊 APK信息：
    dir "%APK_PATH%"
    echo.
    echo 🚀 安装到设备：
    echo    adb install %APK_PATH%
) else (
    echo ❌ 未找到APK文件
    pause
    exit /b 1
)

echo.
echo === 构建完成 ===
pause
