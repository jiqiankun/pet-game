#!/usr/bin/env bash
# ============================================================
# 宠物精灵游戏 - 启动脚本 (Linux/Mac)
# ============================================================
# 使用方式：./start.sh
# 前提：已通过 build.sh 构建出 release/pet-game.jar
# ============================================================

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
JAR_PATH="$PROJECT_ROOT/release/pet-game.jar"

if [ ! -f "$JAR_PATH" ]; then
    echo "[ERROR] 未找到 pet-game.jar，请先运行 build.sh 构建"
    exit 1
fi

echo "============================================================"
echo "  宠物精灵游戏 - 启动中..."
echo "  访问地址: http://127.0.0.1:8080"
echo "============================================================"
echo ""

cd "$PROJECT_ROOT/release"
java -jar pet-game.jar "$@"
