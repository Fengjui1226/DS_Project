# 前後端連接問題診斷與解決方案

## 問題診斷結果

### 當前狀態
- ❌ **後端服務未運行** (8080連接埠未被佔用)
- ❌ **前端開發服務器未運行**
- ❌ **網路DNS解析失敗** (無法下載Maven依賴)
- ✅ 前端依賴已安裝 (node_modules存在)
- ✅ 專案配置正確

### 根本原因
1. 前後端服務都沒有啟動
2. 後端無法編譯 - Maven無法解析域名下載依賴 (DNS configuration issue)

## 架構說明

### 後端 (Java)
- **連接埠**: 8080
- **API路徑**: `/api/v1/*` 和 `/api/*` (向後兼容)
- **主類**: `app.web.SimpleServer`
- **依賴**: Gson (2.10.1), Jsoup (1.17.2)

### 前端 (React + Vite)
- **開發連接埠**: 默認 5173
- **代理配置**: `/api` -> `http://localhost:8080`
- **API調用**: `fetch('/api/search?...')` 會被代理到後端

## 解決方案

### 方案 A：修復網路並正常啟動 (推薦)

#### 1. 修復DNS配置
\`\`\`bash
# 創建或更新 /etc/resolv.conf
sudo bash -c 'echo "nameserver 8.8.8.8" > /etc/resolv.conf'
sudo bash -c 'echo "nameserver 8.8.4.4" >> /etc/resolv.conf'
\`\`\`

#### 2. 編譯並啟動後端
\`\`\`bash
# 在專案根目錄
mvn clean compile

# 啟動後端服務
mvn exec:java -Dexec.mainClass="app.web.SimpleServer"

# 或者後台運行
nohup mvn exec:java -Dexec.mainClass="app.web.SimpleServer" > backend.log 2>&1 &
\`\`\`

#### 3. 啟動前端
\`\`\`bash
cd frontend
npm run dev
\`\`\`

#### 4. 訪問應用
- 前端: http://localhost:5173
- 後端API: http://localhost:8080/health

---

### 方案 B：手動下載依賴 (網路問題時)

#### 1. 手動下載JAR文件
\`\`\`bash
mkdir -p lib
cd lib

# 下載依賴 (在有網路的機器上下載後傳輸)
# Gson 2.10.1
wget https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar

# Jsoup 1.17.2
wget https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar

cd ..
\`\`\`

#### 2. 手動編譯
\`\`\`bash
# 創建輸出目錄
mkdir -p target/classes

# 編譯Java源文件
javac -cp "lib/*" -d target/classes \\
  \$(find src/main/java -name "*.java")
\`\`\`

#### 3. 運行後端
\`\`\`bash
java -cp "target/classes:lib/*" app.web.SimpleServer
\`\`\`

#### 4. 啟動前端 (同方案A步驟3)

---

### 方案 C：使用已編譯的JAR (如果存在)

\`\`\`bash
# 如果有預編譯的JAR
java -jar eventfinder-1.0.0.jar

# 或帶依賴的fat jar
java -jar target/eventfinder-1.0.0-jar-with-dependencies.jar
\`\`\`

## 快速啟動腳本

### start-backend.sh
\`\`\`bash
#!/bin/bash
echo "正在啟動後端服務..."
cd "\$(dirname "\$0")"
mvn exec:java -Dexec.mainClass="app.web.SimpleServer"
\`\`\`

### start-frontend.sh
\`\`\`bash
#!/bin/bash
echo "正在啟動前端開發服務器..."
cd "\$(dirname "\$0")/frontend"
npm run dev
\`\`\`

### start-all.sh
\`\`\`bash
#!/bin/bash
echo "正在啟動前後端服務..."

# 啟動後端 (後台)
./start-backend.sh > backend.log 2>&1 &
BACKEND_PID=\$!
echo "後端進程ID: \$BACKEND_PID"

# 等待後端啟動
sleep 5

# 啟動前端
./start-frontend.sh
\`\`\`

## 驗證連接

### 1. 檢查後端健康狀態
\`\`\`bash
curl http://localhost:8080/health
# 預期輸出: {"status":"ok","timestamp":...}
\`\`\`

### 2. 測試API
\`\`\`bash
# 分類
curl http://localhost:8080/api/categories

# 搜尋
curl "http://localhost:8080/api/search?query=音樂&city=台北"

# 建議
curl "http://localhost:8080/api/suggestions?query=音"
\`\`\`

### 3. 檢查前端代理
- 啟動前端後訪問: http://localhost:5173
- 打開瀏覽器控制台 (F12)
- 執行搜尋，觀察Network標籤
- API請求應該是 \`/api/search\`，並被代理到 \`http://localhost:8080/api/search\`

## 常見問題

### Q: 後端啟動後立即停止？
檢查日誌中的錯誤訊息，可能是：
- 連接埠8080已被佔用: \`lsof -i :8080\`
- 配置文件缺失
- 依賴版本不匹配

### Q: 前端無法連接後端？
1. 確認後端正在運行: \`curl http://localhost:8080/health\`
2. 確認Vite代理配置正確: 檢查 \`frontend/vite.config.js\`
3. 查看瀏覽器控制台的網路錯誤
4. 檢查CORS配置

### Q: CORS錯誤？
後端已配置CORS允許所有來源 (\`cors.origin=*\`)，如果仍有問題：
- 檢查 \`src/main/resources/config.properties\`
- 確認後端ServerContext正確設置CORS headers

### Q: Maven依賴下載失敗？
- 檢查網路連接: \`curl -I https://repo.maven.apache.org\`
- 檢查DNS: \`cat /etc/resolv.conf\`
- 使用方案B手動下載依賴
- 或使用Maven鏡像配置

## 下一步

當前環境由於DNS配置問題，需要先修復網路，然後按照 **方案A** 啟動服務。

如果無法修復網路，請使用 **方案B** 手動下載依賴。
