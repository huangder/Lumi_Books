@echo off
chcp 65001 >nul
echo ============================================
echo   LUMI Dream - 自动录制脚本
echo ============================================
echo.

REM 检查 FFmpeg 是否安装
where ffmpeg >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 未检测到 FFmpeg
    echo 请从 https://ffmpeg.org/download.html 下载并添加到 PATH
    pause
    exit /b 1
)

REM 创建输出目录
if not exist "output" mkdir output

echo [1/5] 准备录制环境...
echo.

REM 检查是否有本地服务器运行
echo [提示] 请确保已在另一个终端运行本地服务器：
echo         python -m http.server 8000
echo.
echo         如果还没运行，请按 Ctrl+C 退出，先启动服务器
echo.
pause

echo [2/5] 正在启动 Chrome 全屏模式...
start chrome --new-window --start-fullscreen --app=http://localhost:8000/lumi-dream-enhanced.html

echo [等待] 给页面 5 秒时间加载...
timeout /t 5 >nul

echo [3/5] 开始录制 32 秒...
echo [提示] 录制开始后，在浏览器窗口会自动播放动画
echo.

REM 录制桌面（Windows 10/11）
ffmpeg -f gdigrab -framerate 30 -video_size 1920x1080 -i desktop -t 32 -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p output\lumi-dream-raw.mp4

if %ERRORLEVEL% NEQ 0 (
    echo [错误] 录制失败
    pause
    exit /b 1
)

echo.
echo [4/5] 录制完成！正在优化视频...

REM 裁剪和优化
ffmpeg -i output\lumi-dream-raw.mp4 -ss 2 -t 30 -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p output\lumi-dream-final.mp4 -y

echo.
echo [5/5] 优化完成！

echo.
echo ============================================
echo   录制完成！
echo ============================================
echo.
echo 原始文件: output\lumi-dream-raw.mp4
echo 最终文件: output\lumi-dream-final.mp4
echo.
echo 下一步：
echo   1. 查看视频是否满意
echo   2. 如需添加音乐，运行 add-music.bat
echo   3. 如需压缩文件，运行 compress.bat
echo.
pause
