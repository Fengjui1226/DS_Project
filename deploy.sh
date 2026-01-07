#!/bin/bash
# EventFinder 一键部署脚本

set -e

echo "========================================="
echo "  EventFinder 部署脚本"
echo "========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查依赖
check_dependencies() {
    echo "检查依赖..."

    if ! command -v docker &> /dev/null; then
        echo -e "${RED}❌ Docker未安装${NC}"
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null; then
        echo -e "${RED}❌ Docker Compose未安装${NC}"
        exit 1
    fi

    echo -e "${GREEN}✅ 依赖检查通过${NC}"
}

# 编译后端
build_backend() {
    echo ""
    echo "编译后端..."

    # 检查并下载依赖
    if [ ! -d "lib" ] || [ ! -f "lib/gson-2.10.1.jar" ]; then
        echo "下载依赖..."
        mkdir -p lib
        curl -L -o lib/gson-2.10.1.jar \
            https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
        curl -L -o lib/jsoup-1.17.2.jar \
            https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar
    fi

    # 编译
    mkdir -p target/classes
    find src/main/java -name "*.java" > /tmp/sources.txt
    javac -cp "lib/*" -d target/classes @/tmp/sources.txt

    echo -e "${GREEN}✅ 后端编译完成${NC}"
}

# 构建前端
build_frontend() {
    echo ""
    echo "构建前端..."

    cd frontend

    # 安装依赖（如果需要）
    if [ ! -d "node_modules" ]; then
        echo "安装依赖..."
        npm install
    fi

    # 构建生产版本
    echo "构建生产版本..."
    npm run build

    cd ..

    echo -e "${GREEN}✅ 前端构建完成${NC}"
}

# 构建Docker镜像
build_docker() {
    echo ""
    echo "构建Docker镜像..."

    docker-compose build

    echo -e "${GREEN}✅ Docker镜像构建完成${NC}"
}

# 启动服务
start_services() {
    echo ""
    echo "启动服务..."

    # 停止旧服务
    docker-compose down 2>/dev/null || true

    # 启动新服务
    docker-compose up -d

    echo -e "${GREEN}✅ 服务已启动${NC}"
}

# 等待服务就绪
wait_for_services() {
    echo ""
    echo "等待服务就绪..."

    for i in {1..30}; do
        if curl -s http://localhost:8080/health > /dev/null 2>&1; then
            echo -e "${GREEN}✅ 后端服务就绪${NC}"
            break
        fi
        echo "等待后端启动... ($i/30)"
        sleep 2
    done

    for i in {1..30}; do
        if curl -s http://localhost:80 > /dev/null 2>&1; then
            echo -e "${GREEN}✅ 前端服务就绪${NC}"
            break
        fi
        echo "等待前端启动... ($i/30)"
        sleep 2
    done
}

# 显示状态
show_status() {
    echo ""
    echo "========================================="
    echo -e "${GREEN}🎉 部署成功！${NC}"
    echo "========================================="
    echo ""
    echo "服务地址："
    echo "  前端: http://localhost"
    echo "  后端API: http://localhost:8080"
    echo "  健康检查: http://localhost:8080/health"
    echo ""
    echo "查看日志："
    echo "  docker-compose logs -f"
    echo ""
    echo "停止服务："
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
        echo -e "${GREEN}✅ 服务已停止${NC}"
        ;;
    restart)
        docker-compose restart
        wait_for_services
        echo -e "${GREEN}✅ 服务已重启${NC}"
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
