#!/bin/bash
echo "========================================"
echo "  EventFinder 完整啟動腳本"
echo "========================================"
echo ""

cd "$(dirname "$0")"

# 檢查後端是否已運行
if lsof -i :8080 >/dev/null 2>&1; then
    echo "⚠️  連接埠8080已被佔用，後端可能已在運行"
    read -p "是否繼續？這將嘗試啟動新實例 (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo "📦 步驟 1/3: 檢查並編譯後端..."
echo "================================"
mvn compile
if [ $? -ne 0 ]; then
    echo "❌ 後端編譯失敗"
    echo "📖 請查看 START_GUIDE.md 獲取幫助"
    exit 1
fi

echo ""
echo "✅ 後端編譯成功"
echo ""
echo "🚀 步驟 2/3: 啟動後端服務 (後台運行)..."
echo "================================"
nohup mvn exec:java -Dexec.mainClass="app.web.SimpleServer" > backend.log 2>&1 &
BACKEND_PID=$!
echo "後端進程ID: $BACKEND_PID"
echo "日誌文件: backend.log"

echo ""
echo "⏳ 等待後端啟動 (5秒)..."
sleep 5

# 檢查後端是否成功啟動
if ! lsof -i :8080 >/dev/null 2>&1; then
    echo "❌ 後端啟動失敗！請檢查 backend.log"
    cat backend.log
    exit 1
fi

echo "✅ 後端服務已啟動"
echo ""
echo "🌐 步驟 3/3: 啟動前端開發服務器..."
echo "================================"
cd frontend
npm run dev

# 當前端停止時，也停止後端
echo ""
echo "🛑 正在停止所有服務..."
kill $BACKEND_PID 2>/dev/null
echo "✅ 所有服務已停止"
