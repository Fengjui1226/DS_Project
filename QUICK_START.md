# 🚀 快速启动参考

## 启动后端的多种方式

### 方式 1: 使用 Maven（推荐）✨

```bash
# 直接使用 Maven 启动
mvn -q -DskipTests exec:java -Dexec.mainClass="app.web.SimpleServer"

# 或使用启动脚本
./start-backend.sh
# 或
./start-backend.sh maven
```

**优点：**
- ✅ 自动管理依赖
- ✅ 无需手动编译
- ✅ 支持热重载（开发模式）
- ✅ 简单方便

**适用场景：** 开发环境、有 Maven 的服务器

### 方式 2: 手动编译启动

```bash
# 1. 下载依赖（首次）
mkdir -p lib
curl -L -o lib/gson-2.10.1.jar \
  https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
curl -L -o lib/jsoup-1.17.2.jar \
  https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar

# 2. 编译
mkdir -p target/classes
find src/main/java -name "*.java" > /tmp/sources.txt
javac -cp "lib/*" -d target/classes @/tmp/sources.txt

# 3. 启动
java -cp "target/classes:lib/*" app.web.SimpleServer

# 或使用启动脚本
./start-backend.sh manual
```

**优点：**
- ✅ 不依赖 Maven
- ✅ 轻量级
- ✅ 适合生产环境

**适用场景：** 生产环境、无 Maven 的服务器

### 方式 3: 使用 Docker

```bash
# 完整部署（前后端）
./deploy.sh

# 或单独启动
docker-compose up -d backend
```

**优点：**
- ✅ 完全隔离
- ✅ 易于扩展
- ✅ 统一环境

**适用场景：** 生产部署、容器化环境

## 启动前端

### 开发模式

```bash
cd frontend
npm run dev
```

或使用脚本：
```bash
./start-frontend.sh
```

### 生产构建

```bash
cd frontend
npm run build
# 构建产物在 dist/ 目录
```

## 一键启动（前后端）

```bash
# 启动所有服务
./start-all.sh

# 或使用 Docker
./deploy.sh
```

## 常用命令对照表

| 操作 | Maven 方式 | 手动方式 | Docker 方式 |
|------|-----------|---------|------------|
| 启动后端 | `mvn -q -DskipTests exec:java -Dexec.mainClass="app.web.SimpleServer"` | `java -cp "target/classes:lib/*" app.web.SimpleServer` | `docker-compose up -d backend` |
| 编译 | `mvn compile` | `javac -cp "lib/*" -d target/classes @sources.txt` | `docker-compose build` |
| 清理 | `mvn clean` | `rm -rf target/` | `docker-compose down -v` |
| 测试 | `mvn test` | - | - |
| 打包 | `mvn package` | - | - |

## 后台运行

### Maven 方式后台运行

```bash
nohup mvn -q -DskipTests exec:java -Dexec.mainClass="app.web.SimpleServer" > backend.log 2>&1 &
```

### 手动方式后台运行

```bash
nohup java -cp "target/classes:lib/*" app.web.SimpleServer > backend.log 2>&1 &
```

### 查看后台日志

```bash
tail -f backend.log
```

### 停止后台服务

```bash
# Maven 方式
pkill -f "exec:java"

# 手动方式
pkill -f "app.web.SimpleServer"

# Docker 方式
docker-compose down
```

## 检查服务状态

```bash
# 检查端口
lsof -i :8080

# 检查进程
ps aux | grep SimpleServer

# 测试 API
curl http://localhost:8080/health

# 完整测试
curl http://localhost:8080/api/categories
curl "http://localhost:8080/api/search?query=音樂&city=台北"
```

## 开发工作流

### 1. 启动开发环境

```bash
# 终端 1: 启动后端（Maven 方式，支持修改代码后重启）
./start-backend.sh

# 终端 2: 启动前端（支持热重载）
./start-frontend.sh
```

### 2. 修改代码

前端修改会自动热重载，后端修改需要重启服务（Ctrl+C 然后重新运行）

### 3. 测试

```bash
# 浏览器访问
open http://localhost:5173
```

## 生产部署工作流

### 使用 Docker（推荐）

```bash
# 1. 拉取最新代码
git pull origin main

# 2. 部署
./deploy.sh

# 3. 验证
curl http://localhost/health
curl http://localhost/api/categories
```

### 手动部署

```bash
# 1. 拉取代码
git pull origin main

# 2. 后端
./start-backend.sh manual &

# 3. 前端
cd frontend
npm install
npm run build
# 将 dist/ 目录部署到 Nginx

# 4. 配置 Nginx
sudo cp nginx.conf /etc/nginx/sites-available/eventfinder
sudo ln -s /etc/nginx/sites-available/eventfinder /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

## 故障排除

### 问题：端口已被占用

```bash
# 查找占用进程
lsof -i :8080

# 停止进程
kill -9 <PID>
```

### 问题：Maven 依赖下载失败

使用手动方式：
```bash
./start-backend.sh manual
```

### 问题：前端无法连接后端

```bash
# 1. 确认后端运行
curl http://localhost:8080/health

# 2. 检查 Vite 代理配置
cat frontend/vite.config.js

# 3. 重启前端
cd frontend
npm run dev
```

## 性能优化建议

### 开发环境

- 使用 Maven 方式（方便调试）
- 前端使用 `npm run dev`（热重载）

### 生产环境

- 使用 Docker（易于管理）
- 或手动方式 + Systemd（轻量级）
- 启用 Nginx 缓存
- 配置 JVM 参数：
  ```bash
  java -Xms512m -Xmx1g -XX:+UseG1GC \
       -cp "target/classes:lib/*" \
       app.web.SimpleServer
  ```

---

**快速开始：**
```bash
./start-backend.sh    # 后端
./start-frontend.sh   # 前端
```

或一键启动：
```bash
./deploy.sh           # Docker 方式
```
