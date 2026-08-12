@REM ============================================================
@REM 宠物精灵游戏 - 统一构建脚本 (Windows)
@REM ============================================================
@REM 流程：前端 build -> 清理并复制 dist 到后端静态资源 -> Maven package -> 输出 release 目录
@REM 要求：Node.js 18+ 和 Java 21 已安装且在 PATH 中
@REM ============================================================

@echo off
setlocal enabledelayedexpansion

set PROJECT_ROOT=%~dp0
set FRONTEND_DIR=%PROJECT_ROOT%frontend
set BACKEND_DIR=%PROJECT_ROOT%backend
set RELEASE_DIR=%PROJECT_ROOT%release
set STATIC_DIR=%BACKEND_DIR%\src\main\resources\static

echo.
echo ============================================================
echo   宠物精灵游戏 - 统一构建
echo ============================================================
echo.

REM ---- 检查环境 ----
where node >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Node.js 未安装，请先安装 Node.js 18+
    exit /b 1
)

where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java 未安装，请先安装 Java 21
    exit /b 1
)

REM ---- Step 1: 前端构建 ----
echo [1/4] 前端构建...
cd /d "%FRONTEND_DIR%"

if not exist node_modules (
    echo   安装前端依赖...
    call npm install
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] npm install 失败
        exit /b 1
    )
)

call npm run build
if %ERRORLEVEL% neq 0 (
    echo [ERROR] 前端构建失败
    exit /b 1
)
echo   前端构建完成 ✓

REM ---- Step 2: 清理并复制静态资源 ----
echo [2/4] 复制前端构建产物到后端静态资源...
if exist "%STATIC_DIR%" (
    rd /s /q "%STATIC_DIR%"
)
xcopy /e /i /q "%FRONTEND_DIR%\dist" "%STATIC_DIR%"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] 复制静态资源失败
    exit /b 1
)
echo   静态资源复制完成 ✓

REM ---- Step 3: Maven 打包 ----
echo [3/4] Maven 打包...
cd /d "%BACKEND_DIR%"

if exist "mvnw.cmd" (
    call mvnw.cmd clean package -DskipTests -q
) else (
    call mvn clean package -DskipTests -q
)
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven 打包失败
    exit /b 1
)
echo   Maven 打包完成 ✓

REM ---- Step 4: 输出到 release 目录 ----
echo [4/4] 输出到 release 目录...
if exist "%RELEASE_DIR%" (
    rd /s /q "%RELEASE_DIR%"
)
mkdir "%RELEASE_DIR%"

copy "%BACKEND_DIR%\target\pet-game-1.0.0.jar" "%RELEASE_DIR%\pet-game.jar" >nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] 复制 JAR 失败
    exit /b 1
)

REM 复制 config-example 到 release
if exist "%PROJECT_ROOT%config-example" (
    xcopy /e /i /q "%PROJECT_ROOT%config-example" "%RELEASE_DIR%\config" >nul
)

echo   release 输出完成 ✓

echo.
echo ============================================================
echo   构建完成！
echo   输出：%RELEASE_DIR%\pet-game.jar
echo   运行：java -jar release\pet-game.jar
echo ============================================================
echo.
