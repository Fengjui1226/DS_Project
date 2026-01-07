# EventFinder - Production Dockerfile
FROM eclipse-temurin:17-jre-alpine

# 设置工作目录
WORKDIR /app

# 创建非root用户
RUN addgroup -g 1001 -S appuser && adduser -u 1001 -S appuser -G appuser

# 复制依赖
COPY lib/*.jar /app/lib/

# 复制编译好的classes
COPY target/classes /app/classes

# 复制配置文件
COPY src/main/resources/*.properties /app/config/

# 设置权限
RUN chown -R appuser:appuser /app && \
    mkdir -p /app/logs && \
    chown -R appuser:appuser /app/logs

# 切换到非root用户
USER appuser

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# 启动命令
CMD ["java", "-cp", "/app/classes:/app/lib/*", \
     "-Xms256m", "-Xmx512m", \
     "-XX:+UseG1GC", \
     "-XX:MaxGCPauseMillis=200", \
     "-Dconfig.file=/app/config/config.properties", \
     "app.web.SimpleServer"]
