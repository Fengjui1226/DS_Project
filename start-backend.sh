#!/bin/bash
echo "================================"
echo "正在启动EventFinder后端服务..."
echo "================================"

cd "$(dirname "$0")"

# 检查启动方式参数
USE_MAVEN=${1:-"auto"}

# 检查 Maven 是否可用
if command -v mvn &> /dev/null; then
    MAVEN_AVAILABLE=true
else
    MAVEN_AVAILABLE=false
fi

# 使用 Maven 启动（推荐）
if [ "$USE_MAVEN" = "maven" ] || ([ "$USE_MAVEN" = "auto" ] && [ "$MAVEN_AVAILABLE" = true ]); then
    echo "📦 使用 Maven 启动..."
    echo "🚀 正在启动后端服务 (端口: 8080)..."
    echo ""
    echo "按 Ctrl+C 停止服务"
    echo "================================"

    # 使用 Maven exec 插件启动（安静模式，跳过测试）
    mvn -q -DskipTests exec:java -Dexec.mainClass="app.web.SimpleServer"

# 使用手动编译方式启动
else
    echo "📦 使用手动编译方式启动..."

    # 检查依赖
    if [ ! -d "lib" ] || [ ! -f "lib/gson-2.10.1.jar" ]; then
        echo "⚠️  未找到依赖，正在下载..."
        mkdir -p lib
        curl -L -o lib/gson-2.10.1.jar \
            https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
        curl -L -o lib/jsoup-1.17.2.jar \
            https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar
    fi

    # 检查是否已编译
    if [ ! -d "target/classes/app" ]; then
        echo "⚠️  检测到项目未编译，正在编译..."
        mkdir -p target/classes
        find src/main/java -name "*.java" > /tmp/sources.txt
        javac -cp "lib/*" -d target/classes @/tmp/sources.txt
        if [ $? -ne 0 ]; then
            echo "❌ 编译失败！请检查错误信息"
            exit 1
        fi
    fi

    echo "✅ 后端已编译"
    echo "🚀 正在启动后端服务 (端口: 8080)..."
    echo ""
    echo "按 Ctrl+C 停止服务"
    echo "================================"

    # 启动服务
    java -cp "target/classes:lib/*" app.web.SimpleServer
fi
