# 子網頁功能說明

## 功能概述

子網頁功能允許用戶查看搜尋結果中某個網站的相關子頁面，類似於 Google 搜尋結果下方的「站內連結」。

## 工作原理

### 1. 數據來源

子網頁數據來自搜尋引擎爬取的 `PageNode` 對象：
- 每個搜尋結果（PageNode）可能包含多個子頁面（SubPageNode）
- 子頁面在搜尋時一起爬取並儲存在 Session 中

### 2. 使用流程

```
用戶搜尋 → 後端爬取結果和子頁面 → 存入 Session
        ↓
用戶點擊「查看子頁面」→ 前端調用 /api/subpages
        ↓  
後端從 Session 查找對應 URL 的 PageNode → 返回子頁面列表
```

### 3. API 調用

**端點**: `GET /api/subpages?url={網站URL}`

**前置條件**: 必須先執行搜尋（建立 Session）

**示例**:
```bash
# 1. 先搜尋（會建立 Session 並存儲 Cookie）
curl -c cookies.txt "http://localhost:8080/api/search?query=音樂&city=台北"

# 2. 查詢某個結果的子頁面（使用步驟1中返回的某個 URL）
curl -b cookies.txt "http://localhost:8080/api/subpages?url=https://example.com"
```

**成功響應**:
```json
{
  "success": true,
  "subPages": [
    {
      "url": "https://example.com/event1",
      "title": "活動標題1",
      "score": 85.3
    },
    {
      "url": "https://example.com/event2",
      "title": "活動標題2",
      "score": 78.9
    }
  ]
}
```

**錯誤響應**:
```json
{
  "success": false,
  "error": "查無相符結果（請先執行搜尋）"
}
```

## 前端實現

在 `App.jsx` 中：

```javascript
// 點擊「查看子頁面」按鈕
const handleSubpageQuery = async (url, domainLabel = '') => {
  if (!url) return;
  
  try {
    const res = await fetch(`${API_BASE}/subpages?url=${encodeURIComponent(url)}`);
    const data = await res.json();
    if (data.success) {
      setSubpageModal({ open: true, domain: domainLabel || url, data });
    }
  } catch {}
};
```

## 後端實現

在 `SubpagesHandler.java` 中：

```java
// 從 Session 獲取搜尋結果
SessionManager.Session session = ctx.sessionManager.getSession(ex);
List<PageNode> lastResults = session.lastResults;

// 查找匹配的 PageNode
for (PageNode p : lastResults) {
    if (p != null && url.equals(p.getUrl())) {
        targetResult = p;
        break;
    }
}

// 返回子頁面列表
List<SubPageNode> subs = targetResult.getSubPages();
```

## 當前狀態

✅ **功能正常** - 按設計工作

**依賴條件**:
1. 用戶必須先執行搜尋
2. 搜尋結果必須包含子頁面數據
3. Session 必須有效（默認 30 分鐘）

## 可能的問題

### 問題 1: 「查無相符結果」

**原因**: 
- 未先執行搜尋
- Session 已過期
- Cookie 未正確傳遞

**解決**:
- 確保先搜尋再查詢子頁面
- 檢查 Session 配置
- 驗證 Cookie 傳遞

### 問題 2: 子頁面列表為空

**原因**:
- 該網站沒有爬取到子頁面
- 爬蟲未正確解析子頁面

**解決**:
- 檢查 `PageNode.getSubPages()` 是否返回數據
- 優化爬蟲邏輯以更好地提取子頁面

### 問題 3: 前端無法打開子頁面彈窗

**原因**:
- API 請求失敗
- 數據格式不匹配

**解決**:
- 檢查瀏覽器控制台錯誤
- 驗證 API 響應格式
- 確認 SubpageModal 組件正常

## 測試方法

### 手動測試

1. 打開前端應用
2. 執行任意搜尋
3. 點擊某個結果的「查看相關頁面」按鈕
4. 應該看到子頁面彈窗

### API 測試

```bash
# 測試腳本
#!/bin/bash

# 1. 執行搜尋並保存 Cookie
RESULT=$(curl -c /tmp/cookies.txt -s "http://localhost:8080/api/search?query=音樂&city=台北")

# 2. 提取第一個結果的 URL
URL=$(echo $RESULT | jq -r '.results[0].url')

# 3. 查詢子頁面
curl -b /tmp/cookies.txt "http://localhost:8080/api/subpages?url=$URL" | jq

# 清理
rm /tmp/cookies.txt
```

## 改進建議

### 短期
1. 添加前端錯誤提示
2. 優化子頁面爬取邏輯
3. 增加子頁面緩存

### 長期
1. 支持直接通過 URL 查詢（不依賴 Session）
2. 異步爬取子頁面
3. 提供更豐富的子頁面信息（描述、圖片等）

---

**更新日期**: 2026-01-07  
**狀態**: 功能正常，按設計工作
