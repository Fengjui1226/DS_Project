# ✅ 前后端连接问题已解决

## 问题总结

### 原始问题
- ❌ 前后端服务都未运行
- ❌ 后端无法编译（Maven DNS解析失败）
- ❌ 前端node_modules依赖不完整

### 解决过程

#### 1. 诊断阶段
- 发现8080端口未被占用（后端未启动）
- 发现前端开发服务器未运行
- 识别出DNS配置问题导致Maven无法下载依赖

#### 2. 修复阶段

**后端修复：**
- ✅ 配置DNS (8.8.8.8)
- ✅ 手动下载依赖JAR:
  - gson-2.10.1.jar (277KB)
  - jsoup-1.17.2.jar (436KB)
- ✅ 修复文件名大小写问题 (Keywordsuggester.java → KeywordSuggester.java)
- ✅ 使用javac手动编译41个Java源文件
- ✅ 成功启动后端服务在8080端口

**前端修复：**
- ✅ 清理并重新安装node_modules
- ✅ 修复@rollup/rollup-linux-x64-gnu依赖问题
- ✅ 成功启动Vite开发服务器在5173端口

## 当前状态

### ✅ 服务运行状态

#### 后端 (Java)
- **状态**: ✅ 正常运行
- **端口**: 8080
- **进程**: PID 16060
- **日志**: backend.log

**API端点测试:**
```bash
# 健康检查
curl http://localhost:8080/health
# 返回: {"success":true,"status":"healthy","version":"4.1",...}

# 分类列表
curl http://localhost:8080/api/categories
# 返回: {"success":true,"categories":["市集","展覽","演唱會",...]}
```

#### 前端 (React + Vite)
- **状态**: ✅ 正常运行
- **端口**: 5173
- **进程**: PID 19407
- **访问**: http://localhost:5173

**前端页面测试:**
```bash
curl http://localhost:5173
# 返回: <title>EventFinder 台灣活動搜尋</title>
```

### 🔗 前后端连接验证

**Vite代理配置** (frontend/vite.config.js):
```javascript
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
    secure: false
  }
}
```

**工作流程:**
1. 前端发起请求: `fetch('/api/search?query=...')`
2. Vite代理转发到: `http://localhost:8080/api/search?query=...`
3. 后端处理请求并返回JSON
4. 前端接收并渲染数据

## 如何使用

### 方式一：使用现有运行的服务

服务已经在后台运行，可以直接访问：
- **前端**: http://localhost:5173
- **后端API**: http://localhost:8080/health

### 方式二：重新启动服务

如果服务停止了，可以使用以下方法重启：

#### 快速启动（推荐）
```bash
# 在项目根目录

# 启动后端（后台运行）
java -cp "target/classes:lib/*" app.web.SimpleServer > backend.log 2>&1 &

# 启动前端
cd frontend && npm run dev
```

#### 使用启动脚本
```bash
# 分别启动
./start-backend.sh &    # 后端后台运行
./start-frontend.sh     # 前端前台运行

# 或使用一键启动（会同时启动前后端）
# 注意：此脚本需要Maven，如果Maven有网络问题，请使用上面的快速启动方式
./start-all.sh
```

### 方式三：手动编译后启动

如果需要重新编译：

```bash
# 后端编译
find src/main/java -name "*.java" > /tmp/sources.txt
javac -cp "lib/*" -d target/classes @/tmp/sources.txt

# 启动后端
java -cp "target/classes:lib/*" app.web.SimpleServer &

# 前端安装依赖（如果需要）
cd frontend
npm install

# 启动前端
npm run dev
```

## 停止服务

```bash
# 停止后端
pkill -f "app.web.SimpleServer"

# 停止前端
# 在运行npm run dev的终端按 Ctrl+C
# 或者
pkill -f "vite"
```

## 查看服务状态

```bash
# 查看运行的服务
lsof -i :8080  # 后端
lsof -i :5173  # 前端

# 查看后端日志
tail -f backend.log

# 查看进程
ps aux | grep -E "(SimpleServer|vite)" | grep -v grep
```

## 常用API测试

```bash
# 健康检查
curl http://localhost:8080/health

# 分类
curl http://localhost:8080/api/categories

# 搜索
curl "http://localhost:8080/api/search?query=音樂&city=台北"

# 建议
curl "http://localhost:8080/api/suggestions?query=音"

# 测试CORS（从前端）
curl http://localhost:5173/api/categories
```

## 文件结构

```
DS_Project/
├── lib/                          # 手动下载的依赖
│   ├── gson-2.10.1.jar
│   └── jsoup-1.17.2.jar
├── target/classes/               # 编译后的class文件
├── frontend/                     # 前端项目
│   └── node_modules/            # 前端依赖（已重新安装）
├── backend.log                   # 后端运行日志
├── START_GUIDE.md               # 详细启动指南
├── CONNECTION_FIXED.md          # 本文件
├── start-backend.sh             # 后端启动脚本
├── start-frontend.sh            # 前端启动脚本
└── start-all.sh                 # 一键启动脚本
```

## 下次启动注意事项

1. **后端**: 由于依赖已下载且代码已编译，下次可以直接运行：
   ```bash
   java -cp "target/classes:lib/*" app.web.SimpleServer
   ```

2. **前端**: 依赖已安装，直接运行：
   ```bash
   cd frontend && npm run dev
   ```

3. **如果修改了代码**:
   - Java代码: 重新编译 (使用javac命令)
   - React代码: Vite会自动热重载，无需重启

## 常见问题

### Q: 端口已被占用怎么办？
```bash
# 找到占用进程
lsof -i :8080  # 或 :5173

# 停止进程
kill -9 <PID>
```

### Q: 后端修改代码后如何重新编译？
```bash
# 重新编译所有文件
find src/main/java -name "*.java" > /tmp/sources.txt
javac -cp "lib/*" -d target/classes @/tmp/sources.txt

# 重启后端
pkill -f SimpleServer
java -cp "target/classes:lib/*" app.web.SimpleServer &
```

### Q: 如果Maven网络恢复了？
可以使用Maven正常编译和运行：
```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="app.web.SimpleServer"
```

---

**总结**: 前后端已成功连接，所有服务正常运行！🎉
