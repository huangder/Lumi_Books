@echo off
chcp 65001 >nul
echo ============================================
echo   添加背景音乐
echo ============================================
echo.

if not exist "output\lumi-dream-final.mp4" (
    echo [错误] 找不到 output\lumi-dream-final.mp4
    echo 请先运行 record-video.bat 录制视频
    pause
    exit /b 1
)

echo [提示] 请将背景音乐文件命名为 bgm.mp3 并放在当前目录
echo.

if not exist "bgm.mp3" (
    echo [错误] 找不到 bgm.mp3
    echo 请将音乐文件重命名为 bgm.mp3 并放在当前目录
    pause
    exit /b 1
)

echo [处理] 正在合并视频和音乐...
echo.

ffmpeg -i output\lumi-dream-final.mp4 -i bgm.mp3 -c:v copy -c:a aac -b:a 192k -shortest output\lumi-dream-with-music.mp4 -y

if %ERRORLEVEL% NEQ 0 (
    echo [错误] 合并失败
    pause
    exit /b 1
)

echo.
echo ============================================
echo   完成！
echo ============================================
echo.
echo 输出文件: output\lumi-dream-with-music.mp4
echo.
pause
