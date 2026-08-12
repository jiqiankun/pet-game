@REM ============================================================
@REM 宠物精灵游戏 - 启动脚本 (Windows)
@REM ============================================================
@REM 使用方式：start.bat
@REM 前提：已通过 build.bat 构建出 release/pet-game.jar
@REM ============================================================

@echo off
setlocal

set PROJECT_ROOT=%~dp0
set JAR_PATH=%PROJECT_ROOT%release\pet-game.jar

if not exist "%JAR_PATH%" (
    echo [ERROR] 未找到 pet-game.jar，请先运行 build.bat 构建
    exit /b 1
)

echo ============================================================
echo   宠物精灵游戏 - 启动中...
echo   访问地址: http://127.0.0.1:8080
echo ============================================================
echo.

cd /d "%PROJECT_ROOT%release"
java -jar pet-game.jar %*
