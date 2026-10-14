@echo off
chcp 65001 >nul
echo ============================================
echo   视频压缩工具
echo ============================================
echo.

if not exist "output\lumi-dream-final.mp4" (
    echo [错误] 找不到 output\lumi-dream-final.mp4
    pause
    exit /b 1
)

echo 请选择压缩级别：
echo.
echo [1] 轻度压缩 (保持高质量，约 20MB)
echo [2] 中度压缩 (适合网络分享，约 10MB)
echo [3] 重度压缩 (适合移动端，约 5MB)
echo.
set /p choice="请输入选项 (1-3): "

if "%choice%"=="1" (
    set crf=20
    set maxrate=5M
    set bufsize=10M
    set output_name=lumi-dream-light-compressed.mp4
    echo [处理] 轻度压缩中...
) else if "%choice%"=="2" (
    set crf=23
    set maxrate=3M
    set bufsize=6M
    set output_name=lumi-dream-medium-compressed.mp4
    echo [处理] 中度压缩中...
) else if "%choice%"=="3" (
    set crf=28
    set maxrate=1.5M
    set bufsize=3M
    set output_name=lumi-dream-heavy-compressed.mp4
    echo [处理] 重度压缩中...
) else (
    echo [错误] 无效的选项
    pause
    exit /b 1
)

echo.

ffmpeg -i output\lumi-dream-final.mp4 -c:v libx264 -preset medium -crf %crf% -maxrate %maxrate% -bufsize %bufsize% -pix_fmt yuv420p output\%output_name% -y

if %ERRORLEVEL% NEQ 0 (
    echo [错误] 压缩失败
    pause
    exit /b 1
)

echo.
echo ============================================
echo   压缩完成！
echo ============================================
echo.
echo 输出文件: output\%output_name%
echo.

REM 显示文件大小对比
for %%A in (output\lumi-dream-final.mp4) do set size_original=%%~zA
for %%A in (output\%output_name%) do set size_compressed=%%~zA

echo 原始大小: %size_original% 字节
echo 压缩后: %size_compressed% 字节
echo.
pause
