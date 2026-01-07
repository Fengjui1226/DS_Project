#!/bin/bash
echo "================================"
echo "正在启动EventFinder后端服务..."
echo "================================"

cd "$(dirname "$0")"

# 检查是否已编译
if [ ! -d "target/classes/app" ]; then
    echo "⚠️  检测到项目未编译，正在编译..."
    mvn compile
    if [ $? -ne 0 ]; then
        echo "❌ 编译失败！请检查错误信息"
        echo "如果是网络问题，请参考 START_GUIDE.md 中的方案B"
        exit 1
    fi
fi

echo "✅ 后端已编译"
echo "🚀 正在启动后端服务 (端口: 8080)..."
echo "📝 日志输出位置: backend.log"
echo ""
echo "按 Ctrl+C 停止服务"
echo "================================"

# 启动服务
mvn exec:java -Dexec.mainClass="app.web.SimpleServer"
