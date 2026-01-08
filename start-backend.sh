#!/bin/bash
echo "================================"
echo "正在啟動EventFinder後端服務..."
echo "================================"

cd "$(dirname "$0")"

# 檢查啟動方式參數
USE_MAVEN=${1:-"auto"}

# 檢查 Maven 是否可用
if command -v mvn &> /dev/null; then
    MAVEN_AVAILABLE=true
else
    MAVEN_AVAILABLE=false
fi

# 使用 Maven 啟動（推薦）
if [ "$USE_MAVEN" = "maven" ] || ([ "$USE_MAVEN" = "auto" ] && [ "$MAVEN_AVAILABLE" = true ]); then
    echo "📦 使用 Maven 啟動..."
    echo "🚀 正在啟動後端服務 (連接埠: 8080)..."
    echo ""
    echo "按 Ctrl+C 停止服務"
    echo "================================"

    # 使用 Maven exec 插件啟動（安靜模式，跳過測試）
    mvn -q -DskipTests exec:java -Dexec.mainClass="app.web.SimpleServer"

# 使用手動編譯方式啟動
else
    echo "📦 使用手動編譯方式啟動..."

    # 檢查依賴
    if [ ! -d "lib" ] || [ ! -f "lib/gson-2.10.1.jar" ]; then
        echo "⚠️  未找到依賴，正在下載..."
        mkdir -p lib
        curl -L -o lib/gson-2.10.1.jar \
            https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
        curl -L -o lib/jsoup-1.17.2.jar \
            https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar
    fi

    # 檢查是否已編譯
    if [ ! -d "target/classes/app" ]; then
        echo "⚠️  檢測到專案未編譯，正在編譯..."
        mkdir -p target/classes
        find src/main/java -name "*.java" > /tmp/sources.txt
        javac -cp "lib/*" -d target/classes @/tmp/sources.txt
        if [ $? -ne 0 ]; then
            echo "❌ 編譯失敗！請檢查錯誤訊息"
            exit 1
        fi
    fi

    echo "✅ 後端已編譯"
    echo "🚀 正在啟動後端服務 (連接埠: 8080)..."
    echo ""
    echo "按 Ctrl+C 停止服務"
    echo "================================"

    # 啟動服務
    java -cp "target/classes:lib/*" app.web.SimpleServer
fi
