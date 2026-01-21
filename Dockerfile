FROM eclipse-temurin:21-jdk-jammy

COPY ./target/test-spring-cloud-gateway-1.0.0-SNAPSHOT.jar /app/app.jar

ENV TZ=Asia/Shanghai

WORKDIR /app

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
