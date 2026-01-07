# 前后端连接问题诊断与解决方案

## 问题诊断结果

### 当前状态
- ❌ **后端服务未运行** (8080端口未被占用)
- ❌ **前端开发服务器未运行**
- ❌ **网络DNS解析失败** (无法下载Maven依赖)
- ✅ 前端依赖已安装 (node_modules存在)
- ✅ 项目配置正确

### 根本原因
1. 前后端服务都没有启动
2. 后端无法编译 - Maven无法解析域名下载依赖 (DNS configuration issue)

## 架构说明

### 后端 (Java)
- **端口**: 8080
- **API路径**: `/api/v1/*` 和 `/api/*` (向后兼容)
- **主类**: `app.web.SimpleServer`
- **依赖**: Gson (2.10.1), Jsoup (1.17.2)

### 前端 (React + Vite)
- **开发端口**: 默认 5173
- **代理配置**: `/api` -> `http://localhost:8080`
- **API调用**: `fetch('/api/search?...')` 会被代理到后端

## 解决方案

### 方案 A：修复网络并正常启动 (推荐)

#### 1. 修复DNS配置
```bash
# 创建或更新 /etc/resolv.conf
sudo bash -c 'echo "nameserver 8.8.8.8" > /etc/resolv.conf'
sudo bash -c 'echo "nameserver 8.8.4.4" >> /etc/resolv.conf'
```

#### 2. 编译并启动后端
```bash
# 在项目根目录
mvn clean compile

# 启动后端服务
mvn exec:java -Dexec.mainClass="app.web.SimpleServer"

# 或者后台运行
nohup mvn exec:java -Dexec.mainClass="app.web.SimpleServer" > backend.log 2>&1 &
```

#### 3. 启动前端
```bash
cd frontend
npm run dev
```

#### 4. 访问应用
- 前端: http://localhost:5173
- 后端API: http://localhost:8080/health

---

### 方案 B：手动下载依赖 (网络问题时)

#### 1. 手动下载JAR文件
```bash
mkdir -p lib
cd lib

# 下载依赖 (在有网络的机器上下载后传输)
# Gson 2.10.1
wget https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar

# Jsoup 1.17.2
wget https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar

cd ..
```

#### 2. 手动编译
```bash
# 创建输出目录
mkdir -p target/classes

# 编译Java源文件
javac -cp "lib/*" -d target/classes \
  $(find src/main/java -name "*.java")
```

#### 3. 运行后端
```bash
java -cp "target/classes:lib/*" app.web.SimpleServer
```

#### 4. 启动前端 (同方案A步骤3)

---

### 方案 C：使用已编译的JAR (如果存在)

```bash
# 如果有预编译的JAR
java -jar eventfinder-1.0.0.jar

# 或带依赖的fat jar
java -jar target/eventfinder-1.0.0-jar-with-dependencies.jar
```

## 快速启动脚本

### start-backend.sh
```bash
#!/bin/bash
echo "正在启动后端服务..."
cd "$(dirname "$0")"
mvn exec:java -Dexec.mainClass="app.web.SimpleServer"
```

### start-frontend.sh
```bash
#!/bin/bash
echo "正在启动前端开发服务器..."
cd "$(dirname "$0")/frontend"
npm run dev
```

### start-all.sh
```bash
#!/bin/bash
echo "正在启动前后端服务..."

# 启动后端 (后台)
./start-backend.sh > backend.log 2>&1 &
BACKEND_PID=$!
echo "后端进程ID: $BACKEND_PID"

# 等待后端启动
sleep 5

# 启动前端
./start-frontend.sh
```

## 验证连接

### 1. 检查后端健康状态
```bash
curl http://localhost:8080/health
# 预期输出: {"status":"ok","timestamp":...}
```

### 2. 测试API
```bash
# 分类
curl http://localhost:8080/api/categories

# 搜索
curl "http://localhost:8080/api/search?query=音乐&city=台北"

# 建议
curl "http://localhost:8080/api/suggestions?query=音"
```

### 3. 检查前端代理
- 启动前端后访问: http://localhost:5173
- 打开浏览器控制台 (F12)
- 执行搜索，观察Network标签
- API请求应该是 `/api/search`，并被代理到 `http://localhost:8080/api/search`

## 常见问题

### Q: 后端启动后立即停止？
检查日志中的错误信息，可能是：
- 端口8080已被占用: `lsof -i :8080`
- 配置文件缺失
- 依赖版本不匹配

### Q: 前端无法连接后端？
1. 确认后端正在运行: `curl http://localhost:8080/health`
2. 确认Vite代理配置正确: 检查 `frontend/vite.config.js`
3. 查看浏览器控制台的网络错误
4. 检查CORS配置

### Q: CORS错误？
后端已配置CORS允许所有来源 (`cors.origin=*`)，如果仍有问题：
- 检查 `src/main/resources/config.properties`
- 确认后端ServerContext正确设置CORS headers

### Q: Maven依赖下载失败？
- 检查网络连接: `curl -I https://repo.maven.apache.org`
- 检查DNS: `cat /etc/resolv.conf`
- 使用方案B手动下载依赖
- 或使用Maven镜像配置

## 下一步

当前环境由于DNS配置问题，需要先修复网络，然后按照 **方案A** 启动服务。

如果无法修复网络，请使用 **方案B** 手动下载依赖。
