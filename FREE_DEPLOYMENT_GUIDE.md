# 🌐 EventFinder 免費部署指南

本指南將幫助你將 EventFinder 部署到免費雲端平台，讓任何人都能通過網址訪問！

## 📋 部署架構

- **前端**: Vercel（免費，自動HTTPS）
- **後端**: Render.com（免費層）

## 🚀 部署步驟

### 第一步：部署後端到 Render

#### 1. 註冊 Render 帳號

前往 https://render.com 註冊免費帳號（可用 GitHub 登入）

#### 2. 連接 GitHub 倉庫

1. 點擊 "New +" → "Web Service"
2. 連接你的 GitHub 帳號
3. 選擇 `DS_Project` 倉庫

#### 3. 配置後端服務

填寫以下配置：

| 設置項 | 值 |
|-------|-----|
| **Name** | `eventfinder-backend` |
| **Region** | Singapore（新加坡，速度最快）|
| **Branch** | `main` 或你的主分支 |
| **Runtime** | Docker |
| **Plan** | Free |

高級設置（Advanced）：
- **Health Check Path**: `/health`
- **Environment Variables**:
  ```
  PORT = 8080
  JAVA_OPTS = -Xms256m -Xmx512m
  ```

#### 4. 部署

1. 點擊 "Create Web Service"
2. 等待 5-10 分鐘構建完成
3. 部署成功後，你會獲得一個 URL，例如：
   ```
   https://eventfinder-backend.onrender.com
   ```
4. 測試後端：訪問 `https://eventfinder-backend.onrender.com/health`

### 第二步：部署前端到 Vercel

#### 1. 註冊 Vercel 帳號

前往 https://vercel.com 註冊免費帳號（可用 GitHub 登入）

#### 2. 導入專案

1. 點擊 "Add New..." → "Project"
2. 選擇你的 `DS_Project` 倉庫
3. 點擊 "Import"

#### 3. 配置前端

**Framework Preset**: Vite

**Root Directory**: 點擊 "Edit" → 選擇 `frontend`

**Build Command**: 
```bash
npm run build
```

**Output Directory**:
```
dist
```

**Environment Variables**（重要！）:
添加環境變量：
```
VITE_API_BASE_URL = https://eventfinder-backend.onrender.com
```
（把這個替換成你第一步獲得的 Render 後端 URL）

#### 4. 部署

1. 點擊 "Deploy"
2. 等待 2-3 分鐘構建完成
3. 部署成功後，你會獲得一個 URL，例如：
   ```
   https://eventfinder.vercel.app
   ```

### 第三步：測試網站

訪問你的 Vercel URL（例如 `https://eventfinder.vercel.app`）

測試功能：
- ✅ 搜尋活動
- ✅ 切換語言
- ✅ 查看分類
- ✅ 收藏功能

## 🔧 後續配置（可選）

### 綁定自定義域名

#### Vercel 綁定域名
1. 在 Vercel 專案設置中，點擊 "Domains"
2. 添加你的域名（例如 `eventfinder.com`）
3. 按照提示配置 DNS 記錄

#### Render 綁定域名
1. 在 Render 服務設置中，點擊 "Custom Domain"
2. 添加你的子域名（例如 `api.eventfinder.com`）
3. 更新 Vercel 環境變量中的 `VITE_API_BASE_URL`

### 更新代碼後自動部署

兩個平台都支持自動部署：
- 推送代碼到 GitHub → 自動觸發部署
- 前端更新：推送到 `main` 分支
- 後端更新：推送到 `main` 分支

## ⚠️ 免費層限制

### Render（後端）
- ✅ 免費 750 小時/月
- ⚠️ 15分鐘無活動會進入睡眠（首次訪問需要30秒喚醒）
- ⚠️ 512 MB 記憶體
- ⚠️ 每月 100 GB 流量

**解決睡眠問題**：
使用免費監控服務（如 UptimeRobot）每 10 分鐘 ping 一次你的後端

### Vercel（前端）
- ✅ 100 GB 流量/月
- ✅ 無睡眠問題
- ✅ 自動 HTTPS
- ✅ 全球 CDN

## 📊 監控和維護

### 查看日誌

**Render 後端日誌**:
1. 登入 Render
2. 選擇你的服務
3. 點擊 "Logs" 標籤

**Vercel 前端日誌**:
1. 登入 Vercel
2. 選擇你的專案
3. 點擊部署記錄查看日誌

### 性能監控

**設置 UptimeRobot（免費）**:
1. 前往 https://uptimerobot.com
2. 添加監控：
   - URL: `https://eventfinder-backend.onrender.com/health`
   - 檢查間隔: 10 分鐘
   - 類型: HTTP(s)

這樣可以：
- ✅ 防止後端睡眠
- ✅ 獲得服務中斷通知
- ✅ 查看運行時間統計

## 🐛 常見問題

### Q: 前端無法連接後端？

**檢查步驟**：
1. 確認 Vercel 環境變量 `VITE_API_BASE_URL` 設置正確
2. 訪問後端健康檢查：`https://你的後端URL/health`
3. 檢查瀏覽器控制台的網路錯誤
4. 重新部署前端（Vercel Dashboard → Deployments → Redeploy）

### Q: 後端響應很慢？

原因：Render 免費層會在 15 分鐘無活動後睡眠

**解決方案**：
- 使用 UptimeRobot 定期 ping（推薦）
- 升級到 Render 付費版（$7/月）

### Q: CORS 錯誤？

確保後端 CORS 配置允許你的前端域名：

編輯 `src/main/resources/config.properties`:
```properties
cors.origin=https://eventfinder.vercel.app
```

然後重新部署後端。

### Q: 如何查看後端錯誤？

1. 登入 Render
2. 選擇 `eventfinder-backend` 服務
3. 點擊 "Logs" 查看即時日誌

### Q: 構建失敗？

**前端構建失敗**：
- 檢查 `frontend/package.json` 依賴是否正確
- 查看 Vercel 構建日誌

**後端構建失敗**：
- 檢查 Dockerfile 是否正確
- 查看 Render 構建日誌
- 確保 `lib/` 目錄包含所需的 JAR 文件

## 🎯 快速命令參考

### 本地測試生產構建

```bash
# 測試前端
cd frontend
npm run build
npm run preview

# 測試後端
./deploy.sh
```

### 更新環境變量

**Vercel**：
```bash
# 安裝 Vercel CLI
npm i -g vercel

# 設置環境變量
vercel env add VITE_API_BASE_URL production
```

**Render**：
通過 Web Dashboard 更新環境變量後，需要手動重新部署。

## 🎉 完成！

現在你的 EventFinder 已經上線了！

分享你的網址給朋友：
```
https://你的專案名.vercel.app
```

---

**需要幫助？**
- Vercel 文檔: https://vercel.com/docs
- Render 文檔: https://render.com/docs
- 提交 Issue: https://github.com/Fengjui1226/DS_Project/issues
