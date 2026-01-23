FROM eclipse-temurin:21-jdk-jammy

COPY ./target/test-spring-cloud-gateway-1.0.0-SNAPSHOT.jar /app/app.jar

ENV TZ=Asia/Shanghai

WORKDIR /app

EXPOSE 8080

# 加 netty 配置，是因为 MVP 容器环境不支持 netty 默认的 epoll 域名解析系统调用的协议
ENTRYPOINT ["java","-Dio.netty.transport.noNative=true","-jar","app.jar"]
