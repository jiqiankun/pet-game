#!/usr/bin/env bash
# ============================================================
# 宠物精灵游戏 - 统一构建脚本 (Linux/Mac)
# ============================================================
# 流程：前端 build -> 清理并复制 dist 到后端静态资源 -> Maven package -> 输出 release 目录
# 要求：Node.js 18+ 和 Java 21 已安装且在 PATH 中
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
BACKEND_DIR="$PROJECT_ROOT/backend"
RELEASE_DIR="$PROJECT_ROOT/release"
STATIC_DIR="$BACKEND_DIR/src/main/resources/static"

echo ""
echo "============================================================"
echo "  宠物精灵游戏 - 统一构建"
echo "============================================================"
echo ""

# ---- 检查环境 ----
if ! command -v node &> /dev/null; then
    echo "[ERROR] Node.js 未安装，请先安装 Node.js 18+"
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo "[ERROR] Java 未安装，请先安装 Java 21"
    exit 1
fi

# ---- Step 1: 前端构建 ----
echo "[1/4] 前端构建..."
cd "$FRONTEND_DIR"

if [ ! -d "node_modules" ]; then
    echo "  安装前端依赖..."
    npm install
fi

npm run build
echo "  前端构建完成 ✓"

# ---- Step 2: 清理并复制静态资源 ----
echo "[2/4] 复制前端构建产物到后端静态资源..."
rm -rf "$STATIC_DIR"
cp -r "$FRONTEND_DIR/dist" "$STATIC_DIR"
echo "  静态资源复制完成 ✓"

# ---- Step 3: Maven 打包 ----
echo "[3/4] Maven 打包..."
cd "$BACKEND_DIR"

if [ -f "mvnw" ]; then
    chmod +x mvnw
    ./mvnw clean package -DskipTests -q
else
    mvn clean package -DskipTests -q
fi
echo "  Maven 打包完成 ✓"

# ---- Step 4: 输出到 release 目录 ----
echo "[4/4] 输出到 release 目录..."
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"

cp "$BACKEND_DIR/target/pet-game-1.0.0.jar" "$RELEASE_DIR/pet-game.jar"

# 复制 config-example 到 release
if [ -d "$PROJECT_ROOT/config-example" ]; then
    cp -r "$PROJECT_ROOT/config-example" "$RELEASE_DIR/config"
fi

echo "  release 输出完成 ✓"

echo ""
echo "============================================================"
echo "  构建完成！"
echo "  输出：$RELEASE_DIR/pet-game.jar"
echo "  运行：java -jar release/pet-game.jar"
echo "============================================================"
echo ""
