# 🎪 EventFinder - 全台活動搜尋引擎

> 一個專為台灣設計的活動搜尋引擎，幫助使用者快速找到展覽、市集、演唱會等各類活動資訊。

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.java.net/)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://reactjs.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## 專案簡介

EventFinder 是一個結合 **Google Custom Search API** 與**自定義排名演算法**的活動搜尋引擎。系統會自動爬取、分析網頁內容，並根據活動日期、地點相關性、內容品質等多種因素進行智慧排名，讓使用者能快速找到最相關且尚未過期的活動。

### 核心特色

- **智慧搜尋**：自動優化查詢關鍵字，提升搜尋精準度
- **日期過濾**：自動辨識活動日期，過濾已結束的活動
- **城市定位**：支援 GPS 定位，優先顯示附近活動
- **TF-IDF 排名**：結合文字相關度與多重因素的排名演算法
- **多語系支援**：支援中文、英文、日文、韓文介面
- **效能優化**：平行爬蟲、快取機制、限流保護

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
├── src/
│   └── main/
│       └── java/
│           └── app/
│               ├── bl/                    # Business Logic
│               │   ├── SearchEngine.java      # 搜尋引擎核心
│               │   ├── RankCalculator.java    # 排名計算器
│               │   ├── TFIDFCalculator.java   # TF-IDF 演算法
│               │   ├── WebCrawler.java        # 網頁爬蟲
│               │   ├── Deduplicator.java      # 去重處理
│               │   ├── EventInfoExtractor.java # 活動資訊提取
│               │   ├── KeywordSuggester.java  # 關鍵字建議
│               │   └── GoogleConnector.java   # Google API 連接
│               │
│               ├── ds/                    # Data Structures
│               │   ├── Tree.java              # 樹狀結構
│               │   ├── PageNode.java          # 網頁節點
│               │   ├── SubPageNode.java       # 子網頁節點
│               │   └── UserProfile.java       # 使用者資料
│               │
│               └── web/                   # Web Server
│                   └── SimpleServer.java      # HTTP 伺服器
│
├── frontend/                          # React 前端
│   ├── src/
│   │   ├── App.jsx                        # 主應用程式
│   │   ├── i18n.js                        # 多語系設定
│   │   └── components/                    # UI 元件
│   │       ├── SearchBar.jsx
│   │       ├── ResultCard.jsx
│   │       ├── Sidebar.jsx
│   │       ├── Pagination.jsx
│   │       └── ...
│   ├── package.json
│   └── vite.config.js
│
├── pom.xml                            # Maven 設定
└── README.md
```

---

## 使用的資料結構

| 資料結構 | Java 類別 | 用途 | 時間複雜度 |
|---------|----------|------|-----------|
| **陣列列表** | `ArrayList` | 儲存搜尋結果、關鍵字 | 查找 O(1)、搜尋 O(n) |
| **雜湊表** | `HashMap` | API 參數解析、TF-IDF 詞頻統計、快取 | 查找 O(1) |
| **雜湊集合** | `HashSet` | URL 去重、黑名單檢查 | 查找 O(1) |
| **樹** | `Tree` (自定義) | 組織網頁階層結構 | - |
| **優先佇列** | `PriorityQueue` | 排名排序 | 插入 O(log n) |

### HashMap 應用範例

```java
// 1. API 參數解析
Map<String, String> params = new HashMap<>();
params.put("query", "台北市集");
String query = params.get("query");  // O(1)

// 2. 詞頻統計 (TF-IDF)
Map<String, Integer> wordCount = new HashMap<>();
wordCount.merge("市集", 1, Integer::sum);  // O(1)

// 3. 搜尋快取
Map<String, CacheEntry> cache = new HashMap<>();
cache.put("台北市集-台北-1", searchResult);  // O(1)
```

---

## 排名演算法

### 公式

```
最終分數 = (BaseScore + FreshnessScore + TextScore + DateScore) × Multiplier
```

### 各項分數說明

| 分數項目 | 說明 | 權重 |
|---------|------|------|
| **BaseScore** | Google 原始排名分數 | 基礎分 |
| **FreshnessScore** | 活動日期新鮮度（越近越高） | +0~30 |
| **TextScore** | TF-IDF 文字相關度 | +0~20 |
| **DateScore** | 有明確日期加分 | +5 |
| **CityMultiplier** | 城市匹配加成 | ×1.2 |
| **AuthorityMultiplier** | 權威網站加成 | ×1.1 |

### 過期處理

```java
if (eventDate.isBefore(today)) {
    score = 0;  // 過期活動直接 0 分
}
```

---

## 📅 日期解析功能

系統支援多種日期格式的自動解析：

| 格式類型 | 範例 | 解析結果 |
|---------|------|---------|
| 完整西元 | `2025/12/25`、`2025-12-25` | 2025-12-25 |
| 中文格式 | `2025年12月25日` | 2025-12-25 |
| 無年份 | `12月25日`、`12/25` | 自動補今年 |
| 民國年 | `114年12月25日` | 2025-12-25 |
| 相對日期 | `今天`、`明天`、`這週末` | 動態計算 |

### 實作方式

```java
// 使用正則表達式匹配
Pattern p = Pattern.compile("(20[2-9]\\d)[/.\\-年](0?[1-9]|1[0-2])[/.\\-月](0?[1-9]|[12]\\d|3[01])");

// 民國年轉換
int adYear = rocYear + 1911;  // 114 + 1911 = 2025
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
git clone https://github.com/your-username/eventfinder.git
cd eventfinder
```

#### 2. 設定 Google API Key

在 `GoogleConnector.java` 中設定：

```java
private static final String API_KEY = "你的 API Key";
private static final String CX = "你的 Search Engine ID";
```

#### 3. 啟動後端

```bash
# 編譯並啟動 Java 後端
mvn clean compile exec:java -Dexec.mainClass="app.web.SimpleServer"

# 看到以下訊息表示成功：
# ╔═══════════════════════════════════════════════════╗
# ║     🎪 EventFinder API Server                     ║
# ║  Status:  ✅ Running                              ║
# ║  Port:    8080                                    ║
# ╚═══════════════════════════════════════════════════╝
```

#### 4. 啟動前端

```bash
# 開啟新的終端機視窗
cd frontend
npm install
npm run dev

# 看到以下訊息表示成功：
# VITE v6.x.x  ready in xxx ms
# ➜  Local:   http://localhost:3000/
```

#### 5. 開啟瀏覽器

```
http://localhost:3000
```

---

## 📡 API 文件

### 搜尋 API

```http
GET /api/search?query={關鍵字}&city={城市}&page={頁碼}
```

**參數：**
| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| query | string | ✅ | 搜尋關鍵字（至少 2 字） |
| city | string | ❌ | 城市篩選（預設：台北） |
| page | number | ❌ | 頁碼（預設：1） |

**回應範例：**

```json
{
  "success": true,
  "query": "台北市集",
  "city": "台北",
  "page": 1,
  "totalCount": 25,
  "totalPages": 3,
  "responseTime": 2500,
  "results": [
    {
      "rank": 1,
      "title": "2025 台北聖誕市集",
      "url": "https://example.com/christmas",
      "domain": "example.com",
      "score": 87.5,
      "city": "台北",
      "eventDate": "2025-12-25",
      "subPageCount": 3
    }
  ]
}
```

### 其他 API

| 端點 | 說明 |
|------|------|
| `GET /api/suggestions?query={q}` | 搜尋建議（輸入時自動完成） |
| `GET /api/related?query={q}&city={c}` | 推薦搜尋（其他人也搜尋） |
| `GET /api/subpages?domain={d}` | 子網頁列表 |
| `GET /api/categories` | 活動分類列表 |
| `GET /api/health` | 健康檢查 |

---

## 效能優化

### 後端優化

| 機制 | 說明 |
|------|------|
| **平行爬蟲** | 同時爬取最多 15 個網頁 |
| **快取** | 搜尋結果快取 5 分鐘 |
| **限流** | 10 秒內最多 10 次請求 |
| **超時控制** | 爬蟲超時 20 秒 |

### 前端優化

| 機制 | 說明 |
|------|------|
| **防抖動** | 搜尋建議延遲 300ms |
| **快取** | 相同搜尋 5 分鐘內不重複請求 |
| **localStorage** | 搜尋歷史、收藏列表本地儲存 |

---

## 多語系支援

系統支援以下語言：

- 🇹🇼 繁體中文 (預設)
- 🇺🇸 English
- 🇯🇵 日本語
- 🇰🇷 한국어

語言設定儲存於 `localStorage`，切換後自動套用。

---

## 📸 畫面截圖

### 首頁搜尋
![首頁](docs/screenshots/home.png)

### 搜尋結果
![搜尋結果](docs/screenshots/results.png)

### 推薦搜尋
![推薦搜尋](docs/screenshots/related.png)

---

## 測試

```bash
# 執行單元測試
mvn test

# 測試 API
curl "http://localhost:8080/api/search?query=台北市集&city=台北"
curl "http://localhost:8080/api/health"
```
---

## 👥 開發團隊

- **劉豐睿** - 國立政治大學 資訊管理學系
- **鄭子誼** - 國立政治大學 地政學系（雙主修資訊管理學系） 

---

## 授權

本專案採用 MIT 授權條款 - 詳見 [LICENSE](LICENSE) 檔案。

---
本專案為114-1學期資料結構期末專案
