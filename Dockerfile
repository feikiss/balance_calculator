# 运行阶段
FROM openjdk:17-slim
WORKDIR /app

# 创建非 root 用户
RUN groupadd -r spring && useradd -r -g spring spring \
    && mkdir -p /app \
    && chown -R spring:spring /app

# 复制本地构建的 jar 包
COPY target/*.jar app.jar
RUN chown spring:spring app.jar

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 设置JVM参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/var/log/heap-dump.hprof \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8"

# 创建日志目录并设置权限
RUN mkdir -p /var/log && chown -R spring:spring /var/log

# 暴露端口
EXPOSE 8080

# 切换到非 root 用户
USER spring

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"] 