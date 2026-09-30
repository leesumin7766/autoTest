FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY common-lib common-lib
COPY api-gateway api-gateway
COPY auth-service auth-service
COPY report-service report-service
COPY test-management-service test-management-service
COPY test-execution-service test-execution-service
RUN chmod +x gradlew
RUN ./gradlew :report-service:bootJar -x test --no-daemon
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/report-service/build/libs/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
