FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY test-management-service/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
