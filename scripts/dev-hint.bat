@REM ============================================================
@REM 宠物精灵游戏 - 开发模式启动辅助脚本 (Windows)
@REM ============================================================
@REM 同时启动前端 dev server 和后端 Spring Boot。
@REM 前端: http://localhost:5173 (Vite dev server + HMR)
@REM 后端: http://localhost:8080 (Spring Boot)
@REM 前端通过 Vite proxy 将 /api 代理到后端。
@REM ============================================================
@echo off
echo ============================================================
echo   开发模式启动辅助
echo   请在两个终端分别执行：
echo     终端 1: cd frontend ^&^& npm run dev
echo     终端 2: cd backend  ^&^& mvn spring-boot:run (或 IDE 启动 PetGameApplication)
echo ============================================================
