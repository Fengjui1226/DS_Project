# 🎪 EventFinder - 台灣活動搜尋引擎

[![授權: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![React](https://img.shields.io/badge/React-18.2-blue.svg)](https://reactjs.org/)

探索台灣精彩活動，音樂會、展覽、市集、戶外活動一網打盡！

## 🌐 線上演示

**前端網站**：https://ds-project-eight.vercel.app/

**後端 API**：https://ds-project-o9lk.onrender.com

## ✨ 功能特色

- 🔍 **智能搜尋** - 整合 Google Custom Search API 的相關性排序
- 🏙️ **城市篩選** - 支援全台 17 個主要城市
- 🎨 **12 大分類** - 市集、展覽、演唱會、音樂節、講座、工作坊等
- 🌏 **多語言支援** - 繁中、英文、日文、韓文
- ⚡ **即時過濾** - 自動排除過期活動
- 📱 **響應式設計** - 完美支援桌面和移動設備
- ❤️ **收藏功能** - 保存喜愛的活動
- 🎯 **智能排序** - 基於地理位置和相關性的排序
- 📊 **TF-IDF 分析** - 精準的關鍵字匹配
- 🕷️ **網頁爬取** - 自動提取活動詳細資訊

## 🚀 快速開始

### 前置需求

- Java 17 或更高版本
- Node.js 16 或更高版本
- Maven 3.6 或更高版本
- Google Custom Search API 金鑰

### 本地開發

#### 1. 克隆專案

```bash
git clone https://github.com/Fengjui1226/DS_Project.git
cd DS_Project
```

#### 2. 配置環境變數

創建 `src/main/resources/application.properties` 並填入：

```properties
google.cse.enabled=true
google.cse.apiKey=你的_GOOGLE_API_KEY
google.cse.cx=你的_搜尋引擎_ID
server.port=8080
```

#### 3. 啟動後端

```bash
# 下載依賴（如果沒有 lib 目錄）
mkdir -p lib
wget -O lib/gson-2.10.1.jar https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
wget -O lib/jsoup-1.17.2.jar https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar

# 啟動後端服務
mvn -q -DskipTests exec:java -Dexec.mainClass="app.web.SimpleServer"
```

後端將運行於 `http://localhost:8080`

#### 4. 啟動前端

```bash
cd frontend
npm install
npm run dev
```

前端將運行於 `http://localhost:5173`

## 📖 API 文檔

### 搜尋活動

```http
GET /api/search?query=音樂&city=台北&page=1
```

**參數：**
- `query` (必填): 搜尋關鍵字
- `city` (選填): 城市名稱
- `page` (選填): 頁碼，預設為 1

**回應範例：**

```json
{
  "results": [
    {
      "title": "台北音樂祭 2026",
      "url": "https://example.com/event",
      "city": "台北",
      "eventDate": "2026-03-15",
      "score": 85.5
    }
  ],
  "totalCount": 20,
  "totalPages": 2
}
```

### 健康檢查

```http
GET /health
```

## 🏗️ 技術架構

### 後端

- **語言**: Java 17
- **HTTP 伺服器**: `com.sun.net.httpserver.HttpServer`
- **JSON 處理**: Gson 2.10.1
- **網頁爬取**: Jsoup 1.17.2
- **搜尋 API**: Google Custom Search API
- **部署**: Render.com (Docker)

### 前端

- **框架**: React 18.2
- **建置工具**: Vite 4
- **樣式**: Tailwind CSS (內聯樣式)
- **部署**: Vercel

### 核心算法

- **TF-IDF** - 關鍵字相關性分析
- **日期解析** - 智能識別活動日期
- **地理位置優化** - 基於城市的排序
- **去重算法** - URL 和內容去重
- **網頁爬取** - 並行爬取和資訊提取

## 📂 專案結構

```
DS_Project/
├── src/main/java/app/
│   ├── web/                 # HTTP 伺服器
│   │   ├── SimpleServer.java
│   │   └── handlers/        # API 處理器
│   ├── bl/                  # 業務邏輯
│   │   ├── SearchEngine.java
│   │   ├── RankCalculator.java
│   │   ├── DateParser.java
│   │   └── WebCrawler.java
│   └── da/                  # 數據存取
│       ├── GoogleConnector.java
│       └── Config.java
├── frontend/
│   ├── src/
│   │   ├── App.jsx
│   │   └── components/
│   ├── package.json
│   └── vite.config.js
├── lib/                     # Java 依賴庫
├── Dockerfile              # Docker 配置
└── README.md
```

## 🌐 部署指南

### 部署到 Render（後端）

1. Fork 此專案到您的 GitHub
2. 在 [Render.com](https://render.com) 創建新的 Web Service
3. 連接您的 GitHub 儲存庫
4. 配置環境變數：
   - `GOOGLE_CSE_API_KEY`: 您的 Google API Key
   - `GOOGLE_CSE_CX`: 您的搜尋引擎 ID
5. 選擇 Docker 部署方式
6. 點擊 Deploy

### 部署到 Vercel（前端）

1. Fork 此專案到您的 GitHub
2. 在 [Vercel.com](https://vercel.com) 導入專案
3. 設定根目錄為 `frontend`
4. 添加環境變數：
   - `VITE_API_BASE_URL`: 您的 Render 後端 URL
5. 點擊 Deploy

## ⚙️ 環境變數配置

### 後端環境變數

| 變數名稱 | 說明 | 必填 |
|---------|------|------|
| `GOOGLE_CSE_API_KEY` | Google Custom Search API 金鑰 | 是 |
| `GOOGLE_CSE_CX` | Google 搜尋引擎 ID | 是 |
| `PORT` | 伺服器端口（預設: 8080） | 否 |

### 前端環境變數

| 變數名稱 | 說明 | 必填 |
|---------|------|------|
| `VITE_API_BASE_URL` | 後端 API 基礎 URL | 否 |

## 🔑 獲取 Google Custom Search API

1. 前往 [Google Cloud Console](https://console.cloud.google.com/)
2. 創建新專案或選擇現有專案
3. 啟用 **Custom Search API**
4. 創建 API 金鑰（憑證 → API 金鑰）
5. 限制金鑰只能使用 Custom Search API
6. 前往 [Programmable Search Engine](https://programmablesearchengine.google.com/)
7. 創建新的搜尋引擎，選擇「搜尋整個網路」
8. 複製搜尋引擎 ID (cx)

## 🔒 安全性

- ✅ CORS 配置
- ✅ 環境變數管理
- ✅ 輸入驗證
- ✅ XSS 防護
- ✅ API 金鑰保護

## 📊 性能優化

- **爬取優化**: 並行爬取最多 5 個網頁
- **超時控制**: 總請求時間控制在 30 秒內（Render 限制）
- **快取機制**: 搜尋結果快取
- **去重算法**: 高效的 URL 和內容去重

## 🐛 常見問題

### Q: 搜尋結果為空？
A: 請確認 Google API 金鑰和搜尋引擎 ID 配置正確。

### Q: 部署後顯示「伺服器忙碌」？
A: 檢查 Render 環境變數是否正確設定，並查看後端日誌。

### Q: 前端無法連接後端？
A: 確認 Vercel 的 `VITE_API_BASE_URL` 環境變數指向正確的 Render URL。

## 🤝 貢獻

歡迎提交問題和拉取請求！

## 📄 授權

本專案採用 MIT 授權條款

## 🙏 致謝

- Google Custom Search API
- Render.com 免費託管
- Vercel 免費前端託管
- 所有開源貢獻者

---

用 ❤️ 為台灣打造 🇹🇼
