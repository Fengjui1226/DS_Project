#!/bin/bash
echo "========================================"
echo "  EventFinder 完整启动脚本"
echo "========================================"
echo ""

cd "$(dirname "$0")"

# 检查后端是否已运行
if lsof -i :8080 >/dev/null 2>&1; then
    echo "⚠️  端口8080已被占用，后端可能已在运行"
    read -p "是否继续？这将尝试启动新实例 (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo "📦 步骤 1/3: 检查并编译后端..."
echo "================================"
mvn compile
if [ $? -ne 0 ]; then
    echo "❌ 后端编译失败"
    echo "📖 请查看 START_GUIDE.md 获取帮助"
    exit 1
fi

echo ""
echo "✅ 后端编译成功"
echo ""
echo "🚀 步骤 2/3: 启动后端服务 (后台运行)..."
echo "================================"
nohup mvn exec:java -Dexec.mainClass="app.web.SimpleServer" > backend.log 2>&1 &
BACKEND_PID=$!
echo "后端进程ID: $BACKEND_PID"
echo "日志文件: backend.log"

echo ""
echo "⏳ 等待后端启动 (5秒)..."
sleep 5

# 检查后端是否成功启动
if ! lsof -i :8080 >/dev/null 2>&1; then
    echo "❌ 后端启动失败！请检查 backend.log"
    cat backend.log
    exit 1
fi

echo "✅ 后端服务已启动"
echo ""
echo "🌐 步骤 3/3: 启动前端开发服务器..."
echo "================================"
cd frontend
npm run dev

# 当前端停止时，也停止后端
echo ""
echo "🛑 正在停止所有服务..."
kill $BACKEND_PID 2>/dev/null
echo "✅ 所有服务已停止"
