FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl services/ai-control-service -am package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/services/ai-control-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
