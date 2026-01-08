# 多階段構建 - 第一階段：編譯
FROM eclipse-temurin:17-jdk-alpine AS builder

# 設定工作目錄
WORKDIR /build

# 安裝 wget（用於下載依賴）
RUN apk add --no-cache wget

# 下載依賴（不依賴本地 lib 目錄）
RUN mkdir -p lib && \
    wget -q -O lib/gson-2.10.1.jar \
    https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar && \
    wget -q -O lib/jsoup-1.17.2.jar \
    https://repo1.maven.org/maven2/org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar

# 複製源代碼
COPY src ./src

# 手動編譯（不依賴 Maven）
RUN mkdir -p target/classes && \
    find src/main/java -name "*.java" > sources.txt && \
    javac -cp "lib/*" -d target/classes @sources.txt && \
    rm sources.txt

# 多階段構建 - 第二階段：運行
FROM eclipse-temurin:17-jre-alpine

# 設定工作目錄
WORKDIR /app

# 安裝 wget（用於健康檢查）
RUN apk add --no-cache wget

# 複製編譯好的 class 文件和依賴
COPY --from=builder /build/target/classes ./target/classes
COPY --from=builder /build/lib ./lib

# 建立非 root 使用者
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# 暴露連接埠
EXPOSE 8080

# 健康檢查
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# 啟動應用
CMD ["java", "-cp", "target/classes:lib/*", "app.web.SimpleServer"]
