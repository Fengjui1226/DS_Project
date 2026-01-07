# EventFinder - 生產環境 Dockerfile
FROM eclipse-temurin:17-jre-alpine

# 設定工作目錄
WORKDIR /app

# 建立非root使用者
RUN addgroup -g 1001 -S appuser && adduser -u 1001 -S appuser -G appuser

# 複製依賴檔案
COPY lib/*.jar /app/lib/

# 複製編譯好的類別檔案
COPY target/classes /app/classes

# 複製配置檔案
COPY src/main/resources/*.properties /app/config/

# 設定權限
RUN chown -R appuser:appuser /app && \
    mkdir -p /app/logs && \
    chown -R appuser:appuser /app/logs

# 切換到非root使用者
USER appuser

# 暴露連接埠
EXPOSE 8080

# 健康檢查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# 啟動指令
CMD ["java", "-cp", "/app/classes:/app/lib/*", \
     "-Xms256m", "-Xmx512m", \
     "-XX:+UseG1GC", \
     "-XX:MaxGCPauseMillis=200", \
     "-Dconfig.file=/app/config/config.properties", \
     "app.web.SimpleServer"]
