# EventFinder 部署文檔

本文檔提供詳細的生產環境部署指南。

## 📋 前置需求

### 硬體需求
- **CPU**: 2核心以上
- **記憶體**: 2GB以上
- **硬碟**: 10GB可用空間

### 軟體需求
- Docker 20.10+
- Docker Compose 1.29+
- 或者：Java 17 + Node.js 18 + Nginx

## 🚀 部署選項

### 選項 1: Docker Compose（推薦）

#### 1. 準備環境

\`\`\`bash
# 安裝 Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# 安裝 Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
\`\`\`

#### 2. 克隆並部署

\`\`\`bash
git clone https://github.com/Fengjui1226/DS_Project.git
cd DS_Project
./deploy.sh
\`\`\`

#### 3. 驗證部署

\`\`\`bash
# 檢查服務狀態
docker-compose ps

# 測試後端
curl http://localhost:8080/health

# 測試前端
curl http://localhost

# 查看日誌
docker-compose logs -f
\`\`\`

### 選項 2: 手動部署

#### 後端部署

\`\`\`bash
# 1. 編譯
./start-backend.sh

# 2. 以服務方式運行
sudo tee /etc/systemd/system/eventfinder-backend.service << EOF
[Unit]
Description=EventFinder Backend
After=network.target

[Service]
Type=simple
User=eventfinder
WorkingDirectory=/opt/eventfinder
ExecStart=/usr/bin/java -cp target/classes:lib/* -Xms256m -Xmx512m app.web.SimpleServer
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl enable eventfinder-backend
sudo systemctl start eventfinder-backend
\`\`\`

#### 前端部署

\`\`\`bash
# 1. 構建
cd frontend
npm install
npm run build

# 2. 配置 Nginx
sudo cp dist/* /var/www/eventfinder/
sudo cp ../nginx.conf /etc/nginx/sites-available/eventfinder
sudo ln -s /etc/nginx/sites-available/eventfinder /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
\`\`\`

## 🌐 域名配置

### 1. DNS 設定

將域名 A 記錄指向服務器 IP：
\`\`\`
eventfinder.com.tw  A  123.456.789.0
\`\`\`

### 2. SSL 證書（Let's Encrypt）

\`\`\`bash
# 安裝 Certbot
sudo apt-get install certbot python3-certbot-nginx

# 獲取證書
sudo certbot --nginx -d eventfinder.com.tw

# 自動續期
sudo certbot renew --dry-run
\`\`\`

### 3. 更新 Nginx 配置

修改 \`nginx.conf\` 中的 \`server_name\`:
\`\`\`nginx
server {
    listen 443 ssl http2;
    server_name eventfinder.com.tw;
    
    ssl_certificate /etc/letsencrypt/live/eventfinder.com.tw/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/eventfinder.com.tw/privkey.pem;
    
    # ... 其他配置
}
\`\`\`

## 📊 監控和日誌

### Docker 日誌

\`\`\`bash
# 實時查看所有日誌
docker-compose logs -f

# 查看特定服務
docker-compose logs -f backend
docker-compose logs -f frontend

# 查看最近100行
docker-compose logs --tail=100
\`\`\`

### 應用日誌

後端日誌位置：
- Docker: \`docker exec eventfinder-backend cat /app/logs/app.log\`
- 手動部署: \`/opt/eventfinder/logs/app.log\`

### 性能監控

使用 Prometheus + Grafana:

\`\`\`yaml
# 添加到 docker-compose.yml
prometheus:
  image: prom/prometheus
  volumes:
    - ./prometheus.yml:/etc/prometheus/prometheus.yml
  ports:
    - "9090:9090"

grafana:
  image: grafana/grafana
  ports:
    - "3000:3000"
  depends_on:
    - prometheus
\`\`\`

## 🔧 維護操作

### 更新應用

\`\`\`bash
# 拉取最新代碼
git pull origin main

# 重新部署
./deploy.sh restart
\`\`\`

### 備份數據

\`\`\`bash
# 備份配置和日誌
tar -czf backup-$(date +%Y%m%d).tar.gz \
    config/ logs/ docker-compose.yml nginx.conf

# 上傳到備份服務器
scp backup-*.tar.gz user@backup-server:/backups/
\`\`\`

### 擴容

水平擴展後端：

\`\`\`yaml
# docker-compose.yml
backend:
  deploy:
    replicas: 3  # 運行3個實例
    
  # 或使用外部負載均衡器
\`\`\`

## 🔒 安全加固

### 1. 防火牆配置

\`\`\`bash
# UFW
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable

# iptables
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT
\`\`\`

### 2. 速率限制

Nginx 配置：
\`\`\`nginx
http {
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
    
    server {
        location /api/ {
            limit_req zone=api burst=20;
        }
    }
}
\`\`\`

### 3. CORS 配置

修改 \`config.properties\`:
\`\`\`properties
cors.origin=https://eventfinder.com.tw
\`\`\`

## 📈 性能優化

### 1. Nginx 緩存

\`\`\`nginx
proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=api_cache:10m max_size=1g;

location /api/categories {
    proxy_cache api_cache;
    proxy_cache_valid 200 1h;
}
\`\`\`

### 2. Java JVM 調優

\`\`\`bash
java -cp target/classes:lib/* \
  -Xms512m -Xmx1g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:ParallelGCThreads=4 \
  app.web.SimpleServer
\`\`\`

### 3. CDN 配置

使用 Cloudflare 或其他 CDN:
- 緩存靜態資源
- 啟用 Brotli 壓縮
- 設置適當的緩存規則

## 🆘 故障排除

### 服務無法啟動

\`\`\`bash
# 檢查端口占用
sudo lsof -i :8080
sudo lsof -i :80

# 檢查 Docker 狀態
docker-compose ps
docker-compose logs

# 檢查磁碟空間
df -h
\`\`\`

### 性能問題

\`\`\`bash
# 檢查資源使用
docker stats

# 檢查系統負載
top
htop

# 檢查網絡連接
netstat -tuln
\`\`\`

### 數據庫連接問題

檢查配置文件和網絡連接。

## 📞 支持

遇到問題？
- 查看 [FAQ](FAQ.md)
- 提交 [Issue](https://github.com/Fengjui1226/DS_Project/issues)
- 聯繫支持團隊

---

**更新日期**: 2026-01-07
