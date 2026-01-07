#!/bin/bash
echo "================================"
echo "正在启动EventFinder前端服务..."
echo "================================"

cd "$(dirname "$0")/frontend"

# 检查node_modules
if [ ! -d "node_modules" ]; then
    echo "⚠️  检测到依赖未安装，正在安装..."
    npm install
    if [ $? -ne 0 ]; then
        echo "❌ 依赖安装失败！请检查npm配置"
        exit 1
    fi
fi

echo "✅ 前端依赖已就绪"
echo "🚀 正在启动前端开发服务器..."
echo ""
echo "前端将在浏览器自动打开"
echo "按 Ctrl+C 停止服务"
echo "================================"

# 启动Vite开发服务器
npm run dev
