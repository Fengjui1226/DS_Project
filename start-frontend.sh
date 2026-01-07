#!/bin/bash
echo "================================"
echo "正在啟動EventFinder前端服務..."
echo "================================"

cd "$(dirname "$0")/frontend"

# 檢查node_modules
if [ ! -d "node_modules" ]; then
    echo "⚠️  檢測到依賴未安裝，正在安裝..."
    npm install
    if [ $? -ne 0 ]; then
        echo "❌ 依賴安裝失敗！請檢查npm配置"
        exit 1
    fi
fi

echo "✅ 前端依賴已就緒"
echo "🚀 正在啟動前端開發服務器..."
echo ""
echo "前端將在瀏覽器自動打開"
echo "按 Ctrl+C 停止服務"
echo "================================"

# 啟動Vite開發服務器
npm run dev
