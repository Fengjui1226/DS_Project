#!/bin/bash
# EventFinder 一鍵部署腳本

set -e

echo "========================================="
echo "  EventFinder 部署腳本"
echo "========================================="
echo ""

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 檢查依賴
check_dependencies() {
    echo "檢查依賴..."

    if ! command -v docker &> /dev/null; then
        echo -e "${RED}❌ Docker未安裝${NC}"
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null; then
        echo -e "${RED}❌ Docker Compose未安裝${NC}"
        exit 1
    fi

    echo -e "${GREEN}✅ 依賴檢查通過${NC}"
}

# 編譯後端
build_backend() {
    echo ""
    echo "編譯後端..."

    # 檢查並下載依賴
    if [ ! -d "lib" ] || [ ! -f "lib/gson-2.10.1.jar" ]; then
        echo "下載依賴..."
        mkdir -p lib
        curl -L -o lib/gson-2.10.1.jar \
            https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
        curl -L -o lib/jsoup-1.17.2.jar \
            https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar
    fi

    # 編譯
    mkdir -p target/classes
    find src/main/java -name "*.java" > /tmp/sources.txt
    javac -cp "lib/*" -d target/classes @/tmp/sources.txt

    echo -e "${GREEN}✅ 後端編譯完成${NC}"
}

# 構建前端
build_frontend() {
    echo ""
    echo "構建前端..."

    cd frontend

    # 安裝依賴（如果需要）
    if [ ! -d "node_modules" ]; then
        echo "安裝依賴..."
        npm install
    fi

    # 構建生產版本
    echo "構建生產版本..."
    npm run build

    cd ..

    echo -e "${GREEN}✅ 前端構建完成${NC}"
}

# 構建Docker鏡像
build_docker() {
    echo ""
    echo "構建Docker鏡像..."

    docker-compose build

    echo -e "${GREEN}✅ Docker鏡像構建完成${NC}"
}

# 啟動服務
start_services() {
    echo ""
    echo "啟動服務..."

    # 停止舊服務
    docker-compose down 2>/dev/null || true

    # 啟動新服務
    docker-compose up -d

    echo -e "${GREEN}✅ 服務已啟動${NC}"
}

# 等待服務就緒
wait_for_services() {
    echo ""
    echo "等待服務就緒..."

    for i in {1..30}; do
        if curl -s http://localhost:8080/health > /dev/null 2>&1; then
            echo -e "${GREEN}✅ 後端服務就緒${NC}"
            break
        fi
        echo "等待後端啟動... ($i/30)"
        sleep 2
    done

    for i in {1..30}; do
        if curl -s http://localhost:80 > /dev/null 2>&1; then
            echo -e "${GREEN}✅ 前端服務就緒${NC}"
            break
        fi
        echo "等待前端啟動... ($i/30)"
        sleep 2
    done
}

# 顯示狀態
show_status() {
    echo ""
    echo "========================================="
    echo -e "${GREEN}🎉 部署成功！${NC}"
    echo "========================================="
    echo ""
    echo "服務地址："
    echo "  前端: http://localhost"
    echo "  後端API: http://localhost:8080"
    echo "  健康檢查: http://localhost:8080/health"
    echo ""
    echo "查看日誌："
    echo "  docker-compose logs -f"
    echo ""
    echo "停止服務："
    echo "  docker-compose down"
    echo ""
}

# 主流程
main() {
    check_dependencies
    build_backend
    build_frontend
    build_docker
    start_services
    wait_for_services
    show_status
}

# 处理参数
case "${1:-deploy}" in
    deploy)
        main
        ;;
    backend)
        build_backend
        ;;
    frontend)
        build_frontend
        ;;
    start)
        docker-compose up -d
        wait_for_services
        show_status
        ;;
    stop)
        docker-compose down
        echo -e "${GREEN}✅ 服務已停止${NC}"
        ;;
    restart)
        docker-compose restart
        wait_for_services
        echo -e "${GREEN}✅ 服務已重啟${NC}"
        ;;
    logs)
        docker-compose logs -f
        ;;
    status)
        docker-compose ps
        ;;
    *)
        echo "用法: $0 {deploy|backend|frontend|start|stop|restart|logs|status}"
        exit 1
        ;;
esac
