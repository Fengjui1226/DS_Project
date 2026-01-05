# 🎪 EventFinder - 全台活動搜尋引擎

> 一個專為台灣設計的活動搜尋引擎，幫助使用者快速找到展覽、市集、演唱會等各類活動資訊。

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.java.net/)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://reactjs.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## 專案簡介

EventFinder 是一個結合 **Google Custom Search API** 與**自定義排名演算法**的活動搜尋引擎。系統會自動爬取、分析網頁內容，並根據活動日期、地點相關性、內容品質等多種因素進行智慧排名，讓使用者能快速找到最相關且尚未過期的活動。

### 核心特色

- 🔍 **智慧搜尋**：自動優化查詢關鍵字，提升搜尋精準度
- 📅 **日期過濾**：自動辨識活動日期，過濾已結束的活動
- 📍 **城市定位**：支援 GPS 定位，優先顯示附近活動
- 📊 **TF-IDF 排名**：結合文字相關度與多重因素的排名演算法
- 🌐 **多語系支援**：支援中文、英文、日文、韓文介面
- ⚡ **效能優化**：平行爬蟲、快取機制、限流保護

---

## 系統架構
```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (React)                         │
│                     localhost:3000                              │
├─────────────────────────────────────────────────────────────────┤
│  SearchBar │ ResultCard │ Sidebar │ Pagination │ i18n          │
└─────────────────────────────────────────────────────────────────┘
                              │
                        HTTP / JSON
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Backend (Java)                           │
│                     localhost:8080                              │
├─────────────────────────────────────────────────────────────────┤
│  SimpleServer (HTTP Server)                                     │
│       │                                                         │
│       ├── /api/search      → SearchEngine                       │
│       ├── /api/suggestions → KeywordSuggester                   │
│       ├── /api/subpages    → SubPageNode                        │
│       ├── /api/related     → Related Searches                   │
│       └── /api/categories  → Categories                         │
├─────────────────────────────────────────────────────────────────┤
│  Core Modules:                                                  │
│  SearchEngine │ RankCalculator │ TFIDFCalculator │ WebCrawler   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Google Custom Search API                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 專案結構
```
DS_Project/
├── src/main/java/app/
│   ├── bl/                    # Business Logic
│   │   ├── SearchEngine.java      # 搜尋引擎核心
│   │   ├── RankCalculator.java    # 排名計算器
│   │   ├── TFIDFCalculator.java   # TF-IDF 演算法
│   │   ├── WebCrawler.java        # 網頁爬蟲
│   │   ├── Deduplicator.java      # 去重處理
│   │   ├── EventInfoExtractor.java # 活動資訊提取
│   │   └── KeywordSuggester.java  # 關鍵字建議
│   ├── da/                    # Data Access
│   │   ├── GoogleConnector.java   # Google API 連接
│   │   └── Config.java            # 設定管理
│   └── web/
│       └── SimpleServer.java      # HTTP 伺服器
├── frontend/                  # React 前端
│   ├── src/
│   │   ├── App.jsx
│   │   ├── i18n.js
│   │   └── components/
│   ├── package.json
│   └── vite.config.js
├── pom.xml
├── .env.example
└── README.md
```

---

## 快速開始

### 環境需求

- Java 17+
- Node.js 18+
- Maven 3.6+
- Google Custom Search API Key

### 安裝步驟

#### 1. Clone 專案
```bash
git clone https://github.com/Fengjui1226/DS_Project.git
cd DS_Project
```

#### 2. 設定環境變數
```bash
cp .env.example .env
# 編輯 .env 填入你的 Google API Key
```

#### 3. 啟動後端
```bash
mvn clean compile exec:java -Dexec.mainClass="app.web.SimpleServer"
```

#### 4. 啟動前端
```bash
cd frontend
npm install
npm run dev
```

#### 5. 開啟瀏覽器
```
http://localhost:3000
```

---

## API 文件

### 搜尋 API
```http
GET /api/search?query={關鍵字}&city={城市}&page={頁碼}
```

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| query | string | ✅ | 搜尋關鍵字 |
| city | string | ❌ | 城市篩選（預設：台北） |
| page | number | ❌ | 頁碼（預設：1） |

### 其他 API

| 端點 | 說明 |
|------|------|
| `GET /api/suggestions?query={q}` | 搜尋建議 |
| `GET /api/related?query={q}` | 相關搜尋 |
| `GET /api/health` | 健康檢查 |

---

## 使用的資料結構

| 資料結構 | 用途 | 時間複雜度 |
|---------|------|-----------|
| `ArrayList` | 儲存搜尋結果 | O(1) 查找 |
| `HashMap` | TF-IDF 詞頻統計、快取 | O(1) 查找 |
| `HashSet` | URL 去重 | O(1) 查找 |
| `PriorityQueue` | 排名排序 | O(log n) 插入 |

---

## 開發團隊

- **劉豐睿** - 國立政治大學 資訊管理學系
- **鄭子誼** - 國立政治大學 地政學系（雙主修資訊管理學系）
- **何凱榆** - 國立政治大學 資訊管理學系
- **林宣岑** - 國立政治大學 資訊管理學系
- **許伃萱** - 國立政治大學 資訊管理學系

---

## 授權

本專案採用 MIT 授權條款 - 詳見 [LICENSE](LICENSE) 檔案。

---

*本專案為 114-1 學期資料結構期末專案*
