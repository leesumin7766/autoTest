# syntax=docker/dockerfile:1.7
# 운영용 Java 서비스 이미지. 호스트에 JDK·Gradle이 없어도 이미지 안에서 빌드한다.
# 사용: docker-compose.prod.yml 의 build.args.SERVICE (예: test-management-service)
FROM eclipse-temurin:21-jdk AS build
ARG SERVICE
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY common-lib common-lib
COPY api-gateway api-gateway
COPY auth-service auth-service
COPY report-service report-service
COPY test-management-service test-management-service
COPY test-execution-service test-execution-service
# test-management-service 가 리소스로 포함하는 메뉴트리 공통 규칙
COPY frontend/public/downloads/menu-tree-common-rules.md frontend/public/downloads/menu-tree-common-rules.md
RUN --mount=type=cache,target=/root/.gradle \
    test -n "$SERVICE" \
    && sed -i 's/\r$//' gradlew && chmod +x gradlew \
    && ./gradlew ":${SERVICE}:bootJar" -x test --no-daemon \
    && find "${SERVICE}/build/libs" -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app.jar \;

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
# 컨테이너 mem_limit 기준으로 힙을 잡는다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "app.jar"]
