FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY api-gateway/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
