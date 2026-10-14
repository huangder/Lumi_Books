@echo off
chcp 65001 >nul
echo ============================================
echo   LUMI Dream - 一键启动
echo ============================================
echo.
echo 欢迎使用 LUMI Dream 宣传片制作工具
echo.
echo 请选择操作：
echo.
echo [1] 预览动画（在浏览器中打开）
echo [2] 启动本地服务器
echo [3] 录制视频（全自动）
echo [4] 添加背景音乐
echo [5] 压缩视频
echo [6] 生成 GIF 预览
echo [7] 查看使用说明
echo [0] 退出
echo.
set /p choice="请输入选项 (0-7): "

if "%choice%"=="1" goto preview
if "%choice%"=="2" goto server
if "%choice%"=="3" goto record
if "%choice%"=="4" goto music
if "%choice%"=="5" goto compress
if "%choice%"=="6" goto gif
if "%choice%"=="7" goto help
if "%choice%"=="0" exit
goto end

:preview
echo.
echo [启动] 在浏览器中打开增强版...
start chrome lumi-dream-enhanced.html
echo.
echo [提示] 按 R 重新播放，按 Space 暂停/继续
pause
goto end

:server
echo.
echo [启动] 本地服务器在端口 8000...
echo [访问] http://localhost:8000/lumi-dream-enhanced.html
echo.
python -m http.server 8000
goto end

:record
call record-video.bat
goto end

:music
call add-music.bat
goto end

:compress
call compress.bat
goto end

:gif
echo.
echo [处理] 正在生成 GIF 预览...
if not exist "output\lumi-dream-final.mp4" (
    echo [错误] 找不到视频文件
    pause
    goto end
)
ffmpeg -i output\lumi-dream-final.mp4 -vf "fps=15,scale=640:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" output\lumi-dream-preview.gif -y
echo.
echo [完成] 输出: output\lumi-dream-preview.gif
pause
goto end

:help
start README-LUMI-DREAM-FINAL.md
goto end

:end
echo.
