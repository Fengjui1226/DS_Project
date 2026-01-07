# 🎪 EventFinder - 台灣活動搜尋引擎

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![React](https://img.shields.io/badge/React-18.2-blue.svg)](https://reactjs.org/)

探索台灣精彩活動，音樂會、展覽、市集、戶外活動一網打盡！

## ✨ 功能特色

- 🔍 **智能搜尋** - AI驅動的相關性排序
- 🏙️ **城市篩選** - 支援全台17個主要城市  
- 🎨 **12大分類** - 市集、展覽、演唱會、音樂節等
- 🌏 **多語言** - 支援繁中、英文、日文、韓文
- ⚡ **即時過濾** - 自動排除過期活動
- 📱 **響應式設計** - 完美支援桌面和移動設備
- ❤️ **收藏功能** - 保存喜歡的活動
- 🎯 **定位優先** - 基於地理位置的智能排序

## 🚀 快速開始

### 使用 Docker（推薦）

\`\`\`bash
# 1. 克隆項目
git clone https://github.com/Fengjui1226/DS_Project.git
cd DS_Project

# 2. 一鍵部署
./deploy.sh

# 3. 訪問應用
open http://localhost
\`\`\`

### 手動部署

查看 [START_GUIDE.md](START_GUIDE.md) 獲取詳細說明。

## 📖 API 文檔

### 搜尋活動
\`\`\`http
GET /api/search?query=音樂&city=台北&page=1
\`\`\`

### 獲取分類
\`\`\`http
GET /api/categories
\`\`\`

### 健康檢查
\`\`\`http
GET /health
\`\`\`

完整API文檔請查看 [API.md](docs/API.md)

## 🏗️ 技術架構

- **後端**: Java 17 + HttpServer + Gson + Jsoup
- **前端**: React 18 + Vite
- **部署**: Docker + Nginx + Docker Compose

## 📂 項目結構

\`\`\`
DS_Project/
├── src/main/java/app/       # Java後端
├── frontend/                 # React前端
├── lib/                      # 依賴庫
├── Dockerfile                # Docker鏡像
├── docker-compose.yml        # 服務編排
├── deploy.sh                 # 部署腳本
└── README.md                 # 本文件
\`\`\`

## 🐳 Docker 命令

\`\`\`bash
# 構建並啟動
./deploy.sh

# 單獨命令
docker-compose build   # 構建鏡像
docker-compose up -d   # 啟動服務
docker-compose logs -f # 查看日誌
docker-compose down    # 停止服務
\`\`\`

## ⚙️ 配置

生產環境配置請查看：
- [config.properties](src/main/resources/config.properties)
- [nginx.conf](nginx.conf)
- [docker-compose.yml](docker-compose.yml)

## 🔒 安全措施

- ✅ CORS 配置
- ✅ 速率限制
- ✅ 輸入驗證
- ✅ XSS 防護
- ✅ 非 root 用戶

## 📝 文檔

- [快速開始指南](START_GUIDE.md)
- [連接問題修復](CONNECTION_FIXED.md)
- [部署文檔](docs/DEPLOYMENT.md)
- [貢獻指南](CONTRIBUTING.md)

## 📄 授權

MIT License

## 🙏 致謝

Built with ❤️ for Taiwan 🇹🇼
